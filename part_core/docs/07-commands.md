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
| `/rpg spawn <entity> <level> {json}` | 召唤自定义属性的 RPG 生物。JSON 中指定的属性作为最终值直接应用（不经过等级缩放），未指定的属性使用配置默认值 + 等级缩放。 |

### HUD 命令

| 命令 | 说明 |
|------|------|
| `/rpg hud` | 显示当前 HUD 开关状态。 |
| `/rpg hud on` | 开启 HUD（左上角属性面板 + 准星提示）。 |
| `/rpg hud off` | 关闭 HUD（左上角属性面板 + 准星提示）。自定义生命条不受影响，始终显示。 |

- HUD 开关状态按玩家独立存储，服务端和客户端同步。
- 需要管理员权限（gamemaster）。

### 随机刷新命令

| 命令 | 说明 |
|------|------|
| `/rpg randspawn` | 显示当前随机刷新开关状态。 |
| `/rpg randspawn on` | 开启自然刷新随机等级/评级（从 `mob_attributes.json` 的 `spawn` 权重表中随机选择）。 |
| `/rpg randspawn off` | 关闭随机刷新（默认）。所有自然刷新使用配置静态等级 + NORMAL 评级。 |

- 全局开关（非按玩家），影响整个服务端。
- 默认关闭。开启后，自然生成的怪物会根据 `mob_attributes.json` 中配置的 `spawn.level_weights` 和 `spawn.rating_weights` 随机选择等级和评级。
- 无 `spawn` 配置的实体类型不受影响，仍使用静态等级。
- `/rpg spawn` 指令召唤的实体不受此开关影响，始终使用指令指定的等级和评级。
- 需要管理员权限（gamemaster）。

- 使用 `BuiltInRegistries.ENTITY_TYPE.keySet()` 提供实体类型补全建议。
- 实体在命令执行者位置生成（玩家或命令方块）。
- 如果实体类型不在 `MobAttributeConfig` 中，仍然正常生成但不会应用 RPG 属性，并显示警告。
- 非 LivingEntity（如矿车、船等）正常生成，但提示不支持 RPG 等级。
- 等级不影响怪物的基础经验值（`base_exp` 保持配置原值）。
- 需要 gamemaster 权限。

#### JSON 属性覆盖语法

JSON 格式与 `mob_attributes.json` 配置文件内层结构一致。所有字段均为可选：

```json
{
  "attack_type": "MAGIC",
  "base_exp": 500,
  "life": 1000,
  "strength": 200,
  "defense": 50,
  "resistance": 30,
  "critical_rate": 80,
  "critical_ratio": 200
}
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `attack_type` | 字符串 | 攻击类型覆盖：`PHYSICAL`、`MAGIC`、`PHYSICAL_WITH_MAGIC`、`MAGIC_WITH_PHYSICAL`、`MIX_TYPE` |
| `rating` | 字符串 | 评级覆盖：`NORMAL`、`STRONG`、`ELITE`、`NOTORIOUS_ELITE`、`BOSS`、`LORD`（详见下表） |
| `base_exp` | 整数 | 击杀经验覆盖（覆盖配置的 `base_exp`） |
| `life` | 整数 | 生命值（最终值，不缩放） |
| `strength` | 整数 | 力量（最终值，不缩放） |
| `defense` | 整数 | 防御力（最终值，不缩放） |
| `resistance` | 整数 | 法抗（最终值，不缩放） |
| `critical_rate` | 整数 | 暴击率（最终值，不缩放） |
| `critical_ratio` | 整数 | 暴击伤害（最终值，不缩放） |

**语义**：
- JSON 中的数值属性（`life`、`strength` 等）在评级之前计算，作为"基础值"参与后续评级倍率。
- 计算流程：配置基础值 → 等级缩放 → JSON 覆盖 → 评级倍率 → 最终值。
- 未在 JSON 中指定的属性使用配置默认值 + 按 `<level>` 参数缩放。
- `attack_type` 和 `base_exp` 覆盖存储在 `MobLevelData` 附件中，在伤害计算和经验计算时优先读取。
- `rating` 评级对所有属性值（包括 JSON 覆盖的值）最后乘以对应倍率。
- JSON 语法错误或未知字段会返回错误提示。

#### 评级系统

| 枚举名 | 中文显示 | 全属性倍率 |
|--------|---------|-----------|
| `NORMAL` | 普通 | 1.0x |
| `STRONG` | 强壮 | 1.25x |
| `ELITE` | 精英 | 1.5x |
| `NOTORIOUS_ELITE` | 恶名精英 | 2.0x |
| `BOSS` | 头目 | 3.0x |
| `LORD` | 领主 | 5.0x |

- 默认评级为 `NORMAL`（1.0x，不影响属性）。
- 评级倍率在所有其他计算（等级缩放、JSON 覆盖）之后应用。
- 评级存储在 `MobLevelData` 附件中，准星提示中显示评级名称。

**示例**：
```
/rpg spawn minecraft:zombie 5
/rpg spawn minecraft:zombie 5 {"life":500}
/rpg spawn minecraft:zombie 5 {"rating":"ELITE"}
/rpg spawn minecraft:zombie 10 {"rating":"BOSS","life":500,"base_exp":500,"attack_type":"MAGIC"}
```

## 实现细节

- 使用 `IAttributeRegistry.getAllEntries()` 提供属性名建议。
- 使用 `IAttributeEntry` 进行属性查找。
- `/rpg set` 仅修改 currentValue（被现有 maxValue 钳制）；`/rpg setmax` 修改 maxValue（可能向下钳制 currentValue）。这两个是独立的功能。
- `/rpg list` 还显示当前死亡恢复模式。
- 等级命令需要 gamemaster 权限。
