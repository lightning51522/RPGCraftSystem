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
- **`MobAttributeConfig`** — Loads `data/rpgcraftcore/rpg/mob_attributes.json` for mob attribute presets. Supports `/reload` hot-reload. `MobAttributes` record includes `attackType` (AttackType enum, defaults to `PHYSICAL` if absent in JSON) alongside numeric stats.
- **`DeathAttributeMode`** — Enum controlling death/respawn attribute recovery: `SNAPSHOT` (restore death values verbatim) or `RESCAN` (recompute from base + current equipment). Static `currentMode` field with getter/setter. Default: `SNAPSHOT`. Toggled via `/rpg deathmode` command.

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

- **`AttributeHudOverlay`** — `@EventBusSubscriber(Dist.CLIENT)` that renders via NeoForge 26.1's `GuiLayer` system:
  - **Custom health bar** — Replaces `VanillaGuiLayers.PLAYER_HEALTH` via `RegisterGuiLayersEvent.replaceLayer()`. Renders a rounded-rectangle progress bar with gradient red fill (top: `0xFFE03030`, bottom: `0xFF8B0000`), dark gray lost-health area (`0xFF373737`), dark border (`0xFF222222`), and centered "current/max" text. Position matches vanilla hearts (`screenWidth/2 - 91`, `screenHeight - 39`). Bar: 90×9px, border 1px, corner radius 2px. Uses manual `fillRounded()`/`fillRoundedGradient()` helper methods.
  - **Attribute text panel** — Registered via `registerAboveAll`, iterates `IAttributeRegistry.getAllEntries()` and displays all attributes (including life) as text in the top-left corner. Shows "name: value / maxValue" for capped attributes, "name: value" for uncapped.
- **`RPGCraftCoreClient`** — Client-side entry point (`@Mod(dist = Dist.CLIENT)`).
- **`EquipmentTooltipHandler`** — Client-side tooltip rendering for equipment bonuses. See Tooltip Display section below.

### Combat System (`combat/`)

- **`CombatEventHandler`** — `@EventBusSubscriber` on the game bus:
  - `onEntityJoinLevel()` — Server-side only (has `isClientSide()` guard). Sets mob attributes from `MobAttributeConfig` JSON.
  - `onLivingDamagePre()` — Flat damage system (not proportional). Three damage paths:
    - **Bypass** (`BYPASSES_INVULNERABILITY`): sets custom life to 0, passes vanilla damage through to trigger death.
    - **Combat** (attacker is `LivingEntity`): Looks up attacker's `attackType` from `MobAttributeConfig` (defaults to `PHYSICAL`). RPG formula via `IDamageCalculator` produces absolute damage value, applied directly to custom life using the configured attack type.
    - **Environmental** (no attacker): vanilla damage value applied directly to custom life (no scaling by max life).
    - After custom life change, vanilla damage is set to a proportional value so vanilla health syncs (keeps vanilla healing working via `LivingHealEvent`). Guarded: skips proportional calculation when `maxValue ≤ 0` to prevent division by zero.
  - `onLivingDamagePost()` — Re-syncs vanilla health to match custom life ratio (handles absorption edge cases). Guarded: skips when `maxValue ≤ 0`. Calls `checkAndSnapshotIfDying()` for early death snapshot creation.
  - `onLivingHeal()` — Converts vanilla heal amounts to custom life proportionally. Guarded: skips when vanilla `getMaxHealth() ≤ 0`. Uses `setHealth()` (not `heal()`) so no loop with `syncVanillaHealth()`.

### Commands (`command/`)

- **`RPGCommands`** — `/rpg list [player]`, `/rpg get <attr> [player]`, `/rpg set <attr> <value> [player]`, `/rpg setmax <attr> <value> [player]`, `/rpg reset [player]`, `/rpg deathmode <snapshot|rescan>`. Uses `IAttributeRegistry.getAllEntries()` for suggestions and `IAttributeEntry` for attribute lookup. `/rpg set` only modifies currentValue (clamped by maxValue); `/rpg setmax` modifies maxValue. Both require gamemaster permission. `/rpg list` also displays the current death recovery mode. `/rpg deathmode` toggles between SNAPSHOT and RESCAN modes (gamemaster permission).

### Equipment System (`equipment/`)

Equipment bonuses are JSON-driven, with an API layer following the same `api/` pattern as the attribute module.

#### Equipment API Layer (`equipment/api/`)

