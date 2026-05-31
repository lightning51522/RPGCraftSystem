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

This is the core module of the RPGCraftSystem multi-project workspace. It provides the foundational RPG attribute system and damage calculation that other modules will depend on.

### Attribute API Layer (`attribute/api/`)

Public interfaces that other mods depend on. Each functional module has its own `api/` sub-package under its domain:

- **`IAttribute`** — Attribute value contract: `getValue()`, `setValue()`, `getMaxValue()`, `setMaxValue()`, `hasMaxValue()`, `fillMax()`.
- **`IAttributeEntry`** — Metadata for a registered attribute: `getId()`, `getDisplayName()`, `getSupplier()`, `getDefaultValue()`, `getDefaultMaxValue()`, `isCapped()`, `shouldResetOnRespawn()`.
- **`IAttributeRegistry`** — Registration, lookup, and iteration: `register()` (4-param and 5-param with `resetOnRespawn`), `getEntry()`, `getTypeById()`, `getAllEntries()`, `getAttribute()`, `createSnapshot()`, `applySnapshot()`.
- **`AttributeSnapshot`** — Immutable snapshot of all attribute values. Contains nested `record AttributeData(int currentValue, int maxValue, String displayName)` for self-describing per-attribute data. Created by `createSnapshot()`, restored by `applySnapshot()`.
- **`IDamageCalculator`** — Attribute-based damage formula contract: `calculateIncomingDamage()`, `calculateOutgoingDamage()`. Replaceable by extension mods via `GenericEntityData.setDamageCalculator()`.
- **`IDamageType`** — Damage type contract: `getName()`, `isPhysical()`, `isMagic()`. Implemented by `AttackType` enum.
- **`IAttributeProvider`** — SPI for mods to register custom attributes: `registerAttributes(IAttributeRegistry)`.

### Default Implementations (`attribute/`)

- **`EntityAttribute`** — `IAttribute` default impl. Value object with `currentValue` and `maxValue`, `Math.clamp` on mutation, `MapCodec` for save serialization. `fillMax()` sets currentValue directly to maxValue (used for resource attribute respawn reset).
- **`DefaultAttributeRegistry`** — `IAttributeRegistry` default impl. Manages `DeferredRegister<AttachmentType<?>>`, `Map<Identifier, DefaultEntry>`, and cached entry list. Inner `DefaultEntry` class implements `IAttributeEntry` including `shouldResetOnRespawn()`. Provides `getRawSupplier(Identifier)` for direct Supplier access (performance optimization). Separate `respawnResetEntries` list tracks resource attributes.
- **`DefaultDamageCalculator`** — `IDamageCalculator` default impl. Attribute-based damage formulas:
  - Physical incoming: `max(0, originalDamage - defense)`
  - Magic incoming: `originalDamage * (1 - resistance%)`
  - Physical outgoing: base = strength, crit check, crit bonus
  - Magic outgoing: base = mana, crit check, crit bonus
- **`AttackType`** — Enum implementing `IDamageType`: `PHYSICAL`, `MAGIC`, `PHYSICAL_WITH_MAGIC`, `MAGIC_WITH_PHYSICAL`, `MIX_TYPE`. Only `PHYSICAL` and `MAGIC` are implemented in damage calculations.
- **`GenericEntityData`** — Backward-compatible facade. Holds `Identifier` constants and `Supplier<AttachmentType<EntityAttribute>>` accessors, delegates to `DefaultAttributeRegistry` and `DefaultDamageCalculator`. Must call `init()` before registering its `DeferredRegister` on the mod event bus.
- **`MobAttributeConfig`** — Loads `data/rpgcraftcore/rpg/mob_attributes.json` for mob attribute presets. Supports `/reload` hot-reload.

#### Attribute Classification

- **Resource attributes** (capped + reset on respawn): life(100), skill_point(100), magic_point(100). These restore to max on respawn via `fillMax()`.
- **Ability attributes** (preserve on respawn): strength(10), mana(10), agile(10), precision(10), defense(10), critical_ratio(50). These keep pre-death values.
- **Capped ability attributes** (capped but preserved): resistance(2→100), critical_rate(5→100). Have max but keep values on respawn.
- Note: `isCapped()` and `shouldResetOnRespawn()` are orthogonal — resistance/critical_rate are capped but NOT reset on respawn.

