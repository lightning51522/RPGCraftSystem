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
- **`IAttributeEntry`** — Metadata for a registered attribute: `getId()`, `getDisplayName()`, `getSupplier()`, `getDefaultValue()`, `getDefaultMaxValue()`, `isCapped()`, `shouldResetOnRespawn()`, `equipmentAffectsMax()`.
- **`IAttributeRegistry`** — Registration, lookup, and iteration: `register()` (4-param, 5-param with `resetOnRespawn`, 6-param with `resetOnRespawn` + `equipmentAffectsMax`), `getEntry()`, `getTypeById()`, `getAllEntries()`, `getAttribute()`, `createSnapshot()`, `applySnapshot()`.
- **`AttributeSnapshot`** — Immutable snapshot of all attribute values. Contains nested `record AttributeData(int currentValue, int maxValue, String displayName)` for self-describing per-attribute data. Created by `createSnapshot()`, restored by `applySnapshot()`.
- **`IDamageCalculator`** — Attribute-based damage formula contract: `calculateIncomingDamage()`, `calculateOutgoingDamage()`. Replaceable by extension mods via `AttributeManager.setDamageCalculator()`.
- **`IDamageType`** — Damage type contract: `getName()`, `isPhysical()`, `isMagic()`. Implemented by `AttackType` enum.
- **`IAttributeProvider`** — SPI for mods to register custom attributes: `registerAttributes(IAttributeRegistry)`.

### Default Implementations (`attribute/`)

- **`EntityAttribute`** — `IAttribute` default impl. Value object with `currentValue` and `maxValue`, `Math.clamp` on mutation, `MapCodec` for save serialization. `fillMax()` sets currentValue directly to maxValue (used for resource attribute respawn reset).
- **`DefaultAttributeRegistry`** — `IAttributeRegistry` default impl. Manages `DeferredRegister<AttachmentType<?>>`, `Map<Identifier, DefaultEntry>`, and cached entry list. Inner `DefaultEntry` class implements `IAttributeEntry` including `shouldResetOnRespawn()` and `equipmentAffectsMax()`. Provides `getRawSupplier(Identifier)` for direct Supplier access (performance optimization). Separate `respawnResetEntries` list tracks resource attributes.
- **`DefaultDamageCalculator`** — `IDamageCalculator` default impl. Attribute-based damage formulas:
  - Physical incoming: `max(0, originalDamage - defense)`
  - Magic incoming: `originalDamage * (1 - resistance%)`
  - Physical outgoing: base = strength, crit check, crit bonus
  - Magic outgoing: base = mana, crit check, crit bonus
- **`AttackType`** — Enum implementing `IDamageType`: `PHYSICAL`, `MAGIC`, `PHYSICAL_WITH_MAGIC`, `MAGIC_WITH_PHYSICAL`, `MIX_TYPE`. Only `PHYSICAL` and `MAGIC` are implemented in damage calculations.
- **`AttributeManager`** — Attribute module facade (consistent naming with `EquipmentManager`). Holds `Identifier` constants and `Supplier<AttachmentType<EntityAttribute>>` accessors, delegates to `DefaultAttributeRegistry` and `DefaultDamageCalculator`. Must call `init()` before registering its `DeferredRegister` on the mod event bus. `getRegistry()` returns `IAttributeRegistry` interface; `getDeferredRegister()` is a convenience static method.
- **`MobAttributeConfig`** — Loads `data/rpgcraftcore/rpg/mob_attributes.json` for mob attribute presets. Supports `/reload` hot-reload.

#### Attribute Classification

- **Resource attributes** (capped + reset on respawn): life(100), skill_point(100), magic_point(100). These restore to max on respawn via `fillMax()`.
- **Ability attributes** (preserve on respawn): strength(10), mana(10), agile(10), precision(10), defense(10), critical_ratio(50). These keep pre-death values.
- **Capped ability attributes** (capped but preserved): resistance(2→100), critical_rate(5→100). Have max but keep values on respawn.
- Note: `isCapped()` and `shouldResetOnRespawn()` are orthogonal — resistance/critical_rate are capped but NOT reset on respawn.
- Note: `equipmentAffectsMax` is orthogonal to both — only `life` has `equipmentAffectsMax=true` (equipment bonuses change max only; equipping raises the cap, unequipping lowers it and clamps current if needed). All other attributes default to `equipmentAffectsMax=false` (equipment bonuses change current value directly). This design prevents the "unequip-requip heal exploit".

### Initialization Flow

