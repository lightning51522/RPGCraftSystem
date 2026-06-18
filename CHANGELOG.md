# 更新日志

本项目遵循 [语义化版本](https://semver.org/)，alpha 阶段次版本号递增代表新子系统落地。

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
