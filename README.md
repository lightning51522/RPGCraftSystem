# RPGCraftSystem

> 一套基于 **微内核 + 插件** 架构的 Minecraft RPG 核心系统模组。
> Minecraft **26.1.2** / NeoForge **26.1.2.68-beta** / Java **25**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Status](https://img.shields.io/badge/status-0.20.3--alpha-orange)](#)
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
| [Player Animation Library (PAL)](https://github.com/ZigyTheBird/PlayerAnimationLib) | skills 模块技能动画（**仅玩家动画**） | MIT |
| [GeckoLib](https://github.com/bernie-g/geckolib) | entities 模块自定义生物模型 / 动画（**仅生物动画**） | MIT |

> **动画库分工（项目硬约束）**：[GeckoLib](https://github.com/bernie-g/geckolib) **只能用于生物（自定义实体）**的模型与动画；[PAL](https://github.com/ZigyTheBird/PlayerAnimationLib) **只能用于玩家**动画。二者职责互斥，**严禁混用**——生物动画不得用 PAL，玩家动画不得用 GeckoLib。详见下方 [动画库分工](#动画库分工)。

---

##  模块组成

```
RPGCraftSystem/
├── core/             微内核：接口 + 属性管线 + 事件总线 + 快照 SPI + RPGSystems 注册门面
├── attributes/       20 个默认 RPG 属性 + 战斗（伤害公式 / 怪物初始化 / 治疗 / 命令）
├── leveling/         等级（上限 300）/ 经验 / 怪物属性按等级缩放
├── equipment/        装备加成（修饰符模式） + 装备追踪
├── gemstone/         镶嵌宝石（每件装备 1 颗，词条加成 + 战斗特效，铁砧镶嵌）
├── profession/       职业注册 + 职业树 / 进阶 / 副职业 / 职业等级与经验池
├── attributepoints/  自由属性点分配系统（升级获点，玩家自行分配到任意属性）
├── skills/           主动技能系统（PAL 玩家动画 + 资源消耗 + 冷却 + RPG 伤害）
├── region/           区域系统（多边形柱体 + 环境属性增益/减益 + 元素伤害倍率）
├── entities/         自定义生物（BlockBench 建模 + GeckoLib 动画）
└── client/           HUD / 角色信息界面 / 职业面板 / Tooltip / UI 插件系统
```

| 模块 | mod ID | 入口类 | 依赖 |
|------|--------|--------|------|
| core | `rpgcraftcore` | `RPGCraftCore` | — |
| attributes | `rpgcraftattributes` | `AttributesMod` | core |
| leveling | `rpgcraftleveling` | `LevelingMod` | core |
| equipment | `rpgcraftequipment` | `EquipmentMod` | core |
| gemstone | `rpgcraftgemstone` | `GemstoneMod` | core |
| profession | `rpgcraftprofession` | `ProfessionMod` | core |
| professions | `rpgcraftprofessions` | `ProfessionsMod` | core |
| attributepoints | `rpgcraftattributepoints` | `AttributePointsMod` | core |
| skills | `rpgcraftskills` | `SkillsMod` | core + PAL(playeranimator) |
| region | `rpgcraftregion` | `RegionMod` | core + attributes（optional） |
| entities | `rpgcraftentities` | `EntitiesMod` | core + GeckoLib(required) |
| client | `rpgcraftclient` | `ClientMod` | core |

> 插件模块**互不依赖**（依赖图为严格星形，所有插件只依赖 core），跨模块通信全部走 core 的 `RPGSystems` 注册门面。`skills` 模块额外依赖外部库 PAL（仅客户端）。`profession`（引擎）与 `professions`（内置职业内容）虽运行时有 `AFTER` 加载顺序约束，但编译期同样只依赖 core——`professions` 通过 `RPGSystems.getProfessionRegistry()` 获取注册中心注册职业。`region` 编译期依赖 `attributes`（引用元素抗性/伤害加成属性 ID 常量），运行时 `attributes` 为可选依赖（缺失时区域属性注入对未注册属性静默降级，不崩溃）。`entities` 模块依赖外部库 GeckoLib（**required**，承载 BlockBench 生物的模型与动画；GeckoLib 缺失时该模块无意义，但不影响其他模块）。

---

## 动画库分工

本项目使用两个独立的动画库，**职责互斥，严禁混用**：

| 库 | 适用对象 | 使用模块 | 依赖性质 |
|----|---------|---------|---------|
| **[GeckoLib](https://github.com/bernie-g/geckolib)** | **生物（自定义实体）**的模型与动画 | `entities` | required |
| **[PAL (Player Animation Library)](https://github.com/ZigyTheBird/PlayerAnimationLib)** | **玩家**动画（技能释放等） | `skills` | optional |

- **GeckoLib 只能用于生物**：在 `entities` 模块中为 BlockBench 建模的自定义生物驱动 GeoEntity 模型与动画。
- **PAL 只能用于玩家**：在 `skills` 模块中驱动玩家释放技能时的玩家身体动画。
- **不得混用**：生物动画不得用 PAL，玩家动画不得用 GeckoLib。任何新增动画功能须先判断动画对象（生物 / 玩家），再选择对应库。

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
| `registerElementResolver()` | equipment（可选） | `IElementResolver`（解析攻击元素标签，无实现时兜底返回 NONE） |
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
| `RPGDamageEvent.Pre/Post` | 伤害计算前/后（Pre 可取消/修改伤害值、攻击类型与元素标签） |
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

> 共 27 个：LIFE 由 core 提供；1 个资源型 + 11 个能力/综合派生属性 + 7 个元素抗性属性 + 7 个元素伤害加成属性由 `attributes` 模块注册，可被第三方完全替换。
> 另有 5 个**综合属性**（物理攻击/魔法攻击/防御/暴击率/暴击伤害）由公式动态计算，在角色界面「属性」区显示。暴击率/暴击伤害不可加点，仅受装备加成和职业公式影响。
> 7 个**元素抗性**（电/火/风/水/光/毒/暗抗）默认 0、上限 100、不可加点，装备加成生效，在角色界面「属性抗性」区显示。
> 7 个**元素伤害加成**（电/火/风/水/光/毒/暗）默认 1000（千分制 1.0× 倍率）、不可加点、装备/区域加成生效，作用于输出端（攻击者造成带元素标签的伤害时，输出公式后乘以 加成/1000）。

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
| `exp_bonus` | 经验加成 | 0 | 无上限 | **不可加点**，装备/职业加成生效；按百分比提升击杀经验（叠加在等级差曲线之上） |
| `electric_resistance` | 电抗 | 0 | 100 | **不可加点**，装备加成生效；减免电属性攻击伤害（基础减伤后 ×(1−电抗/100)） |
| `fire_resistance` | 火抗 | 0 | 100 | **不可加点**，装备加成生效；减免火属性攻击伤害（基础减伤后 ×(1−火抗/100)） |
| `wind_resistance` | 风抗 | 0 | 100 | **不可加点**，装备加成生效；减免风属性攻击伤害（基础减伤后 ×(1−风抗/100)） |
| `water_resistance` | 水抗 | 0 | 100 | **不可加点**，装备加成生效；减免水属性攻击伤害（基础减伤后 ×(1−水抗/100)） |
| `light_resistance` | 光抗 | 0 | 100 | **不可加点**，装备加成生效；减免光属性攻击伤害（基础减伤后 ×(1−光抗/100)） |
| `poison_resistance` | 毒抗 | 0 | 100 | **不可加点**，装备加成生效；减免毒属性攻击伤害（基础减伤后 ×(1−毒抗/100)） |
| `dark_resistance` | 暗抗 | 0 | 100 | **不可加点**，装备加成生效；减免暗属性攻击伤害（基础减伤后 ×(1−暗抗/100)） |
| `electric_damage_bonus` | 电属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的电属性伤害（千分制，输出公式后 ×电加成/1000，1000=基准不变） |
| `fire_damage_bonus` | 火属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的火属性伤害（千分制，输出公式后 ×火加成/1000，1000=基准不变） |
| `wind_damage_bonus` | 风属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的风属性伤害（千分制，输出公式后 ×风加成/1000，1000=基准不变） |
| `water_damage_bonus` | 水属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的水属性伤害（千分制，输出公式后 ×水加成/1000，1000=基准不变） |
| `light_damage_bonus` | 光属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的光属性伤害（千分制，输出公式后 ×光加成/1000，1000=基准不变） |
| `poison_damage_bonus` | 毒属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的毒属性伤害（千分制，输出公式后 ×毒加成/1000，1000=基准不变） |
| `dark_damage_bonus` | 暗属性伤害加成 | 1000 | 无上限 | **不可加点**，装备/区域加成生效；提升造成的暗属性伤害（千分制，输出公式后 ×暗加成/1000，1000=基准不变） |

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
- **综合派生型**（注册、不可加点、角色界面「属性」区显示）：暴击率、暴击伤害、经验加成 —— 装备/职业加成生效
- **综合型**（不注册，公式计算，不可加点）：物理攻击力、魔法攻击力、物理防御力
- **元素抗性型**（注册、不可加点、角色界面「属性抗性」区显示）：电抗/火抗/风抗/水抗/光抗/毒抗/暗抗 —— 默认 0，上限 100，装备加成生效；减免对应元素标签攻击的伤害
- **元素伤害加成型**（注册、不可加点）：电/火/风/水/光/毒/暗伤害加成 —— 默认 1000（千分制 1.0× 倍率），装备/区域加成生效；作用于输出端，提升造成的对应元素伤害
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

第 2 层 · 元素增伤（仅当攻击带元素标签，非 NONE）：
  基础输出结果 × 对应元素伤害加成 / 1000
```

> 固定伤害在暴击倍率之后额外加算，不参与暴击，但参与第 2 层元素增伤。
> 暴击派生公式由各主职业覆写（如神射手敏捷对暴击率加成更高、大法师精准对暴击伤害加成更高）。
> 元素伤害加成千分制（1000 = 基准不变），与元素抗性对称：加成作用于输出端攻击者，抗性作用于受击端目标。

**承伤减免（防御方）**：

减伤分两层：基础减伤（由攻击类型决定）→ 元素减伤（由攻击元素标签决定）。

```
第 1 层 · 基础减伤：
  物理：max(0, 原伤 - max(0, 力量×2 - 攻击方物理穿透))
  魔法：原伤 × (1 - max(0, 法抗 - 攻击方法术穿透) / 100)
  混合：物理半伤 + 魔法半伤

第 2 层 · 元素减伤（仅当攻击带元素标签，非 NONE）：
  基础减伤结果 × (1 - 对应元素抗性 / 100)
```

> 物理防御力由目标的当前主职业公式派生（默认 `力量×2`），魔法防御力仅来自装备，无属性派生。
> 元素标签与攻击类型正交：火属性物理攻击 = 物理基础减伤后再应用火抗百分比；水属性魔法攻击 = 法抗百分比减伤后再应用水抗百分比。无属性（NONE）攻击不触发第 2 层。

### 元素系统

攻击除「伤害类型」（物理/魔法/混合）外，还带有正交的「元素标签」：电/火/风/水/光/毒/暗 或无属性（NONE）。

- **元素抗性属性**（受击端减伤）：电抗/火抗/风抗/水抗/光抗/毒抗/暗抗，默认 0、上限 100、不可加点、装备加成生效。
- **元素伤害加成属性**（输出端增伤）：电/火/风/水/光/毒/暗伤害加成，默认 1000（千分制 1.0× 倍率）、不可加点、装备/区域加成生效。与抗性**对称**——抗性减免受击者伤害，加成提升攻击者伤害。
- **抗性减伤**：带元素标签的攻击在基础减伤**之后**额外乘以 `(1 − 对应元素抗性/100)`；NONE 攻击不触发此层。
- **伤害加成**：带元素标签的攻击在输出公式（综合属性 + 暴击 + 固定伤害）**之后**乘以 `对应元素伤害加成/1000`；NONE 攻击不触发此层。
- **默认行为**：当前所有攻击元素**默认为 NONE**（伤害流程零变化）。玩家武器通过 SPI `IElementResolver` 解析（无模块注册时兜底返回 NONE）；怪物/箭矢/环境/魔法源暂固定 NONE。
- **扩展 SPI**：实现 `RPGSystems.registerElementResolver(IElementResolver)` 可为武器配置元素标签启用元素系统，与 `IAttackTypeResolver` 平行（装备模块可同时解析攻击类型与元素）。子模块也可在 `RPGDamageEvent.Pre` 中通过 `setElement()` 动态覆盖元素标签。

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

##  区域系统

`region` 模块提供**区域（Region）**：由 XZ 多边形 + Y 范围构成的柱体空间，对区域内实体施加环境属性增益 / 减益与元素伤害加成倍率。未被任何区域包含的位置视为「一般区域」，无任何属性影响。

### 区域定义（datapack 驱动）

每个区域一个文件，放在 `data/rpgcraftcore/rpg/regions/`，文件名（去 `.json`）即区域 ID 的 path，命名空间固定 `rpgcraftcore`：

```jsonc
// data/rpgcraftcore/rpg/regions/volcano.json
{
  "name": "火山",
  "dimension": "minecraft:overworld",     // 可省略，默认 overworld
  "bounds": {
    "polygon": [                           // XZ 封闭多边形顶点（整数 [x,z]，≥3 个）
      [100, 100], [300, 100], [300, 300], [100, 300]
    ],
    "y_range": [60, 120]                   // 可省略，默认全高 [-64, 320]
  },
  "attribute_mods": [                      // 可省略，默认空
    { "attr": "rpgcraftcore:fire_resistance", "op": "ADDITION", "value": 20 },
    { "attr": "rpgcraftcore:water_resistance", "op": "ADDITION", "value": -10 }
  ],
  "element_damage_bonus": {                // 可省略，默认空；千分制，1000=基准不变
    "FIRE": 1300,                          // 火伤 +30%
    "WATER": 700,                          // 水伤 -30%
    "POISON": 700                          // 毒伤 -30%
  }
}
```

- **`bounds.polygon`**：XZ 平面封闭多边形，顶点为整数坐标；首尾自动闭合。不要求凸，但自相交多边形行为未定义。
- **`bounds.y_range`**：纵向柱体高度 `[minY, maxY]`（含端点）。缺省为 `[-64, 320]`（全高）。
- **`attribute_mods`**：区域内实体获得的 RPG 属性修饰符。`op` 可选 `ADDITION`（默认）/ `MULTIPLY_BASE` / `MULTIPLY_TOTAL`。
- **`element_damage_bonus`**：区域内攻击者造成的对应元素伤害倍率（千分制，1000=基准不变）。配置项会自动转换为对 `<element>_damage_bonus` 属性的 `ADDITION` 修饰符（如 FIRE=1300 → `fire_damage_bonus +300`），统一走属性管线。

### 内置示例

`volcano`（火山区域）：主世界 XZ 100-300 正方形、Y 60-120，火抗 +20、水抗 -10、毒抗 -10，火伤 +30%、水伤 -30%、毒伤 -30%。

### 运行时创建（游戏内动态定义）

除 datapack 静态区域外，玩家可在游戏内通过命令**实时创建并保存**区域，无需重启 / 重载。创建流程基于「环境类型模板 + 草稿构建 + 定稿」三段式：

```
/rpg setregion <ID> <NAME> <SIZE> init   # 1. 初始化草稿：玩家为中心、边长 SIZE 的正方形
/rpg addregion <NAME>                    # 2. 重复添加点：玩家当前坐标加入草稿，重算凹包边界
/rpg setregion <ID> <NAME> done          # 3. 定稿：草稿转为正式区域，套用环境类型效果并持久化
```

- **`ID`**：环境类型（见下节），如 `volcano`。可用完整形式 `rpgcraftcore:volcano` 或 path `volcano`
- **`NAME`**：区域名（全局唯一，与正式区域共享命名空间）。done 时 `ID` 必须与 init 一致
- **`SIZE`**：初始正方形边长（≥1）。done 时忽略此参数
- **凹包算法**：`addregion` 每次增量重算边界——找到离新点最近的边界顶点，在其旁插入新点（顶点+1），保持多边形为简单多边形（无自相交）。若插入必导致边界自相交，则**抛弃该点**（保持原边界）。详见 `ConcaveHull`
- **绑定维度**：草稿与区域绑定 init 时玩家所在维度；`addregion` 跨维度报错
- **Y 范围全高**：运行时创建的区域 Y 范围固定 `[-64, 320]`
- **持久化**：`done` 后区域保存到存档（`RuntimeRegionSavedData`），服务器重启保留；草稿仅存内存（重启丢失）

### 环境类型模板（datapack）

运行时创建的区域**不内联定义效果**，而是套用预定义的**环境类型模板**（纯效果，无几何）。模板放在 `data/rpgcraftcore/rpg/environments/`，文件名（去 `.json`）即环境类型 ID 的 path：

```jsonc
// data/rpgcraftcore/rpg/environments/volcano.json
{
  "name": "火山",
  "attribute_mods": [
    { "attr": "rpgcraftcore:fire_resistance", "op": "ADDITION", "value": 20 },
    { "attr": "rpgcraftcore:water_resistance", "op": "ADDITION", "value": -10 }
  ],
  "element_damage_bonus": { "FIRE": 1300, "WATER": 700, "POISON": 700 }
}
```

`setregion <ID>` 的 `ID` 必须是已注册的环境类型。`/reload` 后模板立即重载。

### 作用机制

| 实体类型 | 注入路径 |
|---------|---------|
| 玩家 | 通过 RPG 属性附件，进 / 出区域时由 `RegionManager` 按 sourceId 精确 add/remove 修饰符（每 10 tick 检查位置，diff 出进入 / 离开区域） |
| 非玩家 LivingEntity | 监听 `GatherAttributeEvent`，构建属性快照时按实体位置即时注入修饰符 |

- **几何判定**：AABB 包围盒粗筛 → Y 范围 → XZ 多边形 inside（射线法，落在边上视为内部）
- **性能优化**：加载时预算每个区域覆盖的 chunk，建 `Long2ObjectMap` 索引；运行期查询 O(1) chunk 查表 + 少量候选区域多边形精判
- **维度隔离**：每个区域绑定维度（`dimension`），跨维度天然隔离
- **属性未注册降级**：若目标属性 ID 未注册（如 `attributes` 模块缺失），注入静默跳过，不崩溃
- **`/reload` 即时生效**：重载后区域注册表与 chunk 索引整体重建

---

##  属性点系统

`attributepoints` 模块提供自由属性点分配：玩家每升一级自动获得 1 个可分配点数，可在角色界面（按 `R`）分配到除 `life`/`skill_point` 外的任意能力型属性（综合属性不可加点）。

| 配置项 | 文件 | 默认值 | 说明 |
|--------|------|--------|------|
| `allow_decrease` | `attribute_points_config.json` | `true` | 是否允许回收/减少已分配点数；`false` 时服务端拒绝回收，角色界面隐藏 `[-]` 按钮 |

- 分配/回收通过属性管线以 `ADDITION` 修饰符表达，**不直接修改属性基础值**
- 配置在玩家登录时推送客户端，`/reload` 后对在线玩家即时生效

### 属性点系数

不同属性的「每点分配 = 实际属性值」系数不同，用整数比表达（实际加成 = 已分配点数 × 分子 / 分母，向下取整），避免浮点累积误差：

| 属性 | 系数 | 效果 |
|------|------|------|
| 力量 / 智力 / 敏捷 / 精准 / 固定伤害 | 1.0 | 每 1 点 = 1 属性值 |
| `physical_penetrate`（物理穿透） | 0.2 | 每 5 点 = 1 物理穿透 |
| `resistance`（法抗） | 0.1 | 每 10 点 = 1 法抗 |
| `magical_penetrate`（法术穿透） | 0.1 | 每 10 点 = 1 法术穿透 |

> 系数在 `AttributePointsManager` 中按属性 ID 硬编码（`COEFFICIENTS` 表），换算后才作为 `ADDITION` 修饰符应用。未达到阈值时不产生加成（例：法抗分配 5 点 → 加成 0）。

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

所有命令以 `/rpg` 为根节点，统一格式为 `/rpg <模块> <动作> [参数...]`。`op-2` 表示需要 `LEVEL_GAMEMASTERS` 权限（管理员等级 2）；查询/个人开关类命令对所有人开放，指定 `[player]`（作用于他人）时需 op-2。

### 属性（attribute）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg attribute list [player]` | 无 / op-2 | 列出指定玩家（或自己）的全部属性值 |
| `/rpg attribute get <属性名> [player]` | 无 / op-2 | 查询单个属性值 |
| `/rpg attribute set <属性名> <值> [player]` | op-2 | 设置当前值（自动同步原版血条） |
| `/rpg attribute setmax <属性名> <值> [player]` | op-2 | 设置上限值 |
| `/rpg attribute reset [player]` | op-2 | 重置全部属性到默认值 |

### core（全局）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg core deathmode <snapshot\|rescan>` | op-2 | 设置/查看死亡恢复模式 |

### 等级（leveling）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg leveling level [player]` | 无 / op-2 | 查看等级和经验 |
| `/rpg leveling setlevel <等级> [player]` | op-2 | 设置等级（经验清零） |
| `/rpg leveling addexp <经验值> [player]` | op-2 | 增加经验（自动升级） |

### 职业（profession）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg profession` | 无 | 查看当前职业 |
| `/rpg profession list` | 无 | 列出所有已注册职业 |
| `/rpg profession set <职业ID> [player]` | op-2 | 切换职业（自动移除旧加成、应用新加成） |

> 职业**升级、进阶、副职业切换**等操作不通过命令，而是在 **职业面板**（按 `P` 键打开）中完成。命令只负责查看和 GM 强制切换。

### 属性点（attrpoints）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg attrpoints` | 无 | 查看可分配点数与各属性已分配情况 |
| `/rpg attrpoints add <数量> [player]` | op-2 | 授予可分配点数 |
| `/rpg attrpoints reset [player]` | op-2 | 重置全部分配并退还所有已分配点数 |

> 玩家每升一级自动获得 1 个可分配点数；点数可在角色界面（按 `R`）分配到除 `life`/`skill_point` 之外的能力型属性（综合属性不可加点）。

### 战斗（combat）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg combat log [on\|off]` | 无 | 查询/开关个人战斗日志（持久化） |
| `/rpg combat randspawn [on\|off]` | op-2 | 查询/开关随机刷怪等级化（不持久化） |
| `/rpg combat spawn <实体ID> <等级> [json覆盖]` | op-2 | 生成指定等级的自定义怪物 |

`/rpg combat spawn` 的 JSON 覆盖字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `attributes` | object | 属性覆盖：键为属性 ID、值为整数 |
| `attack_type` | string | `PHYSICAL` / `MAGIC` / `MIX_TYPE` |
| `rating` | string | `NORMAL` / `STRONG` / `ELITE` / `NOTORIOUS_ELITE` / `BOSS` / `LORD` |
| `base_exp` | int | 击杀经验覆盖 |

```bash
/rpg combat spawn minecraft:zombie 5
/rpg combat spawn minecraft:skeleton 10 {"rating":"ELITE"}
/rpg combat spawn minecraft:spider 15 {"attack_type":"MAGIC","attributes":{"life":1000,"strength":200}}
```

### 区域（region）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg region find [名称]` | 无 | 查找最近的区域，返回其中心地面/水面坐标 |

- **省略名称**：返回命令源当前维度下、距离最近的区域
- **带名称**：按区域显示名（如 `火山`）或区域 ID（`rpgcraftcore:volcano` / `volcano`）任一匹配，在匹配集合中取最近的
- **中心地面坐标**：区域多边形 XZ 包围盒中心 + 该位置的地表 Y（`MOTION_BLOCKING` 高度图，含水面/树叶）。目标 chunk 未生成时会**强制加载**以获得准确高度，避免返回世界底部（如主世界 -64 虚空）

```bash
/rpg region find              # 当前维度最近的区域
/rpg region find 火山          # 按显示名查找
/rpg region find volcano      # 按 ID path 查找
```

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg region set <ID> <NAME> <SIZE> init` | op-2 | 初始化草稿：玩家为中心、边长 SIZE 的正方形，绑定环境类型 ID |
| `/rpg region add <NAME>` | op-2 | 将玩家当前整数坐标加入 NAME 草稿，重算凹包边界（自相交则抛弃该点） |
| `/rpg region set <ID> <NAME> done` | op-2 | 定稿：草稿转为正式区域，套用环境类型效果并持久化（忽略 SIZE） |
| `/rpg region delete <NAME>` | op-2 | 删除 NAME 运行时区域（仅能删 region set 创建的，不删 datapack 静态区域） |
| `/rpg region notify [on\|off]` | 无 | 查询/开关区域进出聊天提示（每玩家独立，持久化，默认开启） |
| `/rpg region biome [on\|off]` | op-2 | 查询/开关生物群系区域全局功能（影响全服，默认关闭） |

```bash
/rpg region set volcano camp1 20 init   # 玩家为中心 20×20 正方形草稿，环境=火山
/rpg region add camp1                    # 走到某处加点（重复多次扩展边界）
/rpg region set volcano camp1 done       # 定稿为正式区域
/rpg region delete camp1                 # 删除
/rpg region notify off                   # 关闭进出提示
```

> 草稿仅存内存（服务器重启丢失），定稿后才持久化。运行时区域与 datapack 静态区域共存，查询（region find / 属性生效）合并两者。

### 装备（equipment）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg equipment setrarity <物品ID> <稀有度> [player]` | op-2 | 设置指定玩家背包中所有该 ID 物品的稀有度 |
| `/rpg equipment setlevel <物品ID> <等级> [player]` | op-2 | 设置指定玩家背包中所有该 ID 物品的装备等级 |
| `/rpg equipment give <物品ID> [稀有度] [等级] [数量] [player]` | op-2 | 发放带指定稀有度/等级的装备（仿原版 give） |

- **物品ID**：原版或任意模组物品 ID（如 `minecraft:diamond_sword`），支持 Tab 补全
- **稀有度**：`gray`/`white`/`green`/`blue`/`purple`/`orange`/`pink`/`gold`/`red`/`rainbow`（不区分大小写）
- **等级**：0~6 的整数（0 = 清除等级）
- **范围**：整个背包（主背包 + 护甲 + 副手），所有匹配 ID 的物品堆叠统一设为指定稀有度/等级
- **`[player]`**：省略时作用于自己
- **give 的可选参数**：稀有度/等级/数量/player 均可选，省略时分别默认 `gray`/`0`/`1`/自己（位置参数，需按顺序补齐：物品 → 稀有度 → 等级 → 数量 → player）

```bash
/rpg equipment setrarity minecraft:diamond_sword blue               # 把自己背包所有钻石剑设为蓝色
/rpg equipment setrarity minecraft:diamond_sword rainbow Steve      # 把 Steve 背包所有钻石剑设为彩虹色
/rpg equipment setrarity minecraft:netherite_sword gray Notch       # 清除（设回灰色）
/rpg equipment setlevel minecraft:diamond_sword 4                   # 把自己背包所有钻石剑设为 4 级（★☆☆）
/rpg equipment setlevel minecraft:diamond_sword 0 Steve             # 清除 Steve 背包所有钻石剑的等级
/rpg equipment give minecraft:diamond_sword blue 4        # 给自己发 1 把蓝色 4 级钻石剑
/rpg equipment give minecraft:diamond_sword rainbow 6 5 Steve  # 给 Steve 发 5 把彩虹 6 级钻石剑
/rpg equipment give minecraft:diamond_sword                # 给自己发 1 把普通（gray/0）钻石剑
```

#### 装备稀有度与等级（铁砧锻造）

装备有两套独立的成长维度，均在铁砧中升级：

| 维度 | 来源 | 升级方式 | 上限 |
|------|------|---------|------|
| **稀有度** | 10 级（灰/白/绿/蓝/紫/橙/粉/金/红/彩虹） | 铁砧右槽放**稀有度宝石**，按目标稀有度消耗一定数量并有几率提升一级（失败退部分宝石） | 彩虹（默认每升一级 +10% 属性加成） |
| **等级** | 0~6 | 铁砧右槽放**同物品 ID + 同等级**的另一件装备（不要求同稀有度），无失败提升一级 | 6（默认每升一级 +20% 属性加成） |

**属性加成系数**：稀有度系数与等级系数**相乘**作用于属性加成（`最终值 = floor(基础加成 × 稀有度系数 × 等级系数)`）。两套系数的每级增幅可由 `data/rpgcraftcore/rpg/equipment_bonus_multipliers.json` 配置（`rarityBonusPerTier` 默认 0.1、`levelBonusPerLevel` 默认 0.2，`/reload` 热更新）。

**等级星形展示**（装备名后缀，最多 3 个星位）：L0 无星；L1~L3 为 1~3 个空心星（☆~☆☆☆）；L4~L6 从左到右依次变实心（★☆☆ / ★★☆ / ★★★）。

### 技能（skills）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg skills` | 无 | 查看技能信息与冷却状态 |
| `/rpg skills list` | 无 | 列出所有已注册技能定义 |
| `/rpg skills cast <技能ID> [player]` | op-2 | 强制释放某技能（仍走完整校验） |
| `/rpg skills cooldown reset [player]` | op-2 | 重置玩家全部技能冷却 |

```bash
/rpg skills list
/rpg skills cast heavy_strike
/rpg skills cooldown reset
```

### 客户端 UI（client）

| 命令 | 权限 | 说明 |
|------|------|------|
| `/rpg client hud [on\|off]` | 无 | 查询/切换 HUD 十字准星目标信息浮窗 |
| `/rpg client character` | 无 | 打开角色信息界面（也可按 R） |

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
| `data/rpgcraftcore/rpg/profession_load.json` | **职业加载开关**（按职业 ID 显式标 `enabled`，ID→布尔映射，未列出者默认启用/opt-out）。被关闭的职业不进职业树、无法被进阶与升级（注册实例仍保留，可被其他附属按 ID 查找）。**级联语义**：当前置职业被关闭时，其后继职业也会被级联关闭（即使其本身标为 `true`）；平民 `commoner` 是树根/默认主职业，恒为启用，写 `false` 会被忽略并告警。`/reload` 后立即生效并向在线玩家重推职业树 |
| `data/rpgcraftcore/rpg/professions/*.json` | **具体职业定义**（每个文件一个职业，文件名即职业 ID；含 name/type/prerequisite/bonuses/per_level/exp_table）。详见[职业系统](#-职业系统)章节 |
| `data/rpgcraftcore/rpg/attribute_points_config.json` | 属性点配置（`allow_decrease`：是否允许回收已分配点数，默认 `true`） |
| `data/rpgcraftcore/rpg/regions/*.json` | **静态区域定义**（每个文件一个区域，文件名即区域 ID；含 name/dimension/bounds/attribute_mods/element_damage_bonus）。详见[区域系统](#-区域系统)章节 |
| `data/rpgcraftcore/rpg/environments/*.json` | **环境类型模板**（纯效果无几何，供运行时创建的区域套用；含 name/attribute_mods/element_damage_bonus）。详见[区域系统-环境类型模板](#环境类型模板-datapack)小节 |

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