`RPGCraftCore` constructor:
1. `EquipmentManager.init()` — creates `DefaultEquipmentRegistry`, `DefaultEquipmentHandler`
2. `AttributeManager.init()` — creates `DefaultAttributeRegistry`, `DefaultDamageCalculator`, registers all 11 attributes
3. `AttributeManager.getDeferredRegister().register(modEventBus)` — submits AttachmentTypes to NeoForge
4. `EquipmentData.getAttachmentRegister().register(modEventBus)` — submits equipment bonus tracking attachment
5. Other registrations (blocks, items, packets, config)

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
  - `onLivingDamagePre()` — Overrides vanilla damage via `IDamageCalculator` (obtained from `AttributeManager.getDamageCalculator()`). Only fires on server thread — no side check needed.
  - `onLivingDamagePost()` — Syncs custom life attribute with vanilla health. Calls `checkAndSnapshotIfDying()` for early death snapshot creation.

### Commands (`command/`)

- **`RPGCommands`** — `/rpg list [player]`, `/rpg get <attr> [player]`, `/rpg set <attr> <value> [player]`, `/rpg setmax <attr> <value> [player]`, `/rpg reset [player]`. Uses `IAttributeRegistry.getAllEntries()` for suggestions and `IAttributeEntry` for attribute lookup. `/rpg set` only modifies currentValue (clamped by maxValue); `/rpg setmax` modifies maxValue. Both require gamemaster permission.

### Equipment System (`equipment/`)

Equipment bonuses are JSON-driven, with an API layer following the same `api/` pattern as the attribute module.

#### Equipment API Layer (`equipment/api/`)

- **`IEquipmentRegistry`** — Equipment bonus registration and lookup: `register(itemId, bonuses)`, `register(itemId, bonuses, rarity)`, `getBonuses(itemId)`, `getRarity(itemId)`. Default impl: `DefaultEquipmentRegistry`.
- **`IEquipmentHandler`** — Replaceable equipment bonus application logic: `calculateTotalBonus(player)`, `onEquipmentChange(player)`, `restoreBonusTracking(player)`. Default impl: `DefaultEquipmentHandler`. Replaceable via `EquipmentManager.setHandler()`.
- **`IEquipmentProvider`** — SPI for sub-mods to register custom equipment bonuses: `registerEquipment(IEquipmentRegistry)`.

#### Equipment Data Types

- **`EquipmentBonus`** — `record EquipmentBonus(int value)`. Pure integer bonus with `add()` for stacking and `ZERO` constant.
- **`EquipmentRarity`** — Enum with six tiers: `COMMON`(白色), `UNCOMMON`(绿色), `RARE`(蓝色), `EPIC`(紫色), `LEGENDARY`(金色), `MYTHIC`(红色). Each has `displayName` (Chinese) and `colorCode` (MC formatting). Static `fromName(String)` for JSON parsing.

#### Default Implementations

- **`DefaultEquipmentRegistry`** — Implements `IEquipmentRegistry`. Loads from JSON via `loadFromJson(JsonObject)`, also supports programmatic `register()`. Stores separate maps for bonuses (`configMap`) and rarities (`rarityMap`). JSON uses `"rarity": "rare"` key (skipped during attribute parsing) alongside attribute bonus entries.
- **`DefaultEquipmentHandler`** — Implements `IEquipmentHandler`. Takes `IEquipmentRegistry` in constructor. Core logic:
  - `calculateTotalBonus()`: iterates all equipment slots, queries registry, sums bonuses per attribute via `EquipmentBonus.add()`.
  - `onEquipmentChange()`: computes diff between old and new totals, applies via `applyBonusDiff()`. Uses `equipmentAffectsMax` flag: when `true`, only maxValue changes (equipping raises cap, unequipping lowers cap and clamps currentValue if it exceeds new max); when `false`, only currentValue changes. Min-1-HP guard prevents death from unequipping life-boosting gear.
  - `restoreBonusTracking()`: recalculates and stores tracking data in `EquipmentData.EQUIPMENT_BONUS` attachment.
- **`EquipmentManager`** — Facade (mirrors `AttributeManager`). Static `init()` creates defaults. `getRegistry()`, `getHandler()`, `setHandler()` for sub-module replacement.

#### Glue Code

- **`EquipmentConfig`** — `@EventBusSubscriber` that registers server-side reload listener (`AddServerReloadListenersEvent`). Delegates JSON parsing to `DefaultEquipmentRegistry.loadFromJson()`.
- **`EquipmentEventHandler`** — `@EventBusSubscriber` that listens to `LivingEquipmentChangeEvent`. Delegates to `EquipmentManager.getHandler().onEquipmentChange()`.
- **`EquipmentData`** — Registers `EQUIPMENT_BONUS` attachment (`Map<String, EquipmentBonus>`, non-serialized). Tracks applied bonuses per player for diff calculation.

#### Equipment JSON Format

