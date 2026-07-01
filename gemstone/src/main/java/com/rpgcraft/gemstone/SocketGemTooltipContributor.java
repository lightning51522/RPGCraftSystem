package com.rpgcraft.gemstone;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.GemInstance;
import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.ui.ITooltipImageContributor;
import com.rpgcraft.core.ui.TooltipImageData;
import com.rpgcraft.core.ui.TooltipImageContributorCoordinator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 镶嵌宝石 tooltip 图像贡献者
 * <p>
 * 实现 {@link ITooltipImageContributor}：为装备 tooltip 贡献镶嵌槽的图像数据（方形槽 + 宝石图标 +
 * 词条文本）。由 {@link GemstoneManager#init()} 注册到 {@link TooltipImageContributorCoordinator}，
 * client 模块的通用 {@code ClientTooltipComponent} 渲染器读取本数据并绘制 —— client 模块对 gemstone
 * 模块零编译期依赖。
 * <p>
 * <b>逻辑</b>：
 * <ul>
 *   <li>装备未注册为可装备物 → 返回 {@code null}（不贡献）</li>
 *   <li>未镶嵌 → 返回空槽（{@code iconStack=null}，灰色槽，无词条行）</li>
 *   <li>已镶嵌 → 返回宝石图标（携带 GEM_INSTANCE 组件的 WATERMELON_TOURMALINE 堆叠）、宝石稀有度色、词条文本行</li>
 * </ul>
 * 词条文本：属性词条 {@code §a<属性名> +<值>}，特效词条 {@code §b特殊效果}（占位，待特效实现后细化）。
 *
 * @see TooltipImageContributorCoordinator tooltip 图像贡献者协调器（聚合入口）
 */
public class SocketGemTooltipContributor implements ITooltipImageContributor {

    /** 未镶嵌时的空槽颜色（灰色）。 */
    private static final int EMPTY_SLOT_COLOR = EquipmentRarity.GRAY.getColor();
    /** 特效词条的占位文本颜色（青色，§b）。 */
    static final String SPECIAL_AFFIX_TEXT = "§b◆ 特殊效果";

    @Override
    public String getContributorId() {
        return "rpgcraftgemstone:socket_gem_tooltip";
    }

    @Override
    public @Nullable TooltipImageData contribute(ItemStack stack) {
        GemInstance gem = stack.get(RPGComponents.EQUIPMENT_SOCKET.get());
        if (gem == null) {
            // 未镶嵌：返回空槽（仅当该物品是已注册可装备物时才贡献，避免为无关物品画槽）
            // 此处简化：只要物品上有 EQUIPMENT_SOCKET 组件槽位的语义，就画空槽。
            // 但为避免为所有物品画空槽，仅对已注册可装备物贡献。
            if (!isEquipment(stack)) return null;
            return new TooltipImageData(null, EMPTY_SLOT_COLOR, List.of());
        }

        // 已镶嵌：构造宝石图标（携带 GEM_INSTANCE 组件）+ 稀有度色 + 词条文本
        ItemStack icon = new ItemStack(GemstoneItems.WATERMELON_TOURMALINE.get());
        icon.set(RPGComponents.GEM_INSTANCE.get(), gem);
        int slotColor = gem.rarity().getColor();
        List<Component> lines = buildAffixLines(gem);
        return new TooltipImageData(icon, slotColor, lines);
    }

    /**
     * 构建宝石词条的 tooltip 文本行（供本类与客户端宝石 tooltip 处理共用）。
     * <p>
     * 词条 ID 直接就是 RPG 属性 ID（单层映射）。属性词条查 {@link SocketGemConfig} 数值表
     * （专属表缺失回退默认表）并解析属性显示名；特效词条显示占位文本
     * （待特效实现后可细化为具体效果描述）。
     *
     * @param gem 宝石实例
     * @return 文本行列表
     */
    static List<Component> buildAffixLines(GemInstance gem) {
        List<Component> lines = new ArrayList<>();
        for (Identifier affixId : gem.affixIds()) {
            if (SocketGemConfig.isAttribute(affixId)) {
                // 自定义数值优先（命令指定）；缺失则按宝石稀有度查默认表
                int value = gem.customValueOf(affixId).orElse(
                        SocketGemConfig.getAttributeValue(affixId, gem.rarity()));
                IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(affixId);
                String name = attrEntry != null ? attrEntry.getDisplayName() : affixId.toString();
                // 正数显示「+N」，负数直接显示「-N」（避免「+-N」）
                String sign = value >= 0 ? "+" : "";
                lines.add(Component.literal("§a" + name + " " + sign + value));
            } else if (SocketGemConfig.isSpecial(affixId)) {
                lines.add(Component.literal(SPECIAL_AFFIX_TEXT));
            }
        }
        return lines;
    }

    /**
     * 判断物品是否为已注册可装备物（决定是否为它画空槽）。
     * <p>
     * 通过装备系统门面查询注册中心。无 equipment 模块时返回 false（不画槽）。
     */
    private static boolean isEquipment(ItemStack stack) {
        Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return com.rpgcraft.core.registry.RPGSystems.getEquipmentSystem()
                .getRegistry().getBonuses(itemId).isPresent();
    }
}
