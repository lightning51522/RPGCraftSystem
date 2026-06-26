package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.AttributeSnapshot.AttributeData;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 属性列表面板插件 —— 角色界面中的属性展示区
 * <p>
 * 从 {@link AttributeSnapshot} 读取所有属性数据，以两列布局渲染<b>全部</b>属性。
 * 不做分页 —— 当属性数量超过角色面板可见区域时，由 {@code RPGCharacterScreen} 的
 * 整体滚动条 + scissor 处理溢出。
 * <p>
 * 从快照读取数据（数据-渲染分离），而非直接访问实体 Attachment。
 * <p>
 * 布局（从上到下）：
 * <ol>
 *   <li>顶部分隔线（无标题；标题统一由 {@link CompositeAttributePlugin} 提供）</li>
 *   <li>两列属性列表（每行两个属性，全部渲染）</li>
 * </ol>
 * <p>
 * 仅渲染可加点的能力型属性和资源型属性。综合派生属性（暴击率/暴击伤害等）
 * 由 {@link CompositeAttributePlugin} 展示；元素抗性由 {@link ResistanceAttributePlugin} 展示。
 *
 * @see ICharacterScreenPlugin
 * @see AttributeSnapshot
 */
public class AttributeListPlugin implements ICharacterScreenPlugin {

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 每行高度 */
    private static final int LINE_HEIGHT = 12;

    /** 两列间距 */
    private static final int COLUMN_GAP = 8;

    /** 分隔线上方间距 */
    private static final int SEPARATOR_TOP_GAP = 4;
    /** 分隔线下方间距（与文字之间的留白，优化视觉呼吸感） */
    private static final int SEPARATOR_BOTTOM_GAP = 4;
    /** 顶部区域高度（上方间距 + 1px 分隔线 + 下方间距，无标题） */
    private static final int HEADER_HEIGHT = SEPARATOR_TOP_GAP + 1 + SEPARATOR_BOTTOM_GAP;

    /** 两列布局，每行容纳的属性数 */
    private static final int COLUMNS = 2;

    /** 快照不可用时的最小高度（仅顶部分隔线，避免 0 高度） */
    private static final int MIN_HEIGHT = HEADER_HEIGHT;

    // ====================================================================
    // 颜色常量（ARGB 格式）
    // ====================================================================

    /** 白色文本 */
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** 灰色分隔线（原版灰阶） */
    private static final int COLOR_SEPARATOR = 0xFF373737;

    /** 复用的 StringBuilder */
    private static final StringBuilder SB = new StringBuilder(64);

    /**
     * 获取插件高度（动态值）
     * <p>
     * 高度随当前属性数量变化：顶部分隔线 + 全部属性行（两列）。
     * 超过可见区域的内容由 {@code RPGCharacterScreen} 的整体滚动条 + scissor 处理，
     * 本插件自身不做分页。
     * <p>
     * 同一帧内的查询返回一致值（基于 {@code UISnapshotCache} 当前快照）。
     * 快照不可用时返回 {@value MIN_HEIGHT}（仅分隔线）。
     *
     * @return 插件高度（像素）
     */
    @Override
    public int getHeight() {
        AttributeSnapshot snapshot = com.rpgcraft.core.ui.UISnapshotCache.get();
        int count = snapshot != null ? getEntries(snapshot).size() : 0;
        int rows = (count + COLUMNS - 1) / COLUMNS; // 向上取整
        return Math.max(MIN_HEIGHT, HEADER_HEIGHT + rows * LINE_HEIGHT);
    }

