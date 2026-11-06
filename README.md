# Chunk Unload Cap

A server-side NeoForge mod for Minecraft 1.21.1 that stops a mass chunk release from freezing the server.

When thousands of chunks are released at once (players leaving a busy area, a teleport across the world, a big
forceload being removed), vanilla `ChunkMap.processUnloads` ignores its tick budget while more than 2,000 chunks are
queued and unloads them all in a single tick. On a large world that one tick can take tens of seconds.

This mod replaces that rule with a time floor: every tick does at least `forceMs` (default 5 ms) of unload work, more
while the tick has time to spare. The same chunks unload and save; a storm is just spread over several ticks.
Gameplay does not change.

Designed by DuskyTheDolphin, written with AI assistance.

## Install

Server only. Put the jar in the server's `mods/` folder; clients do not need it.

- Minecraft 1.21.1
- NeoForge 21.1 or newer (built and tested against 21.1.250)
- No other dependencies

Release test for 1.0.1: a dedicated NeoForge 21.1.250 server with only this mod installed; its start-up line reports
the cap ON.

## Config

`config/forgeward_unload_cap-common.toml`

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | `false` = vanilla behaviour |
| `forceMs` | `5` | Milliseconds of unload work every tick gets at least (1..50) |

## Log

The mod logs one summary line at server start, every 5 minutes (only when something changed) and at server stop:

```
[forgeward_unload_cap] at server start: chunk unload cap ON (vanilla's drain-all above 2,000 replaced by a 5 ms floor per tick): processUnloads calls 0, ...
```

A single `processUnloads` call over one second is logged as a warning with the backlog left.

## Building

`build.ps1` compiles with `javac` against a NeoForge 21.1.250 server's `libraries/` (set `SERVER` to that folder and
`JDK` to a JDK 21). Mod id `forgeward_unload_cap`.

## License

MIT, see [LICENSE](LICENSE).
