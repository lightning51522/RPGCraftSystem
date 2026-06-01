# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

This is a Gradle multi-project workspace. Run all commands from the **parent directory** (`project/`):

```bash
# Build
./gradlew :part_core:build

# Run client
./gradlew :part_core:runClient

# Run dedicated server
./gradlew :part_core:runServer

# Run data generators (output goes to src/generated/resources/)
./gradlew :part_core:runData

# Run game test server
./gradlew :part_core:runGameTestServer
```

Version properties (`minecraft_version`, `neo_version`) are defined in the root `project/gradle.properties`, not in this subproject.

## Target Environment

- **Minecraft**: 26.1.2
- **Mod Loader**: NeoForge 26.1.2.68-beta (net.neoforged.moddev Gradle plugin)
- **Java**: 25 (Microsoft OpenJDK)
- **Mod ID**: `rpgcraftcore`

## Architecture Overview

This is the core module of the RPGCraftSystem multi-project workspace. It provides the foundational RPG attribute system and damage calculation that other modules will depend on.

```
属性系统 (attribute)  ← 基础层，无外部依赖
  ↑ 装备系统 (equipment)    — 装备加成修改属性值
  ↑ 战斗系统 (combat)       — 伤害公式读取属性值；查询武器攻击类型（依赖装备）
  ↑ 等级系统 (level)        — 经验事件读取怪物配置中的 level/baseExp（依赖属性）
  ↑ 事件总线 (event)        — RPGEventBus 子模块事件扩展通道（依赖属性+战斗）
```

## Initialization Flow

`RPGCraftCore` constructor:
1. `EquipmentManager.init()` — creates `DefaultEquipmentRegistry`, `DefaultEquipmentHandler`
2. `AttributeManager.init()` — creates `DefaultAttributeRegistry`, `DefaultDamageCalculator`, registers all 11 attributes
3. `LevelManager.init()` — creates level DeferredRegister, registers `PlayerLevelData` attachment
4. `AttributeManager.getDeferredRegister().register(modEventBus)` — submits AttachmentTypes to NeoForge
5. `LevelManager.getDeferredRegister().register(modEventBus)` — submits level AttachmentType to NeoForge
6. `EquipmentData.getAttachmentRegister().register(modEventBus)` — submits equipment bonus tracking attachment
7. Other registrations (blocks, items, packets, config)

## Key Conventions (Critical)

- **Init order matters**: `EquipmentManager.init()` → `AttributeManager.init()` → `LevelManager.init()`，所有必须在 `DeferredRegister.register(modEventBus)` 之前完成。
- **Serialization**: `StreamCodec` 用于网络，`MapCodec`/`Codec` 用于存档。不可混用。
- **Client-side code**: 在 `client/` 下，使用 `@EventBusSubscriber(value = Dist.CLIENT)`。
- **Resources**: 数据生成资源放 `src/generated/resources/`，手写资源放 `src/main/resources/`。
- **Generics**: `AttachmentType<EntityAttribute>` 与 `AttachmentType<IAttribute>` 的类型转换不可避免，使用 `@SuppressWarnings("unchecked")`。
- **Code language**: 代码注释和语言键使用中文 (zh_CN)。
- **Division by zero guards**: 所有比例计算在除法前检查 `maxValue > 0`。
- **Flat damage**: 伤害是扁平的（非比例），仅在同步 vanilla health 时使用比例转换。
- **Death preservation**: 使用 `AttributeSnapshot` API（非 `copyOnDeath()`），因为 NeoForge 26.1.2.68-beta 的 `copyOnDeath()` 不可靠。
- **Sync timing**: `PlayerEvent.Clone` 期间不同步到客户端（客户端尚未创建新实体），使用 `PlayerRespawnEvent`。

## Documentation Structure Policy

本项目的文档采用 **主索引 + 功能子文件** 架构：

- **`CLAUDE.md`（本文件）** 仅保留构建命令、目标环境、架构概览、初始化流程、全局关键约定、文档索引。**不在此文件中添加功能模块的详细描述。**
- **`docs/` 子目录** 按功能域拆分，每个主要功能模块对应一个独立的 markdown 文件。

**新增主要功能模块时必须：**
1. 在 `docs/` 下创建对应的子文档（命名格式：`NN-模块名.md`，NN 为两位数编号，接续现有最大编号）。
2. 将该模块的 API、默认实现、数据格式、关键约定等详细描述写入子文档。
3. 在本文件的 Documentation Index 表中添加一行索引链接。
4. 如该模块引入新的全局性约定（影响多个模块的规则），将简明条目添加到本文件的 Key Conventions 中，详细内容留在子文档。

**不要**将新模块的完整描述直接追加到本文件 — 这会导致主文件膨胀，违背文档拆分原则。
在完成某项任务时**不要**加载无关的子模块的详细说明文档。

## Documentation Index

详细文档按功能域拆分至 `docs/` 子目录：

| 文件 | 内容 |
|------|------|
| [docs/01-attribute.md](docs/01-attribute.md) | 属性系统 — API、默认实现、属性分类 |
| [docs/02-equipment.md](docs/02-equipment.md) | 装备系统 — API、数据类型、默认实现、JSON格式、Tooltip |
| [docs/03-combat.md](docs/03-combat.md) | 战斗系统 — CombatEventHandler、伤害计算路径 |
| [docs/04-level.md](docs/04-level.md) | 等级系统 — API、默认实现、配置JSON、XP公式 |
| [docs/05-death-respawn.md](docs/05-death-respawn.md) | 死亡与重生 — 快照捕获、SNAPSHOT/RESCAN模式、公共流程 |
| [docs/06-network-client.md](docs/06-network-client.md) | 网络同步与客户端HUD — Packet、血条、属性面板 |
| [docs/07-commands.md](docs/07-commands.md) | 命令系统 — /rpg 命令族 |
| [docs/08-submodule.md](docs/08-submodule.md) | 子模块集成 — SPI、依赖架构、开发者指南 |
| [docs/09-neoforge-notes.md](docs/09-neoforge-notes.md) | NeoForge 26.1 API 注意事项 |
| [docs/10-data-flow.md](docs/10-data-flow.md) | 数据流与跨模块API |
