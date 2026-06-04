# 战斗系统 (Combat System)

> 对应源码包：`com.rpgcraft.core.combat`

## CombatEventHandler

`@EventBusSubscriber` 在游戏总线上：

### `onEntityJoinLevel()`

仅服务端（含 `isClientSide()` 守卫）。从 `MobAttributeConfig` JSON 设置怪物属性。

**持久化守卫**：检查 `MobLevelData.isInitialized()`。如果为 `true`（实体从存档加载），跳过重新初始化，保留已有属性值（包括受伤后的当前生命值）。

**初始化流程**（`initialized=false` 的新实体）：
1. 若 `MobLevelData.isSet()`（指令预设等级）→ 使用指定等级和评级
2. 若随机刷新开启（`/rpg randspawn on`）且实体有权重配置 → 从权重表随机选择等级和评级
3. 否则 → 使用配置静态等级 + NORMAL 评级

初始化完成后设置 `initialized=true`。

### `onLivingDamagePre()` — 扁平伤害系统（非比例）

三条伤害路径：

1. **穿透**（`BYPASSES_INVULNERABILITY`）：将自定义 life 设为 0，传递 vanilla 伤害以触发死亡。
2. **战斗**（攻击者是 `LivingEntity`）：根据攻击者类型确定 `AttackType`：
   - 玩家：使用手持武器的 `attack_type`，来自 `IEquipmentRegistry.getAttackType()`
   - 怪物：使用 `MobAttributeConfig` 中的配置
   - 默认 `PHYSICAL`。通过 `IDamageCalculator` 的 RPG 公式产生绝对伤害值，使用配置的攻击类型直接应用到自定义 life。
3. **环境**（无攻击者）：vanilla 伤害值直接应用到自定义 life（不按最大生命缩放）。

在自定义 life 变更后，vanilla 伤害设为比例值以同步 vanilla health（保持 vanilla 治疗通过 `LivingHealEvent` 工作）。有守卫：当 `maxValue ≤ 0` 时跳过比例计算以防止除零。

### `onLivingDamagePost()`

重新同步 vanilla health 以匹配自定义 life 比例（处理吸收边界情况）。有守卫：当 `maxValue ≤ 0` 时跳过。调用 `checkAndSnapshotIfDying()` 进行早期死亡快照创建。

### `onLivingHeal()`

将 vanilla 治疗量按比例转换为自定义 life，通过 RPG 治疗事件系统拦截。有守卫：当 vanilla `getMaxHealth() ≤ 0` 时跳过。

**事件流程：**
1. 将原版治疗量按比例转换为自定义值
2. 发射 `RPGHealEvent.Pre`（`healer=null`, `source=VANILLA`）— 子模块可取消/修改治疗量
3. 若取消 → 取消原版事件，返回
4. 应用治疗到自定义 LIFE
5. 发射 `RPGHealEvent.Post`（`actualHealed`）— 子模块追加效果
6. 取消原版事件（`event.setAmount(0)`）防止原版重复治疗
7. `syncVanillaHealth()` + `sendToClient()` 同步

使用 `setHealth()`（非 `heal()`）同步原版血条，避免与 `syncVanillaHealth()` 形成循环。

### `healEntity()` — 自定义治疗公共 API

```java
public static int healEntity(LivingEntity target, int healAmount, @Nullable LivingEntity healer)
```

供子模块和内部系统触发的模组自定义治疗。固定使用 `HealSource.CUSTOM`。

**事件流程与 `onLivingHeal()` 相同**（发射 Pre/Post 事件），但：
- 不取消原版事件（不走原版路径）
- 对非玩家实体也正常工作（但不同步网络包）
- 返回实际治疗量（经事件修改和上限钳制后的值）

## RPGHealEvent — 治疗事件

> 对应源码：`com.rpgcraft.core.event.combat.RPGHealEvent`

治疗事件在治疗流程的关键节点发射，允许子模块拦截和修改治疗效果。

### HealSource 枚举

| 值 | 含义 |
|----|------|
| `VANILLA` | 原版治疗（饱食度、药水等通过 `LivingHealEvent` 触发） |
| `CUSTOM` | 模组自定义治疗（通过 `healEntity()` API 触发） |

### Pre 事件（治疗应用前）

