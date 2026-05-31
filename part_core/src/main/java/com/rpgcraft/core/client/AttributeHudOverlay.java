package com.rpgcraft.core.client;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * 客户端 HUD 属性面板渲染器
 * <p>
 * 在游戏主界面的左上角以文字形式显示玩家的所有 RPG 属性值。
 * 使用 NeoForge 26.1 的 {@link GuiLayer} 图层系统注册为自定义 HUD 图层，
 * 每帧被渲染管线调用，从客户端玩家的 Attachment 中读取最新属性值并绘制。
 * <p>
 * <h3>GuiLayer 生命周期</h3>
 * <ol>
 *   <li>{@link #onRegisterGuiLayers} 在 Mod 事件总线触发时注册图层（仅执行一次）</li>
 *   <li>{@link #renderOverlay} 每帧由渲染管线调用，读取数据并绘制</li>
 * </ol>
 * <p>
 * <h3>StringBuilder 复用</h3>
 * 使用 {@code sb.setLength(0)} 重置而非每次创建新实例，减少渲染热路径上的 GC 压力。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "rpgcraftcore")
public class AttributeHudOverlay {

    /** 复用的 StringBuilder，避免每帧分配新实例造成 GC 压力 */
    private static final StringBuilder HUD_BUILDER = new StringBuilder(32);

    /**
     * HUD 图层注册回调（Mod 事件总线）
     * <p>
     * 将属性面板注册到所有图层的最上层（{@code registerAboveAll}），
     * 确保属性文本不被其他 HUD 元素遮挡。
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath("rpgcraftcore", "attribute_hud"),
                AttributeHudOverlay::renderOverlay
        );
    }

    /**
     * HUD 图层渲染回调 —— 每帧执行
     * <p>
     * 从注册中心遍历所有属性，读取客户端玩家的 Attachment 值并绘制。
     * 格式示例：
     * <ul>
     *   <li>有上限的属性：{@code "生命: 80 / 100"}</li>
     *   <li>无上限的属性：{@code "力量: 15"}</li>
     * </ul>
     *
     * @param guiGraphics 图形绘制上下文
     * @param deltaTracker 帧间时间增量（本实现未使用，但 GuiLayer 接口要求）
     */
    private static void renderOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        int x = 10;
        int y = 10;
        int lineHeight = 12;

        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            IAttribute attr = player.getData(entry.getSupplier());

            // 复用 StringBuilder：setLength(0) 清空内容但保留已分配的缓冲区
            HUD_BUILDER.setLength(0);
            HUD_BUILDER.append(entry.getDisplayName()).append(": ").append(attr.getValue());
            if (attr.hasMaxValue()) {
                HUD_BUILDER.append(" / ").append(attr.getMaxValue());
            }

            // 0xFFFFFFFF = ARGB 不透明白色，true = 启用阴影以增强可读性
            guiGraphics.text(mc.font, HUD_BUILDER.toString(), x, y, 0xFFFFFFFF, true);
            y += lineHeight;
        }
    }
}