### Initialization Flow

`RPGCraftCore` constructor:
1. `GenericEntityData.init()` — creates `DefaultAttributeRegistry`, `DefaultDamageCalculator`, registers all 11 attributes
2. `GenericEntityData.getRegistry().getDeferredRegister().register(modEventBus)` — submits AttachmentTypes to NeoForge
3. Other registrations (blocks, items, packets, config)

### Network Sync (`network/`)

Client-server attribute synchronization using NeoForge's payload system:

- **`PacketHandler`** — Registers payloads on the mod event bus with protocol version `"1"`.
- **`SyncPlayerAttributePacket`** — A `record` implementing `CustomPacketPayload`. Uses `StreamCodec` (network) — distinct from `MapCodec` (save). Server calls `sendToClient()`, client handles via `handle()` which looks up the AttachmentType through `IAttributeRegistry.getTypeById()`, sets both `setMaxValue()` and `setValue()` on the client attachment, enqueued on the main thread.

### Client HUD (`client/`)

- **`AttributeHudOverlay`** — `@EventBusSubscriber(Dist.CLIENT)` that renders attributes as a HUD overlay via NeoForge 26.1's `GuiLayer` system (registered through `RegisterGuiLayersEvent`). Iterates `IAttributeRegistry.getAllEntries()` and reads `IAttribute` from client player attachments. Uses `GuiGraphicsExtractor.text()` with ARGB colors (`0xFFFFFFFF`).
- **`RPGCraftCoreClient`** — Client-side entry point (`@Mod(dist = Dist.CLIENT)`).

### Combat System (`combat/`)

- **`CombatEventHandler`** — `@EventBusSubscriber` on the game bus:
  - `onEntityJoinLevel()` — Server-side only (has `isClientSide()` guard). Sets mob attributes from `MobAttributeConfig` JSON.
  - `onLivingDamagePre()` — Overrides vanilla damage via `IDamageCalculator` (obtained from `GenericEntityData.getDamageCalculator()`). Only fires on server thread — no side check needed.
  - `onLivingDamagePost()` — Syncs custom life attribute with vanilla health. Calls `checkAndSnapshotIfDying()` for early death snapshot creation.

### Commands (`command/`)

- **`RPGCommands`** — `/rpg list [player]`, `/rpg get <attr> [player]`, `/rpg set <attr> <value> [player]`, `/rpg setmax <attr> <value> [player]`, `/rpg reset [player]`. Uses `IAttributeRegistry.getAllEntries()` for suggestions and `IAttributeEntry` for attribute lookup. `/rpg set` only modifies currentValue (clamped by maxValue); `/rpg setmax` modifies maxValue. Both require gamemaster permission.

### Death & Respawn (`RPGCraftCore`)

Attribute preservation across player death uses the registry's snapshot API, stored in a `ConcurrentHashMap<UUID, AttributeSnapshot>`:

1. **`LivingDeathEvent`** — Primary snapshot trigger. Captures all attribute values via `IAttributeRegistry.createSnapshot(player)`, cached by player UUID using `putIfAbsent`. Handles all death types (combat, /kill, void, etc.).
2. **`PlayerEvent.Clone`** (only when `isWasDeath()`) — Server-side only. Calls `IAttributeRegistry.applySnapshot(newPlayer, snapshot)` to restore:
   - All attributes: restore `maxValue` from snapshot
   - Resource attributes (`shouldResetOnRespawn=true`): `fillMax()` to set currentValue to maxValue
   - Ability attributes: restore snapshot's `currentValue`
   - Does NOT sync to client here — the client hasn't created the new player entity yet at this point.
3. **`PlayerEvent.PlayerRespawnEvent`** — Syncs all attributes to client. This event fires AFTER the client has created the new player entity, so packets are received correctly.
4. **`PlayerEvent.PlayerLoggedInEvent`** — Full sync of all attributes to client on join.
5. **`PlayerEvent.PlayerLoggedOutEvent`** — Cleans up any residual death snapshot for the disconnecting player (prevents memory leak when players die then disconnect without respawning).

