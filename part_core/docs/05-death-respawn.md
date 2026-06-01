# 死亡与重生 (Death & Respawn)

> 主要代码在 `RPGCraftCore` 主类中

玩家死亡时的属性保存使用由 `DeathAttributeMode` 枚举（`SNAPSHOT` 或 `RESCAN`）控制的双模式系统，通过 `/rpg deathmode <snapshot|rescan>` 切换。死亡数据存储在 `ConcurrentHashMap<UUID, DeathData>` 中，`DeathData` 是内部 record，包装 `AttributeSnapshot`、`Map<String, EquipmentBonus>`（死亡时的装备加成）、`int level` 和 `int experience`（死亡时的等级数据）。

## 快照捕获（两种模式相同）

1. **`checkAndSnapshotIfDying()`** — 在 `CombatEventHandler.onLivingDamagePost()` 中自定义 life ≤ 0 时调用。捕获 `AttributeSnapshot` + 装备加成 + 等级/经验数据。使用 `putIfAbsent` 确保第一个快照胜出。
2. **`LivingDeathEvent`** — 非生命归零死亡（虚空、/kill）的后备。相同捕获逻辑，使用 `putIfAbsent`。

## SNAPSHOT 模式（默认）

3. **`PlayerEvent.Clone`** — 调用 `IAttributeRegistry.applySnapshot(newPlayer, snapshot)` 恢复：
   - 所有属性：从快照恢复 `maxValue`
   - 资源属性（`shouldResetOnRespawn=true`）：`fillMax()` 将 currentValue 设为 maxValue
   - 能力属性：恢复快照的 `currentValue`
   - 然后调用 `EquipmentManager.getHandler().restoreBonusTracking()` 从当前装备重新计算装备加成跟踪。
   - 从 `DeathData` 恢复等级/经验。
   - **不在此处同步到客户端** — 客户端尚未创建新的玩家实体。

## RESCAN 模式

3. **`PlayerEvent.Clone`** — 调用 `EquipmentManager.getHandler().rescanAndApplyAttributes(newPlayer, snapshot, deathEquipmentBonuses)`：
   - 计算基础值：`base = snapshotValue - deathBonus`（每个属性）
   - 通过 `calculateTotalBonus(player)` 扫描当前装备（处理快捷栏中的护甲、未来自定义槽位）
   - 应用：`newValue = base + currentBonus`（尊重 `equipmentAffectsMax` 标志）
   - life 属性的最小 1 HP 保障
   - 资源属性：`fillMax()` 将 currentValue 设为 maxValue
   - 更新 `EquipmentData.EQUIPMENT_BONUS` 跟踪 attachment
   - 从 `DeathData` 恢复等级/经验。
   - 同步所有属性到客户端（注意：clone 同步在此是安全的，因为 rescan 直接写入新实体的 attachment）

## 公共死后流程（两种模式）

4. **`PlayerEvent.PlayerRespawnEvent`** — 同步所有属性 + 等级数据到客户端。此事件在客户端创建新玩家实体**之后**触发，因此包能正确接收。
5. **`PlayerEvent.PlayerLoggedInEvent`** — 登录时全量同步所有属性 + 等级数据到客户端。也调用 `restoreBonusTracking()` 初始化装备加成跟踪，以及 `syncVanillaHealth()` 同步 vanilla 血条。
6. **`PlayerEvent.PlayerLoggedOutEvent`** — 清理断线玩家的残留死亡快照（防止玩家死后未重生就断线导致的内存泄漏）。

## 已知问题

NeoForge 26.1.2.68-beta 的 `AttachmentType.builder` 上的 `copyOnDeath()` 无法可靠地在死亡时保留 attachment 数据（旧实体的 attachment map 在 Clone 触发前被清空）。`RPGCraftCore` 中的手动快照方案是有效的解决方案。

## 关键约定（死亡/重生相关）

- 死亡/重生有两种模式，由 `DeathAttributeMode` 枚举控制：`SNAPSHOT`（默认，原样恢复死亡快照）和 `RESCAN`（去除死亡装备加成，从当前装备重新计算）。
- 死亡数据存储为 `DeathData(AttributeSnapshot, Map<String, EquipmentBonus>, int level, int experience)` — 捕获死亡时的属性值、活跃装备加成和等级/经验。装备加成被 RESCAN 模式用于计算基础值。等级/经验在两种模式下都恢复。
- RESCAN 模式中，基础值 = 快照值 − 死亡装备加成。这是正确的，因为快照值包含死亡时的装备加成。
- 死亡/重生属性保存使用 `AttributeSnapshot` API（死亡时 `createSnapshot` → clone 时 `applySnapshot` → 重生时 sync），而非 `copyOnDeath()`。
- `PlayerEvent.Clone` 在客户端创建新实体之前触发：Clone 期间发送的同步包被旧的（已死亡的）客户端实体接收并丢失。重生后的客户端同步请使用 `PlayerRespawnEvent`。
