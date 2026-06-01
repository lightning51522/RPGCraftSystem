# 命令系统 (Commands)

> 对应源码包：`com.rpgcraft.core.command`

## RPGCommands

`/rpg` 命令族，所有命令都需要 gamemaster 权限（管理员等级）。

### 属性命令

| 命令 | 说明 |
|------|------|
| `/rpg list [player]` | 列出所有属性值。同时显示当前死亡恢复模式。 |
| `/rpg get <attr> [player]` | 查看指定属性的当前值。 |
| `/rpg set <attr> <value> [player]` | 设置属性 currentValue（被 maxValue 钳制）。仅修改 currentValue，不改变 maxValue。 |
| `/rpg setmax <attr> <value> [player]` | 设置属性 maxValue（可能将 currentValue 向下钳制）。 |
| `/rpg reset [player]` | 重置所有属性为默认值。 |

### 死亡模式命令

| 命令 | 说明 |
|------|------|
| `/rpg deathmode <snapshot\|rescan>` | 切换死亡恢复模式。`SNAPSHOT`（原样恢复死亡值）或 `RESCAN`（去除死亡装备加成，从当前装备重新计算）。 |

### 等级命令

| 命令 | 说明 |
|------|------|
| `/rpg level [player]` | 显示当前等级和经验值。 |
| `/rpg setlevel <level> [player]` | 直接设置等级（XP 重置为 0）。 |
| `/rpg addexp <amount> [player]` | 添加经验值（通过 `addExperience()` 触发自动升级）。 |

### 召唤命令

| 命令 | 说明 |
|------|------|
| `/rpg spawn <entity> <level>` | 召唤指定等级的 RPG 生物。`entity` 使用完整 ID（如 `minecraft:zombie`），`level` ≥ 1。仅对 MobAttributeConfig 中配置的 LivingEntity 生效 RPG 属性缩放。 |

- 使用 `BuiltInRegistries.ENTITY_TYPE.keySet()` 提供实体类型补全建议。
- 实体在命令执行者位置生成（玩家或命令方块）。
- 如果实体类型不在 `MobAttributeConfig` 中，仍然正常生成但不会应用 RPG 属性，并显示警告。
- 非 LivingEntity（如矿车、船等）正常生成，但提示不支持 RPG 等级。
- 等级不影响怪物的基础经验值（`base_exp` 保持配置原值）。
- 需要 gamemaster 权限。

## 实现细节

- 使用 `IAttributeRegistry.getAllEntries()` 提供属性名建议。
- 使用 `IAttributeEntry` 进行属性查找。
- `/rpg set` 仅修改 currentValue（被现有 maxValue 钳制）；`/rpg setmax` 修改 maxValue（可能向下钳制 currentValue）。这两个是独立的功能。
- `/rpg list` 还显示当前死亡恢复模式。
- 等级命令需要 gamemaster 权限。