    /**
     * 渲染属性列表面板
     * <p>
     * 从 {@link AttributeSnapshot} 中读取所有属性数据，
     * 按两列布局渲染全部属性（不分页；超出可见区域由屏幕滚动条处理）。
     *
     * @param graphics 图形上下文
     * @param x        渲染区域左上角 X
     * @param y        渲染区域左上角 Y
     * @param width    渲染区域宽度
     * @param snapshot 属性快照
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y, int width, AttributeSnapshot snapshot) {
        if (snapshot == null) return;

        Minecraft mc = Minecraft.getInstance();
        int currentY = y;

        // 1. 分隔线（顶部，无标题）
        // 分隔线放在 SEPARATOR_TOP_GAP 之后，SEPARATOR_BOTTOM_GAP 落在分隔线与文字之间
        int separatorY = currentY + SEPARATOR_TOP_GAP;
        graphics.fill(x, separatorY, x + width, separatorY + 1, COLOR_SEPARATOR);
        currentY += HEADER_HEIGHT;

        // 2. 属性列表（两列，全部属性）
        renderAttributes(graphics, x, currentY, width, mc, snapshot);
    }

    /**
     * 鼠标悬停在属性行上时返回该属性的说明 tooltip
     * <p>
     * 碰撞检测与 {@link #renderAttributes} 的两列网格布局对齐：
     * 列表区域起始于插件内 {@value #HEADER_HEIGHT} 处，每行 {@value #LINE_HEIGHT} 高，两列等宽。
     * 仅当命中的属性在注册表中有非空 description 时返回 tooltip。
     *
     * @param relX     相对插件区域的鼠标 X
     * @param relY     相对插件区域的鼠标 Y
     * @param width    插件渲染区域宽度
     * @param snapshot 属性快照
     * @return 说明文字行列表，或 {@code null}
     */
    @Override
    public List<Component> getTooltip(double relX, double relY, int width, AttributeSnapshot snapshot) {
        if (snapshot == null) return null;

        List<Map.Entry<Identifier, AttributeData>> entries = getEntries(snapshot);

        // 列表纵向区域：HEADER_HEIGHT ~ HEADER_HEIGHT + rows*LINE_HEIGHT
        int rows = (entries.size() + COLUMNS - 1) / COLUMNS;
        int listTop = HEADER_HEIGHT;
        int listBottom = HEADER_HEIGHT + rows * LINE_HEIGHT;
        if (relY < listTop || relY >= listBottom) return null;

        // 两列横向区域（与 renderAttributes 的 columnWidth 计算一致）
        int columnWidth = (width - COLUMN_GAP) / 2;
        int col;
        if (relX >= 0 && relX < columnWidth) {
            col = 0;
        } else if (relX >= columnWidth + COLUMN_GAP && relX < width) {
            col = 1;
        } else {
            return null; // 列间隙，不命中任何属性
        }

        int row = (int) ((relY - listTop) / LINE_HEIGHT);
        int idx = row * COLUMNS + col;
        if (idx < 0 || idx >= entries.size()) return null;

        Identifier attrId = entries.get(idx).getKey();
        IAttributeEntry entry = AttributeManager.getRegistry().getEntry(attrId);
        if (entry == null) return null;

        String desc = entry.getDescription();
        if (desc == null || desc.isEmpty()) return null;

        // 按 "\n" 分行构造 tooltip
        List<Component> lines = new ArrayList<>();
        for (String line : desc.split("\n")) {
            lines.add(Component.literal(line));
        }
        return lines;
    }

    // ====================================================================
    // 内部方法
    // ====================================================================

    /**
     * 从快照中筛选本插件展示的属性条目
     * <p>
     * 仅保留可加点或资源型的属性（排除暴击率/暴击伤害等综合派生属性，
     * 由 {@link CompositeAttributePlugin} 展示；元素抗性由
     * {@link ResistanceAttributePlugin} 展示）。
     */
    private static List<Map.Entry<Identifier, AttributeData>> getEntries(AttributeSnapshot snapshot) {
        return snapshot.getAll().entrySet().stream()
                .filter(e -> {
                    IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(e.getKey());
                    if (attrEntry == null) return false;
                    return attrEntry.isAllocatable() || attrEntry.shouldResetOnRespawn();
                })
                .toList();
    }

    /**
     * 渲染两列属性列表
     * <p>
     * 渲染全部属性（不分页）；每行两个属性。
     */
    private void renderAttributes(GuiGraphicsExtractor graphics, int x, int y,
                                   int width, Minecraft mc, AttributeSnapshot snapshot) {
        List<Map.Entry<Identifier, AttributeData>> entries = getEntries(snapshot);
        int columnWidth = (width - COLUMN_GAP) / 2;

        for (int i = 0; i < entries.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;

            Map.Entry<Identifier, AttributeData> mapEntry = entries.get(i);
            AttributeData data = mapEntry.getValue();

            SB.setLength(0);
            SB.append(data.displayName()).append(": ").append(data.currentValue());

            int textColor = COLOR_TEXT;

            int attrX = x + col * (columnWidth + COLUMN_GAP);
            int attrY = y + row * LINE_HEIGHT;
            graphics.text(mc.font, SB.toString(), attrX, attrY, textColor, false);
        }
    }
}
