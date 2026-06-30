package com.rpgcraft.core.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Tooltip 图像贡献数据（不可变值对象）
 * <p>
 * 由 {@link ITooltipImageContributor} 贡献，描述一件物品的 tooltip 中需要渲染的「图像元素」：
 * 一个方形槽位 + （可选）物品图标 + 若干词条文本行。client 模块的通用渲染器
 * （{@code ClientTooltipComponent}）读取本数据并绘制。
 * <p>
 * <b>数据-渲染分离</b>：本类只承载<b>数据</b>（要画什么），不含任何渲染逻辑或客户端类引用，
 * 因此宝石模块（仅依赖 core）可以构造本数据，而 client 模块负责渲染 —— 二者零编译期耦合。
 * <p>
 * 典型用途（宝石系统）：装备 tooltip 显示镶嵌槽，未镶嵌时 {@code iconStack=null}（空槽），
 * 镶嵌后 {@code iconStack} 为宝石物品堆叠、{@code slotColor} 为宝石稀有度色、
 * {@code affixLines} 为该宝石的词条文本。
 *
 * @param iconStack  要渲染的物品图标（如宝石）；{@code null} 表示空槽（仅画槽位背景）
 * @param slotColor  槽位边框/背景的 RGB 颜色（如宝石稀有度对应的颜色）
 * @param affixLines 词条文本行（如「力量 +10」）；空列表表示无文本
 */
public record TooltipImageData(
        @Nullable ItemStack iconStack,
        int slotColor,
        List<Component> affixLines) {

    /**
     * 紧凑构造器：防御性拷贝 + null 兜底。
     *
     * @param iconStack  物品图标；{@code null}=空槽
     * @param slotColor  槽位颜色
     * @param affixLines 词条文本行；{@code null} 视为空列表
     */
    public TooltipImageData {
        affixLines = affixLines == null ? List.of() : List.copyOf(affixLines);
    }
}
