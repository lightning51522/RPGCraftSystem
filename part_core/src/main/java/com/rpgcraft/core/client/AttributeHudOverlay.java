package com.rpgcraft.core.client;

import com.rpgcraft.core.attribute.GenericPlayerData;
import com.rpgcraft.core.attribute.PlayerAttribute;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 客户端 HUD 渲染类
 *
 * 在 NeoForge 26.1 中：
 * - HUD overlay 通过 RegisterGuiLayersEvent 注册 GuiLayer 实现
 * - GuiGraphics 重命名为 GuiGraphicsExtractor
 * - drawString 重命名为 text
 * - RenderGuiEvent.Post 已被移除，改用图层系统
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "rpgcraftcore")
public class AttributeHudOverlay {

    // 使用 LinkedHashMap 保证属性的显示顺序与插入顺序一致
    private static final Map<String, Supplier<AttachmentType<PlayerAttribute>>> ATTRIBUTES_TO_DISPLAY = new LinkedHashMap<>();

    static {
        ATTRIBUTES_TO_DISPLAY.put("生命", GenericPlayerData.LIFE);
        ATTRIBUTES_TO_DISPLAY.put("技力", GenericPlayerData.SKILL_POINT);
        ATTRIBUTES_TO_DISPLAY.put("法力", GenericPlayerData.MAGIC_POINT);
        ATTRIBUTES_TO_DISPLAY.put("力量", GenericPlayerData.STRENGTH);
        ATTRIBUTES_TO_DISPLAY.put("魔力", GenericPlayerData.MANA);
        ATTRIBUTES_TO_DISPLAY.put("敏捷", GenericPlayerData.AGILE);
        ATTRIBUTES_TO_DISPLAY.put("精准", GenericPlayerData.PRECISION);
        ATTRIBUTES_TO_DISPLAY.put("防御", GenericPlayerData.DEFENSE);
        ATTRIBUTES_TO_DISPLAY.put("法抗", GenericPlayerData.RESISTANCE);
        ATTRIBUTES_TO_DISPLAY.put("暴击率", GenericPlayerData.CRITICAL_RATE);
        ATTRIBUTES_TO_DISPLAY.put("暴击伤害", GenericPlayerData.CRITICAL_RATIO);
    }

    /**
     * 在 MOD 总线上注册自定义 HUD 图层
     * registerAboveAll 确保属性面板渲染在所有其他 HUD 元素之上
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath("rpgcraftcore", "attribute_hud"),
                AttributeHudOverlay::renderOverlay
        );
    }

    /**
     * HUD 图层渲染回调
     * GuiLayer 的 render 方法接收 GuiGraphicsExtractor 和 DeltaTracker 两个参数
     */
    private static void renderOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        // 玩家不存在时跳过渲染（主菜单等场景）
        if (player == null) return;

        int x = 10;
        int y = 10;
        int lineHeight = 12;

        for (Map.Entry<String, Supplier<AttachmentType<PlayerAttribute>>> entry : ATTRIBUTES_TO_DISPLAY.entrySet()) {
            String attrName = entry.getKey();
            AttachmentType<PlayerAttribute> type = entry.getValue().get();

            // 从客户端玩家实例读取属性数据（由 SyncPlayerAttributePacket 同步）
            PlayerAttribute attr = player.getData(type);

            String displayText;
            if (attr.getMaxValue() == Integer.MAX_VALUE) {
                displayText = String.format("%s: %d", attrName, attr.getValue());
            } else {
                displayText = String.format("%s: %d / %d", attrName, attr.getValue(), attr.getMaxValue());
            }

            // 26.1 中 drawString 重命名为 text
            guiGraphics.text(mc.font, displayText, x, y, 0xFFFFFF, true);

            y += lineHeight;
        }
    }
}
