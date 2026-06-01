# NeoForge 26.1 API 注意事项

> 版本特定变更，与旧版 NeoForge/Forge 教程不同

## 重命名/变更

| 旧版 | NeoForge 26.1 | 说明 |
|------|--------------|------|
| `GuiGraphics` | `GuiGraphicsExtractor` | 类重命名 |
| `drawString` | `text` | `GuiGraphicsExtractor` 上的方法重命名 |
| `Screen#render` | `Screen#extractRenderState` | 渲染管线根本性变更 |
| `ResourceLocation` | `Identifier` | 类重命名 |

## 行为变更

- **ARGB 颜色需要显式 alpha**：`0xFFFFFF` 渲染为透明（alpha=0）。文本始终使用 `0xFFFFFFFF`。
- **`RenderGuiEvent.Post` 已移除**：使用 `RegisterGuiLayersEvent` + `GuiLayer` 替代。
- **`@EventBusSubscriber` 无 `bus` 参数**：总线路由是自动的 — 处理 `IModBusEvent` 类型的方法发送到模组总线，其他所有方法发送到游戏总线。
- **`GuiLayer` 函数式接口**：`void render(GuiGraphicsExtractor, DeltaTracker)`。
- **混淆已移除**：26.1 附带官方（未混淆）名称。

## 关键事件行为

- **`PlayerEvent.Clone` 在客户端创建新实体之前触发**：Clone 期间发送的同步包被旧的（已死亡的）客户端实体接收并丢失。重生后的客户端同步使用 `PlayerRespawnEvent`。
- **`EntityJoinLevelEvent` 在两端触发**：仅需服务端逻辑时始终使用 `event.getLevel().isClientside()` 守卫。`LivingDamageEvent` 仅在服务端线程触发，无需此守卫。
