# 子模块集成 (Sub-Module Integration)

> 适用于基于 `part_core` 开发扩展模组的开发者

## SPI 机制

核心模组使用 Java `ServiceLoader` SPI 在初始化期间发现提供者。三个 SPI 扩展点：

| SPI 接口 | 声明文件 | 功能 |
|----------|---------|------|
| `IAttributeProvider` | `META-INF/services/com.rpgcraft.core.attribute.api.IAttributeProvider` | 注册自定义属性 |
| `IEquipmentProvider` | `META-INF/services/com.rpgcraft.core.equipment.api.IEquipmentProvider` | 注册自定义装备加成 |
| `ILevelProvider` | `META-INF/services/com.rpgcraft.core.level.api.ILevelProvider` | 注册自定义等级 XP 阈值 |

## 可替换策略

三个 Manager 门面支持运行时策略替换：

| 策略接口 | 替换方法 | 默认实现 |
|---------|---------|---------|
| `IDamageCalculator` | `AttributeManager.setDamageCalculator()` | `DefaultDamageCalculator` |
| `IEquipmentHandler` | `EquipmentManager.setHandler()` | `DefaultEquipmentHandler` |
| `ILevelCalculator` | `LevelManager.setLevelCalculator()` | `DefaultLevelCalculator` |

所有遵循相同模式：接口 + 默认实现 + 通过 Manager 门面运行时替换。

## SPI 调用时机

`EquipmentManager.init()`、`AttributeManager.init()` 和 `LevelManager.init()` 都在初始化末尾调用 `ServiceLoader.load()`，在注册自己的默认数据之后。Provider 调用发生在 `RPGCraftCore` 构造函数中 `DeferredRegister.register(modEventBus)` 之前。

## 添加装备物品工作流（以"龙剑"为例）

1. **注册物品** — 在子模块自己的 `@Mod` 类中使用标准 NeoForge `DeferredRegister.Items`。
2. **创建资源** — 模型在 `resources/assets/mymod/models/item/`，纹理在 `resources/assets/mymod/textures/item/`。标准 NeoForge 资源包。
3. **注册 RPG 数据** — 实现 `IEquipmentProvider` 并通过 `ServiceLoader` 注册：
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
4. **声明 SPI** — 创建 `META-INF/services/com.rpgcraft.core.equipment.api.IEquipmentProvider`，包含完全限定类名。
5. **Tooltip 自动生效** — `EquipmentTooltipHandler` 通过 `EquipmentManager.getRegistry()` 查询任何物品，自定义物品无需额外代码即可获得加成行和稀有度着色。

## 相同模式的扩展

- **自定义属性**：实现 `IAttributeProvider`，声明在 `META-INF/services/com.rpgcraft.core.attribute.api.IAttributeProvider`。
- **自定义等级数据**：实现 `ILevelProvider`，声明在 `META-INF/services/com.rpgcraft.core.level.api.ILevelProvider`。用于注册额外的等级 XP 阈值（例如扩展超过核心配置的最大等级）。

## 视觉兼容性（BlockBench、自定义模型/纹理）

装备 API 是纯数据层 — 它只映射 `Identifier` → 属性加成 + 稀有度。它不涉及物品注册、模型 JSON、纹理 PNG 或渲染。子模块通过标准 NeoForge 机制独立处理所有视觉相关的事务（BlockBench 导出、资源包、`Equippable` 数据组件）。核心的 `EquipmentTooltipHandler` 自动查询注册表进行 tooltip 显示，无论物品来源。这种分离意味着 BlockBench 模型/纹理完全兼容 — 装备系统既不知道也不关心视觉表现。

## 依赖架构

属性系统是基础层 — 所有其他子系统依赖它进行数据存储、查询和共享配置（`MobAttributeConfig`）。依赖层次：

```
属性系统 (attribute)  ← 基础层，无外部依赖
  ↑ 装备系统 (equipment)    — 装备加成修改属性值
  ↑ 战斗系统 (combat)       — 伤害公式读取属性值；查询武器攻击类型（依赖装备）
  ↑ 等级系统 (level)        — 经验事件读取怪物配置中的 level/baseExp（依赖属性）
```

**核心原则：** 可替换接口允许交换**算法**（相同数据 → 不同公式），而非**独立模块**。子模块始终依赖 `part_core` 获取基础设施。

## 子模块最小依赖矩阵