`data/rpgcraftcore/rpg/equipment_attributes.json`:
```json
{
  "minecraft:diamond_sword": {
    "rarity": "rare",
    "rpgcraftcore:strength": 10,
    "rpgcraftcore:critical_rate": 5
  }
}
```
- Top-level keys: item identifiers. Inner keys: `"rarity"` (optional, maps to `EquipmentRarity` enum name) + attribute identifiers with integer bonus values.
- Supports `/reload` hot-reload on server; client-side reload via `AddClientReloadListenersEvent` for tooltip display.

#### Tooltip Display (`client/EquipmentTooltipHandler`)

Client-side `@EventBusSubscriber(Dist.CLIENT)`:
- Registers client-side reload listener to load equipment config for tooltip rendering.
- On `ItemTooltipEvent`: looks up item via `EquipmentManager.getRegistry()`. If item has bonuses:
  - Non-COMMON rarity: colors the item name with the rarity's `colorCode`, inserts a `[稀有度]` label line below the name.
  - Appends green bonus lines: `"§a属性名 +数值"` for each attribute bonus.

### Death & Respawn (`RPGCraftCore`)

Attribute preservation across player death uses the registry's snapshot API, stored in a `ConcurrentHashMap<UUID, AttributeSnapshot>`:

1. **`LivingDeathEvent`** — Primary snapshot trigger. Captures all attribute values via `IAttributeRegistry.createSnapshot(player)`, cached by player UUID using `putIfAbsent`. Handles all death types (combat, /kill, void, etc.).
2. **`PlayerEvent.Clone`** (only when `isWasDeath()`) — Server-side only. Calls `IAttributeRegistry.applySnapshot(newPlayer, snapshot)` to restore:
   - All attributes: restore `maxValue` from snapshot
   - Resource attributes (`shouldResetOnRespawn=true`): `fillMax()` to set currentValue to maxValue
   - Ability attributes: restore snapshot's `currentValue`
   - Then calls `EquipmentManager.getHandler().restoreBonusTracking()` to recompute equipment bonus tracking from current equipment.
   - Does NOT sync to client here — the client hasn't created the new player entity yet at this point.
3. **`PlayerEvent.PlayerRespawnEvent`** — Syncs all attributes to client. This event fires AFTER the client has created the new player entity, so packets are received correctly.
4. **`PlayerEvent.PlayerLoggedInEvent`** — Full sync of all attributes to client on join. Also calls `restoreBonusTracking()` to initialize equipment bonus tracking.
5. **`PlayerEvent.PlayerLoggedOutEvent`** — Cleans up any residual death snapshot for the disconnecting player (prevents memory leak when players die then disconnect without respawning).

`checkAndSnapshotIfDying()` in `CombatEventHandler.onLivingDamagePost()` serves as an early backup for combat deaths (fires before `LivingDeathEvent` due to NeoForge event ordering). Uses `putIfAbsent` so the first snapshot wins.

**Known issue:** NeoForge 26.1.2.68-beta's `copyOnDeath()` on `AttachmentType.builder` does not reliably preserve attachment data across death (old entity's attachment map is cleared before Clone fires). The manual snapshot approach in `RPGCraftCore` is the working solution.

### Cross-Module API Stubs (`api/`)

Placeholder interfaces for the planned RPG system: `INpc`, `IProfession`. Each domain will get its own `api/` sub-package as it develops (following the `attribute/api/` and `equipment/api/` patterns). Equipment module already has its API in `equipment/api/`.

### Data Flow

```
Init: EquipmentManager.init() → AttributeManager.init() → DefaultAttributeRegistry registers 11 attributes → DeferredRegisters on modEventBus
Login: PlayerLoggedInEvent → IAttributeRegistry.getAllEntries() → sendToClient() per entry → restoreBonusTracking()
Runtime: IAttribute change → sendToClient() → network → client handle() → setMaxValue() + setValue()
Equip: LivingEquipmentChangeEvent → EquipmentManager.getHandler().onEquipmentChange() → calculateTotalBonus diff → applyBonusDiff (equipmentAffectsMax flag) → sendToClient()
Combat: LivingDamageEvent.Pre → IDamageCalculator.calculateOutgoingDamage → calculateIncomingDamage → setNewDamage
Death: LivingDeathEvent → createSnapshot (UUID → {id: AttributeData(current, max, displayName)})
Clone: PlayerEvent.Clone → applySnapshot → restore maxValue, fillMax for resources, restore currentValue for abilities → restoreBonusTracking() (server-side only, no sync)
Respawn: PlayerRespawnEvent → sendToClient() per entry (client has new entity now)
Logout: PlayerLoggedOutEvent → deathSnapshot.remove(uuid) (cleanup)
Render: GuiLayer.render() every frame → IAttributeRegistry.getAllEntries() → IAttribute from client attachment → text()
Tooltip: ItemTooltipEvent → EquipmentManager.getRegistry().getBonuses() + getRarity() → color item name + [稀有度] label + bonus lines
```

