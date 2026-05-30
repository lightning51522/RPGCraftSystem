# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

This is a Gradle multi-project workspace. Run all commands from the **parent directory** (`project/`):

```bash
# Build
./gradlew :part_core:build

# Run client
./gradlew :part_core:runClient

# Run dedicated server
./gradlew :part_core:runServer

# Run data generators (output goes to src/generated/resources/)
./gradlew :part_core:runData

# Run game test server
./gradlew :part_core:runGameTestServer
```

Version properties (`minecraft_version`, `neo_version`) are defined in the root `project/gradle.properties`, not in this subproject.

## Target Environment

- **Minecraft**: 26.1.x (1.21.x line)
- **Mod Loader**: NeoForge (net.neoforged.moddev Gradle plugin)
- **Java**: 21+
- **Mod ID**: `rpgcraftcore`

## Architecture

This is the core module of the RPGCraftSystem multi-project workspace. It provides the foundational RPG attribute system that other modules will depend on.

### Player Attribute System (`attribute/`)

The central mechanic. Uses NeoForge's `AttachmentType` system to attach custom RPG attributes to players.

- **`PlayerAttribute`** — Value object with `currentValue` and `maxValue`. Provides a `MapCodec` for save serialization and `Math.clamp` on mutation.
- **`GenericPlayerData`** — Declares all attachment types via a `DeferredRegister<AttachmentType<?>>`. Each attribute (life, skill_point, magic_point, strength, mana, agile, precision, defense, resistance, critical_rate, critical_ratio) is registered with:
  - An explicit `Identifier` constant (used by the network layer)
  - A `Supplier<AttachmentType<PlayerAttribute>>` with default values and the `PlayerAttribute.CODEC`
  - A `getTypeById(Identifier)` lookup for client-side deserialization

Attributes with `maxValue = Integer.MAX_VALUE` are uncapped (stats like strength). Capped ones (life, skill_point, magic_point) have explicit max defaults (100).

### Network Sync (`network/`)

Client-server attribute synchronization using NeoForge's payload system:

- **`PacketHandler`** — Registers payloads on the mod event bus with protocol version `"1"`.
- **`SyncPlayerAttributePacket`** — A `record` implementing `CustomPacketPayload`. Uses `StreamCodec` (network) — distinct from `MapCodec` (save). Server calls `sendToClient()`, client handles via `handle()` which enqueues work on the main thread and writes data into the client player's attachment.

### Client HUD (`client/`)

- **`AttributeHudOverlay`** — `@EventBusSubscriber(Dist.CLIENT)` that renders attributes as an overlay via `RenderGuiEvent.Post`. Reads attachment data from the local player (kept in sync by the network packet).
- **`RPGCraftCoreClient**` — Client-side entry point.

### API Stubs (`api/`)

Placeholder interfaces for the planned RPG system: `IAttribute`, `IEquipment`, `INpc`, `IProfession`.

### Data Flow

```
Server: PlayerAttribute attachment → SyncPlayerAttributePacket.sendToClient()
  → Network wire →
Client: SyncPlayerAttributePacket.handle() → clientPlayer.getData(type).setValue()
  → AttributeHudOverlay reads attachment for rendering
```

## Key Conventions

- Registration names in `GenericPlayerData` must match exactly between the `Identifier` path string and the `DeferredRegister.register()` name parameter.
- `StreamCodec` is for network serialization; `MapCodec`/`Codec` is for save file serialization. Do not mix them.
- Client-side code lives under `client/` and uses `@EventBusSubscriber(value = Dist.CLIENT)`.
- Data-generated resources go to `src/generated/resources/` (already on the resource classpath). Hand-written resources go to `src/main/resources/`.
- The `getTypeById()` lookup in `GenericPlayerData` must be kept in sync when adding new attributes — there is no automatic registry-based alternative.
- Code comments and language keys are in Chinese (zh_CN).
