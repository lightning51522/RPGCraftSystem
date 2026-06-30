package com.rpgcraft.client.tooltip;

import com.rpgcraft.core.ui.TooltipImageData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * RPG tooltip 图像组件的客户端渲染器（通用，不绑定具体业务）
 * <p>
 * 实现 {@link ClientTooltipComponent}，把 {@link RpgTooltipImageComponent} 携带的
 * {@link TooltipImageData} 列表渲染为「方形槽位 + 宝石图标 + 词条文本」。
 * <p>
 * <b>通用性</b>：本渲染器不引用任何具体业务模块（如 gemstone），只读取
 * {@link TooltipImageData} 的抽象字段（图标堆叠、槽位颜色、词条文本）。各贡献者模块（gemstone 等）
 * 提供数据，client 模块统一渲染 —— 二者零编译期耦合。
 * <p>
 * <b>渲染布局</b>：每个 {@link TooltipImageData} 占一行高度（取槽位高度与词条行数的较大者），
 * 左侧画方形槽（{@link #SLOT_SIZE} 像素，含 {@code slotColor} 边框与深色背景），槽内若有图标则
 * {@code item()} 渲染 16×16 物品图标；槽右侧依次绘制词条文本行。
 *
 * @see RpgTooltipImageComponent 数据载体
 * @see RpgTooltipEventHandler    注入本组件到 tooltip 的 GatherComponents 监听器
 */
public class RpgTooltipImageClientComponent implements ClientTooltipComponent {

    /** 方形槽位尺寸（像素，物品图标为 16×16，加 1px 边框）。 */
    private static final int SLOT_SIZE = 18;
    /** 槽位与词条文本之间的水平间距。 */
    private static final int SLOT_TEXT_GAP = 4;
    /** 文本行高（像素）。 */
    private static final int LINE_HEIGHT = 10;
    /** 空槽背景色（半透明深灰，ARGB）。 */
    private static final int SLOT_BG_COLOR = 0x40000000;
    /** 默认词条文本颜色（白色，ARGB）。 */
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    /** 组件上下内边距（像素）。 */
    private static final int PADDING_Y = 2;

    private final List<TooltipImageData> entries;

    public RpgTooltipImageClientComponent(RpgTooltipImageComponent component) {
        this.entries = component.entries();
    }

    @Override
    public int getHeight(Font font) {
        // 总高度 = 各条目高度之和 + 上下内边距
        int total = PADDING_Y * 2;
        for (TooltipImageData data : entries) {
            total += entryHeight(data);
        }
        return total;
    }

    @Override
    public int getWidth(Font font) {
        // 总宽度 = 各条目宽度的最大值（槽位 + 间距 + 最长词条文本）
        int maxWidth = 0;
        for (TooltipImageData data : entries) {
            int w = SLOT_SIZE + SLOT_TEXT_GAP + longestLineWidth(font, data);
            maxWidth = Math.max(maxWidth, w);
        }
        return maxWidth;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int cursorY = y + PADDING_Y;
        for (TooltipImageData data : entries) {
            renderEntry(graphics, font, x, cursorY, data);
            cursorY += entryHeight(data);
        }
    }

    /**
     * 渲染单个条目：方形槽 + （可选）图标 + 词条文本。
     *
     * @param graphics 图形上下文
     * @param font     字体
     * @param x        条目左上角 X
     * @param y        条目左上角 Y
     * @param data     图像数据
     */
    private void renderEntry(GuiGraphicsExtractor graphics, Font font, int x, int y, TooltipImageData data) {
        // 1. 方形槽：先画深色背景，再画稀有度色边框（outline = 上/下/左/右四条线）
        int slotX = x;
        int slotY = y + 1; // 槽位垂直居中偏移
        graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BG_COLOR);
        // 边框（用 slotColor 画 1px 描边）
        int edge = data.slotColor() | 0xFF000000; // 强制不透明
        graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + 1, edge);                 // 上
        graphics.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, edge); // 下
        graphics.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE, edge);                 // 左
        graphics.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, edge); // 右

        // 2. 槽内物品图标（若存在）
        ItemStack icon = data.iconStack();
        if (icon != null && !icon.isEmpty()) {
            // 物品图标 16×16，居中于 18×18 槽位
            graphics.item(icon, slotX + 1, slotY + 1);
        }

        // 3. 槽右侧词条文本（逐行）
        int textX = slotX + SLOT_SIZE + SLOT_TEXT_GAP;
        int textY = slotY + (SLOT_SIZE - data.affixLines().size() * LINE_HEIGHT) / 2 + 1;
        for (Component line : data.affixLines()) {
            graphics.text(font, line, textX, textY, TEXT_COLOR, false);
            textY += LINE_HEIGHT;
        }
    }

    /** 单个条目的高度：槽位高度与（词条行数×行高）的较大者。 */
    private int entryHeight(TooltipImageData data) {
        int textHeight = Math.max(LINE_HEIGHT, data.affixLines().size() * LINE_HEIGHT);
        return Math.max(SLOT_SIZE + 2, textHeight);
    }

    /** 词条文本行的最大像素宽度（无词条返回 0）。 */
    private int longestLineWidth(Font font, TooltipImageData data) {
        int max = 0;
        for (Component line : data.affixLines()) {
            max = Math.max(max, font.width(line));
        }
        return max;
    }
}
