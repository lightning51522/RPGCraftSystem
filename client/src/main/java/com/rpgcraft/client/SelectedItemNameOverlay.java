package com.rpgcraft.client;

import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.RPGComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 主屏幕下方「选中物品名称」HUD 染色
 * <p>
 * 原版在切换选中物品时会在屏幕底部短暂显示该物品名称（{@code selected_item_name} 图层）。
 * 本类替换该图层：当选中物品携带 RPG 稀有度组件（非 GRAY）时，把名称染为稀有度对应颜色
 * （RAINBOW 同步 {@link EquipmentRarityColors} 的逐 tick 动画色）；否则回落原版默认颜色。
 * <p>
 * <b>计时器自管</b>：原版 {@code Gui.toolHighlightTimer}/{@code lastToolHighlight} 为私有字段，
 * 替换图层后无法访问，故本类在 {@link ClientTickEvent.Post} 中按相同的语义自行维护计时与上次选中项，
 * 复刻原版「切换时显示 {@code 40 × notificationDisplayTime} tick、末段线性淡出」的效果。
 *
 * @see EquipmentTooltipEventHandler 名称 tooltip 染色（与本类的 HUD 染色口径一致）
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ClientMod.MODID)
public final class SelectedItemNameOverlay {

    /** 名称保持显示与淡出的计时器（与原版 toolHighlightTimer 同语义，单位 tick）。 */
    private static int timer = 0;
    /** 上次触发高亮的物品（用于判断是否切换了选中项）。 */
    private static ItemStack lastHighlight = ItemStack.EMPTY;

    private SelectedItemNameOverlay() {
    }

    /**
     * 复刻原版 {@code Gui.tick()} 中的选中项高亮计时逻辑：
     * 选中空手 → 计时归零；切换了物品/名称 → 重置为 {@code 40 × notificationDisplayTime}；否则递减。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            timer = 0;
            lastHighlight = ItemStack.EMPTY;
            return;
        }
        ItemStack selected = mc.player.getInventory().getSelectedItem();
        if (selected.isEmpty()) {
            timer = 0;
        } else if (lastHighlight.isEmpty()
                || !selected.is(lastHighlight.getItem())
                || !selected.getHoverName().equals(lastHighlight.getHoverName())) {
            timer = (int) (40.0 * mc.options.notificationDisplayTime().get());
        } else if (timer > 0) {
            timer--;
        }
        lastHighlight = selected;
    }

    /**
     * 替换原版 {@code selected_item_name} 图层为染色版本。
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.SELECTED_ITEM_NAME, SelectedItemNameOverlay::render);
    }

    /**
     * 渲染染色后的选中物品名称。定位、淡出 alpha 与原版 {@code Gui.extractSelectedItemName} 完全一致，
     * 仅替换名称组件的颜色样式：有 RPG 稀有度（非 GRAY）用 {@link EquipmentRarityColors#resolveColor}，
     * 否则回落原版的 {@code ItemStack.getRarity().color()}。
     */
    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (timer <= 0 || lastHighlight.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return;

        // 决定名称颜色：RPG 稀有度优先，无则原版 rarity 颜色
        int nameColor = resolveNameColor(lastHighlight);

        MutableComponent str = Component.empty().append(lastHighlight.getHoverName()).withStyle(style -> style.withColor(nameColor));
        // 装备等级 > 0 时在名称后追加星形后缀（与 tooltip 口径一致，继承名称颜色）
        int level = currentLevel(lastHighlight);
        if (level > 0) {
            str.append(" " + com.rpgcraft.core.equipment.EquipmentLevelStars.stars(level));
        }
        if (lastHighlight.has(DataComponents.CUSTOM_NAME)) {
            str.withStyle(ChatFormatting.ITALIC);
        }

        int strWidth = mc.font.width(str);
        int x = (graphics.guiWidth() - strWidth) / 2;
        int y = graphics.guiHeight() - 59;
        if (!mc.gameMode.canHurtPlayer()) {
            y += 14;
        }

        int alpha = (int) (timer * 256.0F / 10.0F);
        if (alpha > 255) alpha = 255;
        if (alpha > 0) {
            graphics.textWithBackdrop(mc.font, str, x, y, strWidth, ARGB.white(alpha));
        }
    }

    /** 读取堆叠的当前装备等级（无组件视为 0）。 */
    private static int currentLevel(ItemStack stack) {
        Integer level = stack.get(RPGComponents.EQUIPMENT_LEVEL.get());
        return level != null ? level : 0;
    }

    /**
     * 解析选中物品名称应使用的颜色（RGB）。
     * <p>
     * 按优先级查找 RPG 稀有度：装备读 {@code EQUIPMENT_RARITY} 组件、宝石物品读 {@code GEM_INSTANCE}
     * 组件的稀有度。任一非 GRAY → {@link EquipmentRarityColors#resolveColor}（含 RAINBOW 动画）；
     * 否则回落原版 {@link ItemStack#getRarity} 的颜色（{@code Rarity.color()} 返回的 ChatFormatting）。
     * <p>
     * {@code Rarity.color()} 在本 MC 版本被标记为过时，但原版 {@code Gui.extractSelectedItemName}
     * 仍使用之，回落路径保持与原版一致即可。
     */
    @SuppressWarnings("deprecation") // Rarity.color() 已过时，但原版选中名渲染仍用之，回落路径保持一致
    private static int resolveNameColor(ItemStack stack) {
        // 装备稀有度（EQUIPMENT_RARITY 组件）
        EquipmentRarity rarity = stack.get(RPGComponents.EQUIPMENT_RARITY.get());
        // 宝石物品自身稀有度（GEM_INSTANCE 组件，与装备组件互斥：一件物品不会同时是两者）
        if (rarity == null) {
            com.rpgcraft.core.equipment.GemInstance gem = stack.get(RPGComponents.GEM_INSTANCE.get());
            if (gem != null) rarity = gem.rarity();
        }
        if (rarity != null && rarity != EquipmentRarity.GRAY) {
            return EquipmentRarityColors.resolveColor(rarity);
        }
        // 回落原版：ItemStack.getRarity().color() 返回 ChatFormatting（默认白色 COMMON、附魔黄色 UNCOMMON）
        Integer vanilla = stack.getRarity().color().getColor();
        return vanilla == null ? 0xFFFFFF : vanilla;
    }
}
