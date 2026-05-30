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
 * 客户端 HUD 属性面板渲染器
 * <p>
 * 在游戏主界面的左上角以文字形式显示玩家的所有 RPG 属性值。
 * 使用 NeoForge 26.1 的 {@link GuiLayer} 图层系统注册为自定义 HUD 图层，
 * 每帧被渲染管线调用，从客户端玩家的 Attachment 中读取最新属性值并绘制。
 * <p>
 * <b>渲染机制（NeoForge 26.1）：</b>
 * <ul>
 *   <li>通过 {@link RegisterGuiLayersEvent} 在 Mod 事件总线上注册自定义 HUD 图层</li>
 *   <li>{@link GuiLayer} 是函数式接口，签名为 {@code render(GuiGraphicsExtractor, DeltaTracker)}</li>
 *   <li>{@link GuiGraphicsExtractor} 是 26.1 中 {@code GuiGraphics} 的新名称</li>
 *   <li>文字绘制方法从旧版 {@code drawString} 更名为 {@code text}</li>
 *   <li>颜色参数使用 ARGB 格式，必须指定 alpha 通道（如 {@code 0xFFFFFFFF} 表示不透明白色）</li>
 * </ul>
 * <p>
 * <b>数据来源：</b>
 * 属性数据由 {@link com.rpgcraft.core.network.SyncPlayerAttributePacket} 从服务端同步到客户端，
 * 写入客户端玩家的 Attachment 中。本类只负责读取和渲染，不修改数据。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "rpgcraftcore")
public class AttributeHudOverlay {

    /**
     * 属性显示配置表
     * <p>
     * 定义了在 HUD 上显示的属性名称（中文）和对应的 AttachmentType 供应器。
     * 使用 {@link LinkedHashMap} 保证显示顺序与插入顺序一致（生命在最上方，暴击伤害在最下方）。
     * <p>
     * 如果未来需要新增或隐藏某个属性的显示，只需修改此 Map 即可。
     */
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
     * HUD 图层注册回调
     * <p>
     * 在 Mod 事件总线上监听 {@link RegisterGuiLayersEvent}（FML 自动检测为 IModBusEvent 并路由到 Mod 总线）。
     * 将 {@link #renderOverlay} 方法注册为名为 {@code rpgcraftcore:attribute_hud} 的 HUD 图层，
     * 使用 {@code registerAboveAll} 确保属性面板渲染在所有其他 HUD 元素（血条、快捷栏等）之上。
     *
     * @param event NeoForge 提供的 GUI 图层注册事件
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
     * 由 NeoForge 渲染管线每帧调用，从客户端玩家的 Attachment 中读取属性值并绘制文字。
     * <p>
     * <b>显示格式：</b>
     * <ul>
     *   <li>有上限属性（maxValue ≠ MAX_VALUE）：{@code "属性名: 当前值 / 最大值"}</li>
     *   <li>无上限属性（maxValue == MAX_VALUE）：{@code "属性名: 当前值"}</li>
     * </ul>
     *
     * @param guiGraphics  GUI 绘图上下文（26.1 中 GuiGraphics 的新名称）
     * @param deltaTracker 帧间时间增量，可用于动画插值
     */
    private static void renderOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        // 玩家不存在时跳过渲染（如主菜单、加载界面等场景）
        if (player == null) return;

        // 绘制起始坐标和行间距
        int x = 10;
        int y = 10;
        int lineHeight = 12;

        for (Map.Entry<String, Supplier<AttachmentType<PlayerAttribute>>> entry : ATTRIBUTES_TO_DISPLAY.entrySet()) {
            String attrName = entry.getKey();
            AttachmentType<PlayerAttribute> type = entry.getValue().get();

            // 从客户端玩家的 Attachment 中读取属性数据
            // 数据由 SyncPlayerAttributePacket 从服务端同步
            PlayerAttribute attr = player.getData(type);

            // 根据是否有上限选择不同的显示格式
            String displayText;
            if (attr.getMaxValue() == Integer.MAX_VALUE) {
                displayText = String.format("%s: %d", attrName, attr.getValue());
            } else {
                displayText = String.format("%s: %d / %d", attrName, attr.getValue(), attr.getMaxValue());
            }

            // 绘制文字（ARGB 颜色：0xFFFFFFFF = 不透明白色，true = 带阴影）
            guiGraphics.text(mc.font, displayText, x, y, 0xFFFFFFFF, true);

            // Y 坐标下移，绘制下一行属性
            y += lineHeight;
        }
    }
}
