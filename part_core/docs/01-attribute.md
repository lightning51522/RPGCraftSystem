# 属性系统 (Attribute System)

> 对应源码包：`com.rpgcraft.core.attribute`

## API 层 (`attribute/api/`)

公开接口，供其他模组依赖。每个功能模块在各自的域下拥有自己的 `api/` 子包：

- **`IAttribute`** — 属性值契约：`getValue()`, `setValue()`, `getMaxValue()`, `setMaxValue()`, `hasMaxValue()`, `fillMax()`。
- **`IAttributeEntry`** — 已注册属性的元数据：`getId()`, `getDisplayName()`, `getSupplier()`, `getDefaultValue()`, `getDefaultMaxValue()`, `isCapped()`, `shouldResetOnRespawn()`, `equipmentAffectsMax()`。
- **`IAttributeRegistry`** — 注册、查找和迭代：`register()`（4参数、5参数含 `resetOnRespawn`、6参数含 `resetOnRespawn` + `equipmentAffectsMax`），`getEntry()`, `getTypeById()`, `getAllEntries()`, `getAttribute()`, `createSnapshot()`, `applySnapshot()`。
- **`AttributeSnapshot`** — 所有属性值的不可变快照。包含嵌套 `record AttributeData(int currentValue, int maxValue, String displayName)` 用于自描述的逐属性数据。由 `createSnapshot()` 创建，`applySnapshot()` 恢复。
- **`IDamageCalculator`** — 基于属性的伤害公式契约：`calculateIncomingDamage()`, `calculateOutgoingDamage()`。扩展模组可通过 `AttributeManager.setDamageCalculator()` 替换。
- **`IDamageType`** — 伤害类型契约：`getName()`, `isPhysical()`, `isMagic()`。由 `AttackType` 枚举实现。
- **`IAttributeProvider`** — SPI，供模组注册自定义属性：`registerAttributes(IAttributeRegistry)`。

## 默认实现 (`attribute/`)

- **`EntityAttribute`** — `IAttribute` 默认实现。值对象，含 `currentValue` 和 `maxValue`，变更时 `Math.clamp`，`MapCodec` 用于存档序列化。`fillMax()` 将 currentValue 直接设置为 maxValue（用于资源属性重生重置）。
- **`DefaultAttributeRegistry`** — `IAttributeRegistry` 默认实现。管理 `DeferredRegister<AttachmentType<?>>`、`Map<Identifier, DefaultEntry>` 和缓存条目列表。内部 `DefaultEntry` 类实现 `IAttributeEntry`，包括 `shouldResetOnRespawn()` 和 `equipmentAffectsMax()`。提供 `getRawSupplier(Identifier)` 用于直接 Supplier 访问（性能优化）。独立的 `respawnResetEntries` 列表跟踪资源属性。
- **`DefaultDamageCalculator`** — `IDamageCalculator` 默认实现。基于属性的伤害公式：
  - 物理受击：`max(0, originalDamage - defense)`
  - 魔法受击：`originalDamage * (1 - resistance%)`
  - 物理输出：基础 = 力量，暴击检查，暴击加成
  - 魔法输出：基础 = 法力，暴击检查，暴击加成
- **`AttackType`** — 实现 `IDamageType` 的枚举：`PHYSICAL`, `MAGIC`, `PHYSICAL_WITH_MAGIC`, `MAGIC_WITH_PHYSICAL`, `MIX_TYPE`。仅 `PHYSICAL` 和 `MAGIC` 在伤害计算中实现。
- **`AttributeManager`** — 属性模块门面（与 `EquipmentManager` 命名一致）。持有 `Identifier` 常量和 `Supplier<AttachmentType<EntityAttribute>>` 访问器，委托给 `DefaultAttributeRegistry` 和 `DefaultDamageCalculator`。必须在将 `DeferredRegister` 注册到模组事件总线之前调用 `init()`。`getRegistry()` 返回 `IAttributeRegistry` 接口；`getDeferredRegister()` 是便捷静态方法。
- **`MobAttributeConfig`** — 加载 `data/rpgcraftcore/rpg/mob_attributes.json` 的怪物属性预设。支持 `/reload` 热重载。`MobAttributes` record 包含 `attackType`（AttackType 枚举，默认 `PHYSICAL`）、`level`（int，默认 1）、`baseExp`（int，默认 100）以及数值属性。
- **`DeathAttributeMode`** — 控制死亡/重生属性恢复的枚举：`SNAPSHOT`（原样恢复死亡值）或 `RESCAN`（从基础值 + 当前装备重新计算）。静态 `currentMode` 字段，含 getter/setter。默认：`SNAPSHOT`。通过 `/rpg deathmode` 命令切换。

## 属性分类

- **资源属性**（有上限 + 重生重置）：life(100), skill_point(100), magic_point(100)。重生时通过 `fillMax()` 恢复至最大值。
- **能力属性**（重生保留）：strength(10), mana(10), agile(10), precision(10), defense(10), critical_ratio(50)。保留死亡前的值。
- **有上限能力属性**（有上限但保留）：resistance(2→100), critical_rate(5→100)。有最大值但重生时保留值。
- 注意：`isCapped()` 和 `shouldResetOnRespawn()` 是正交的 — resistance/critical_rate 有上限但不在重生时重置。
- 注意：`equipmentAffectsMax` 与上述两者正交 — 仅 `life` 的 `equipmentAffectsMax=true`（装备加成仅改变最大值；装备提高上限，卸装备降低上限并按需钳制当前值）。所有其他属性默认 `equipmentAffectsMax=false`（装备加成直接改变当前值）。此设计防止"卸装-重装回血漏洞"。

## 关键约定（属性相关）

- 新属性应通过 `AttributeManager.init()` 中的 `IAttributeRegistry.register()` 注册。`Identifier` 路径必须匹配 DeferredRegister 条目名称。
- 伤害公式可通过 `AttributeManager.setDamageCalculator(IDamageCalculator)` 运行时替换。
- 自定义属性来自子模块，使用 `IAttributeProvider` SPI（Java `ServiceLoader`），声明在 `META-INF/services/com.rpgcraft.core.attribute.api.IAttributeProvider`。
- `equipmentAffectsMax` 在 `IAttributeEntry` 上决定装备加成是仅影响最大值（true，如 life）还是仅影响当前值（false，默认）。在属性注册时设置，不在装备 JSON 中。
- `shouldResetOnRespawn` 与 `isCapped` 正交：资源属性（life, skill_point, magic_point）通过 `fillMax()` 重置为最大值；能力属性（strength, defense 等）保留死亡前的值。
- `SyncPlayerAttributePacket.handle()` 同时设置 `setMaxValue` 和 `setValue`。两者都必须应用 — 省略 `setMaxValue` 会导致客户端最大值显示不同步。
- 所有比例计算（`customValue / customMax * vanillaMax`）在除法前检查 `maxValue > 0`。适用于 `CombatEventHandler`、`AttributeManager.syncVanillaHealth()` 和 `AttributeHudOverlay`。
