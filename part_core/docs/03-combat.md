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

将 vanilla 治疗量按比例转换为自定义 life。有守卫：当 vanilla `getMaxHealth() ≤ 0` 时跳过。使用 `setHealth()`（非 `heal()`）避免与 `syncVanillaHealth()` 形成循环。

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
