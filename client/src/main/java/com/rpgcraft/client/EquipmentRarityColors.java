package com.rpgcraft.client;

import com.rpgcraft.core.equipment.EquipmentRarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.awt.Color;

/**
 * 装备稀有度颜色解析（含彩虹动画）
 * <p>
 * {@link EquipmentRarity#RAINBOW} 等级的颜色不是静态 RGB，而是逐 tick 按 HSV 色相轮转的动态彩虹。
 * 本类维护一个客户端 tick 计数器 {@link #rainbowHue}（0..359），每 tick 推进若干度，
 * 通过 {@link #resolveColor(EquipmentRarity)} 统一对外提供「当前应使用的颜色」：
 * 普通等级返回其静态 RGB，彩虹等级返回当前轮转色。
 * <p>
 * 由于 {@code ItemTooltipEvent} 每帧触发、tooltip 每帧重建，{@code resolveColor} 读到的
 * {@code rainbowHue} 在不同帧会有差异，从而呈现真彩虹动画。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ClientMod.MODID)
public final class EquipmentRarityColors {

    /** 每 tick 推进的色相度数（4°/tick ≈ 1.5 秒走完一圈，节奏明显但不刺眼）。 */
    private static final int HUE_STEP = 4;

    /** 当前彩虹色相（0..359），由客户端 tick 推进。 */
    private static int rainbowHue = 0;

    private EquipmentRarityColors() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        rainbowHue = (rainbowHue + HUE_STEP) % 360;
    }

    /**
     * 解析某稀有度当前应使用的 RGB 颜色。
     * <p>
     * 彩虹等级返回基于 {@link #rainbowHue} 的动态 HSV→RGB；其余等级返回静态 {@link EquipmentRarity#getColor()}。
     *
     * @param rarity 稀有度
     * @return RGB 颜色（不含 alpha）
     */
    public static int resolveColor(EquipmentRarity rarity) {
        if (rarity.isRainbow()) {
            return Color.HSBtoRGB(rainbowHue / 360f, 0.85f, 1.0f) & 0xFFFFFF;
        }
        return rarity.getColor();
    }
}
