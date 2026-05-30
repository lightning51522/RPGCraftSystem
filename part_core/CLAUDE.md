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

- **Minecraft**: 26.1.2
- **Mod Loader**: NeoForge 26.1.2.68-beta (net.neoforged.moddev Gradle plugin)
- **Java**: 25 (Microsoft OpenJDK)
- **Mod ID**: `rpgcraftcore`

## Architecture

This is the core module of the RPGCraftSystem multi-project workspace. It provides the foundational RPG attribute system and combat calculation that other modules will depend on.

### Entity Attribute System (`attribute/`)

The central mechanic. Uses NeoForge's `AttachmentType` system to attach custom RPG attributes to entities.

- **`EntityAttribute`** — Value object with `currentValue` and `maxValue`. Provides a `MapCodec` for save serialization and `Math.clamp` on mutation.
- **`GenericEntityData`** — Declares all attachment types via a `DeferredRegister<AttachmentType<?>>`. Each attribute (life, skill_point, magic_point, strength, mana, agile, precision, defense, resistance, critical_rate, critical_ratio) is registered with:
  - An explicit `Identifier` constant (used by the network layer)
  - A `Supplier<AttachmentType<EntityAttribute>>` with default values and the `EntityAttribute.CODEC`
  - A `getTypeById(Identifier)` lookup for client-side deserialization
  - An `ALL_ATTRIBUTES` list of `AttributeEntry` records for batch iteration (e.g., login sync)
- **`AttackType`** — Enum defining damage types: `PHYSICAL`, `MAGIC`, `PHYSICAL_WITH_MAGIC`, `MAGIC_WITH_PHYSICAL`, `MIX_TYPE`. Only `PHYSICAL` and `MAGIC` are currently implemented in combat calculations.

#### Combat Calculations (`GenericEntityData` static methods)

- **`getHurt(Player, int, AttackType)`** — Calculates damage after reduction:
  - Physical: `max(0, originalDamage - defense)`
  - Magic: `originalDamage * (1 - resistance%)`
  - Mixed types: pass-through (not yet implemented)
- **`causeDamage(Player, AttackType)`** — Calculates outgoing damage:
  - Physical base = strength, Magic base = mana
  - Crit check: `random < critRate%` → crit bonus: `base * (1 + critRatio%)`

#### Attribute Classification

- **Capped** (maxValue < Integer.MAX_VALUE): life(100), skill_point(100), magic_point(100), resistance(100→2), critical_rate(100→5)
- **Uncapped** (maxValue = Integer.MAX_VALUE): strength(10), mana(10), agile(10), precision(10), defense(10), critical_ratio(50)

### Network Sync (`network/`)

Client-server attribute synchronization using NeoForge's payload system:

- **`PacketHandler`** — Registers payloads on the mod event bus with protocol version `"1"`.
- **`SyncPlayerAttributePacket`** — A `record` implementing `CustomPacketPayload`. Uses `StreamCodec` (network) — distinct from `MapCodec` (save). Server calls `sendToClient()`, client handles via `handle()` which enqueues work on the main thread and writes data into the client player's attachment.

### Client HUD (`client/`)

- **`AttributeHudOverlay`** — `@EventBusSubscriber(Dist.CLIENT)` that renders attributes as a HUD overlay via NeoForge 26.1's `GuiLayer` system (registered through `RegisterGuiLayersEvent` on the mod bus, NOT the old `RenderGuiEvent.Post`). Uses `GuiGraphicsExtractor.text()` for string rendering with ARGB colors (`0xFFFFFFFF` for opaque white). `@EventBusSubscriber` has no `bus` parameter in 26.1 — routing is automatic per method based on whether the event type implements `IModBusEvent`.
- **`RPGCraftCoreClient`** — Client-side entry point (`@Mod(dist = Dist.CLIENT)`).

### Login Sync (`RPGCraftCore`)

- **`onPlayerLogin(PlayerLoggedInEvent)`** — Registered on `NeoForge.EVENT_BUS` (game bus). Iterates `GenericEntityData.ALL_ATTRIBUTES` and sends each attribute to the client via `SyncPlayerAttributePacket.sendToClient()`. Fires on every login regardless of game mode.

### API Stubs (`api/`)

Placeholder interfaces for the planned RPG system: `IAttribute`, `IEquipment`, `INpc`, `IProfession`.

### Data Flow

```
Login: PlayerLoggedInEvent → iterate ALL_ATTRIBUTES → sendToClient() per attribute
Runtime: EntityAttribute change → sendToClient() → network → client handle() → setValue()
Render: GuiLayer.render() every frame → read client attachment → GuiGraphicsExtractor.text()
```

## NeoForge 26.1 API Notes

These are version-specific changes that differ from older NeoForge/Forge tutorials:

- **`GuiGraphics` → `GuiGraphicsExtractor`**: Class renamed in 26.1.
- **`drawString` → `text`**: Method renamed on `GuiGraphicsExtractor`.
- **ARGB colors require explicit alpha**: `0xFFFFFF` renders as transparent (alpha=0). Always use `0xFFFFFFFF` for opaque text.
- **`Screen#render` → `Screen#extractRenderState`**: Rendering pipeline fundamentally changed.
- **`RenderGuiEvent.Post` removed**: Use `RegisterGuiLayersEvent` + `GuiLayer` instead.
- **`@EventBusSubscriber` has no `bus` parameter**: Bus routing is automatic — methods handling `IModBusEvent` types go to the mod bus, all others to the game bus.
- **`ResourceLocation` → `Identifier`**: Class renamed in 26.1.
- **`GuiLayer` functional interface**: `void render(GuiGraphicsExtractor, DeltaTracker)`.
- **Obfuscation removed**: 26.1 ships with official (unobfuscated) names.

## Key Conventions

- Registration names in `GenericEntityData` must match exactly between the `Identifier` path string and the `DeferredRegister.register()` name parameter.
- `StreamCodec` is for network serialization; `MapCodec`/`Codec` is for save file serialization. Do not mix them.
- Client-side code lives under `client/` and uses `@EventBusSubscriber(value = Dist.CLIENT)`.
- Data-generated resources go to `src/generated/resources/` (already on the resource classpath). Hand-written resources go to `src/main/resources/`.
- The `getTypeById()` lookup in `GenericEntityData` must be kept in sync when adding new attributes — there is no automatic registry-based alternative.
- Code comments and language keys are in Chinese (zh_CN).