| 目标 | 必需依赖 | 可替换接口 |
|------|---------|-----------|
| 添加新属性 | `part_core` | — (SPI: `IAttributeProvider`) |
| 添加新装备 | `part_core`（含属性） | — (SPI: `IEquipmentProvider`) |
| 自定义伤害公式 | `part_core`（属性数据） | `IDamageCalculator` |
| 自定义装备加成逻辑 | `part_core`（属性系统） | `IEquipmentHandler` |
| 自定义 XP 公式 | `part_core`（等级数据） | `ILevelCalculator` |
| 添加更高等级 | `part_core` | — (SPI: `ILevelProvider`) |
| 闪避/无敌/吸血等战斗机制 | `part_core`（属性+战斗） | `RPGEventBus` 监听器 |

## RPG 事件总线

核心模组提供自定义 RPG 事件总线（`RPGEventBus`），在关键计算点发射事件，允许子模块拦截和修改游戏行为。与 NeoForge 游戏事件总线分离 — RPG 事件是内部通信通道。

### 架构

```
策略接口（单实现，替换公式）
  ↕ 互补
RPG 事件总线（多监听共存，附加效果/拦截）
```

- **策略接口**：替换核心公式 — 伤害公式、经验公式等 — 一个接口只有一个活跃实现
- **RPG 事件**：附加效果和拦截 — 闪避、吸血、反伤等 — 多个监听器共存

### 事件类型

#### 战斗事件

| 事件类 | 触发时机 | 可取消 | 可修改 | 用途 |
|--------|----------|--------|--------|------|
| `RPGDamageEvent.Pre` | 伤害计算前 | ✅ | attackType, damage | 闪避、无敌、伤害类型修改 |
| `RPGDamageEvent.Post` | 伤害应用后 | — | — (只读) | 吸血、反伤、连击触发 |

#### 伤害流程中的事件位置

```
CombatEventHandler.onLivingDamagePre():
  1. 不可减免伤害检查（虚空/kill，不触发事件）
  2. RPGDamageEvent.Pre → 子模块可取消/修改
  3. IDamageCalculator → 公式计算（策略模式）
  4. 应用伤害到 LIFE
  5. RPGDamageEvent.Post → 子模块追加效果
  6. 同步原版生命条
```

### 注册监听器

```java
// 在子模块初始化时（如 FMLCommonSetupEvent）
RPGEventBus.register(RPGDamageEvent.Pre.class, event -> {
    // 闪避逻辑
    if (shouldDodge(event.getTarget())) {
        event.cancel();  // 完全取消伤害
    }
}, RPGEvent.PRIORITY_EARLY);

RPGEventBus.register(RPGDamageEvent.Post.class, event -> {
    // 吸血逻辑
    LivingEntity attacker = event.getAttacker();
    if (attacker != null && hasLifesteal(attacker)) {
        int heal = event.getDamageDealt() * getLifestealRate(attacker) / 100;
        healEntity(attacker, heal);
    }
}, RPGEvent.PRIORITY_NORMAL);
```

### 优先级

| 常量 | 值 | 用途 |
|------|----|------|
| `PRIORITY_FIRST` | 0 | 无敌判定、免疫检查 |
| `PRIORITY_EARLY` | 100 | 闪避、护盾吸收 |
| `PRIORITY_NORMAL` | 200 | 默认 |
| `PRIORITY_LATE` | 300 | 伤害日志 |
| `PRIORITY_LAST` | 400 | 统计、结算 |

优先级数值越小越先执行。相同优先级按注册顺序执行。

### 设计原则

1. **事件不中断监听链**：即使事件被 `cancel()`，后续监听器仍被通知（以便日志/清理）
2. **核心模组检查取消**：`RPGEventBus.post()` 后检查 `isCancelled()` 决定是否跳过默认行为
3. **异常隔离**：单个监听器异常不影响其他监听器，错误记录到日志

1. 始终依赖 `part_core` — 它提供所有基础设施（attachment、序列化、网络同步、HUD、命令）。
2. 通过 SPI 注册数据（`IAttributeProvider`、`IEquipmentProvider`、`ILevelProvider`）。
3. 通过 Manager 门面注入自定义策略（`setDamageCalculator()`、`setHandler()`、`setLevelCalculator()`）。
4. 通过 `RPGEventBus` 注册事件监听器实现附加效果（闪避、吸血、反伤等）。
5. 仅引用 `api/` 包接口 — 绝不引用 `DefaultXxx` 具体类。
6. 纯数据扩展（新属性、新装备、新等级）可以完全通过 JSON + SPI 驱动，零算法代码。
