# 更新日志

本项目遵循 [语义化版本](https://semver.org/)，alpha 阶段次版本号递增代表新子系统落地。

---

## [0.20.1-alpha] - 2026-06-30

> 新增独立的「RPG 宝石」创造模式标签页，集中存放所有宝石物品（稀有度宝石、镶嵌宝石），与原版物品分离。

### 新增

#### 「RPG 宝石」创造标签页

- core 新增 `RpgCreativeTabs`：用 `DeferredRegister<CreativeModeTab>` 注册 `rpgcraftcore:gemstones` 标签页，暴露 `GEMSTONES_TAB_ID` 常量，并在 `RPGCraftCore` 接入 Mod 事件总线
- **图标用稀有度宝石**：core 通过物品 ID（`rpgcraftequipment:rarity_gemstone`）运行时查 `BuiltInRegistries.ITEM` 获取图标（零编译期依赖 equipment；equipment 未加载时回退钻石）
- equipment 的 `RarityGemstoneCreativeTab`、gemstone 的 `GemstoneCreativeTab` 从「工具与实用物品」改为贡献到新标签（通过 `Identifier` 字符串匹配，互不依赖）
- 语言文件新增 `creativetab.rpgcraftcore.gemstones`（中文「RPG 宝石」/英文「RPG Gemstones」）

### 变更

- 西瓜电气石材质手动微调更新

---

## [0.20.0-alpha] - 2026-06-30

> 新增 `gemstone` 模块（镶嵌宝石系统），并通过 core 的两个贡献者 SPI（装备加成贡献者、tooltip 图像贡献者）实现与 equipment / client 模块的彻底解耦 —— 删除 gemstone 模块，equipment + client 仍正常工作。

### 新增

#### gemstone 模块（rpgcraftgemstone）

独立的镶嵌宝石系统，每件装备可镶嵌 **1 颗**宝石（通过铁砧，无失败），带来属性词条加成（灰~紫）或战斗特殊效果（橙及以上）。与 equipment 模块的「稀有度宝石」（升级装备稀有度）是**完全不同的系统**。

- 模块骨架：`gradle.properties`、`build.gradle`、`mods.toml` 模板（依赖 rpgcraftcore required、rpgcraftequipment optional）、`@Mod` 入口 `GemstoneMod`、mod id `rpgcraftgemstone`、包 `com.rpgcraft.gemstone`、版本 `0.20.0-alpha`
- **解耦设计**：gemstone 模块**仅依赖 core**，零 equipment / client 依赖。通过 core 的两个扩展点 SPI 接入，铁砧三 handler 靠右槽物品天然互斥
- 镶嵌宝石物品 `watermelon_tourmaline`（西瓜电气石，不加入创造栏，仅由指令生成）

#### core 扩展点 SPI（解耦关键）

仿 `SnapshotCoordinator` / `ISnapshotContributor` 的贡献者协调模式，新增两个扩展点，使外部模块能为装备追加加成 / tooltip 而不修改 equipment：

- `IEquipmentBonusContributor` + `EquipmentBonusCoordinator`（`core/.../equipment/api/`）：装备加成贡献者。`DefaultEquipmentHandler.calculateTotalBonus` 算完基础加成后聚合所有贡献者
- `ITooltipImageContributor` + `TooltipImageData` + `TooltipImageContributorCoordinator`（`core/.../ui/`）：tooltip 图像贡献者（数据-渲染分离，纯数据，core 可见）。client 通用渲染器读取所有贡献者数据

#### core 数据结构

- `GemInstance`（`core/.../equipment/`）：宝石实例 record（稀有度 + 1~3 个 affixId），带 Codec / StreamCodec，紧凑构造器校验词条数量
- `RPGComponents` 新增两个 DataComponent：`EQUIPMENT_SOCKET`（装备镶嵌的那颗宝石，单值）、`GEM_INSTANCE`（宝石物品自身实例数据）。命名空间 `rpgcraftcore`

#### 宝石词条配置（留接口）

- `SocketGemConfig`（gemstone）：加载 `data/rpgcraftcore/rpg/socket_gem_affixes.json`，定义属性词条（affixId → 目标属性 + 各稀有度数值表）与特效词条（affixId → effect_id）。**数值为占位（1~8），后续改 JSON + /reload 即可**
- 支持服务端 reload + 客户端镜像加载（`GemstoneClientEventHandler`），使 tooltip 在客户端正确显示数值

#### 铁砧镶嵌（无失败）

- `SocketGemForgeHandler`：铁砧左槽装备 + 右槽镶嵌宝石 → 镶嵌到装备（每件 1 颗，确定性输出无随机，仅需 `AnvilUpdateEvent`）。校验：装备未镶嵌、宝石稀有度不超过装备两级以上

#### tooltip 真实贴图渲染（client 通用框架）

- `RpgTooltipImageComponent`（数据载体，`TooltipComponent`）+ `RpgTooltipImageClientComponent`（渲染器，`ClientTooltipComponent`，用 MC 26.1 `extractImage` API + `GuiGraphicsExtractor.fill/item/text` 画方形槽 + 物品图标 + 词条文本）
- `RpgTooltipEventHandler`：注册工厂（Mod 事件总线）+ `RenderTooltipEvent.GatherComponents` 注入（Game 事件总线，因装备是原版物品无法覆写 `getTooltipImage`）
- 通用渲染器不绑定任何业务，只读 `TooltipImageData` 抽象字段；client 对 gemstone 零依赖

#### 战斗特效框架（纯接口）

- `GemSpecialEffect`（接口）+ `GemSpecialEffectRegistry`（静态注册表）+ `GemCombatEventListener`（向 core 的 `RPGEventBus` 注册 `RPGDamageEvent.Pre/Post`，遍历攻击者装备宝石收集特效 effect_id 调用对应实现）
- **本次不实现任何特效**，注册表为空，监听器空跑 —— 仅验证管线连通（接入 RPGEventBus 而非 NeoForge LivingDamageEvent，避免与战斗公式重复）

#### 指令

- `/rpg gemstone givegem <稀有度> <词条ID> [词条ID2] [词条ID3] [player]`：生成带指定稀有度与 1~3 个词条的镶嵌宝石

### 变更

- `DefaultEquipmentHandler.calculateTotalBonus` 接入 `EquipmentBonusCoordinator.collectAll`（聚合外部加成贡献者；无贡献者时行为不变）—— equipment 模块**唯一**改动
- `settings.gradle` 新增 `include 'gemstone'`；`core/build.gradle` 注册 `rpgcraftgemstone` source set + `runtimeOnly project(':gemstone')`

---

## [0.14.0-alpha] - 2026-06-28

> 新增 `entities` 模块（BlockBench 自定义生物承载点）并引入 GeckoLib 动画库；同时在文档中明确规定 GeckoLib 与 PAL 的动画库分工。

### 新增

#### entities 模块（rpgcraftentities）

承载由 **BlockBench** 制作、经 **GeckoLib** 驱动模型与动画的自定义生物，作为微内核的可选内容插件（与 skills / region 同级）。

- 模块骨架：`gradle.properties`、`build.gradle`、`mods.toml` 模板、`@Mod` 入口 `EntitiesMod`（含空 `ENTITY_TYPES` DeferredRegister）、`CLAUDE.md`（含完整「如何添加一个 BlockBench 生物」流程：导出 geo/animation/texture → 资源放置 → GeoEntity/Renderer → 注册）
- 内容型插件，**不注册任何 RPGSystems 接口**；未来需 RPG 属性 / 等级联动时复用 core 的 `GatherAttributeEvent` / `IMobDataProvider`
- 入口类 `EntitiesMod`、mod id `rpgcraftentities`、包 `com.rpgcraft.entities`、版本 `0.14.0-alpha`

#### GeckoLib 依赖引入（entities + 工程）

- 坐标 `com.geckolib:geckolib-neoforge-26.1:5.5`（5.x 起 GroupId 由 `software.bernie.geckolib` 迁移为 `com.geckolib`；ArtifactId 用 MC 主版本号 `26.1`，非 `26.1.2`）
- 仓库 `https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/` 统一接入根 `build.gradle` 的 `subprojects.repositories`（与 PAL 同位）
- `entities/build.gradle` 用 `transitive=false` 排除 GeckoLib POM 与 NeoForge `{strictly...}` 冲突的传递依赖（gson/slf4j/joml 等），与 skills 模块对 PAL 的排除策略同理
- `gradle.properties` 新增 `geckolib_version=5.5` 与 `minecraft_gecko_mc_version=26.1`
- `core/build.gradle` 在 `neoForge.mods` 注册 `rpgcraftentities` source set + `runtimeOnly project(':entities')`，开发期由 core 托管加载
- entities `mods.toml` 将 GeckoLib 声明为 `type="required"`（区别于 skills 中 PAL 的 optional：模块的存在意义即 GeckoLib 动画生物）

#### 动画库分工规则（文档硬约束）

在 README 新增「## 动画库分工」小节、根 `CLAUDE.md`「关键约定」中明确规定：
- **GeckoLib 只能用于生物（自定义实体）**的模型与动画（entities 模块）
- **PAL 只能用于玩家**动画（skills 模块）
- 二者职责互斥，**严禁混用**：生物动画不得用 PAL，玩家动画不得用 GeckoLib

### 变更

- README 徽章升级 `0.13.2` → `0.14.0`；第三方依赖表、模块组成树、模块表新增 entities 行
- 根 `CLAUDE.md` 模块结构树、依赖方向图新增 entities（`→ core + GeckoLib`）
- `THIRDPARTY_LICENSES.md` 新增 GeckoLib (MIT) 许可证段落
- `settings.gradle` 新增 `include 'entities'`
- **core** `mod_version` `0.13.1` → `0.14.0`（`build.gradle` 接线 entities，随本次发布升级）

### 验证

- `clean build` 全工程 BUILD SUCCESSFUL（含 core 聚合 entities）
- 游戏内手动验证：客户端正常启动，模组界面正确识别 GeckoLib 依赖

---

## [0.13.1-alpha] - 2026-06-27

### 修复

#### 综合属性派生公式：四舍五入 → 向下取整

所有综合属性派生公式（物攻/魔攻/物防/有效暴击率/有效暴击伤害）的浮点→整数转换由 `Math.round`（四舍五入）改为 `Math.floor`（向下取整），使「每 N 点属性 +1」类描述严格成立。

- **现象**：原「每 5 点敏捷 +1 暴击率」在敏捷 3 点时就生效（3/5=0.6 被 `round` 进位成 1），与描述不符。
- **修复后**：敏捷 3 点 → 加成 0；第 5 点才 +1，符合描述。
- **改动范围**（共 11 处公式 + 对应 Javadoc，6 个文件）：
  - `core.IProfession` 5 个默认方法（含 Javadoc「四舍五入」→「向下取整」）
  - 5 个职业基类/叶子类覆写：`WarriorSeriesProfession`（物攻/物防）、`MageSeriesProfession`（魔攻）、`ArcherSeriesProfession`（物攻）、`ArchmageProfession`（暴伤）、`MarksmanProfession`（暴击率）
- **自动生效的依赖链**：`ProfessionFormulas`（委托 IProfession 默认）、`CompositeAttributePlugin`（客户端显示，委托同一公式）、`DefaultDamageCalculator`（战斗计算）均无需单独改动，服务端/客户端无漂移。
- **测试**：`ProfessionFormulasTest` 2 个用例预期值随行为变更更新（暴击率 9.6→9、暴击伤害 54.8→54）。
- **范围外（未改）**：`getBonusAtLevel`（职业每级属性加成）、伤害公式取整、经验公式 —— 均按需保留原行为。

---

## [0.13.0-alpha] - 2026-06-27

> 生产级代码审查后的整改版本。基于对全工程的审查报告，按重要性逐项修复可维护性、健壮性与工程卫生缺陷，不引入新玩法。

### 重构

#### 属性 ID 常量集中化（core + 全模块）

新增 `core.attribute.AttributeIds` 作为全部 RPG 属性标识符（`Identifier`）的**唯一真相源**，消除此前散落在 17+ 文件、~80 处的 `fromNamespaceAndPath("rpgcraftcore", "...")` 字面量重复声明。

- 删除 `attributes.module.DefaultAttributes`（B 方案，所有引用直接改指 `AttributeIds`）
- `core.Element` 的 14 处元素↔属性映射字面量、`attributepoints.AttributePointsManager`、`leveling.DefaultLevelCalculator`、`skills.SkillsManager`、`client.ui.ClientAttributes`、8 个 `professions/*Profession` 均改为引用 `AttributeIds`
- **不影响属性的可第三方覆盖行为**：覆盖的是「属性注册行为」（`IAttributeModule` 实现 + `OVERRIDE_PRIORITY`），与 ID 字符串常量正交；`RPGSystems`/`IAttributeModule`/`DefaultAttributeRegistry` 全程未改动，`RPGSystemsTest` 仍全绿

#### ProfessionManager 拆分（profession 模块）

`ProfessionManager`（746 行）拆为 3 个职责单一的类，对外门面 API 零破坏：

- `ProfessionBonusApplier`（加成应用）：把 3 段近乎重复的 `reapply*` 合并为一个参数化 `reapply(player, prof, level, secondary)`
- `ProfessionHookDispatcher`（战斗/生命周期钩子调度）：把 `dispatchAttack/Damaged/Kill` 三段「遍历主+副」合并为 `forEachActiveProfession` + 方法引用
- `ProfessionManager` 退化为薄门面（584 行），保留全部公开方法作委托，外部调用方无需改动

#### DamageClassifier 抽取（attributes 模块）

从 `CombatEventHandler.onLivingDamagePre`（~120 行）抽离伤害分类逻辑为独立类 `DamageClassifier`，返回 `DamageClassification(attackType, element)` 记录。事件处理器收缩为「分类 → 事件 → 计算 → 应用 → 同步 → 日志」的线性流程。

#### 千分制基数常量化

新增 `Element.DAMAGE_BONUS_BASE = 1000` 作为元素伤害加成千分制基数的唯一真相源，替换 `DefaultDamageCalculator`（2 处）、`Region.allMods`（2 处）、`DefaultAttributeModule`（7 处默认值）的裸 `1000` 字面量。

### 新增

#### i18n 命令/反馈消息系统（core + 全模块）

建立国际化基础设施，迁移玩家最常看到的命令反馈消息：

- 新建 `assets/rpgcraftcore/lang/zh_cn.json`（~95 条键）+ 填实 `en_us.json`（英文翻译）
- 11 个文件 ~90 处 `Component.literal("中文§a...")` → `Component.translatable("rpgcraft.<module>.<key>", args...)`；§ 颜色码随文本迁入 lang；on/off、类型标签等子串独立为 lang 键
- 涵盖 9 个 `*Commands` 类、`RegionManager` 进出提示、`CombatEventHandler` 实时战斗日志
- **暂留**：UI 屏幕（`RPGProfessionScreen`/`RPGCharacterScreen`/属性面板插件）作为后续独立任务

#### 单元测试基建与首批用例

- 根 `build.gradle` 的 `subprojects {}` 集中 JUnit 5 配置，全 10 模块开箱可测（此前仅 core/leveling 配置）
- `core/build.gradle` 新增 `testImplementation.extendsFrom(modDevCompileDependencies)`，使测试源集可访问 `net.minecraft.*` 类
- 新增 `AttributePipelineTest`（8 项）：覆盖修饰符管线数学（ADDITION→MULTIPLY_BASE→MULTIPLY_TOTAL）、百分比先求和再乘（非复利）、负值截断、`AttributePostAdditionEvent`/`AttributeFinalizeEvent` 事件插手

#### DeathRestoreMode 持久化（core）

`/rpg deathmode` 的切换此前仅写内存 `volatile` 字段，服务端重启即重置为 `SNAPSHOT`。新增 `DeathRestoreModeSavedData`（复用 `RandomSpawnSavedData` 的双层 SavedData + 内存镜像模式），服务端启动时由 `ServerStartedEvent` 从存档恢复内存镜像，跨重启保留。

### 修复

#### EntityAttribute 缓存数据竞争（core）

`cachedValue`/`cachedMaxValue` 此前为普通 `int`（仅 `cacheValid` 为 volatile），存在「读到 `cacheValid==true` 却读到旧缓存值」的数据竞争。两个字段改为 `volatile` 并补充可见性 Javadoc。

### 变更

#### core

- 新增 `AttributeIds`、`DeathRestoreModeSavedData`、`AttributePipelineTest`
- `AttributeManager.LIFE_ID` 改为委托 `AttributeIds.LIFE_ID`（保留为便捷别名）
- `RPGCommands` Javadoc 把 "combat 模块" 修正为 "combat 子系统（位于 attributes 模块）"
- `EntityAttribute.simpleCompute` 补充 Javadoc 说明其与 `AttributePipeline.compute` 的故意重复（无事件兜底路径）

#### attributes

- 新增 `DamageClassifier`；`CombatEventHandler` 引用其分类结果，删除冗余 import
- `DefaultDamageCalculator`/`DefaultAttributeModule` 改用 `Element.DAMAGE_BONUS_BASE`
- `CombatCommands` 全部反馈消息迁移到 lang

#### profession

- 新增 `ProfessionBonusApplier`、`ProfessionHookDispatcher`
- `ProfessionCommands` 反馈消息迁移到 lang

#### region

- `Region.allMods` 改用 `Element.DAMAGE_BONUS_BASE`
- `RegionCommands`/`RegionManager` 反馈消息迁移到 lang
- `RegionsDefinitionLoader`/`EnvironmentTypeLoader` 的 `result().get()` → `.orElseThrow(...)`（自文档化）

#### attributepoints / leveling / skills / professions / client

- 各自 `*Commands` 反馈消息迁移到 lang；属性 ID 字面量改引用 `AttributeIds`

### 工程卫生

- 删除误提交的孤立 NeoForge 框架源码副本：`core/IAttachmentHolder.java`、`net/neoforged/.../ServerTickEvent.java`（不在任何源集）
- `.gitignore` 新增 `/net/`、`/core/net/`、`**/src/main/**/net/neoforged/`、`**/*.bak`、`**/*.toml.bak` 防护规则
- 收窄 `RPGProfessionScreen` 的 `catch (Throwable)` → `catch (Exception)` + debug 日志；`ProfessionConfigLoader`/`SkillsDefinitionLoader` 共 4 处 `catch (Exception ignored)` 补 debug 日志
- 保留 `MobLevelData`（NBT 反序列化热路径）与 `AttributeHudOverlay`（每帧渲染）的静默 fallback（热路径中静默是正确行为）

### 模块版本

| 模块 | 旧版本 | 新版本 | 是否改动 |
|------|--------|--------|---------|
| core | 0.12.0-alpha | 0.13.0-alpha | ✅ |
| attributes | 0.11.0-alpha | 0.13.0-alpha | ✅ |
| leveling | 0.10.0-alpha | 0.13.0-alpha | ✅ |
| equipment | 0.10.0-alpha | 0.10.0-alpha | — |
| profession | 0.10.0-alpha | 0.13.0-alpha | ✅ |
| professions | 0.10.0-alpha | 0.13.0-alpha | ✅ |
| client | 0.10.4-alpha | 0.13.0-alpha | ✅ |
| attributepoints | 0.10.5-alpha | 0.13.0-alpha | ✅ |
| skills | 0.10.0-alpha | 0.13.0-alpha | ✅ |
| region | 0.12.0-alpha | 0.13.0-alpha | ✅ |

---

## [0.12.0-alpha] - 2026-06-27

### 新增

#### 区域游戏内实时创建（region 模块，新子系统）

玩家可在游戏内通过命令**实时创建、编辑、保存区域**，无需重启 / 重载。基于「环境类型模板 + 草稿构建 + 定稿」三段式：

- **`/rpg setregion <ID> <NAME> <SIZE> init`**（op-2）：以玩家当前坐标为中心、边长 SIZE 的正方形初始化草稿，绑定环境类型 ID
- **`/rpg addregion <NAME>`**（op-2）：将玩家当前整数坐标加入草稿，增量重算凹包边界
- **`/rpg setregion <ID> <NAME> done`**（op-2）：定稿，草稿转为正式区域并持久化
- **`/rpg delregion <NAME>`**（op-2）：删除运行时区域

**凹包算法（`ConcaveHull`，增量边界替换法）**：每次新增点 P，找到离 P 最近的边界顶点 V，在 V 旁插入 P（顶点+1）。校验新边是否与现有边相交（`SegmentIntersection` 规范相交判定，共享端点不算相交）；相交则抛弃该点。算法成立前提是 `init` 提供合法初始正方形。已通过 9 个独立测试用例（凸起/凹陷/L形/U形/细长形/四向凸起等）验证始终无自相交。

**环境类型模板（`EnvironmentType`）**：将「效果」与「几何」解耦。环境类型（火山等）由 `data/rpgcraftcore/rpg/environments/*.json` 预定义效果，运行时区域套用模板效果 + 玩家构建几何生成完整 `Region`。

**双层存储（`RegionsRegistry`）**：static（datapack，reload 替换）+ runtime（玩家创建，`RuntimeRegionSavedData` 持久化）双层，查询合并两者。`replaceDatapack` 只换 static 不动 runtime，故 `/reload` 不丢失运行时区域。服务器重启后由 `ServerStartedEvent` 从存档恢复 runtime 层。

**草稿管理（`RegionDraft` / `RegionDraftManager`）**：草稿仅存内存（不持久化，重启丢失），定稿后才持久化。绑定创建维度，`addregion` 跨维度报错。

#### 区域进出聊天提示（region + core）

- 进入/离开区域时在聊天栏输出提示（`§a[区域] §7你进入了/离开了 <名称>`）
- **`/rpg regionnotify [on|off]`**（无权限）：每玩家独立开关，默认开启，持久化到 `PlayerPreferences` 附件
- 提示挂载在既有进出检测点（`RegionManager.updatePlayerRegions` 的 entered/left 差集），零额外开销

### 变更

#### core

- `PlayerPreferences` 新增 `regionNotifyEnabled` 字段（默认开启，`optionalFieldOf` 兼容旧存档）

#### region

- `RegionsRegistry` 改为双层存储（static + runtime）+ 增量 API（`addRuntime`/`removeRuntime`/`replaceDatapack`/`replaceAllRuntime`）
- `RegionsDefinitionLoader`：`replaceAll` → `replaceDatapack`（只换 static）；运行时区域恢复移至 `ServerStartedEvent`（reload 时拿不到 server）
- `RegionPolygon` 新增 `getVertices()`（供 ConcaveHull 增量算法读取当前边界）

### 版本号

- 工程版本号：`0.11.1-alpha` → `0.12.0-alpha`（区域实时创建属新子系统，副版本 +1、次版本归零）
- 改动模块版本号升级 `0.12.0-alpha`：`core`、`region`
- 未触及模块保持原版本

---

## [0.11.1-alpha] - 2026-06-27

### 新增

#### findregion 命令（region 模块）

新增 `/rpg findregion [名称]` 命令，查找最近的区域并返回其**中心地面/水面坐标**：

- **省略名称**：返回命令源当前维度下、距离最近的区域（平面 XZ 距离）
- **带名称**：按区域显示名（如 `火山`）或区域 ID（`rpgcraftcore:volcano` / `volcano`）任一匹配，在匹配集合中取最近的
- **中心地面坐标**：多边形 XZ 包围盒中心 `(minX+maxX)/2, (minZ+maxZ)/2`，Y 取该位置 `MOTION_BLOCKING` 高度图（最高阻挡运动方块，含水面/树叶，最贴近可站立地面）
- **目标 chunk 未生成处理**：高度图只存在于已生成到 FULL 状态的 chunk。命令会先 `getChunk(chunkX, chunkZ, ChunkStatus.FULL, true)` **强制加载目标 chunk**，再查高度图，避免对未探索区域返回世界底部（如主世界 -64 虚空）
- **权限**：无要求（仅查询，所有玩家可用）

配套小工具：
- `RegionPolygon.centerX()` / `centerZ()`：XZ 包围盒中心
- `RegionsRegistry.matchByName(name)`：按显示名或 ID 匹配区域

### 修复

#### region 模块未加载到运行环境（core 构建配置遗漏）

`region` 模块创建时漏在 `core/build.gradle` 注册运行时加载，导致整个模块（含 `findregion` 命令、区域属性系统、tick 检查）在游戏中**完全不生效**：

- 现象：游戏中找不到 `/rpg findregion`；`RegionMod` 类未被 NeoForge 扫描
- 根因：`core/build.gradle` 缺 `runtimeOnly project(':region')` 依赖与 `neoForge.mods` 块的 source set 声明，`InDevFolderLocator` 的 mod 坐标列表不含 `rpgcraftregion`
- 修复：在 `core/build.gradle` 补齐两项（与 skills 等模块一致），region 正确加载

> IntelliJ 调试需 Refresh Gradle 同步运行配置后重启游戏，运行配置的 `fml.modFolders` 才会纳入新模块。

### 版本号

- 工程版本号：`0.11.0-alpha` → `0.11.1-alpha`（单模块功能新增，次版本 +1）
- 改动模块版本号升级 `0.11.1-alpha`：`core`、`region`
- 未触及模块保持原版本

---

## [0.11.0-alpha] - 2026-06-27

### 新增

#### 区域系统（region 模块，新子系统）

新增 `region` 插件模块（mod ID `rpgcraftregion`），提供由 **XZ 多边形 + Y 范围** 构成的柱体空间区域，对区域内实体施加环境属性增益 / 减益与元素伤害加成倍率。未被任何区域包含的位置视为「一般区域」，无任何属性影响。

- **几何模型**：水平面 (X,Z) 是封闭多边形（顶点为整数坐标，≥3 个），纵向 `[minY, maxY]` 限定柱体高度。判定顺序：AABB 包围盒粗筛 → Y 范围 → XZ 多边形 inside（射线法，落在边上视为内部）
- **数据驱动**：区域定义由 datapack JSON 驱动（`data/rpgcraftcore/rpg/regions/*.json`），由 `RegionsDefinitionLoader` 在服务端 reload 时加载，`/reload` 即时生效（照搬 `SkillsDefinitionLoader` 范式）
- **性能优化**：加载时预算每个区域覆盖的 chunk，建 `Long2ObjectMap` 索引；运行期位置查询 O(1) chunk 查表 + 少量候选区域多边形精判；每 10 tick（0.5 秒）节流检查玩家位置
- **属性注入双路径**：
  - 玩家：通过 RPG 属性附件，进 / 出区域时由 `RegionManager` 按 sourceId 精确 add/remove 修饰符
  - 非玩家 `LivingEntity`：监听 `GatherAttributeEvent`，构建属性快照时按位置即时注入
- **维度隔离**：每个区域绑定维度（`dimension` 字段），跨维度天然隔离
- **属性未注册降级**：`attributes` 模块缺失时注入静默跳过，不崩溃（运行期可选依赖）
- **内置示例**：`volcano.json`（火山区域：主世界 XZ 100-300 正方形、Y 60-120，火抗 +20、水抗 -10、毒抗 -10，火伤 +30%、水伤 -30%、毒伤 -30%）

#### 元素伤害加成属性（与元素抗性对称，core + attributes）

为支撑区域「元素伤害输出倍率」语义，并使元素系统在输出端与受击端对称，新增 7 个元素伤害加成属性：

- `electric/fire/wind/water/light/poison/dark_damage_bonus`（命名空间 `rpgcraftcore`）
- 默认值 1000（千分制 1.0× 倍率）、不可加点、装备/区域加成生效
- **作用机制**：带元素标签（非 NONE）的攻击在输出公式（综合属性 + 暴击 + 固定伤害）**之后**乘以 `对应元素伤害加成属性值 / 1000`；NONE 攻击不触发此层，默认行为零变化
- 与元素抗性**对称且正交**：抗性作用于受击端（减伤），加成作用于输出端（增伤）；同一元素可同时「攻击者火伤+30%」与「受击者火抗减伤」

### 变更

#### core

- `Element` 枚举新增 `damageBonusId()` 方法，与 `resistanceId()` 完全对称，是「元素 ↔ 伤害加成属性」映射的唯一真相源
- `IDamageCalculator` 新增 `calculateOutgoingDamage(attacker, type, element)` 重载（默认委托旧方法 `calculateOutgoingDamage(attacker, type)`，向后兼容）

#### attributes

- `DefaultAttributes` 新增 7 个 `*_DAMAGE_BONUS_ID` 常量
- `DefaultAttributeModule` 注册 7 个元素伤害加成属性
- `DefaultDamageCalculator` 输出公式拆出 `computeBaseOutgoing`（基础输出）+ 新增 `applyElementalBonus`（元素增伤层，基础输出后乘 `bonus/1000`）
- `CombatEventHandler` 战斗伤害调用改为带 element 的输出重载（环境伤害路径不变）

### 版本号

- 工程版本号：`0.10.5-alpha` → `0.11.0-alpha`（新子系统落地，副版本 +1、次版本归零）
- 改动模块版本号升级 `0.11.0-alpha`：`core`、`attributes`、`region`
- 未触及模块（`leveling`/`equipment`/`profession`/`professions`/`attributepoints`/`skills`/`client`）保持原版本

---

## [0.10.5-alpha] - 2026-06-26

### 调整

#### 属性点系数（按属性差异化）

`AttributePointsManager` 引入按属性的「每点分配 = 实际属性值」系数表（`COEFFICIENTS`），用整数比（分子/分母）表达以避免浮点累积误差：

- `resistance`（法抗）：系数 0.1 —— 每 10 点 = 1 法抗
- `magical_penetrate`（法术穿透）：系数 0.1 —— 每 10 点 = 1 法术穿透
- `physical_penetrate`（物理穿透）：系数 0.2 —— 每 5 点 = 1 物理穿透
- 其余可加点属性：系数 1.0（保持原行为）

换算后的加成才作为 `ADDITION` 修饰符应用；未达阈值时不产生加成（如法抗分配 5 点 → 加成 0）。

---

## [0.10.4-alpha] - 2026-06-26

### 优化

#### 角色界面视觉细节

- **标题「属性」与 ? 详情图标整体居中**：原实现仅居中标题文字、问号贴在其右侧，导致问号出现时视觉重心右移。改为把「标题 + 间距 + ?」作为整体块居中，并校正问号垂直偏移与较高的中文标题光学居中。
- **基础属性区 / 属性抗性区：分隔线与文字间距修正**：修复分隔线 Y 坐标计算错误（原贴在内容正上方导致 `SEPARATOR_BOTTOM_GAP` 失效），改为分隔线位于上方留白之后，下方留白真正落在分隔线与文字之间（上下各 4px），消除文字紧贴分隔线的拥挤感。

---



## [0.7.0-alpha] - 2026-06-24

### 新增

#### 综合属性派生公式下沉到主职业类

物理攻击力/魔法攻击力/物理防御力/有效暴击率/有效暴击伤害的派生公式从全局硬编码迁移到主职业类：

- **`IProfession` 新增 5 个 default 公式方法**：
  - `computePhysicalAttack(int strength, int intelligence)` 默认 `力量×2+智力`
  - `computeMagicalAttack(int strength, int intelligence)` 默认 `智力×2+力量`
  - `computePhysicalDefense(int strength, int intelligence)` 默认 `力量×2`
  - `computeEffectiveCritRate(int critRate, int agile)` 默认 `暴击率+敏捷/5`
  - `computeEffectiveCritDamage(int critRatio, int precision)` 默认 `暴击伤害+(精准/5)×2`
- **`ProfessionFormulas`** 工具类统一封装"实体→主职业→公式→回退"解析链
- **战士**：`computePhysicalAttack` = `力量×3+智力`，`computePhysicalDefense` = `力量×3`
- **法师**：`computeMagicalAttack` = `智力×3+力量`
- **神射手**：`computeEffectiveCritRate` = `暴击率+敏捷/3`
- **大法师**：`computeEffectiveCritDamage` = `暴击伤害+(精准/3)×2`
- 其余职业与怪物保留默认公式，零行为变化

#### 暴击率/暴击伤害改为不可加点，移至综合属性区展示

- **`IAttributeEntry` 新增 `isAllocatable()` 标记**（默认 `!shouldResetOnRespawn()`）
- 暴击率/暴击伤害注册为 `allocatable=false`，属性点系统排除
- 角色界面「综合属性」区新增**有效暴击率/暴击伤害**行，悬停 tooltip 显示拆分（如 `50基础 + 35敏捷`）
- `CompositeAttributePlugin` 新增 `getTooltip` 支持

### 修复

#### 属性序列化污染导致重登后数值无限膨胀

`EntityAttribute.CODEC` 原通过 `getValue()/getMaxValue()`（管线计算结果）序列化，
反序列化构造函数却回填到 `baseValue/baseMaxValue`（管线起点），导致每次退出世界再进入
`reapplyAllModifiers` 在已污染的基础值上再次叠加修饰符，数值随重登次数无限膨胀。

修复：CODEC 改为序列化 `getBaseValue()/getBaseMaxValue()`，修饰符不持久化，由各模块登录时重建。

### 变更

- 改动模块版本号升级 `0.7.0-alpha`：`core`、`attributes`、`attributepoints`、`client`、`professions`
- 未触及模块（`profession`/`leveling`/`equipment`/`skills`）保持原版本

---

## [0.6.1-alpha] - 2026-06-23

### 新增

#### 魔法主职业系列（术士 → 法师 → 大法师）

魔法向的主职业进阶树，与物理系（战士/弓箭手）并行：

| 职业 | 前置 | 加成（基础 / 每级） |
|------|------|---------------------|
| `sorcerer` 术士 | commoner | 智力 +5 / +1；力量 -3 |
| `mage` 法师 | sorcerer（满级） | 智力 +6 / +1；法术穿透 +3 / +1 |
| `archmage` 大法师 | mage（满级） | 智力 +7 / +1；暴击伤害 +5 / +1 |

图标统一为书本。

#### 复合职业系统（compound 类型）

新增第三类职业类型 `COMPOUND`，要求解锁**复数主职业**（均达满级）作为前置：

- `IProfession.ProfessionType` 新增 `COMPOUND`（追加在末尾，保证 ordinal 网络序列化兼容），
  并新增 `isMainLike()` = `PRIMARY || COMPOUND`，集中表达「可作为主职业」。
- `IProfession` 新增 `getPrerequisites()`（默认从单 `getPrerequisite()` 派生），
  `isAdvanced()` 改据此判断；复合职业覆写该方法返回多前置集合。
- `ProfessionManager.canAdvance` 泛化为「所有前置已解锁且达满级」+ 允许 `COMPOUND`。
- 复合职业行为等同主职业：可 advance / switchMain / 走主职业修饰符管线，**不可**作副职业。
- 第一个复合职业：`witchblade`（魔剑士），前置 `berserker + mage`，加成 力量 +4 / 智力 +4（各 +1 每级）。

#### 复合职业面板（标题栏 ⇌ 切换）

职业面板新增「复合职业窗」，与主/副双窗复用同一窗口，通过标题栏 **⇌** 按钮（化学可逆反应符号样式）切换：

- 复合窗复用 `renderTreeWindow` + COMPOUND 类型过滤，单独成树。
- 每个复合节点**上方挂出其前置主职业的只读图标**（暗灰描边 + 虚线连接），不进布局 map 故**不可**进阶/升级/切换。
- ⇌ 图标用手绘两支反向半箭头（`graphics.fill`）实现，几何相对按钮中心计算，绕开 Unicode `⇌` 字形在 Minecraft 字体里居中偏移的问题。
- tooltip 新增 `[复合职业]` 标签 + 多前置满足状态提示。

### 优化

#### 职业面板居中行为

- **进阶/切换主职业后自动重新居中**到新当前职业（原行为：首次居中后被锁死，进阶后停留在旧位置/树左侧）。
- **进阶确认框跳转期间保留全部窗口状态**（矩形/pan/maximized），不再被 `removed()` 重置回默认视图 —— 进阶后保持原最大化状态。
- **窗口从最大化还原为缩小时重新居中**：避免大窗里把树拖到底部后缩小导致树推出可视区。主/副/复合三类窗口均覆盖。
- **视图切换时重置居中**：⇌ 切回主视图时强制重新居中主职业树（两视图共用 mainWindow 的 pan，复合视图里改过的 pan 会错位到主视图）。
- **当前主职业为复合职业时居中回退到几何中心**：主/副双窗视图渲染 PRIMARY 树，复合职业节点不在该树里，回退到整树几何中心保证树可见、pan 不冻结。

### 变更

- `SyncProfessionStatePacket` 节点字段 `prerequisite`（单 nullable id）→ `prerequisites`（id 列表），
  网络协议版本 `1` → `2`（`PacketHandler` + `ProfessionMod`），NeoForge 据此拒绝版本不匹配连接。
- 涉及模块版本号升级 `0.6.1-alpha`：`core`（0.6.0→0.6.1）、`profession`（0.5.4→0.6.1）、
  `professions`（0.5.4→0.6.1）、`client`（0.6.0→0.6.1）。未触及模块（attributes/leveling/equipment/attributepoints/skills）保持原版本。

---

## [0.5.3-alpha] - 2026-06-22

### 修复

#### 角色界面职业等级显示错误（`SyncPlayerProfessionPacket`）
角色面板（`PlayerInfoPlugin`）职业名显示正确，但等级恒显示 Lv.1、已激活副职业行不显示。

- **根因**：`SyncPlayerProfessionPacket`（服务端→客户端单向同步包）只同步了 `professionId`
  （当前主职业 ID），**未同步等级表、已激活副职业集合、经验池**。`PlayerInfoPlugin` 直接读
  客户端附件 → `getProfessionLevel(id)` 返回 0（`Math.max(1,0)=1` 恒 Lv.1）、
  `getActiveSecondaryProfessions()` 返回空集（副职业行不显示）。
  注：职业面板 `RPGProfessionScreen` 走另一个包 `SyncProfessionStatePacket`（本就同步完整状态到
  `ProfessionStateCache`），所以面板里等级是对的 —— 这就是为什么只有属性界面错、职业面板对。
- **修复**：`SyncPlayerProfessionPacket` 从「只同步 ID」扩展为「同步完整状态」：
  - record 新增 `levels`（职业→等级）、`activeSecondary`（已激活副职业集合）、`skillPointPool`（经验池）
  - `sendToClient` 发送完整数据
  - `handle` 写入客户端附件（先清空激活集再逐个设回，不调用服务端权威方法、不触发加成重算）
  - StreamCodec 手写 encode/decode（参考 `SyncProfessionStatePacket` 模式）

### 变更

- 全模块版本号统一升级 `0.5.2-alpha` → `0.5.3-alpha`

---

## [0.5.2-alpha] - 2026-06-22

### 新增

#### 原版风格 UI（职业面板 + 角色界面）
把 RPG 自定义 UI 全面改为原版 Minecraft 视觉风格。关键技术：MC 26.1.2 的 `GuiGraphicsExtractor`
已内置 `blitSprite(RenderPipelines.GUI_TEXTURED, spriteId, x, y, w, h, color)`（9-slice/stretch 自动分发），
可直接复用原版 GUI 精灵；`Screen.extractMenuBackgroundTexture(...)` 平铺 `menu_background.png` 泥土纹理。

- **面板背景**：职业窗 + 角色左右面板改用 `Screen.extractMenuBackgroundTexture` 平铺原版泥土纹理
  （取代纯色 fill / 自定义 9-slice 贴图）
- **经典斜面边框**：新增 `drawContainerBorder` —— 1px 黑外框 + 上左白高光 `0xFFFFFFFF` +
  下右深灰阴影 `0xFF555555`，配色解码自 `textures/gui/container/inventory.png`（原版边框是画在每张
  容器 PNG 里的，无独立可复用精灵，故用 fill 复刻）
- **可读性**：`menu_background` 本身仅 25% 黑（每像素 `rgba(0,0,0,64)`），叠一层 `0x80000000`
  （50% 黑）覆盖提高文字可读性
- **节点框**：职业节点改用原版 `advancements/task_frame_obtained`/`_unobtained`（26×26 正好等于
  `NODE_SIZE`，1:1 无缩放，原版为「方形节点+图标+状态」专门设计的资产）
- **节点图标**：`graphics.fakeItem(ItemStack, x+5, y+5)` 渲染原版物品图标（居中偏移 `(26-16)/2=5`），
  新增 `NODE_ITEM_ICONS` 映射：warrior→铁剑、berserker→钻石斧、archer→弓、marksman→弩、
  commoner→木锄、apprentice→书；无映射时回退中文字符
- **按钮**：`+`/最大化/属性 `+`/`-` 按钮改用原版 `widget/button`/`widget/button_highlighted`/
  `widget/button_disabled` 9-slice 精灵；文字垂直居中用原版公式 `(height-9)/2+1`（lineHeight=9）
- **滚动条**：角色面板滚动条改用原版 `widget/scroller` + `widget/scroller_background` 精灵
- **标题分隔线**：标题栏底部画原版 `HEADER_SEPARATOR`（32×2 水平平铺）
- **节点下行**：等级与升级合并到节点正下方单个加宽按钮「Lv.N +」（canInvest 高亮可点，
  经验不足显禁用态；满级显「Lv.MAX」无按钮）—— 取代旧的「上方徽章 + 下方按钮」分离布局，
  避免上方徽章遮挡上一节点
- **属性按钮**：去方括号（`[+]`→`+`、`[-]`→`-`），符号原版公式垂直居中

### 变更

- 调色板统一原版灰阶（标题黄 `0xFFFFE000`、分隔线 `0xFF373737`、提示灰 `0xFFA0A0A0`）
- 删除上一版的自定义面板贴图（`panel/profession.png` 等 6 文件）—— 改用原版泥土平铺后不再需要
- `RPGProfessionScreen`/`RPGCharacterScreen` 删除 `fillRounded`（八角形伪圆角）—— 原版面板无圆角
- 全模块版本号统一升级 `0.5.1-alpha` → `0.5.2-alpha`

### 已知限制

- 实机视觉验证未做（无头环境无 `runClient`）。所有验证为编译期 + 原版精灵/像素解码的静态确认。
  `menu_background` 平铺、9-slice 按钮缩放、`fakeItem` 图标、斜面边框配色均依据 MC 26.1.2
  反编译源码与 PNG 像素解码确认正确，但实际渲染效果建议 `runClient` 复核

---

## [0.5.1-alpha] - 2026-06-22

### 重构

#### 职业面板 UI 重设计（取消固定详情页）
取消右侧固定职业详情面板，改为悬停气泡 + 节点交互。布局从「双窗 + 详情」三栏简化为「双窗居中」。

- **悬停气泡**：鼠标悬停节点时在光标处显示职业信息（名称/类型/状态/描述/等级/下一级消耗），
  复用 vanilla `setComponentTooltipForNextFrame`，在 scissor 之外触发避免被窗内裁剪
- **节点下 `+` 按钮**：仅在「可投入一级」的已解锁职业（已解锁、未满级、经验池够）下方显示 12×12 小按钮，
  点击投入一级；命中检测用屏幕坐标，渲染用逻辑坐标
- **双击交互**（300ms 内同节点二次点击）：
  - 已解锁、非当前主职业 → 直接切换为主职业
  - 可进阶、未解锁的主职业 → 弹 `ConfirmScreen` 确认 → 进阶并切换
  - 已解锁副职业 → 切换该副职业的激活状态
- **激活副职业蓝色外框**：激活的副职业节点外加蓝色框（`COLOR_SECONDARY_ACTIVE`），优先于当前主职业金框
- **节点上方等级徽章**：已解锁职业节点顶部显示蓝底白字等级数字
- **标题栏最大化按钮**：主/副窗右上角 `□`/`⊟` 按钮，点击最大化铺满全屏（水平居中）、隐藏另一窗；
  再点还原自动恢复双窗默认布局（提取 `defaultMainRect/defaultSecondaryRect` 每帧重算）
- **窗口水平居中**：双窗与最大化窗均水平居中（旧版详情面板取消后右侧不再留白）
- **坐标系统澄清**：节点渲染用逻辑坐标（靠 `pose().translate(panX/panY)` 平移），命中检测用屏幕坐标

#### 副职业模型重构：多副职业独立激活（加成共存）
从「单一当前副职业 + 全局开关」改为「多个副职业各自独立激活、加成共存」。跨 core / profession / client 三层。

- **`ProfessionData`**：删除 `secondaryProfessionId` + `secondaryActive`，新增
  `Set<Identifier> activeSecondaryProfessions`；CODEC 字段改为 `active_secondary` 列表（`Optional` 防 null）
- **`IProfessionSystem` SPI**：删除 `getSecondary`/`setSecondary(id)`/`isSecondaryActive()`/`setSecondaryActive(bool)`，
  新增 `getActiveSecondary()`/`isSecondaryActive(id)`/`setSecondaryActive(id, bool)`
- **`ProfessionActionPacket`**：删除 `SET_SECONDARY`/`CLEAR_SECONDARY`，`TOGGLE_SECONDARY` 语义改为
  「针对 `professionId` 指定的副职业切换激活」
- **`ProfessionManager`**：`setSecondaryActive(player, id, active)` 严格校验后写入 + 立即应用/移除该副职业加成；
  `applyBonusAtLevel` 的 `secondary` 改为显式参数（不再从单副职业推断）；`investLevel`/`reapplySecondaryBonuses`
  遍历激活集合逐个重算
- **`SyncProfessionStatePacket`**：序列化改为 `active_secondary` 集合（`currentSecondary + secondaryActive` 两字段合一）
- **`ProfessionStateView`**：`currentSecondary + secondaryActive` → `Set<Identifier> activeSecondary`
- **`ProfessionLoginEventHandler`**：登录修复改为遍历激活集合剔除「失效/类型违规/等于主职业」的项
- **`ProfessionSnapshotContributor`**：死亡快照改为捕获/恢复激活集合
- **`PlayerInfoPlugin`**：角色面板副职业显示改为遍历激活集合每行一个；`getHeight()` 改为动态（随激活副职业数变化）

### 变更

- `ICharacterScreenPlugin.getHeight()` Javadoc 放宽为「确需随玩家状态变化时同一帧内查询须一致」
  （原约束「会话内稳定」不再适用多副职业场景）
- 全模块版本号统一升级 `0.5.0-alpha` → `0.5.1-alpha`

### 已知限制 / 迁移注意

- **旧存档副职业数据丢失**：旧存档的 `secondary`（单一副职业）字段将被忽略，
  玩家需重新双击激活副职业。模组仍是 alpha 阶段，可接受
- **最大化→还原会重置窗口位置**：用户拖动过的窗口在最大化→还原后被重置为默认居中布局
  （原位置在最大化时已被覆盖，未持久化拖动位置）

---

## [0.5.0-alpha] - 2026-06-19

### 新增

#### 主动技能系统（`skills` 模块）
首个完整的「按键 → 资源 → 冷却 → 动画 → 伤害」闭环，作为 `0.4.x` 之后的新增子系统。

- **core 层 SPI**：新增 `ISkillSystem` / `ISkill` / `ISkillRegistry` / `ISkillProvider` 接口，
  `RPGSystems.registerSkillSystem()` 注册门面；`PlayerSkillData` 附件持久化冷却与已学技能
- **datapack 驱动**：技能定义放 `data/rpgcraftcore/rpg/skills/*.json`（`name` / `resource_cost` /
  `cooldown_ticks` / `damage_amount` / `attack_type` / `animation_id` / `range`），`/reload` 即时重载
- **释放闭环**：按键 → `CastSkillPacket`(C→S) → 服务端权威校验（资源/冷却/学习）→ 扣 `skill_point` →
  启动冷却 → `PlaySkillAnimationPacket`(S→C) 播 PAL 动画 → 前方 AABB 目标走 vanilla hurt（`CombatEventHandler` 接管 RPG 公式）
- **示范技能**：内置 `heavy_strike`（重击）—— 消耗 10 技能点，冷却 5 秒，造成 30 物理伤害，默认数字键 `1` 释放
- **命令**：`/rpg skills [list|cast|cooldown reset]`
- **快照**：`SkillSnapshotContributor` 保证死亡/重生不掉冷却进度与已学技能

#### PAL（Player Animation Library）集成
- 接入 PAL `1.2.4+mc.26.1`（playerAnimator 官方继任者），仅客户端
- `PalBridge` 隔离全部 PAL 类引用；`SkillAnimationHandler` 反射探测 PAL 可用性
- 占位动画 `assets/rpgcraftskills/player_animations/heavy_strike.json`（右臂抬-劈-复位）

### 修复

- **PAL dev 环境 LinkageError（PAL issue #128）**：PAL `1.2.2`/`1.2.3` 在 NeoForge 26.1 dev 环境，
  Core 的 `MolangLoader` 跨类加载器引用 `mochafloats` 导致 `LinkageError`。升级到作者重新发布的 `1.2.4+mc.26.1` 后修复
  （仅需 `--refresh-dependencies` 刷新本地缓存）
- **PAL 动画 layer 注册时机**：原 `static{}` 懒加载导致 layer 工厂注册晚于玩家构造，`REGISTER_ANIMATION_EVENT`
  回调永不触发、controller 查不到、动画静默不播放。改为在 `FMLClientSetupEvent` 通过
  `PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory` 注册（对齐 PAL 自身初始化模式）
- **PAL 动画键名映射**：PAL 以 `Identifier(namespace=文件命名空间, path=JSON内动画键名)` 作动画 ID，
  原 JSON 键名 `animation.rpgcraftskills.heavy_strike` 与 `triggerAnimation(rpgcraftskills:heavy_strike)` 不匹配。
  改为键名 `heavy_strike`（与 animation_id 的 path 精确一致）
- **PAL 依赖版本冲突**：PAL Core POM 声明的 gson/slf4j/netty/joml 与 NeoForge `{strictly ...}` 锁定版本冲突，
  在 `skills/build.gradle` 排除这 4 个传递依赖（运行期由 NeoForge/Minecraft 提供）

### 变更

- PAL 依赖接线从临时 `compileOnly`（降级模式）恢复为官方推荐的 `implementation`
- `skills` 模块 mods.toml 的 PAL 依赖 `versionRange` 从 `[1.2,)` 收紧为 `[1.2.4,)`（规避带 bug 的旧版）
- 全模块版本号统一升级 `0.4.2-alpha` → `0.5.0-alpha`

### 已知限制（MVP）

- 技能槽固定（数字键 1 硬编码释放示范技能，无技能栏 UI）
- 无技能学习系统（所有已注册技能默认可释放，`canCast` 自动学习兜底）
- 无 buff/debuff / 状态机 / 精确 raycast
- 技能 JSON 的 `attack_type` 仅展示用，实际生效由手持武器决定
- PAL 动画仅在第三人称可见（第一人称原版不渲染玩家自身模型）

---

## [0.4.1-alpha] - 职业系统 JSON 化

- 职业系统 JSON 化 + 主副职业严格区分 + 双浮窗面板 + 修复附件持久化

## [0.4.0-alpha] - 职业系统（初版）

- 职业经验/等级/职业树/进阶/副职业 + 职业面板
