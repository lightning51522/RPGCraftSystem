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

`mob_attributes.json` 条目包含 `"level"`（int，默认 1）和 `"base_exp"`（int，默认 100）。`LevelEventHandler` 在击杀时使用这些值计算 XP。

### 怪物数据持久化

`MobLevelData` 附件序列化到实体 NBT（通过 `MapCodec`），包含等级、评级、攻击类型覆盖、经验覆盖和 `initialized` 标志。

- **指令召唤的怪物**：自定义等级、评级、属性覆盖跨 chunk 重载持久化
- **自然刷新的怪物**：初始化后的属性值（包括随机等级/评级）同样持久化
- **initialized 标志**：`true` 时跳过 `EntityJoinLevelEvent` 中的重新初始化，保留受伤后的生命值

### 随机刷新系统

自然刷新的怪物可从权重表中随机选择等级和评级。通过 `/rpg randspawn [on|off]` 全局控制，默认关闭。

**权重表配置**（`mob_attributes.json` 中可选的 `spawn` 段）：

```json
{
  "minecraft:zombie": {
    "level": 1,
    "base_exp": 100,
    "life": 20,
    "strength": 5,
    "defense": 2,
    "resistance": 0,
    "critical_rate": 5,
    "critical_ratio": 150,
    "spawn": {
      "level_weights": {
        "1": 60, "2": 20, "3": 10, "5": 7, "8": 2, "10": 1
      },
      "rating_weights": {
        "NORMAL": 85, "STRONG": 10, "ELITE": 4, "NOTORIOUS_ELITE": 0.8, "BOSS": 0.2
      }
    }
  }
}
```

- `level_weights`：等级 → 权重（按比例随机选择）。未配置则使用静态 `level` 字段。
- `rating_weights`：评级枚举名 → 权重。未配置则默认 NORMAL。
- `spawn` 段整体可选。无 `spawn` → 行为与未开启随机刷新一致。
- JSON 中以下划线开头的键（如 `_global_spawn`）会被跳过，可用于注释。

## 死亡保存

等级和经验通过现有的 `DeathData` 快照系统在死亡时保存。`DeathData` record 包含 `level` 和 `experience` 字段，与 `AttributeSnapshot` 和装备加成并列。SNAPSHOT 和 RESCAN 模式都在 clone 时恢复等级数据。在登录和重生时同步到客户端。

## 关键约定（等级相关）

- 等级系统独立于 vanilla XP。玩家等级和经验使用自定义 `PlayerLevelData` attachment 和 `MapCodec` 序列化。最低等级为 1。
- 经验计算可通过 `LevelManager.setLevelCalculator(ILevelCalculator)` 运行时替换。默认：`DefaultLevelCalculator`，公式 `sqrt(mobLevel/playerLevel)*baseExp`。
- 等级 XP 表通过 `LevelManager.getRegistry()` 访问（返回 `ILevelRegistry` 接口）。默认实现：`LevelConfig`。
- 自定义等级 XP 阈值来自子模块，使用 `ILevelProvider` SPI（Java `ServiceLoader`），声明在 `META-INF/services/com.rpgcraft.core.level.api.ILevelProvider`。
- 等级配置（`level_config.json`）使用增量 XP 格式：键 = 当前等级，值 = 升到下一级所需 XP。最大等级 = 最高键 + 1。等级必须从 1 开始连续。
- 怪物等级和基础 XP 来自 `mob_attributes.json`（`"level"` 和 `"base_exp"` 字段，默认 1 和 100）。怪物等级通过 `MobLevelData` 附件序列化到实体 NBT，支持持久化和随机刷新。`MobLevelData.initialized` 标志防止 chunk 重载时覆盖已有属性值。
- 击杀 XP 公式：`(int)(sqrt(mobLevel / playerLevel) * baseExp)`。`playerLevel` 保障 ≥ 1 以防止除零。
- `PlayerLevelData.addExperience()` 内部通过循环处理自动升级：`while (level < maxLevel && experience >= expForLevel(level)) { experience -= exp; level++; }`。返回是否发生了升级。
- 等级数据通过 `DeathData` 快照在死亡时保存，在 SNAPSHOT 和 RESCAN 模式的 clone 时恢复。
- `SyncPlayerLevelPacket` 发送 `expForNextLevel`（最大等级时为 -1），以便客户端 HUD 无需配置查找即可显示进度。
- 怪物属性 JSON 现在包含 `"level"` 和 `"base_exp"` 以及 `"attack_type"`。三者均可选，有合理默认值（level=1, base_exp=100, attack_type=PHYSICAL）。
