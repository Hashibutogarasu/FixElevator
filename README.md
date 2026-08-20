# FixElevator

A compatibility mod for Minecraft 1.21.1 (NeoForge) that fixes OpenBlocks Elevator's teleport function when its elevator blocks are placed inside a Create: Aeronautics / Create: Simulated sub-level.

## Key feature

FixElevator's headline feature is letting OpenBlocks Elevator's elevator blocks keep working normally while they are physicalized by Sable — that is, while they are part of a moving Create: Simulated / Aeronautics sub-level structure, rather than sitting as ordinary blocks in the overworld. Without this mod, an elevator block still renders correctly once it becomes part of a sub-level, but riding it up or down silently does nothing.

## Problem

When an OpenBlocks Elevator block is placed on a moving structure managed by Create: Simulated's sub-level system (built on the Sable library), the block's camouflage appearance renders correctly, but riding the elevator up or down does nothing. This happens because OpenBlocks Elevator resolves nearby elevator blocks through `player.level()` using the player's visual (global) position, while the block actually lives at a different position inside the sub-level's internal storage (local) coordinates.

FixElevator uses Mixin to redirect these block lookups through the Sable sub-level API so the elevator can find its counterpart block correctly, without modifying either mod's source.

## Supported versions

- Minecraft 1.21.1
- NeoForge

## Dependencies

- [OpenBlocks Elevator](https://www.curseforge.com/minecraft/mc-mods/openblocks-elevator) (required)
- [Create: Aeronautics](https://www.curseforge.com/minecraft/mc-mods/create-aeronautics) (optional, enables the fix when present)
- [Create](https://www.curseforge.com/minecraft/mc-mods/create) (required by Create: Aeronautics)
- [Sable AABB Fix](https://modrinth.com/mod/sable-aabb-fix) (required) — riding an elevator while the player is in an unusual physics state (e.g. suffocating in a tight gap inside a sub-level) can otherwise corrupt the local-to-global coordinate conversion and send the player to the sub-level's raw internal plot coordinates instead of the correct position in the world. Sable AABB Fix guards against exactly that kind of runaway coordinate jump.

## License

MIT License. See [LICENSE.md](LICENSE.md).