| 字段 | 类型 | 可修改 | 说明 |
|------|------|--------|------|
| `healer` | `@Nullable LivingEntity` | — | 治疗者（null = 自然回复） |
| `target` | `LivingEntity` | — | 被治疗目标 |
| `healSource` | `HealSource` | ✅ | 治疗来源类型 |
| `healAmount` | `int` | ✅ (≥ 0) | 治疗量（自定义生命值的扁平值） |

可取消。使用场景：禁疗 debuff、治疗减免/加成。

### Post 事件（治疗应用后）

| 字段 | 类型 | 说明 |
|------|------|------|
| `healer` | `@Nullable LivingEntity` | 治疗者 |
| `target` | `LivingEntity` | 被治疗目标 |
| `healSource` | `HealSource` | 治疗来源类型 |
| `actualHealed` | `int` | 实际治疗量（经过 maxValue 钳制后的真实增量） |

不可取消（治疗已生效）。使用场景：过量治疗转化护盾、治疗触发 buff、治疗统计。

### 治疗流程图

```
原版治疗路径 (LivingHealEvent):
  onLivingHeal()
    → 比例转换 vanilla 治疗量
    → RPGHealEvent.Pre (healer=null, source=VANILLA)
      → 子模块可取消/修改治疗量
    → 应用到自定义 LIFE
    → RPGHealEvent.Post (actualHealed)
      → 子模块追加效果
    → event.setAmount(0) 阻止原版重复治疗
    → syncVanillaHealth() + sendToClient()

自定义治疗路径 (API):
  CombatEventHandler.healEntity(target, amount, healer)
    → RPGHealEvent.Pre (healer, source=CUSTOM)
      → 子模块可取消/修改治疗量
    → 应用到自定义 LIFE
    → RPGHealEvent.Post (actualHealed)
      → 子模块追加效果
    → syncVanillaHealth() + sendToClient() [仅 ServerPlayer]
    → return actualHealed
```

## MobLevelData — 怪物数据持久化

`MobLevelData` 附件通过 `MapCodec` 序列化到实体 NBT，确保指令召唤的自定义怪物跨 chunk 重载持久化。

**序列化字段**：`level`, `base_exp`, `attack_type`(nullable), `rating`, `initialized`

**initialized 标志**：`true` 表示属性已完成初始化。`EntityJoinLevelEvent` 中，`initialized=true` 的实体跳过重新初始化，从而保留：
- 自定义等级、评级、攻击类型覆盖、经验覆盖
- 受伤后的当前生命值（不会被重置为满血）

## 随机刷新系统

自然刷新的怪物可从权重表中随机选择等级和评级。默认关闭，通过 `/rpg randspawn on` 开启。

**权重表配置**：在 `mob_attributes.json` 中可选的 `spawn` 段（详见 `docs/04-level.md`）。

**查找顺序**：
1. 随机刷新开启 → 查找实体类型的 `spawn` 分布配置
2. 有配置 → `weightedRandomLevel()` + `weightedRandomRating()` 随机选择
3. 无配置 → 使用静态 `level` 字段 + NORMAL 评级

## 关键约定（战斗相关）

- 伤害是扁平的（非比例）：环境伤害直接使用 vanilla 值，战斗伤害使用 RPG 公式输出。比例转换仅在同步到 vanilla health 时发生（以保持 vanilla 治疗工作）。
- 怪物攻击类型在 `mob_attributes.json` 中通过 `"attack_type"` 字段按实体配置（映射到 `AttackType` 枚举）。缺失字段默认为 `PHYSICAL`。
- 玩家武器攻击类型在 `equipment_attributes.json` 中通过相同 `"attack_type"` 字段按物品配置，通过 `IEquipmentRegistry.getAttackType()` 查询。
- `CombatEventHandler` 分支处理：玩家使用手持武器的攻击类型，怪物使用配置的攻击类型。这决定使用哪种伤害公式（物理：力量基础，防御减免；魔法：法力基础，抗性百分比减免）。
- 所有比例计算在除法前检查 `maxValue > 0` 以防止除零。
- `MobLevelData` 序列化到实体 NBT。`initialized=true` 的实体在 `EntityJoinLevelEvent` 中跳过重新初始化，保留自定义属性值。
