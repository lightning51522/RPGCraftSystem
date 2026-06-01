# 等级系统 (Level System)

> 对应源码包：`com.rpgcraft.core.level`

独立的等级/经验系统，与 vanilla XP 分离。同时影响玩家和怪物。遵循与属性和装备模块相同的 API/实现/门面模式。

## API 层 (`level/api/`)

- **`ILevelCalculator`** — 经验计算策略：`calculateExperienceGain(ServerPlayer killer, LivingEntity victim, int mobLevel, int baseExp)`。可通过 `LevelManager.setLevelCalculator()` 替换。默认实现：`DefaultLevelCalculator`。
- **`ILevelRegistry`** — 等级 XP 阈值注册和查找：`registerExpRequirement(int level, int expRequired)`, `getExpForLevel(int level)`, `getMaxLevel()`, `loadFromJson(JsonObject)`。默认实现：`LevelConfig`。
- **`ILevelProvider`** — SPI，供子模组注册自定义等级 XP 阈值：`registerLevelData(ILevelRegistry)`。通过 `ServiceLoader` 在 `LevelManager.init()` 中发现。

## 默认实现

- **`DefaultLevelCalculator`** — 默认经验公式：`(int)(sqrt(mobLevel / playerLevel) * baseExp)`。奖励击杀高等级怪物，减少低等级怪物的 XP。
- **`LevelConfig`** — `ILevelRegistry` 默认实现。从 `data/rpgcraftcore/rpg/level_config.json` 加载。存储 `volatile int[] expTable`。通过 `AddServerReloadListenersEvent` 支持 `/reload`。也支持编程式 `registerExpRequirement()` 用于 SPI。
- **`PlayerLevelData`** — Attachment 类，存储 `level`（int，默认 1，最小 1）和 `experience`（int，默认 0）。含 `MapCodec` 用于存档序列化。`addExperience(int)` 通过 `ILevelRegistry` 从 `LevelManager.getRegistry()` 处理自动升级。
- **`LevelManager`** — 门面，持有 `ILevelRegistry` 和 `ILevelCalculator`。`init()` 创建默认值，注册 `PlayerLevelData` attachment，发现 `ILevelProvider` SPI。`setLevelCalculator()` 用于运行时替换。`getRegistry()`, `getLevelCalculator()`, `syncToClient()`。
- **`LevelEventHandler`** — 委托 XP 计算给 `LevelManager.getLevelCalculator()` 的 `ILevelCalculator`。从 `MobAttributeConfig` 查找怪物 level/baseExp。

## 等级配置 JSON 格式

`data/rpgcraftcore/rpg/level_config.json`：
```json
{
  "1": 100,
  "2": 250,
  "3": 500,
  "4": 1000,
  "5": 2000
}
```
- 键 = 当前等级，值 = 升到下一级所需的增量 XP。最大等级 = 最高键 + 1（本例中为 6）。
- 配置中缺失的等级被视为需要 0 XP（会记录警告）。
- 支持 `/reload` 热重载。

## 怪物等级与基础 XP

`mob_attributes.json` 条目包含 `"level"`（int，默认 1）和 `"base_exp"`（int，默认 100）。`LevelEventHandler` 在击杀时使用这些值计算 XP。怪物等级是静态的（来自配置），不存储在实体上 — 在击杀时按实体类型查找。

## 死亡保存

等级和经验通过现有的 `DeathData` 快照系统在死亡时保存。`DeathData` record 包含 `level` 和 `experience` 字段，与 `AttributeSnapshot` 和装备加成并列。SNAPSHOT 和 RESCAN 模式都在 clone 时恢复等级数据。在登录和重生时同步到客户端。

## 关键约定（等级相关）

- 等级系统独立于 vanilla XP。玩家等级和经验使用自定义 `PlayerLevelData` attachment 和 `MapCodec` 序列化。最低等级为 1。
- 经验计算可通过 `LevelManager.setLevelCalculator(ILevelCalculator)` 运行时替换。默认：`DefaultLevelCalculator`，公式 `sqrt(mobLevel/playerLevel)*baseExp`。
- 等级 XP 表通过 `LevelManager.getRegistry()` 访问（返回 `ILevelRegistry` 接口）。默认实现：`LevelConfig`。
- 自定义等级 XP 阈值来自子模块，使用 `ILevelProvider` SPI（Java `ServiceLoader`），声明在 `META-INF/services/com.rpgcraft.core.level.api.ILevelProvider`。
- 等级配置（`level_config.json`）使用增量 XP 格式：键 = 当前等级，值 = 升到下一级所需 XP。最大等级 = 最高键 + 1。等级必须从 1 开始连续。
- 怪物等级和基础 XP 来自 `mob_attributes.json`（`"level"` 和 `"base_exp"` 字段，默认 1 和 100）。怪物等级是静态的，不存储在实体上 — 击杀时按实体类型查找。
- 击杀 XP 公式：`(int)(sqrt(mobLevel / playerLevel) * baseExp)`。`playerLevel` 保障 ≥ 1 以防止除零。
- `PlayerLevelData.addExperience()` 内部通过循环处理自动升级：`while (level < maxLevel && experience >= expForLevel(level)) { experience -= exp; level++; }`。返回是否发生了升级。
- 等级数据通过 `DeathData` 快照在死亡时保存，在 SNAPSHOT 和 RESCAN 模式的 clone 时恢复。
- `SyncPlayerLevelPacket` 发送 `expForNextLevel`（最大等级时为 -1），以便客户端 HUD 无需配置查找即可显示进度。
- 怪物属性 JSON 现在包含 `"level"` 和 `"base_exp"` 以及 `"attack_type"`。三者均可选，有合理默认值（level=1, base_exp=100, attack_type=PHYSICAL）。
