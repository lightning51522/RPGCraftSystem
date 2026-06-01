# 网络同步与客户端渲染 (Network & Client)

> 对应源码包：`com.rpgcraft.core.network`、`com.rpgcraft.core.client`

## 网络同步 (`network/`)

使用 NeoForge 的 payload 系统进行客户端-服务端属性同步：

- **`PacketHandler`** — 在模组事件总线上注册 payload，协议版本 `"1"`。
- **`SyncPlayerAttributePacket`** — 实现 `CustomPacketPayload` 的 `record`。使用 `StreamCodec`（网络）— 区别于 `MapCodec`（存档）。服务端调用 `sendToClient()`，客户端通过 `handle()` 处理，后者通过 `IAttributeRegistry.getTypeById()` 查找 AttachmentType，在客户端 attachment 上同时设置 `setMaxValue()` 和 `setValue()`，在主线程排队执行。
- **`SyncPlayerLevelPacket`** — `record(int level, int experience, int expForNextLevel)` 用于等级/XP 同步。`sendToClient()` 从 `PlayerLevelData.getExpForNextLevel()` 计算 `expForNextLevel`（最大等级时为 -1）。客户端 `handle()` 在客户端 attachment 上设置 level 和 experience。

## 客户端 HUD (`client/`)

### AttributeHudOverlay

`@EventBusSubscriber(Dist.CLIENT)`，通过 NeoForge 26.1 的 `GuiLayer` 系统渲染：

#### 自定义血条

- 通过 `RegisterGuiLayersEvent.replaceLayer()` 替换 `VanillaGuiLayers.PLAYER_HEALTH`。
- 渲染圆角矩形进度条：渐变红色填充（顶部 `0xFFE03030`，底部 `0xFF8B0000`）、深灰色丢失生命区域（`0xFF373737`）、深色边框（`0xFF222222`）和居中 "current/max" 文本。
- 位置匹配 vanilla 心形（`screenWidth/2 - 91`, `screenHeight - 39`）。
- 血条尺寸：90×9px，边框 1px，圆角半径 2px。
- 使用手动 `fillRounded()`/`fillRoundedGradient()` 辅助方法（NeoForge 26.1 无原生圆角矩形 API）。

#### 属性文本面板

- 通过 `registerAboveAll` 注册。
- 显示等级信息（黄色文本："等级: X  经验: XXX / YYYY"，最大等级时为 "等级: X (MAX)"）。
- 然后遍历 `IAttributeRegistry.getAllEntries()` 在左上角显示所有属性（包括 life）为文本。
- 有上限属性显示 "name: value / maxValue"，无上限属性显示 "name: value"。

### RPGCraftCoreClient

客户端入口点（`@Mod(dist = Dist.CLIENT)`）。

### EquipmentTooltipHandler

客户端 tooltip 渲染。详见 [装备系统文档](02-equipment.md#tooltip-显示-clientequipmenttooltiphandler)。

## 关键约定（网络/客户端相关）

- `StreamCodec` 用于网络序列化；`MapCodec`/`Codec` 用于存档序列化。不要混用。
- 客户端代码在 `client/` 下，使用 `@EventBusSubscriber(value = Dist.CLIENT)`。
- `SyncPlayerAttributePacket.handle()` 同时设置 `setMaxValue` 和 `setValue`。两者都必须应用 — 省略 `setMaxValue` 导致客户端最大值显示不同步。
- `SyncPlayerLevelPacket` 发送 `expForNextLevel`（最大等级时为 -1），以便客户端 HUD 无需配置查找即可显示进度。
- `AttributeHudOverlay` 使用手动 `fillRounded()`/`fillRoundedGradient()` 方法 — NeoForge 26.1 无原生圆角矩形 API。
- 自定义血条通过 `RegisterGuiLayersEvent.replaceLayer()` 替换 `VanillaGuiLayers.PLAYER_HEALTH`。Life 属性包含在文本 HUD 覆盖中（不过滤）。
- ARGB 颜色需要显式 alpha：`0xFFFFFF` 渲染为透明（alpha=0）。文本始终使用 `0xFFFFFFFF`。
- 所有比例计算在除法前检查 `maxValue > 0` 以防止除零。
