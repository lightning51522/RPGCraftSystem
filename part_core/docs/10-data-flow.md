# 数据流与跨模块 API (Data Flow & Cross-Module API)

> 本文档描述系统运行时的完整数据流向

## 跨模块 API 存根 (`api/`)

计划中的 RPG 系统占位接口：`INpc`、`IProfession`。每个域在开发时将获得自己的 `api/` 子包（遵循 `attribute/api/`、`equipment/api/`、`level/api/` 模式）。装备和等级模块已有各自的 API。

## 数据流总览

```
初始化: EquipmentManager.init() → AttributeManager.init() → LevelManager.init() → DefaultAttributeRegistry 注册 11 个属性 → DeferredRegisters 注册到 modEventBus

登录: PlayerLoggedInEvent → IAttributeRegistry.getAllEntries() → 每条目 sendToClient() → restoreBonusTracking() → syncVanillaHealth() → LevelManager.syncToClient()

属性运行时: IAttribute 变更 → sendToClient() → 网络 → 客户端 handle() → setMaxValue() + setValue()

等级运行时: LevelManager.syncToClient() → SyncPlayerLevelPacket(level, experience, expForNextLevel) → 客户端 handle() → PlayerLevelData.setLevel() + setExperience()

装备: LivingEquipmentChangeEvent → EquipmentManager.getHandler().onEquipmentChange() → calculateTotalBonus (快捷栏护甲检查) → applyBonusDiff (equipmentAffectsMax 标志) → sendToClient() (不调用 syncVanillaHealth 以避免受伤动画)

战斗: LivingDamageEvent.Pre → 扁平伤害: 穿透→life=0, 战斗→玩家: 武器 attackType 来自 IEquipmentRegistry / 怪物: attackType 来自 MobAttributeConfig→RPG 公式 with attackType, 环境→vanilla 值 → 应用到自定义 life → 设置比例 vanilla 伤害

战斗后: LivingDamageEvent.Post → 重新同步 vanilla health 匹配自定义 life 比例 → checkAndSnapshotIfDying → sendToClient()

治疗: LivingHealEvent → RPGHealEvent.Pre (可取消/修改) → 应用到自定义 life → RPGHealEvent.Post → event.setAmount(0) + syncVanillaHealth() → sendToClient()

自定义治疗: CombatEventHandler.healEntity() → RPGHealEvent.Pre (可取消/修改) → 应用到自定义 life → RPGHealEvent.Post → syncVanillaHealth() → sendToClient() [仅ServerPlayer]

击杀XP: LivingDeathEvent → 玩家击杀怪物 → 从 MobAttributeConfig 查找 mob level+baseExp → sqrt(mobLevel/playerLevel)*baseExp → PlayerLevelData.addExperience() → LevelManager.syncToClient()

死亡: LivingDeathEvent → createSnapshot + 捕获装备加成 + 捕获等级/经验 → DeathData(UUID → {snapshot, equipmentBonuses, level, experience})

Clone-SNAPSHOT: PlayerEvent.Clone → applySnapshot → 恢复 maxValue, 资源属性 fillMax, 能力属性恢复 currentValue → restoreBonusTracking() → 恢复等级/经验

Clone-RESCAN: PlayerEvent.Clone → rescanAndApplyAttributes → 计算基础 (快照 - 死亡加成) → 扫描当前装备 → 应用当前加成 → 资源属性 fillMax → 更新跟踪 → 恢复等级/经验

重生: PlayerRespawnEvent → 每条目 sendToClient() (客户端已有新实体) → syncVanillaHealth() → LevelManager.syncToClient()

登出: PlayerLoggedOutEvent → deathSnapshot.remove(uuid) (清理)

渲染-血条: GuiLayer (替换 VanillaGuiLayers.PLAYER_HEALTH) → 圆角渐变进度条 with current/max 文本

渲染-等级: GuiLayer (above all, attributes 之前) → 客户端 attachment 的 PlayerLevelData → "等级: X  经验: XXX / YYYY" (黄色) 或 "等级: X (MAX)"

渲染-属性: GuiLayer (above all, level 之后) → IAttributeRegistry.getAllEntries() → 客户端 attachment 的 IAttribute → text()

Tooltip: ItemTooltipEvent → EquipmentManager.getRegistry().getBonuses() + getRarity() → 着色物品名 + [稀有度] 标签 + 加成行
```
