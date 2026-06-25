# RPGCraftSystem

> 一套基于 **微内核 + 插件** 架构的 Minecraft RPG 核心系统模组。
> Minecraft **26.1.2** / NeoForge **26.1.2.68-beta** / Java **25**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Status](https://img.shields.io/badge/status-0.10.1--alpha-orange)](#)
[![Minecraft](https://img.shields.io/badge/minecraft-26.1.2-brightgreen)](#)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.68--beta-blue)](#)
[![Java](https://img.shields.io/badge/Java-25-red)](#)

---

## 碎碎念
&emsp;&emsp;本人有一定的软件开发经验，但是日常使用的语言是C/C++，几乎没有Java的经验，故本模组采用了大量的AI辅助开发。我个人不是很容易找出里面不健康的部分，所以如果有大佬能指出项目上的缺陷，在此先行谢过。这个模组在我的想象中应该是一个所有子系统都可插拔自定义的RPG系统核心，计划中包括实体属性、实体等级、职业系统、词条系统、附魔/宝石系统、副本系统等等很多子系统，通过一个核心将这些系统连接起来形成一个完整的传统RPG系统。不过理想和现实总是有很大的差距，先写一步是一步。另外，由于日常需要忙于工作可能不是经常上git看消息，而且实际上我也没有经营公开仓库的经验，也算是一种锻炼了吧(如果有人看这个仓库的话XD)。

---

## 第三方依赖

本项目使用了以下第三方开源库，许可证详见 [THIRDPARTY_LICENSES.md](./THIRDPARTY_LICENSES.md)：

| 库 | 用途 | 许可证 |
|----|------|--------|
| [Player Animation Library (PAL)](https://github.com/ZigyTheBird/PlayerAnimationLib) | skills 模块技能动画 | MIT |

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
| professions | `rpgcraftprofessions` | `ProfessionsMod` | core |
| attributepoints | `rpgcraftattributepoints` | `AttributePointsMod` | core |
| skills | `rpgcraftskills` | `SkillsMod` | core + PAL(playeranimator) |
| client | `rpgcraftclient` | `ClientMod` | core |

> 插件模块**互不依赖**（依赖图为严格星形，所有插件只依赖 core），跨模块通信全部走 core 的 `RPGSystems` 注册门面。`skills` 模块额外依赖外部库 PAL（仅客户端）。`profession`（引擎）与 `professions`（内置职业内容）虽运行时有 `AFTER` 加载顺序约束，但编译期同样只依赖 core——`professions` 通过 `RPGSystems.getProfessionRegistry()` 获取注册中心注册职业。

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
| `registerProfessionRegistry()` | profession | `IProfessionRegistry`（供内容模块注册职业） |
| `registerAttributePointSystem()` | attributepoints | `IAttributePointSystem` |
| `registerSkillSystem()` | skills | `ISkillSystem` |
| `registerClientSystem()` | client | `IClientSystem` |

所有 `register` 方法都接受可选的 `priority` 参数，优先级三档：`OVERRIDE_PRIORITY (100) > DEFAULT_PRIORITY (0) > FALLBACK_PRIORITY (-100)`。第三方模组用 `OVERRIDE_PRIORITY` 完全覆盖官方实现；core 自身用 `FALLBACK_PRIORITY` 为各系统槽预置 **no-op 兜底实现**，使 core 在无任何插件时也能独立运行（getter 不抛异常，静默降级）：

```java
RPGSystems.registerLevelSystem(myLevelSystem, RPGSystems.OVERRIDE_PRIORITY);
```

> 附件类型 Supplier（`registerPlayerLevelAttachment` 等）同样纳入优先级机制。`profession`/`attributePoint`/`skill` 系统已有 `has*()` 守卫，其余系统由 core no-op 兜底保证安全。

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

> 共 12 个：LIFE 由 core 提供；其余 11 个由 `attributes` 模块注册，可被第三方完全替换。
> 另有 5 个**综合属性**（物理攻击/魔法攻击/防御/暴击率/暴击伤害）由公式动态计算，在角色界面「综合属性」区显示。暴击率/暴击伤害不可加点，仅受装备加成和职业公式影响。

| 属性 ID | 中文名 | 默认值 | 上限 | 说明 |
|---------|--------|--------|------|------|
| `life` | 生命 | 100 | 按配置 | core 注册，对接原版血量；装备加成同时影响上限 |
| `skill_point` | 技力 | 100 | 100 | 资源型，重生恢复；所有技能（物理/魔法）统一消耗 |
| `strength` | 力量 | 10 | 无上限 | 提升物理攻击/物理防御，少量提升魔法攻击 |
| `intelligence` | 智力 | 10 | 无上限 | 提升魔法攻击，少量提升物理攻击 |
| `agile` | 敏捷 | 10 | 无上限 | 每 5 点增加 1 点暴击率 |
| `precision` | 精准 | 10 | 无上限 | 每 5 点增加 2 点暴击伤害 |
| `resistance` | 法抗 | 0 | 100 | 法术伤害百分比减免 |
| `critical_rate` | 暴击率 | 5 | 300 | **不可加点**，装备加成生效；角色界面显示有效值（含敏捷派生），由主职业公式驱动 |
| `critical_ratio` | 暴击伤害 | 50 | 无上限 | **不可加点**，装备加成生效；角色界面显示有效值（含精准派生），由主职业公式驱动 |
| `fixed_damage` | 固定伤害 | 0 | 无上限 | 无视防御的附加伤害（**不参与暴击**） |
| `physical_penetrate` | 物理穿透 | 0 | 无上限 | 穿透目标物理防御 |
| `magical_penetrate` | 法术穿透 | 0 | 无上限 | 穿透目标法术抗性 |

### 综合属性（不注册，由公式计算）

| 综合属性 | 公式（默认） | 说明 |
|---------|------|------|
| 物理攻击力 | `力量×2 + 智力` | 当前主职业可覆写（战士/狂战士 `力×2.5+智`；弓箭手/神射手 `力×1.5+智×1.5`） |
| 魔法攻击力 | `智力×2 + 力量` | 当前主职业可覆写（如法师 `智力×3 + 力量`） |
| 物理防御力 | `力量×2` | 当前主职业可覆写（如战士/狂战士 `力量×2.5`）；魔法防御力仅来自装备 |
| 有效暴击率 | `暴击率 + 敏捷/5` | 当前主职业可覆写（如神射手 `暴击率 + 敏捷/3`）；角色界面显示拆分 |
| 有效暴击伤害 | `暴击伤害 + (精准/5)×2` | 当前主职业可覆写（如大法师 `暴击伤害 + (精准/3)×2`）；角色界面显示拆分 |

### 属性分类

- **资源型**（`resetOnRespawn = true`，不可加点）：生命、技力 —— 重生时自动恢复到最大值
- **能力型**（`resetOnRespawn = false`，可加点）：力量、智力、敏捷、精准、法抗、固定伤害、物理穿透、法术穿透
- **综合派生型**（注册、不可加点、角色界面综合区显示）：暴击率、暴击伤害 —— 装备加成生效，有效值由主职业公式决定
- **综合型**（不注册，公式计算，不可加点）：物理攻击力、魔法攻击力、物理防御力
- **生命**额外满足 `equipmentAffectsMax = true`，防止"脱装穿装回血"漏洞

### 默认伤害公式

**输出伤害（攻击方）**：

> 综合属性（攻击力）由当前主职业的公式派生（默认见上表），详见 `IProfession#computePhysicalAttack` / `computeMagicalAttack`。怪物使用默认公式。

```
物理：(力量×2 + 智力) × 暴击倍率 + 固定伤害
魔法：(智力×2 + 力量) × 暴击倍率 + 固定伤害
混合：[(力量×2 + 智力)/2] × 暴击倍率 + [(智力×2 + 力量)/2] × 暴击倍率 + 固定伤害

暴击倍率 = (1 + 有效暴击伤害/100) ^ 暴击层数
暴击层数 = 有效暴击率/100（保底）+ 剩余概率判定
有效暴击率、有效暴击伤害由主职业公式决定（默认见综合属性表）
```

> 固定伤害在暴击倍率之后额外加算，不参与暴击。
> 暴击派生公式由各主职业覆写（如神射手敏捷对暴击率加成更高、大法师精准对暴击伤害加成更高）。

**承伤减免（防御方）**：

```
物理：max(0, 原伤 - max(0, 力量×2 - 攻击方物理穿透))
魔法：原伤 × (1 - max(0, 法抗 - 攻击方法术穿透) / 100)
混合：物理半伤 + 魔法半伤
```

> 物理防御力由目标的当前主职业公式派生（默认 `力量×2`），魔法防御力仅来自装备，无属性派生。

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

### 严格区分主职业、副职业与复合职业

职业分三类（由职业类的 `ProfessionType` 决定）：

| | 主职业（primary） | 副职业（secondary） | 复合职业（compound） |
|---|---|---|---|
| 职业树 | 链根于 `commoner`（平民，唯一根） | 独立成树 | 独立成树，无单父 |
| `prerequisite` 约束 | 只能指 primary，链最终追溯到 commoner | 只能指 secondary 或为 null | 返回 null；`getPrerequisites()` 指向多个 primary |
| 可做主职业 | ✅（`advance` / `switchMain` 仅限 `isMainLike()` 类型） | ❌ | ✅（与主职业同走主职业修饰符管线） |
| 可做副职业 | ❌ | ✅（`setSecondary` 仅限此类） | ❌ |
| 可投入经验升级 | ✅ | ✅ | ✅ |
| 加成生效 | 当前主职业按等级生效 | 激活的副职业按等级生效 | 当前主职业时按等级生效 |
| 解锁条件 | 单前置达满级 | 单前置达满级 + 消耗经验池 | **所有**前置主职业均达满级 |
| 面板归属 | 主职业窗 | 副职业窗 | 复合窗（标题栏 ⇌ 切换） |

> `ProfessionType.isMainLike()` = `type == PRIMARY || type == COMPOUND`，集中表达「可作为主职业」判定，用于 `canAdvance` / `canSwitchMain` / `setProfession` / 登录主职业校验。
>
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
主职业树（物理系）：commoner（平民）→ warrior（战士）→ berserker（狂战士）
                                  → archer（弓箭手）→ marksman（神射手）
主职业树（魔法系）：commoner（平民）→ sorcerer（术士）→ mage（法师）→ archmage（大法师）
复合职业：         berserker + mage（均满级）→ witchblade（魔剑士）
副职业树：         scholar（学者）→ researcher（研究员）→ naturalist（博物学家）
```

| 职业 | 类型 | 前置 | 加成（基础 / 每级） |
|------|------|------|---------------------|
| `commoner` 平民 | primary | — | 无加成 |
| `warrior` 战士 | primary | commoner | 力量 +5 / +1 每级；敏捷 -3 |
| `archer` 弓箭手 | primary | commoner | 敏捷 +5 / +1 每级；力量 -3 |
| `berserker` 狂战士 | primary | warrior（满级） | 力量 +6 / +1 每级；生命 +10 / +2 每级 |
| `marksman` 神射手 | primary | archer（满级） | 敏捷 +6 / +1 每级；暴击率 +3 / +1 每级 |
| `sorcerer` 术士 | primary | commoner | 智力 +5 / +1 每级；力量 -3 |
| `mage` 法师 | primary | sorcerer（满级） | 智力 +6 / +1 每级；法术穿透 +3 / +1 每级 |
| `archmage` 大法师 | primary | mage（满级） | 智力 +7 / +1 每级；暴击伤害 +5 / +1 每级 |
| `witchblade` 魔剑士 | compound | berserker + mage（均满级） | 力量 +4 / +1 每级；智力 +4 / +1 每级 |
| `scholar` 学者 | secondary | — | 无加成（待设计） |
| `researcher` 研究员 | secondary | scholar（满级） | 无加成（待设计） |
| `naturalist` 博物学家 | secondary | researcher（满级） | 无加成（待设计） |

> 物理/魔法两条主职业树并行，复合职业跨树融合：魔剑士同时需要战系叶子（狂战士）与法系中间层（法师）达满级 —— 不要求系列达最顶级。

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

### 击杀经验倍率曲线

玩家击杀怪物获得的等级经验 = `baseExp × 等级差倍率 × (1 + 经验加成%)`，其中 `baseExp` 来自 `mob_attributes.json` 的 `base_exp`。等级差倍率按玩家与怪物等级的差值 `d = 玩家等级 − 怪物等级` 分段线性计算（实现在 `core` 的 `ExperienceGainCurve`，服务端发放与客户端悬停预览共用同一公式）：

| 差值 d（玩家−怪） | 倍率 | 含义 |
|------------------|------|------|
| `\|d\| ≤ 5` | **5.0×** | 甜区峰值（玩家与怪等级相仿，收益最高） |
| `d = +12.5`（中点） | ≈ 2.5× | 低级怪端线性下降中 |
| `d ≥ +20` | 保底 **1 点经验** | 玩家远高于怪，防止刷低级怪 |
| `d = −27.5`（中点） | ≈ 2.55× | 高级怪端线性下降中 |
| `d = −50` | **0.1×** | 玩家远低于怪，开始封顶保护 |
| `d ≤ −50` | 恒 0.1× | 高级怪封顶保护（防止越级秒杀） |

曲线**不对称**：低级怪端（玩家高）在 15 级跨度内从 5× 跌到保底（衰减更快），高级怪端（玩家低）在 45 级跨度内从 5× 跌到 0.1×（衰减较缓）——鼓励挑战与自身等级相仿或略高的怪物。

**两个扩展入口**（均可由第三方替换）：
- **`exp_bonus` 属性**（`rpgcraftcore:exp_bonus`）：按整数百分比叠加在曲线之上，装备/职业/属性点可通过属性管道注入。默认 0。属性未注册时优雅降级为 0。
- **`IExperienceCurve`** SPI：替换整条等级差曲线（通过 `ExperienceCurveManager.setCurve()`，与 `IDamageCalculator` 同型策略模式）。`ILevelCalculator` 仍可整体替换包含属性加成在内的完整经验计算。

### 玩家等级经验曲线

玩家从 `level` 升到 `level+1` 所需的增量经验由阈值曲线决定，默认公式 `round(50 × level^1.5)`（如 1→2 需 50、10→11 需 1581、100→101 需 50000，最大等级 300）。实现在 `core` 的 `ExpFormula`，玩家等级系统（`leveling`）与职业等级系统（`profession`）共用此默认公式。

**扩展入口**（仅影响玩家等级，职业等级不受影响）：
- **`IExpThresholdCurve`** SPI：替换玩家等级阈值曲线（通过 `ExpThresholdCurveManager.setCurve()`，与 `IExperienceCurve` 同型策略模式）。
- **与 JSON 的优先级**：默认情况下 `data/rpgcraftcore/rpg/level_config.json` 提供每级精确经验值；**一旦注册了自定义 SPI 曲线（非 core 默认实现），JSON 将被忽略并改用 SPI 曲线重建经验表**（日志提示"已被 SPI 覆盖"）。即 SPI（公式级覆盖）优先于 JSON（数据级微调）。
- **运行时替换**：SPI 曲线可在任何时机替换，`LevelConfig` 会自动重建经验表（无需重启）。
- **职业等级**仍直调 `ExpFormula`，并有 per-profession `getExpTable()` 钩子，与本 SPI 正交。

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
| 浮动窗口 | 上下两个可拖动浮窗：**主职业窗**（primary 树）与**副职业窗**（secondary 树）。每个职业用方形节点表示，父子节点用连接线相连；当前主职业节点带绿色角标、已激活副职业带蓝色角标 |
| 标题栏 ⇌ 按钮 | 在「主/副双窗」与「复合职业单窗」之间切换（类似化学可逆反应符号）。复合窗内每个复合职业节点上方挂出其前置主职业的只读图标（虚线连接），**不可**进阶/升级/切换 |
| 标题栏 □/⊟ 按钮 | 单窗最大化/还原（铺满全屏 / 恢复双窗）；还原时自动以当前职业居中，避免大窗拖动后缩小导致树推出可视区 |
| 鼠标拖动 | 按住标题栏拖动窗口；按住窗内空白拖动**平移**职业树 |
| 打开居中 | 首次打开时以**当前主职业**节点为中心；进阶/切换主职业后自动重新居中到新职业 |
| 双击节点 | 主职业/复合职业：未解锁且前置满足→进阶确认框；已解锁非当前→切换为主职业。副职业：未解锁→解锁确认框；已解锁→切换激活状态 |
| 节点下 + 按钮 | 已解锁未满级职业可投入经验升级（按钮显示 `Lv.N +`，满级显示 `Lv.MAX`） |
| 悬停气泡 | 职业类型标签、状态、等级、加成详情、解锁/进阶条件提示 |

> 所有操作通过 `ProfessionActionPacket` 发送至服务端权威处理，防作弊。`/reload` 后职业定义立即重载并推送在线玩家。

---

##  属性点系统

`attributepoints` 模块提供自由属性点分配：玩家每升一级自动获得 1 个可分配点数，可在角色界面（按 `R`）分配到除 `life`/`skill_point` 外的任意能力型属性（综合属性不可加点）。

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

### 版本号规则

整个工程共享**统一版本号**（所有模块同步），格式为：

```
主版本.副版本.次版本-开发阶段
```

| 段 | 含义 | 递增时机 |
|----|------|---------|
| 主版本 | 架构层级的重大变更（如 API 破坏性重构） | 极少递增 |
| 副版本 | 较大的功能新增或重要特性 | 每个功能版本通常递增 |
| 次版本 | 小幅改动、bug 修复、局部增强 | 每次提交按改动幅度递增 |
| 开发阶段 | `alpha` / `beta` / `release` | 当前阶段固定为 `alpha` |

**示例**：`0.5.5-alpha` = 主 0、副 5、次 5、alpha 阶段。

**递增约定**：
- **工程版本号**：每次发布都升级（由 README 徽章标识），代表当前工程的最新版本。
- **模块版本号**：记录该模块**最后一次改动时的工程版本**。每次发布时，只有**本次有改动的模块**把版本号提升到当前工程版本；未改动的模块版本号保持不变。
- **幅度选择**（决定工程版本号如何递增）：
  - 跨模块大重构 / 新子系统 → 副版本 +1、次版本归零（如 `0.5.5` → `0.6.0`）
  - 单模块功能新增 / bug 修复 → 次版本 +1（如 `0.5.5` → `0.5.6`）
  - **在决定提交时，必须回头复盘自从上一次发布以来的所有累积更改**，根据累积改动的整体范围来决定是升级次版本还是副版本，而非仅看当前这一个提交的改动量。
- **模块版本差异是正常的**：不同模块版本号不同，反映各自最后改动时间。例如工程版本为 `0.6.0` 时，某长期未改动的模块可能仍停留在 `0.5.3`，这是预期行为，不是错误。模块版本号**只能 ≤ 工程版本号**（不能超前）。

**版本号位置**：
- 每个模块的 `gradle.properties` 中的 `mod_version`
- README 顶部状态徽章：`![](https://img.shields.io/badge/status-X.Y.Z--alpha-orange)`
- 每次发版的 Git 提交信息末尾标注 `(vX.Y.Z-alpha)`

### 提交约定

- **未经实际运行手动验证，不得提交代码**。完成改动后必须先在游戏中手动测试确认行为正确，除非明确要求提交，否则不要执行 `git commit`。
- 版本号升级、文档更新等非代码改动同样遵循此约定。

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

> 玩家每升一级自动获得 1 个可分配点数；点数可在角色界面（按 `R`）分配到除 `life`/`skill_point` 之外的能力型属性（综合属性不可加点）。

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
| `data/rpgcraftcore/rpg/profession_config.json` | 职业全局配置（`allow_downgrade_switch`：是否允许从进阶职业退回基础职业，默认 `false`；`default_max_level`：职业默认等级上限，默认 `20`；`secondary_unlock_cost`：解锁副职业消耗，默认 `50000`）。三项均在登录/`/reload` 时通过 `SyncProfessionConfigPacket` 推送客户端，职业面板显示与服务端一致 |
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
| `IExperienceCurve` | 自定义等级差经验倍率曲线（`ExperienceCurveManager.setCurve()`） |
| `IExpThresholdCurve` | 自定义玩家等级升下一级所需经验曲线（`ExpThresholdCurveManager.setCurve()`，注册后 JSON 失效） |
| `ILevelProvider` | 自定义经验表 |
| `IMobAttributeScaler` | 自定义怪物属性按等级缩放 |
| `IEquipmentHandler` | 自定义装备加成处理逻辑 |
| `IEquipmentProvider` | 自定义装备加成数据 |
| `IProfessionRegistry`（经 `RPGSystems.getProfessionRegistry()`） | 注册自定义职业（实现 `IProfession` 或继承 `AbstractProfession`）；ID 常量取 `core` 的 `ProfessionIds` |
| `ISkillProvider` | 自定义技能（ServiceLoader 追加） |

> **添加职业的正确模式**（仅依赖 core）：在自己的 `@Mod` 构造函数中调用 `RPGSystems.getProfessionRegistry().register(new MyProfession())`，并通过 `neoforge.mods.toml` 声明 `rpgcraftprofession` 为 `AFTER` 依赖保证引擎先初始化。内置 `professions` 模块即采用此模式，可作为参考。

### UI 扩展

| 接口 | 用途 |
|------|------|
| `ICharacterScreenPlugin` | 角色信息界面新增分区 |
| `IAttributeRenderer` / `IAttributeRendererFactory` | 单个属性的自定义渲染（如进度条） |

> 世界生成不在本系统职责范围内。

---

##  License

本项目采用 [MIT License](LICENSE) 许可发布。
