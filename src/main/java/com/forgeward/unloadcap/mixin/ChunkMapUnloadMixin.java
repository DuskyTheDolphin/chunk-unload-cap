package com.forgeward.unloadcap.mixin;

import com.forgeward.unloadcap.UnloadCap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.function.BooleanSupplier;

/**
 * Vanilla 1.21.1 ChunkMap.processUnloads(BooleanSupplier hasMoreTime) (javap, NeoForge 21.1.250):
 *  (1) drops from toDrop while hasMoreTime || i < 200 || toDrop.size() > 2000;
 *  (2) runs unloadQueue tasks while hasMoreTime || j-- > 0, j = max(0, unloadQueue.size() - 2000);
 *  (3) saves up to 20 chunks while hasMoreTime.
 * The "> 2000" overrides ignore the tick budget: a mass release of chunks is drained in ONE tick (seen as a 30 s server
 * freeze). Here (enabled) the count overrides go (2000 -> never, 200 -> 0) and hasMoreTime becomes
 * "hasMoreTime OR less than forceMs spent in this call": every tick gets at least forceMs of unload work, so a backlog
 * still drains during sustained lag, and a storm costs forceMs per tick instead of one long tick. enabled=false = vanilla.
 */
@Mixin(value = ChunkMap.class, remap = false)
public abstract class ChunkMapUnloadMixin {
    @Shadow @Final private LongSet toDrop;
    @Shadow @Final private Queue<Runnable> unloadQueue;

    @Inject(method = "processUnloads", at = @At("HEAD"), remap = false)
    private void forgeward$unloadStart(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        UnloadCap.begin(toDrop.size(), unloadQueue.size());
    }

    @ModifyVariable(method = "processUnloads", at = @At("HEAD"), argsOnly = true, remap = false)
    private BooleanSupplier forgeward$timeFloor(BooleanSupplier hasMoreTime) {
        return UnloadCap.wrap(hasMoreTime);
    }

    @ModifyConstant(method = "processUnloads", constant = @Constant(intValue = 2000), remap = false)
    private int forgeward$noCountOverride(int vanilla) {
        return UnloadCap.on() ? Integer.MAX_VALUE : vanilla;
    }

    @ModifyConstant(method = "processUnloads", constant = @Constant(intValue = 200), remap = false)
    private int forgeward$noMinimumCount(int vanilla) {
        return UnloadCap.on() ? 0 : vanilla;
    }

    @Inject(method = "processUnloads", at = @At("RETURN"), remap = false)
    private void forgeward$unloadEnd(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        UnloadCap.end(toDrop.size(), unloadQueue.size());
    }
}
