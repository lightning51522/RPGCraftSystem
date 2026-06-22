# RPGCraftSystem

> 一套基于 **微内核 + 插件** 架构的 Minecraft RPG 核心系统模组。
> Minecraft **26.1.2** / NeoForge **26.1.2.68-beta** / Java **25**

[![Status](https://img.shields.io/badge/status-0.5.5--alpha-orange)](#)
[![Minecraft](https://img.shields.io/badge/minecraft-26.1.2-brightgreen)](#)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.68--beta-blue)](#)
[![Java](https://img.shields.io/badge/Java-25-red)](#)

---

## 碎碎念
&emsp;&emsp;本人有一定的软件开发经验，但是日常使用的语言是C/C++，几乎没有Java的经验，故本模组采用了大量的AI辅助开发。我个人不是很容易找出里面不健康的部分，所以如果有大佬能指出项目上的缺陷，在此先行谢过。这个模组在我的想象中应该是一个所有子系统都可插拔自定义的RPG系统核心，计划中包括实体属性、实体等级、职业系统、词条系统、附魔/宝石系统、副本系统等等很多子系统，通过一个核心将这些系统连接起来形成一个完整的传统RPG系统。不过理想和现实总是有很大的差距，先写一步是一步。另外，由于日常需要忙于工作可能不是经常上git看消息，而且实际上我也没有经营公开仓库的经验，也算是一种锻炼了吧(如果有人看这个仓库的话XD)。

---

##  模块组成

```
RPGCraftSystem/
├── core/             微内核：接口 + 属性管线 + 事件总线 + 快照 SPI + RPGSystems 注册门面
├── attributes/       13 个默认 RPG 属性 + 战斗（伤害公式 / 怪物初始化 / 治疗 / 命令）
├── leveling/         等级（上限 300）/ 经验 / 怪物属性按等级缩放
├── equipment/        装备加成（修饰符模式） + 装备追踪
├── profession/       职业注册 + 职业树 / 进阶 / 副职业 / 职业等级与经验池
├── attributepoints/  自由属性点分配系统（升级获点，玩家自行分配到任意属性）
├── skills/           主动技能系统（PAL 玩家动画 + 资源消耗 + 冷却 + RPG 伤害）
└── client/           HUD / 角色信息界面 / 职业面板 / Tooltip / UI 插件系统
```

| 模块 | mod ID | 入口类 | 依赖 |
|------|--------|--------|------|
| core | `rpgcraftcore` | `RPGCraftCore` | — |
| attributes | `rpgcraftattributes` | `AttributesMod` | core |
| leveling | `rpgcraftleveling` | `LevelingMod` | core |
| equipment | `rpgcraftequipment` | `EquipmentMod` | core |
| profession | `rpgcraftprofession` | `ProfessionMod` | core |
| attributepoints | `rpgcraftattributepoints` | `AttributePointsMod` | core |
| skills | `rpgcraftskills` | `SkillsMod` | core + PAL(playeranimator) |
| client | `rpgcraftclient` | `ClientMod` | core |

> 插件模块**互不依赖**，跨模块通信全部走 core 的 `RPGSystems` 注册门面。`skills` 模块额外依赖外部库 PAL（仅客户端）。

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
| `registerAttributePointSystem()` | attributepoints | `IAttributePointSystem` |
| `registerSkillSystem()` | skills | `ISkillSystem` |
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
| `life` | 生命 | 100 | 按配置 | core 注册，对接原版血量；装备加成同时影响上限 |
| `skill_point` | 技能点 | 100 | 1000000 | 资源型，重生恢复 |
| `magic_point` | 法力点 | 100 | 1000000 | 资源型，重生恢复 |
| `strength` | 力量 | 10 | 1000000 | 物理攻击力 |
| `mana` | 魔力 | 10 | 1000000 | 法术攻击力 |
| `agile` | 敏捷 | 10 | 1000000 | 速度/闪避 |
| `precision` | 精准 | 10 | 1000000 | 命中率 |
| `defense` | 防御 | 0 | 1000000 | 物理伤害减免（点） |
| `resistance` | 抗性 | 0 | 100 | 法术伤害减免（百分比） |
| `critical_rate` | 暴击率 | 5 | 1000 | 暴击概率（千分比） |
| `critical_ratio` | 暴击倍率 | 50 | 1000 | 每层暴击伤害加成（百分比） |
| `fixed_damage` | 固定伤害 | 0 | 1000000 | 无视防御的附加伤害（**不参与暴击**） |
| `physical_penetrate` | 物理穿透 | 0 | 1000000 | 穿透目标物理防御 |
| `magical_penetrate` | 法术穿透 | 0 | 1000000 | 穿透目标法术抗性 |

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

##  职业系统

`profession` 模块的**框架**（注册门面、经验/等级、进阶、主副职业逻辑）与**具体职业定义**完全解耦：具体职业全部由 datapack JSON 驱动，框架代码不再硬编码任何职业。核心数据持久化在 `ProfessionData` 附件中。

### 严格区分主职业与副职业

职业分两类（由 JSON 的 `type` 字段决定）：

| | 主职业（primary） | 副职业（secondary） |
|---|---|---|
| 职业树 | 链根于 `commoner`（平民，唯一根） | 独立成树 |
| `prerequisite` 约束 | 只能指 primary，链最终追溯到 commoner | 只能指 secondary 或为 null |
| 可做主职业 | ✅（`advance` / `switchMain` 仅限此类） | ❌ |
| 可做副职业 | ❌ | ✅（`setSecondary` 仅限此类） |
| 可投入经验升级 | ✅ | ✅ |
| 加成生效 | 当前主职业按等级生效 | 激活的副职业按等级生效 |

> 历史版本下"任意已解锁职业都能做副职业"的旧行为已废弃；旧存档若副职业引用了 primary 类型职业，登录时会自动清空（WARN）。

### 职业定义 JSON（datapack 驱动）

每个职业一个文件，放在 `data/rpgcraftcore/rpg/professions/`，文件名（去 `.json`）即职业 ID 的 path，命名空间固定 `rpgcraftcore`：

```jsonc
// data/rpgcraftcore/rpg/professions/warrior.json
{
  "name": "战士",
  "description": "力量提升，敏捷降低",
  "type": "primary",              // 必填："primary" 主职业 | "secondary" 副职业
  "prerequisite": "commoner",     // 父职业 path（同命名空间）；根职业用 null 或省略
  "max_level": 20,                // 可省略，默认读全局 default_max_level
  "bonuses": {                    // 1 级基础加成：完整 attrId → 数值（可负）
    "rpgcraftcore:strength": 5,
    "rpgcraftcore:agile": -3
  },
  "per_level": {                  // 每升 1 级增量：attrId → 数值（省略某属性 = 0）
    "rpgcraftcore:strength": 1
  },
  "exp_table": [50, 141, 260, ...] // 可省略；省略则用全局公式 round(50×L^1.5)
}
```

任意等级加成 = `base + perLevel × (level - 1)`；进阶职业与前置职业的加成**叠加**生效。

### 内置职业（随模组提供，可被 datapack 覆盖）

```
主职业树：commoner（平民）→ warrior（战士）→ berserker（狂战士）
                       → archer（弓箭手）→ marksman（神射手）
副职业树：（首期无内置副职业，由下方占位副职业兜底）
```

| 职业 | 前置 | 加成（基础 / 每级） |
|------|------|---------------------|
| `commoner` 平民 | — | 无加成 |
| `warrior` 战士 | commoner | 力量 +5 / +1 每级；敏捷 -3 |
| `archer` 弓箭手 | commoner | 敏捷 +5 / +1 每级；力量 -3 |
| `berserker` 狂战士 | warrior（满级） | 力量 +6 / +1 每级；生命 +10 / +2 每级 |
| `marksman` 神射手 | archer（满级） | 敏捷 +6 / +1 每级；暴击率 +3 / +1 每级 |

### 默认占位副职业（apprentice）

当 datapack **未提供任何 `type=secondary` 职业**时，系统自动注入一个真实注册的占位副职业 `rpgcraftcore:apprentice`（学徒）：可在职业面板副职业分区看到、可设为副职业、可投入经验升级（加成为空，无实际属性效果）。一旦 datapack 出现任意真实副职业 JSON，`/reload` 后占位即移除；已选中 apprentice 为副职业的玩家副职业会被清空（WARN）。

> 这保证玩家在未配置副职业时也能体验完整的副职业交互流程。

### 兜底与校验（加载器保证不崩溃）

- `commoner.json` 缺失 → 代码注入空 commoner，保证"commoner 必解锁"不变量不致存档损坏
- `prerequisite` 跨类型引用（primary→secondary 或反向）→ 拒绝并 WARN
- 主职业链未追溯到 commoner、存在循环 → 拒绝并 WARN
- 字段缺失/类型错误 → 回退默认值并 WARN

### 职业经验与等级

- 玩家获得等级经验时（不论是否升级），等量经验进入 **可分配职业经验池**（`skill_point_pool`）
- 在职业面板中可将经验池投入某职业升级，每升一级消耗该职业经验表对应值点（职业无专属 `exp_table` 时用全局公式 `round(50×L^1.5)`）
- 投入规则：目标职业需已解锁、未满级、池内经验足够

### 进阶与副职业

| 操作 | 规则 |
|------|------|
| **进阶** | 仅主职业可进阶；前置主职业达到满级后才可解锁进阶职业，进阶后两段加成叠加 |
| **切换主职业** | 仅可在已解锁的**主职业**间切换；默认禁止从进阶职业退回基础职业（可由 `allow_downgrade_switch` 开启） |
| **设为副职业** | 仅**副职业**类型职业可设为副职业；提供被动属性加成 |
| **副职业开关** | 副职业加成可单独开关（`secondaryActive`），关闭时加成失效但不丢失副职业身份 |

### 职业面板（按 `P` 键）

| 元素 | 说明 |
|------|------|
| 左侧大画布 | **上下分区**的职业树：上半为主职业树、下半为副职业树（各自独立成树，用"主职业"/"副职业"小标题分隔）。每个职业用方形节点表示，父子节点用连接线相连；当前主职业节点带金色高亮边框 |
| 鼠标拖动 | 按住左键拖动画布空白处可**平移**整个职业树，支持树超出可见区时查看其余部分 |
| 打开居中 | 每次按 P 打开时，自动以**当前主职业**节点为中心显示 |
| 右侧小详情 | 选中职业的类型标签、等级、经验、加成与操作按钮（投入经验 / 进阶 / 设为主职业 / 设为副职业 / 切换副职业开关） |
| 顶部 | 可分配职业经验池 |

> 所有操作通过 `ProfessionActionPacket` 发送至服务端权威处理，防作弊。`/reload` 后职业定义立即重载并推送在线玩家。

---

##  属性点系统

`attributepoints` 模块提供自由属性点分配：玩家每升一级自动获得 1 个可分配点数，可在角色界面（按 `R`）分配到除 `life`/`skill_point`/`magic_point` 外的任意属性。

| 配置项 | 文件 | 默认值 | 说明 |
|--------|------|--------|------|
| `allow_decrease` | `attribute_points_config.json` | `true` | 是否允许回收/减少已分配点数；`false` 时服务端拒绝回收，角色界面隐藏 `[-]` 按钮 |

- 分配/回收通过属性管线以 `ADDITION` 修饰符表达，**不直接修改属性基础值**
- 配置在玩家登录时推送客户端，`/reload` 后对在线玩家即时生效

---

##  技能系统

`skills` 模块提供主动技能系统（MVP）：玩家按键释放 → 校验资源/冷却 → 扣除 `skill_point` →
启动冷却 → PAL 玩家动画 → 对前方目标造成 RPG 伤害（走 vanilla hurt 由 `CombatEventHandler` 接管）。

> 详细设计见 [core/docs/06-skills.md](core/docs/06-skills.md)

### 技能定义（datapack 驱动）

每个技能一个文件，放在 `data/rpgcraftcore/rpg/skills/`，文件名即技能 ID 的 path：

```jsonc
// data/rpgcraftcore/rpg/skills/heavy_strike.json
{
  "name": "重击",
  "description": "消耗技能点，对前方敌人造成物理伤害",
  "resource_cost": 10,        // 释放消耗的 skill_point 量
  "cooldown_ticks": 100,       // 冷却时长（tick，100 = 5 秒）
  "damage_amount": 30,         // 单目标伤害值（扁平，进入 RPG 公式）
  "attack_type": "PHYSICAL",   // PHYSICAL / MAGIC / MIX_TYPE
  "animation_id": "rpgcraftskills:heavy_strike",  // PAL 动画资源 ID
  "range": 4.0                 // 命中范围（方块）
}
```

内置示范技能 `heavy_strike`（重击）：消耗 10 技能点，冷却 5 秒，造成 30 物理伤害。

### 伤害接入

技能伤害走 vanilla `target.hurt()`，由 `CombatEventHandler` 接管走完整 RPG 公式（暴击/防御/抗性/事件）。
攻击类型由玩家手持武器解析（与普通近战一致），**零侵入 core 战斗接口**。

### 客户端集成

- **PAL 动画**：`SkillAnimationHandler` 在 `FMLClientSetupEvent` 通过
  `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory` 注册动画 layer 工厂，
  收到 `PlaySkillAnimationPacket` 时取回玩家 layer 的 `PlayerAnimationController` 调 `triggerAnimation(id)` 播放。
  动画资源放 `assets/<namespace>/player_animations/*.json`（Bedrock 格式），**文件内 `animations` 的键名必须与
  技能 `animation_id` 的 path 完全一致**（文件名本身不影响 ID 解析）
- **PAL 为可选依赖**：mods.toml 声明 `type="optional"`，PAL 缺失时技能伤害/冷却正常，仅无动画
- **按键**：默认数字键 `1` 释放 `heavy_strike`（MVP 固定），发 `CastSkillPacket` 到服务端权威校验

### 当前限制（MVP）

- 技能槽固定（数字键 1 硬编码释放示范技能，无技能栏 UI）
- 无技能学习系统（所有已注册技能默认可释放）
- 无 buff/debuff / 状态机 / 精确 raycast
- 技能 JSON 的 `attack_type` 仅展示用，实际生效由手持武器决定

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

> 职业**升级、进阶、副职业切换**等操作不通过命令，而是在 **职业面板**（按 `P` 键打开）中完成。命令只负责查看和 GM 强制切换。

### 属性点

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg attrpoints [player]` | 无 | 查看可分配点数与各属性已分配情况 |
| `/rpg attrpoints add <数量> [player]` | op-2 | 授予可分配点数 |
| `/rpg attrpoints reset [player]` | op-2 | 重置全部分配并退还所有已分配点数 |

> 玩家每升一级自动获得 1 个可分配点数；点数可在角色界面（按 `R`）分配到除 `life`/`skill_point`/`magic_point` 之外的任意属性。

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

### 技能

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg skills [player]` | 无 | 查看技能信息与冷却状态 |
| `/rpg skills list` | 无 | 列出所有已注册技能定义 |
| `/rpg skills cast <技能ID> [player]` | op-2 | 强制释放某技能（仍走完整校验） |
| `/rpg skills cooldown reset [player]` | op-2 | 重置玩家全部技能冷却 |

```bash
/rpg skills list
/rpg skills cast heavy_strike
/rpg skills cooldown reset
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
| `R` | 打开/关闭角色信息界面（toggle）—— 显示等级、职业、可分配点数、属性分配按钮 |
| `P` | 打开职业面板 —— 主/副职业双树、职业升级/进阶/副职业操作；按住左键拖动平移画布，打开时自动以当前主职业居中 |
| `1` | 释放技能 1（MVP 固定释放 `heavy_strike` 重击；后续接入技能栏 UI） |
| `ESC` | 关闭角色信息界面 / 职业面板 |

---

##  配置文件

| 文件 | 用途 |
|------|------|
| `data/rpgcraftcore/rpg/level_config.json` | 等级经验表（上限 300 级） |
| `data/rpgcraftcore/rpg/mob_attributes.json` | 怪物属性配置（基础值 / 等级缩放 / 攻击类型 / 掉落经验 / 自然刷新权重） |
| `data/rpgcraftcore/rpg/equipment_attributes.json` | 装备加成配置（稀有度 / 属性加成 / 攻击类型） |
| `data/rpgcraftcore/rpg/profession_config.json` | 职业全局配置（`allow_downgrade_switch`：是否允许从进阶职业退回基础职业，默认 `false`；`default_max_level`：职业默认等级上限，默认 `20`） |
| `data/rpgcraftcore/rpg/professions/*.json` | **具体职业定义**（每个文件一个职业，文件名即职业 ID；含 name/type/prerequisite/bonuses/per_level/exp_table）。详见[职业系统](#-职业系统)章节 |
| `data/rpgcraftcore/rpg/attribute_points_config.json` | 属性点配置（`allow_decrease`：是否允许回收已分配点数，默认 `true`） |

> 配置文件位于 datapack 命名空间 `rpgcraftcore/rpg/` 下，使用 `/reload` 即时重载，重载后对在线玩家即时推送客户端配置。

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
| `IProfessionSystem` | 替换职业系统（职业注册 / 等级 / 进阶 / 副职业） |
| `IAttributePointSystem` | 替换属性点分配系统 |
| `ISkillSystem` | 替换技能系统（技能定义 / 释放 / 冷却） |
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
| `ISkillProvider` | 自定义技能（ServiceLoader 追加） |

### UI 扩展

| 接口 | 用途 |
|------|------|
| `ICharacterScreenPlugin` | 角色信息界面新增分区 |
| `IAttributeRenderer` / `IAttributeRendererFactory` | 单个属性的自定义渲染（如进度条） |

> 世界生成不在本系统职责范围内。

---

##  License

本项目采用 [MIT License](LICENSE) 许可发布。