- **`IEquipmentRegistry`** — Equipment bonus registration and lookup: `register(itemId, bonuses)`, `register(itemId, bonuses, rarity)`, `getBonuses(itemId)`, `getRarity(itemId)`. Default impl: `DefaultEquipmentRegistry`.
- **`IEquipmentHandler`** — Replaceable equipment bonus application logic: `calculateTotalBonus(player)`, `onEquipmentChange(player)`, `restoreBonusTracking(player)`, `rescanAndApplyAttributes(player, deathSnapshot, deathEquipmentBonuses)`. Default impl: `DefaultEquipmentHandler`. Replaceable via `EquipmentManager.setHandler()`.
- **`IEquipmentProvider`** — SPI for sub-mods to register custom equipment bonuses: `registerEquipment(IEquipmentRegistry)`.

#### Equipment Data Types

- **`EquipmentBonus`** — `record EquipmentBonus(int value)`. Pure integer bonus with overflow-safe `add()` for stacking (saturating addition clamps to `Integer.MAX_VALUE`/`Integer.MIN_VALUE` on overflow) and `ZERO` constant.
- **`EquipmentRarity`** — Enum with six tiers: `COMMON`(白色), `UNCOMMON`(绿色), `RARE`(蓝色), `EPIC`(紫色), `LEGENDARY`(金色), `MYTHIC`(红色). Each has `displayName` (Chinese) and `colorCode` (MC formatting). Static `fromName(String)` for JSON parsing.

#### Default Implementations

- **`DefaultEquipmentRegistry`** — Implements `IEquipmentRegistry`. Loads from JSON via `loadFromJson(JsonObject)`, also supports programmatic `register()`. Stores separate maps for bonuses (`configMap`) and rarities (`rarityMap`). JSON uses `"rarity": "rare"` key (skipped during attribute parsing) alongside attribute bonus entries.
- **`DefaultEquipmentHandler`** — Implements `IEquipmentHandler`. Takes `IEquipmentRegistry` in constructor. Core logic:
  - `calculateTotalBonus()`: iterates all equipment slots, queries registry, sums bonuses per attribute via `EquipmentBonus.add()`. **Skips armor items in hand slots** — checks `DataComponents.EQUIPPABLE` component: if item has `Equippable` data targeting `HUMANOID_ARMOR` type and is in a `HAND` type slot, it is skipped. This ensures armor only applies bonuses when in armor slots, while weapons work from hand slots.
  - `onEquipmentChange()`: computes diff between old and new totals, applies via `applyBonusDiff()`. Uses `equipmentAffectsMax` flag: when `true`, only maxValue changes (equipping raises cap, unequipping lowers cap and clamps currentValue if it exceeds new max); when `false`, only currentValue changes. Min-1-HP guard prevents death from unequipping life-boosting gear. Does NOT call `syncVanillaHealth()` — prevents hurt animation on armor equip/unequip.
  - `restoreBonusTracking()`: recalculates and stores tracking data in `EquipmentData.EQUIPMENT_BONUS` attachment.
  - `rescanAndApplyAttributes()`: for RESCAN death mode. Computes base values (death snapshot minus death equipment bonuses, clamped to `≥ 0` to prevent corruption from inconsistent state), scans current equipment via `calculateTotalBonus()`, applies current bonuses to base values. Fills resource attributes to max, updates tracking attachment, syncs all attributes to client. Compatible with future custom equipment slots because it delegates slot iteration to `calculateTotalBonus()`.
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

Attribute preservation across player death uses a dual-mode system controlled by `DeathAttributeMode` enum (`SNAPSHOT` or `RESCAN`), toggled via `/rpg deathmode <snapshot|rescan>`. Death data is stored in a `ConcurrentHashMap<UUID, DeathData>`, where `DeathData` is an inner record wrapping both `AttributeSnapshot` and `Map<String, EquipmentBonus>` (the equipment bonuses active at death time).

#### Snapshot Capture (same for both modes)

1. **`checkAndSnapshotIfDying()`** — Called from `CombatEventHandler.onLivingDamagePost()` when custom life ≤ 0. Captures `AttributeSnapshot` + equipment bonuses from `EquipmentData.EQUIPMENT_BONUS` attachment. Uses `putIfAbsent` so the first snapshot wins.
2. **`LivingDeathEvent`** — Fallback for non-life-zero deaths (void, /kill). Same capture logic with `putIfAbsent`.

#### SNAPSHOT Mode (default)

