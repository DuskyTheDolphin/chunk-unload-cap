package com.forgeward.unloadcap;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.function.BooleanSupplier;

/**
 * A mass chunk release no longer freezes the server. ChunkMapUnloadMixin replaces vanilla's "drain everything while more
 * than 2,000 are queued" with a time floor: each processUnloads call does at least forceMs of unload work (more while the
 * tick has time). Gameplay-neutral: the same chunks unload and save, a storm just spreads over several ticks.
 * Config: config/forgeward_unload_cap-common.toml, enabled (false = vanilla) and forceMs (1..50, default 5).
 * Server thread only. Self-report in its own log.
 */
@Mod(UnloadCap.MODID)
public final class UnloadCap {
    public static final String MODID = "forgeward_unload_cap";
    private static final Logger LOG = LogUtils.getLogger();

    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ENABLED;
    static final ModConfigSpec.IntValue FORCE_MS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED = b.comment("Spread mass chunk unloads over several ticks. false = vanilla (drain everything in one tick above 2,000 queued).")
                .define("enabled", true);
        FORCE_MS = b.comment("Milliseconds of unload work every tick gets at least, even when the tick has no time left (so a backlog still drains under lag).")
                .defineInRange("forceMs", 5, 1, 50);
        SPEC = b.build();
    }

    // read on every processUnloads call: plain fields, refreshed when the config loads or reloads
    private static boolean on = true;
    private static long forceNanos = 5_000_000L;
    private static long startNanos, calls, slowCalls, longestNanos, maxDropBacklog, maxQueueBacklog, stormTicks;
    private static String lastReported = "";

    public UnloadCap(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
        modBus.addListener(ModConfigEvent.Loading.class, e -> refresh(e.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, e -> refresh(e.getConfig()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> LOG.info("[forgeward_unload_cap] at server start: {}", summary()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> LOG.info("[forgeward_unload_cap] at server stop: {}", summary()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (e.getServer().getTickCount() % 6000 != 0) return;
            String s = summary();
            if (s.equals(lastReported)) return;
            lastReported = s;
            LOG.info("[forgeward_unload_cap] every 5 min: {}", s);
        });
    }

    private static void refresh(ModConfig config) {
        if (config.getSpec() != SPEC) return;
        on = ENABLED.get();
        forceNanos = FORCE_MS.get() * 1_000_000L;
    }

    public static boolean on() { return on; }

    public static void begin(int drop, int queue) {
        startNanos = System.nanoTime();
        calls++;
        maxDropBacklog = Math.max(maxDropBacklog, drop);
        maxQueueBacklog = Math.max(maxQueueBacklog, queue);
        if (drop > 2000 || queue > 2000) stormTicks++;
    }

    public static BooleanSupplier wrap(BooleanSupplier hasMoreTime) {
        if (!on) return hasMoreTime;
        long start = System.nanoTime();
        long force = forceNanos;
        return () -> hasMoreTime.getAsBoolean() || System.nanoTime() - start < force;
    }

    public static void end(int drop, int queue) {
        long d = System.nanoTime() - startNanos;
        if (d > longestNanos) longestNanos = d;
        if (d > 50_000_000L) slowCalls++;
        if (d > 1_000_000_000L) LOG.warn("[forgeward_unload_cap] one processUnloads call took {} ms (left: toDrop {}, unloadQueue {}). {}",
                d / 1_000_000, drop, queue, summary());
    }

    public static String summary() {
        return "chunk unload cap " + (on ? "ON (vanilla's drain-all above 2,000 replaced by a " + forceNanos / 1_000_000 + " ms floor per tick)" : "OFF (vanilla)")
                + ": processUnloads calls " + calls + ", ticks with a backlog over 2,000 " + stormTicks + ", calls over 50 ms " + slowCalls
                + ", longest " + longestNanos / 1_000_000 + " ms, largest backlog toDrop " + maxDropBacklog + " / unloadQueue " + maxQueueBacklog;
    }
}