`checkAndSnapshotIfDying()` in `CombatEventHandler.onLivingDamagePost()` serves as an early backup for combat deaths (fires before `LivingDeathEvent` due to NeoForge event ordering). Uses `putIfAbsent` so the first snapshot wins.

**Known issue:** NeoForge 26.1.2.68-beta's `copyOnDeath()` on `AttachmentType.builder` does not reliably preserve attachment data across death (old entity's attachment map is cleared before Clone fires). The manual snapshot approach in `RPGCraftCore` is the working solution.

### Cross-Module API Stubs (`api/`)

Placeholder interfaces for the planned RPG system: `IEquipment`, `INpc`, `IProfession`. Each domain will get its own `api/` sub-package as it develops (following the `attribute/api/` pattern).

### Data Flow

```
Init: GenericEntityData.init() → DefaultAttributeRegistry registers 11 attributes → DeferredRegister on modEventBus
Login: PlayerLoggedInEvent → IAttributeRegistry.getAllEntries() → sendToClient() per entry
Runtime: IAttribute change → sendToClient() → network → client handle() → setMaxValue() + setValue()
Combat: LivingDamageEvent.Pre → IDamageCalculator.calculateOutgoingDamage → calculateIncomingDamage → setNewDamage
Death: LivingDeathEvent → createSnapshot (UUID → {id: AttributeData(current, max, displayName)})
Clone: PlayerEvent.Clone → applySnapshot → restore maxValue, fillMax for resources, restore currentValue for abilities (server-side only, no sync)
Respawn: PlayerRespawnEvent → sendToClient() per entry (client has new entity now)
Logout: PlayerLoggedOutEvent → deathSnapshot.remove(uuid) (cleanup)
Render: GuiLayer.render() every frame → IAttributeRegistry.getAllEntries() → IAttribute from client attachment → text()
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
- **`PlayerEvent.Clone` fires before client creates new entity**: Sync packets sent during Clone are received by the old (dead) client entity and lost. Use `PlayerRespawnEvent` for client sync after respawn.
- **`EntityJoinLevelEvent` fires on both sides**: Always guard with `event.getLevel().isClientSide()` when only server-side logic is needed. `LivingDamageEvent` only fires on the server thread and needs no such guard.

## Key Conventions

- `GenericEntityData.init()` must be called before `getRegistry().getDeferredRegister().register(modEventBus)`. The init order in `RPGCraftCore` constructor matters.
- New attributes should be registered through `IAttributeRegistry.register()` in `GenericEntityData.init()`. The `Identifier` path must match the DeferredRegister entry name.
- Damage formulas can be replaced at runtime via `GenericEntityData.setDamageCalculator(IDamageCalculator)`.
- `StreamCodec` is for network serialization; `MapCodec`/`Codec` is for save file serialization. Do not mix them.
- Client-side code lives under `client/` and uses `@EventBusSubscriber(value = Dist.CLIENT)`.
- Data-generated resources go to `src/generated/resources/` (already on the resource classpath). Hand-written resources go to `src/main/resources/`.
- Generic type casts between `AttachmentType<EntityAttribute>` and `AttachmentType<IAttribute>` are unavoidable due to Java generics limitations with NeoForge's type system. Always annotate with `@SuppressWarnings("unchecked")`.
- Death/respawn attribute preservation uses the `AttributeSnapshot` API (`createSnapshot` on death → `applySnapshot` on clone → sync on respawn), not `copyOnDeath()`, because NeoForge 26.1.2.68-beta clears old entity attachments before the Clone event fires.
- `shouldResetOnRespawn` is orthogonal to `isCapped`: resource attributes (life, skill_point, magic_point) reset to max via `fillMax()`; ability attributes (strength, defense, etc.) preserve pre-death values.
- `/rpg set` only modifies currentValue (clamped by existing maxValue). `/rpg setmax` modifies maxValue (may clamp currentValue down). These are separate concerns.
- `SyncPlayerAttributePacket.handle()` sets both `setMaxValue` and `setValue` on the client attachment. Both must be applied — omitting `setMaxValue` causes client-side max display to desync.
- Code comments and language keys are in Chinese (zh_CN).