3. **`PlayerEvent.Clone`** — Calls `IAttributeRegistry.applySnapshot(newPlayer, snapshot)` to restore:
   - All attributes: restore `maxValue` from snapshot
   - Resource attributes (`shouldResetOnRespawn=true`): `fillMax()` to set currentValue to maxValue
   - Ability attributes: restore snapshot's `currentValue`
   - Then calls `EquipmentManager.getHandler().restoreBonusTracking()` to recompute equipment bonus tracking from current equipment.
   - Does NOT sync to client here — the client hasn't created the new player entity yet.

#### RESCAN Mode (new)

3. **`PlayerEvent.Clone`** — Calls `EquipmentManager.getHandler().rescanAndApplyAttributes(newPlayer, snapshot, deathEquipmentBonuses)`:
   - Computes base values: `base = snapshotValue - deathBonus` for each attribute
   - Scans current equipment via `calculateTotalBonus(player)` (handles armor-in-hotbar, future custom slots)
   - Applies: `newValue = base + currentBonus` (respects `equipmentAffectsMax` flag)
   - Min-1-HP guard for life attribute
   - Resource attributes: `fillMax()` to set currentValue to maxValue
   - Updates `EquipmentData.EQUIPMENT_BONUS` tracking attachment
   - Syncs all attributes to client (note: clone sync is safe here because rescan writes to the new entity's attachments directly)

#### Common Post-Death Flow (both modes)

4. **`PlayerEvent.PlayerRespawnEvent`** — Syncs all attributes to client. This event fires AFTER the client has created the new player entity, so packets are received correctly.
5. **`PlayerEvent.PlayerLoggedInEvent`** — Full sync of all attributes to client on join. Also calls `restoreBonusTracking()` to initialize equipment bonus tracking and `syncVanillaHealth()` to sync vanilla health bar.
6. **`PlayerEvent.PlayerLoggedOutEvent`** — Cleans up any residual death snapshot for the disconnecting player (prevents memory leak when players die then disconnect without respawning).

**Known issue:** NeoForge 26.1.2.68-beta's `copyOnDeath()` on `AttachmentType.builder` does not reliably preserve attachment data across death (old entity's attachment map is cleared before Clone fires). The manual snapshot approach in `RPGCraftCore` is the working solution.

### Cross-Module API Stubs (`api/`)

Placeholder interfaces for the planned RPG system: `INpc`, `IProfession`. Each domain will get its own `api/` sub-package as it develops (following the `attribute/api/` and `equipment/api/` patterns). Equipment module already has its API in `equipment/api/`.

### Data Flow

