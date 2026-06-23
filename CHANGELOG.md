# 更新日志

本项目遵循 [语义化版本](https://semver.org/)，alpha 阶段次版本号递增代表新子系统落地。

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
