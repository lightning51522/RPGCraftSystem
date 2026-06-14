# RPGCraftSystem

> 一套基于 **微内核 + 插件** 架构的 Minecraft RPG 核心系统模组。
> Minecraft **26.1.2** / NeoForge **26.1.2** / Java **25**

[![Status](https://img.shields.io/badge/status-0.2.0--alpha-orange)](#)
[![Minecraft](https://img.shields.io/badge/minecraft-26.1.2-brightgreen)](#)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2-blue)](#)
[![Java](https://img.shields.io/badge/Java-25-red)](#)

---

##  项目说明
### 碎碎念
&emsp;&emsp;本人有一定的软件开发经验，但是日使用的语言是C/C++，几乎没有Java的经验，故本模组采用了大量的AI辅助开发。我个人不是很容易找出里面不健康的部分，所以如果有大佬能指出项目上的缺陷，在此先行谢过。
&emsp;&emsp;&emsp;这个模组在我的想象中应该是一个所有子系统都可插拔自定义的RPG系统核心，计划中包括实体属性、实体等级、职业系统、词条系统、附魔/宝石系统、副本系统等等很多子系统，通过一个核心将这些系统连接起来形成一个完整的传统RPG系统。不过理想和现实总是有很大的差距，先写一步是一步。

---

##  模块组成

```
RPGCraftSystem/
├── core/         微内核：接口 + 属性管线 + 事件总线 + 快照 SPI + RPGSystems 注册门面
├── attributes/   13 个默认 RPG 属性 + 战斗（伤害公式 / 怪物初始化 / 治疗 / 命令）
├── leveling/     等级 / 经验 / 怪物属性按等级缩放
├── equipment/    装备加成（修饰符模式） + 装备追踪
├── profession/   职业注册 + 职业切换加成
└── client/       HUD / 角色信息界面 / Tooltip / UI 插件系统
```

| 模块 | mod ID | 入口类 | 依赖 |
|------|--------|--------|------|
| core | `rpgcraftcore` | `RPGCraftCore` | — |
| attributes | `rpgcraftcore-attributes` | `AttributesMod` | core |
| leveling | `rpgcraftleveling` | `LevelingMod` | core |
| equipment | `rpgcraftequipment` | `EquipmentMod` | core |
| profession | `rpgcraftprofession` | `ProfessionMod` | core |
| client | `rpgcraftclient` | `ClientMod` | core |

> 插件模块**互不依赖**，跨模块通信全部走 core 的 `RPGSystems` 注册门面。

---

##  架构核心

### RPGSystems 注册门面

每个插件在 `@Mod` 构造函数中向 `RPGSystems` 注册自己的实现，其他模块通过统一接口查询：

| 注册方法 | 注册者 | 接口 |
|---------|---------|------|
| `registerAttributeModule()` | attributes | `IAttributeModule` |
| `registerCombatSystem()` | attributes | `ICombatSystem` |
| `registerLevelSystem()` | leveling | `ILevelSystem` |
| `registerMobDataProvider()` | leveling | `IMobDataProvider` |
| `registerEquipmentSystem()` | equipment | `IEquipmentSystem` |
| `registerAttackTypeResolver()` | equipment | `IAttackTypeResolver` |
| `registerProfessionSystem()` | profession | `IProfessionSystem` |
| `registerClientSystem()` | client | `IClientSystem` |

所有 `register` 方法都接受可选的 `priority` 参数，`OVERRIDE_PRIORITY (100) > DEFAULT_PRIORITY (0)`，第三方模组可借此完全覆盖官方实现：

```java
RPGSystems.registerLevelSystem(myLevelSystem, RPGSystems.OVERRIDE_PRIORITY);
```

### 属性管线（5 阶段）

```
baseValue
  → 累加所有 ADDITION 修饰符
  → AttributePostAdditionEvent
  → MULTIPLY_BASE 修饰符
  → MULTIPLY_TOTAL 修饰符
  → AttributeFinalizeEvent
  → Math.max(0, clamp)
  → 最终值
```

- 装备/职业加成通过 `addModifier()` / `removeModifier()` 表达，**禁止直接 `setValue()`**（仅战斗扣血/回血等覆盖场景使用，下帧管线会重新计算）
- 玩家：每个属性独立 `AttachmentType<EntityAttribute>`，附件自带管线缓存
- 非玩家：`EntityAttributeAttachment`（数据袋）+ `GatherAttributeEvent`（动态收集）
- **生命（LIFE）由 core 直接注册**（对接原版血量与死亡机制，任何配置下都必然存在）；其他 13 个游戏属性由 `attributes` 模块通过 `IAttributeModule` 注册，可被第三方完全替换
- core 不硬编码任何具体游戏属性；消费方模块（profession/client/combat）各自声明本地 `Identifier` 常量引用所需属性，属性未注册时优雅降级（读取返回 0、加成无效，但不崩溃）

### 修饰符操作类型

| 操作 | 公式 | 典型用途 |
|------|------|---------|
| `ADDITION` | `value += modifier` | 装备 +10 力量 |
| `MULTIPLY_BASE` | `value × (1 + Σ/100)` | 天赋 +20% 力量 |
| `MULTIPLY_TOTAL` | `value × (1 + Σ/100)` | 狂暴 buff +50% 总攻击 |

### 三级缓存防线

| 防线 | 机制 | 生命周期 |
|------|------|---------|
| Tick 级缓存 | `Int2ObjectMap<AttributeSnapshot>` | 每 Tick 自动清空 |
| 批量事件优化 | `GatherAttributeBatchEvent` 一次分发 | 多实体同时请求时触发，AOE 优化 |
| 跨 Tick 缓存 | `CacheEntry` + `entity.tickCount` 校验 | 跨 Tick 有效，`markDirty()` 失效 |

### RPGEventBus（mod 内部事件总线）

| 事件 | 触发时机 |
|------|---------|
| `RPGDamageEvent.Pre/Post` | 伤害计算前/后（Pre 可取消/修改伤害值与类型） |
| `RPGHealEvent.Pre/Post` | 治疗前/后 |
| `AttributePostAdditionEvent` | 属性管线 ADDITION 阶段后 |
| `AttributeFinalizeEvent` | 属性管线乘算后、钳制前 |
| `GatherAttributeEvent` | 单实体属性收集（动态修饰符） |
| `GatherAttributeBatchEvent` | 批量实体属性收集（AOE 优化） |

### 快照 SPI（死亡/重生）

各模块实现 `ISnapshotContributor` 并向 `SnapshotCoordinator` 注册，统一协调 capture / restore / sync 三阶段：

| 模式 | 行为 |
|------|------|
| `SNAPSHOT` | 死亡时保存属性快照，重生时完全恢复（不掉属性） |
| `RESCAN` | 死亡时保存基础值，重生时重新扫描装备加成（装备效果保留，非装备状态清除） |

---

##  默认游戏属性

> 共 14 个：LIFE 由 core 提供；其余 13 个由 `attributes` 模块注册，可被第三方完全替换。

| 属性 ID | 中文名 | 默认值 | 上限 | 说明 |
|---------|--------|--------|------|------|
| `life` | 生命 | 100 | 100 | core 注册，对接原版血量；装备加成同时影响上限 |
| `skill_point` | 技能点 | 100 | 100 | 资源型，重生恢复 |
| `magic_point` | 法力点 | 100 | 100 | 资源型，重生恢复 |
| `strength` | 力量 | 10 | ∞ | 物理攻击力 |
| `mana` | 魔力 | 10 | ∞ | 法术攻击力 |
| `agile` | 敏捷 | 10 | ∞ | 速度/闪避 |
| `precision` | 精准 | 10 | ∞ | 命中率 |
| `defense` | 防御 | 10 | ∞ | 物理伤害减免（点） |
| `resistance` | 抗性 | 2 | 100 | 法术伤害减免（百分比） |
| `critical_rate` | 暴击率 | 5 | 300 | 暴击概率 |
| `critical_ratio` | 暴击倍率 | 50 | ∞ | 每层暴击伤害加成 |
| `fixed_damage` | 固定伤害 | 0 | ∞ | 无视防御的附加伤害（**不参与暴击**） |
| `physical_penetrate` | 物理穿透 | 0 | ∞ | 穿透目标物理防御 |
| `magical_penetrate` | 法术穿透 | 0 | ∞ | 穿透目标法术抗性 |

### 属性分类

- **资源型**（`resetOnRespawn = true`）：生命、技能点、法力点 —— 重生时自动恢复到最大值
- **能力型**（`resetOnRespawn = false`）：力量、魔力、敏捷、精准、防御、抗性、暴击率、暴击倍率、固定伤害、物理穿透、法术穿透
- **生命**额外满足 `equipmentAffectsMax = true`，防止"脱装穿装回血"漏洞

### 默认伤害公式

**输出伤害（攻击方）**：

```
物理：STRENGTH × 暴击倍率 + FIXED_DAMAGE
魔法：MANA × 暴击倍率 + FIXED_DAMAGE
混合：(STRENGTH/2) × 暴击倍率 + (MANA/2) × 暴击倍率 + FIXED_DAMAGE

暴击倍率 = (1 + CRITICAL_RATIO/100) ^ 暴击层数
暴击层数 = CRITICAL_RATE/100（保底）+ 剩余概率判定
```

> `FIXED_DAMAGE` 在暴击倍率之后额外加算，不参与暴击。

**承伤减免（防御方）**：

```
物理：max(0, 原伤 - max(0, DEFENSE - 攻击方PHYSICAL_PENETRATE))
魔法：原伤 × (1 - max(0, RESISTANCE - 攻击方MAGICAL_PENETRATE) / 100)
混合：物理半伤 + 魔法半伤
```

---

##  怪物等级与评级

`attributes` 模块在生物初始化时，依据 `mob_attributes.json` 配置赋予 RPG 属性，并按等级缩放。

### 评级倍率（应用于全部属性最终值）

| 评级 | 中文 | 倍率 |
|------|------|------|
| `NORMAL` | 普通 | 1.0× |
| `STRONG` | 强壮 | 1.25× |
| `ELITE` | 精英 | 1.5× |
| `NOTORIOUS_ELITE` | 恶名精英 | 2.0× |
| `BOSS` | 头目 | 3.0× |
| `LORD` | 领主 | 5.0× |

计算流程：`配置基础值 → 等级缩放 → JSON 覆盖 → 评级倍率 → 最终值`。

---

##  构建与运行

需要 **JDK 25**。项目自带 Gradle Wrapper，使用 `./gradlew` 而非系统 gradle。

```bash
# 构建全部模块
./gradlew build

# 启动客户端 / 服务端（core 模块统一托管运行配置）
./gradlew :core:runClient
./gradlew :core:runServer

# 数据生成 / 游戏测试
./gradlew :core:runData
./gradlew :core:runGameTestServer

# 清理 / 刷新依赖
./gradlew clean build
./gradlew --refresh-dependencies
```

> 开发期所有插件由 core 通过 `runtimeOnly project(':xxx')` 托管加载；生产环境每个插件单独打包。无单元测试框架，验证方式为 `runGameTestServer` 或游戏内手动测试。

---

##  游戏命令

所有命令以 `/rpg` 为根节点。`op-2` 表示需要 `LEVEL_GAMEMASTERS` 权限（管理员等级 2）。

### 属性管理

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg list [player]` | op-2 | 列出指定玩家（或自己）的全部属性值 |
| `/rpg get <属性名> [player]` | op-2 | 查询单个属性值 |
| `/rpg set <属性名> <值> [player]` | op-2 | 设置当前值（自动同步原版血条） |
| `/rpg setmax <属性名> <值> [player]` | op-2 | 设置上限值 |
| `/rpg reset [player]` | op-2 | 重置全部属性到默认值 |
| `/rpg deathmode <snapshot\|rescan\|status>` | op-2 | 设置/查看死亡恢复模式 |

### 等级与经验

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg level [player]` | 无 | 查看等级和经验 |
| `/rpg setlevel <等级> [player]` | op-2 | 设置等级（经验清零） |
| `/rpg addexp <经验值> [player]` | op-2 | 增加经验（自动升级） |

### 职业

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg profession` | 无 | 查看当前职业 |
| `/rpg profession list` | 无 | 列出所有已注册职业 |
| `/rpg profession set <职业ID> [player]` | op-2 | 切换职业（自动移除旧加成、应用新加成） |

内置职业：`commoner`（平民，无加成）、`warrior`（战士，力量加成）、`archer`（弓箭手，敏捷加成）。

### 战斗 / 怪物

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg combatlog on\|off` | 无 | 开关个人战斗日志（持久化） |
| `/rpg randspawn on\|off` | op-2 | 开关随机刷怪等级化（不持久化） |
| `/rpg spawn <实体ID> <等级> [json覆盖]` | op-2 | 生成指定等级的自定义怪物 |

`/rpg spawn` 的 JSON 覆盖字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `attributes` | object | 属性覆盖：键为属性 ID、值为整数 |
| `attack_type` | string | `PHYSICAL` / `MAGIC` / `MIX_TYPE` |
| `rating` | string | `NORMAL` / `STRONG` / `ELITE` / `NOTORIOUS_ELITE` / `BOSS` / `LORD` |
| `base_exp` | int | 击杀经验覆盖 |

```bash
/rpg spawn minecraft:zombie 5
/rpg spawn minecraft:skeleton 10 {"rating":"ELITE"}
/rpg spawn minecraft:spider 15 {"attack_type":"MAGIC","attributes":{"life":1000,"strength":200}}
```

### 客户端 UI

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg hud [on\|off]` | 无 | 切换 HUD 十字准星目标信息浮窗 |
| `/rpg character` | 无 | 打开角色信息界面（也可按 R） |

---

##  键盘快捷键

| 按键 | 功能 |
|------|------|
| `R` | 打开/关闭角色信息界面（toggle） |
| `ESC` | 关闭角色信息界面 |

---

##  配置文件

| 文件 | 用途 |
|------|------|
| `config/rpgcraftleveling/rpg/level_config.json` | 等级经验表 |
| `config/rpgcraftcore/rpg/mob_attributes.json` | 怪物属性配置（基础值 / 等级缩放 / 攻击类型 / 掉落经验 / 自然刷新权重） |
| `config/rpgcraftcore/rpg/equipment_attributes.json` | 装备加成配置（稀有度 / 属性加成 / 攻击类型） |

---

##  第三方扩展

第三方模组**只需依赖 `core`** 就能完成所有扩展。核心扩展点：

### 替换型接口（高优先级注册即可覆盖）

| 接口 | 替换内容 |
|------|---------|
| `IAttributeModule` | 完全替换默认 13 个游戏属性（LIFE 不可替换） |
| `IDamageCalculator` | 替换伤害公式（通过 `AttributeManager.setDamageCalculator()`） |
| `ILevelSystem` | 替换等级系统 |
| `IEquipmentSystem` | 替换装备系统 |
| `IProfessionSystem` | 替换职业系统 |
| `ICombatSystem` | 替换战斗系统 |
| `IClientSystem` | 替换客户端系统 |

### 扩展型 SPI

| 接口 | 用途 |
|------|------|
| `ILevelCalculator` | 自定义经验公式 |
| `ILevelProvider` | 自定义经验表 |
| `IMobAttributeScaler` | 自定义怪物属性按等级缩放 |
| `IEquipmentHandler` | 自定义装备加成处理逻辑 |
| `IEquipmentProvider` | 自定义装备加成数据 |
| `IProfessionProvider` | 自定义职业 |

### UI 扩展

| 接口 | 用途 |
|------|------|
| `ICharacterScreenPlugin` | 角色信息界面新增分区 |
| `IAttributeRenderer` / `IAttributeRendererFactory` | 单个属性的自定义渲染（如进度条） |

> 世界生成不在本系统职责范围内。

---

##  License

本项目采用 [MIT License](LICENSE) 许可发布。
