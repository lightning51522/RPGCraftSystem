package com.rpgcraft.core.client;

import com.rpgcraft.core.attribute.GenericEntityData;
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
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "rpgcraftcore")
public class AttributeHudOverlay {

    /**
     * HUD 图层注册回调
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
     */
    private static void renderOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        int x = 10;
        int y = 10;
        int lineHeight = 12;
        StringBuilder sb = new StringBuilder(32);

        for (IAttributeEntry entry : GenericEntityData.getRegistry().getAllEntries()) {
            IAttribute attr = player.getData(entry.getSupplier());

            sb.setLength(0);
            sb.append(entry.getDisplayName()).append(": ").append(attr.getValue());
            if (attr.hasMaxValue()) {
                sb.append(" / ").append(attr.getMaxValue());
            }

            guiGraphics.text(mc.font, sb.toString(), x, y, 0xFFFFFFFF, true);
            y += lineHeight;
        }
    }
}