```
Init: EquipmentManager.init() → AttributeManager.init() → DefaultAttributeRegistry registers 11 attributes → DeferredRegisters on modEventBus
Login: PlayerLoggedInEvent → IAttributeRegistry.getAllEntries() → sendToClient() per entry → restoreBonusTracking() → syncVanillaHealth()
Runtime: IAttribute change → sendToClient() → network → client handle() → setMaxValue() + setValue()
Equip: LivingEquipmentChangeEvent → EquipmentManager.getHandler().onEquipmentChange() → calculateTotalBonus (armor-in-hotbar check) → applyBonusDiff (equipmentAffectsMax flag) → sendToClient() (no syncVanillaHealth to avoid hurt animation)
Combat: LivingDamageEvent.Pre → flat damage: bypass→life=0, combat→lookup attacker attackType from MobAttributeConfig→RPG formula with attackType, environmental→vanilla value → apply to custom life → set proportional vanilla damage
CombatPost: LivingDamageEvent.Post → re-sync vanilla health to custom life ratio → checkAndSnapshotIfDying → sendToClient()
Heal: LivingHealEvent → proportional convert vanilla heal → add to custom life → sendToClient()
Death: LivingDeathEvent → createSnapshot + capture equipment bonuses → DeathData(UUID → {snapshot, equipmentBonuses})
Clone-SNAPSHOT: PlayerEvent.Clone → applySnapshot → restore maxValue, fillMax for resources, restore currentValue for abilities → restoreBonusTracking()
Clone-RESCAN: PlayerEvent.Clone → rescanAndApplyAttributes → compute base (snapshot - death bonuses) → scan current equipment → apply current bonuses → fillMax resources → update tracking → sync to client
Respawn: PlayerRespawnEvent → sendToClient() per entry (client has new entity now) → syncVanillaHealth()
Logout: PlayerLoggedOutEvent → deathSnapshot.remove(uuid) (cleanup)
Render-HealthBar: GuiLayer (replaces VanillaGuiLayers.PLAYER_HEALTH) → rounded gradient progress bar with current/max text
Render-Attributes: GuiLayer (above all) → IAttributeRegistry.getAllEntries() → IAttribute from client attachment → text()
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

**Visual compatibility (BlockBench, custom models/textures):** The equipment API is a pure data layer — it only maps `Identifier` → attribute bonuses + rarity. It has zero involvement with item registration, model JSON, texture PNGs, or rendering. Sub-modules handle all visual concerns (BlockBench exports, resource packs, `Equippable` data components) independently through standard NeoForge mechanisms. The core's `EquipmentTooltipHandler` automatically queries the registry for tooltip display regardless of item source. This separation means BlockBench models/textures are fully compatible — the equipment system neither knows nor cares about visual representation.

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
- Death/respawn has two modes controlled by `DeathAttributeMode` enum: `SNAPSHOT` (default, restore from death snapshot verbatim) and `RESCAN` (strip death equipment bonuses, recalculate from current equipment). Toggled via `/rpg deathmode <snapshot|rescan>`.
- Death data is stored as `DeathData(AttributeSnapshot, Map<String, EquipmentBonus>)` — captures both attribute values and active equipment bonuses at death time. Equipment bonuses are needed by RESCAN mode to compute base values.
- In RESCAN mode, base value = snapshot value − death equipment bonus. This is correct because snapshot values include equipment bonuses at the time of death.
- `calculateTotalBonus()` skips armor items in hand slots: checks `DataComponents.EQUIPPABLE` for `HUMANOID_ARMOR` type. Only weapons apply bonuses from hand slots. This is compatible with future custom equipment slots — the method iterates `EquipmentSlot.values()`.
- `applyBonusDiff()` does NOT call `syncVanillaHealth()` — this prevents hurt animation when equipping/unequipping armor that changes max life. The custom health bar reads from the custom life attribute (synced via packet), not vanilla health.
- Damage is flat (not proportional): environmental damage uses vanilla value directly, combat damage uses RPG formula output. Proportional conversion only happens when syncing to vanilla health (to keep vanilla healing working).
- The custom health bar replaces `VanillaGuiLayers.PLAYER_HEALTH` via `RegisterGuiLayersEvent.replaceLayer()`. Life attribute is included in the text HUD overlay (not filtered out).
- `AttributeHudOverlay` uses manual `fillRounded()`/`fillRoundedGradient()` methods (multiple `fill()` calls) — NeoForge 26.1 has no native rounded rectangle API.
- Death/respawn attribute preservation uses the `AttributeSnapshot` API (`createSnapshot` on death → `applySnapshot` on clone → sync on respawn), not `copyOnDeath()`, because NeoForge 26.1.2.68-beta clears old entity attachments before the Clone event fires.
- `shouldResetOnRespawn` is orthogonal to `isCapped`: resource attributes (life, skill_point, magic_point) reset to max via `fillMax()`; ability attributes (strength, defense, etc.) preserve pre-death values.
- `/rpg set` only modifies currentValue (clamped by existing maxValue). `/rpg setmax` modifies maxValue (may clamp currentValue down). These are separate concerns.
- `SyncPlayerAttributePacket.handle()` sets both `setMaxValue` and `setValue` on the client attachment. Both must be applied — omitting `setMaxValue` causes client-side max display to desync.
- Code comments and language keys are in Chinese (zh_CN).
- Division by zero guards: all proportional calculations (`customValue / customMax * vanillaMax`) check `maxValue > 0` before division. Applies to `CombatEventHandler`, `AttributeManager.syncVanillaHealth()`, and `AttributeHudOverlay`.
- `EquipmentBonus.add()` uses overflow-safe saturating addition — clamps to `Integer.MAX_VALUE`/`Integer.MIN_VALUE` instead of wrapping. Prevents negative bonuses from stacking many high-value equipment items.
- RESCAN base value computation clamps to `≥ 0` (`Math.max(0, snapshotValue - deathBonus)`) — prevents attribute corruption when death snapshot and equipment bonuses are inconsistent (e.g., `/reload` changed equipment config between death and respawn).
- Mob attack type is configurable per entity in `mob_attributes.json` via `"attack_type"` field (maps to `AttackType` enum). Missing field defaults to `PHYSICAL` for backward compatibility. In combat, `CombatEventHandler` looks up the attacker's `attackType` from config and passes it to both `calculateOutgoingDamage()` and `calculateIncomingDamage()`. This determines which damage formula is used (physical: strength base, defense reduction; magic: mana base, resistance % reduction).