### Sub-Module Integration

Sub-modules can add new equipment items with RPG stats. The core mod uses Java `ServiceLoader` SPI to discover providers during initialization.

**Workflow for adding a new equipment item (e.g., "Dragon Sword"):**

1. **Register the item** — standard NeoForge `DeferredRegister.Items` in the sub-module's own `@Mod` class.
2. **Create assets** — models in `resources/assets/mymod/models/item/`, textures in `resources/assets/mymod/textures/item/`. Standard NeoForge resource packs.
3. **Register RPG data** — implement `IEquipmentProvider` and register via `ServiceLoader`:
   ```java
   public class MyModEquipment implements IEquipmentProvider {
       public void registerEquipment(IEquipmentRegistry registry) {
           registry.register(
               Identifier.fromNamespaceAndPath("mymod", "dragon_sword"),
               Map.of(AttributeManager.STRENGTH_ID, new EquipmentBonus(25)),
               EquipmentRarity.EPIC
           );
       }
   }
   ```
4. **Declare SPI** — create `META-INF/services/com.rpgcraft.core.equipment.api.IEquipmentProvider` containing the fully qualified class name.
5. **Tooltip display works automatically** — `EquipmentTooltipHandler` queries `EquipmentManager.getRegistry()` for any item, so custom items get bonus lines and rarity coloring without additional code.

**Same pattern for custom attributes:** implement `IAttributeProvider`, declare in `META-INF/services/com.rpgcraft.core.attribute.api.IAttributeProvider`.

**SPI invocation timing:** `EquipmentManager.init()` and `AttributeManager.init()` both call `ServiceLoader.load()` at the end of initialization, after registering their own default data. Provider calls happen before `DeferredRegister.register(modEventBus)` in the `RPGCraftCore` constructor.

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

- `EquipmentManager.init()` must be called before `AttributeManager.init()` in the `RPGCraftCore` constructor. Both must complete before their respective DeferredRegisters are submitted to the mod event bus.
- `AttributeManager.init()` must be called before `getDeferredRegister().register(modEventBus)`. The init order in `RPGCraftCore` constructor matters.
- New attributes should be registered through `IAttributeRegistry.register()` in `AttributeManager.init()`. The `Identifier` path must match the DeferredRegister entry name.
- Damage formulas can be replaced at runtime via `AttributeManager.setDamageCalculator(IDamageCalculator)`.
- Equipment bonus application logic can be replaced at runtime via `EquipmentManager.setHandler(IEquipmentHandler)`.
- Equipment bonuses can be registered programmatically via `EquipmentManager.getRegistry().register()`, configured in `equipment_attributes.json`, or via `IEquipmentProvider` SPI (Java `ServiceLoader`).
- Custom attributes from sub-modules use `IAttributeProvider` SPI (Java `ServiceLoader`), declared in `META-INF/services/com.rpgcraft.core.attribute.api.IAttributeProvider`.
- `equipmentAffectsMax` on `IAttributeEntry` determines whether equipment bonuses affect only max (true, for life) or only current value (false, default). For life, equipping raises the cap without healing, unequipping lowers the cap and clamps currentValue if it exceeds the new max. This flag is set at attribute registration time, not in the equipment JSON.
- When unequipping causes currentValue to be clamped below 1 (for life), the handler forces it to 1 to prevent player death.
- `StreamCodec` is for network serialization; `MapCodec`/`Codec` is for save file serialization. Do not mix them.
- Client-side code lives under `client/` and uses `@EventBusSubscriber(value = Dist.CLIENT)`.
- Data-generated resources go to `src/generated/resources/` (already on the resource classpath). Hand-written resources go to `src/main/resources/`.
- Generic type casts between `AttachmentType<EntityAttribute>` and `AttachmentType<IAttribute>` are unavoidable due to Java generics limitations with NeoForge's type system. Always annotate with `@SuppressWarnings("unchecked")`.
- Death/respawn attribute preservation uses the `AttributeSnapshot` API (`createSnapshot` on death → `applySnapshot` on clone → sync on respawn), not `copyOnDeath()`, because NeoForge 26.1.2.68-beta clears old entity attachments before the Clone event fires.
- `shouldResetOnRespawn` is orthogonal to `isCapped`: resource attributes (life, skill_point, magic_point) reset to max via `fillMax()`; ability attributes (strength, defense, etc.) preserve pre-death values.
- `/rpg set` only modifies currentValue (clamped by existing maxValue). `/rpg setmax` modifies maxValue (may clamp currentValue down). These are separate concerns.
- `SyncPlayerAttributePacket.handle()` sets both `setMaxValue` and `setValue` on the client attachment. Both must be applied — omitting `setMaxValue` causes client-side max display to desync.
- Code comments and language keys are in Chinese (zh_CN).
