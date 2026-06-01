# 装备系统 (Equipment System)

> 对应源码包：`com.rpgcraft.core.equipment`

装备加成是 JSON 驱动的，API 层遵循与属性模块相同的 `api/` 模式。

## API 层 (`equipment/api/`)

- **`IEquipmentRegistry`** — 装备加成注册和查找：`register(itemId, bonuses)`, `register(itemId, bonuses, rarity)`, `getBonuses(itemId)`, `getRarity(itemId)`, `getAttackType(itemId)`。默认实现：`DefaultEquipmentRegistry`。
- **`IEquipmentHandler`** — 可替换的装备加成应用逻辑：`calculateTotalBonus(player)`, `onEquipmentChange(player)`, `restoreBonusTracking(player)`, `rescanAndApplyAttributes(player, deathSnapshot, deathEquipmentBonuses)`。默认实现：`DefaultEquipmentHandler`。可通过 `EquipmentManager.setHandler()` 替换。
- **`IEquipmentProvider`** — SPI，供子模组注册自定义装备加成：`registerEquipment(IEquipmentRegistry)`。

## 数据类型

- **`EquipmentBonus`** — `record EquipmentBonus(int value)`。纯整数加成，含溢出安全的 `add()` 用于堆叠（饱和加法，溢出时钳制到 `Integer.MAX_VALUE`/`Integer.MIN_VALUE`）和 `ZERO` 常量。
- **`EquipmentRarity`** — 六个等级的枚举：`COMMON`(白色), `UNCOMMON`(绿色), `RARE`(蓝色), `EPIC`(紫色), `LEGENDARY`(金色), `MYTHIC`(红色)。每个有 `displayName`（中文）和 `colorCode`（MC 格式代码）。静态 `fromName(String)` 用于 JSON 解析。

## 默认实现

- **`DefaultEquipmentRegistry`** — 实现 `IEquipmentRegistry`。通过 `loadFromJson(JsonObject)` 从 JSON 加载，也支持编程式 `register()`。存储独立的映射：加成（`configMap`）、稀有度（`rarityMap`）和攻击类型（`attackTypeMap`）。JSON 使用 `"rarity"` 和 `"attack_type"` 键（两者在属性解析期间跳过）。
- **`DefaultEquipmentHandler`** — 实现 `IEquipmentHandler`。构造函数接收 `IEquipmentRegistry`。核心逻辑：
  - `calculateTotalBonus()`：遍历所有装备槽位，查询注册表，通过 `EquipmentBonus.add()` 按属性累加加成。**跳过手持槽位中的护甲物品** — 检查 `DataComponents.EQUIPPABLE` 组件：如果物品有目标为 `HUMANOID_ARMOR` 类型的 `Equippable` 数据且位于 `HAND` 类型槽位，则跳过。确保护甲仅在护甲槽位时应用加成，而武器可从手持槽位生效。
  - `onEquipmentChange()`：计算新旧总值的差异，通过 `applyBonusDiff()` 应用。使用 `equipmentAffectsMax` 标志：为 `true` 时仅 maxValue 变化（装备提高上限，卸装备降低上限并在超过新上限时钳制 currentValue）；为 `false` 时仅 currentValue 变化。最小 1 HP 保障防止因卸下增加生命的装备而死亡。**不调用** `syncVanillaHealth()` — 防止装备穿脱时的受伤动画。
  - `restoreBonusTracking()`：重新计算并存储 `EquipmentData.EQUIPMENT_BONUS` attachment 中的跟踪数据。
  - `rescanAndApplyAttributes()`：用于 RESCAN 死亡模式。计算基础值（死亡快照减去死亡装备加成，钳制到 `≥ 0` 以防止不一致状态导致的损坏），通过 `calculateTotalBonus()` 扫描当前装备，将当前加成应用于基础值。资源属性 `fillMax()`，更新跟踪 attachment，同步所有属性到客户端。兼容未来的自定义装备槽位，因为它委托槽位迭代给 `calculateTotalBonus()`。
- **`EquipmentManager`** — 门面（镜像 `AttributeManager`）。静态 `init()` 创建默认值。`getRegistry()`, `getHandler()`, `setHandler()` 供子模块替换。

## 胶水代码

- **`EquipmentConfig`** — `@EventBusSubscriber`，注册服务端重载监听器（`AddServerReloadListenersEvent`）。委托 JSON 解析给 `DefaultEquipmentRegistry.loadFromJson()`。
- **`EquipmentEventHandler`** — `@EventBusSubscriber`，监听 `LivingEquipmentChangeEvent`。委托给 `EquipmentManager.getHandler().onEquipmentChange()`。
- **`EquipmentData`** — 注册 `EQUIPMENT_BONUS` attachment（`Map<String, EquipmentBonus>`，非序列化）。跟踪每个玩家已应用的加成，用于差异计算。

## 装备 JSON 格式

`data/rpgcraftcore/rpg/equipment_attributes.json`：
```json
{
  "minecraft:diamond_sword": {
    "rarity": "rare",
    "attack_type": "physical",
    "rpgcraftcore:strength": 10,
    "rpgcraftcore:critical_rate": 5
  }
}
```
- 顶层键：物品标识符。内层键：`"rarity"`（可选，映射到 `EquipmentRarity` 枚举名）、`"attack_type"`（可选，映射到 `AttackType` 枚举名，默认 `PHYSICAL`）+ 属性标识符及整数加成值。
- 支持 `/reload` 服务端热重载；客户端通过 `AddClientReloadListenersEvent` 重载用于 tooltip 显示。

## Tooltip 显示 (`client/EquipmentTooltipHandler`)

客户端 `@EventBusSubscriber(Dist.CLIENT)`：
- 注册客户端重载监听器以加载装备配置用于 tooltip 渲染。
- `ItemTooltipEvent` 时：通过 `EquipmentManager.getRegistry()` 查找物品。若物品有加成：
  - 非 COMMON 稀有度：用稀有度的 `colorCode` 给物品名称着色，在名称下方插入 `[稀有度]` 标签行。
  - 追加绿色加成行：`"§a属性名 +数值"`。

## 关键约定（装备相关）

- 装备加成可通过 `EquipmentManager.getRegistry().register()` 编程式注册，在 `equipment_attributes.json` 中配置，或通过 `IEquipmentProvider` SPI（Java `ServiceLoader`）。
- 卸装备导致 currentValue 被钳制到 1 以下（针对 life）时，处理器强制设为 1 以防止玩家死亡。
- `calculateTotalBonus()` 跳过手持槽位中的护甲：检查 `DataComponents.EQUIPPABLE` 的 `HUMANOID_ARMOR` 类型。仅武器从手持槽位应用加成。
- `applyBonusDiff()` 不调用 `syncVanillaHealth()` — 防止穿脱改变最大生命的护甲时出现受伤动画。自定义血条从自定义 life 属性（通过包同步）读取，而非 vanilla health。
- `EquipmentBonus.add()` 使用溢出安全的饱和加法 — 溢出时钳制到 `Integer.MAX_VALUE`/`Integer.MIN_VALUE` 而非回绕。
- RESCAN 基础值计算钳制到 `≥ 0`（`Math.max(0, snapshotValue - deathBonus)`）— 防止死亡快照和装备加成不一致时的属性损坏。
