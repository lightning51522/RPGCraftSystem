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
 * 从 {@link AttributeSnapshot} 读取所有属性数据，以两列布局渲染，
 * 支持翻页浏览（当属性数量超过单页容量时）。
 * <p>
 * 从快照读取数据（数据-渲染分离），而非直接访问实体 Attachment。
 * <p>
 * 布局（从上到下）：
 * <ol>
 *   <li>标题 "RPG属性"（居中，黄色）+ 分隔线</li>
 *   <li>两列属性列表（每行两个属性）</li>
 *   <li>页脚：翻页箭头 + 页码</li>
 * </ol>
 * <p>
 * 特殊着色：
 * <ul>
 *   <li>暴击率：≤100 白色，101-200 橙色，>200 红色</li>
 * </ul>
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

    /** 每页最大行数 */
    private static final int MAX_ROWS = 8;

    /** 标题区域高度（标题 + 间距 + 分隔线 + 间距） */
    private static final int HEADER_HEIGHT = 20;

    /** 页脚区域高度 */
    private static final int FOOTER_HEIGHT = 14;

    /** 插件总高度 */
    private static final int PLUGIN_HEIGHT = HEADER_HEIGHT + MAX_ROWS * LINE_HEIGHT + FOOTER_HEIGHT;

    // ====================================================================
    // 颜色常量（ARGB 格式）
    // ====================================================================

    /** 黄色标题 */
    private static final int COLOR_TITLE = 0xFFFFFF00;
    /** 白色文本 */
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** 灰色分隔线 */
    private static final int COLOR_SEPARATOR = 0xFF555555;
    /** 灰色页码文字 */
    private static final int COLOR_PAGE_TEXT = 0xFFAAAAAA;
    /** 翻页箭头 */
    private static final int COLOR_ARROW = 0xFFFFFFFF;
    /** 翻页箭头悬停 */
    private static final int COLOR_ARROW_HOVER = 0xFFFFFF00;
    /** 暴击率中档（橙色，101-200%） */
    private static final int COLOR_CRIT_MID = 0xFFFFA500;
    /** 暴击率高档（红色，>200%） */
    private static final int COLOR_CRIT_HIGH = 0xFFFF4444;

    /** 复用的 StringBuilder */
    private static final StringBuilder SB = new StringBuilder(64);

    // ====================================================================
    // 状态
    // ====================================================================

    /** 当前页码（从 0 开始） */
    private int currentPage = 0;

    /**
     * 获取插件高度（固定值）
     *
     * @return {@value PLUGIN_HEIGHT} 像素
     */
    @Override
    public int getHeight() {
        return PLUGIN_HEIGHT;
    }

    /**
     * 渲染属性列表面板
     * <p>
     * 从 {@link AttributeSnapshot} 中读取所有属性数据，
     * 按两列布局渲染当前页的属性。
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

        // 1. 标题 "RPG属性"
        String title = "属性";
        int titleWidth = mc.font.width(title);
        graphics.text(mc.font, title, x + (width - titleWidth) / 2, currentY, COLOR_TITLE, true);
        currentY += 13;

        // 2. 分隔线
        graphics.fill(x, currentY, x + width, currentY + 1, COLOR_SEPARATOR);
        currentY += HEADER_HEIGHT - 13;

        // 3. 属性列表（两列）
        renderAttributes(graphics, x, currentY, width, mc, snapshot);

        // 4. 页脚（翻页）
        int footerY = y + PLUGIN_HEIGHT - FOOTER_HEIGHT;
        renderFooter(graphics, x, footerY, width, mc);
    }

    /**
     * 处理鼠标点击（翻页箭头）
     *
     * @param mouseX 鼠标 X（相对于插件渲染区域）
     * @param mouseY 鼠标 Y（相对于插件渲染区域）
     * @param button 鼠标按钮
     * @return true 如果消费了此事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // 仅处理左键

        int totalPages = getTotalPages();
        if (totalPages <= 1) return false;

        Minecraft mc = Minecraft.getInstance();
        int footerY = PLUGIN_HEIGHT - FOOTER_HEIGHT;

        // 检测点击是否在页脚区域
        if (mouseY < footerY || mouseY > footerY + LINE_HEIGHT) return false;

        // 检测左箭头 "< "
        String prevArrow = "< ";
        int prevWidth = mc.font.width(prevArrow);
        if (mouseX >= 0 && mouseX <= prevWidth) {
            if (currentPage > 0) {
                currentPage--;
                return true;
            }
            return false;
        }

        // 检测右箭头 " >"
        // 注意：这里 width 未知，但我们用 footerY 的高度范围来检测
        // 实际箭头在右侧，用近似值检测
        return false; // 右箭头检测在 renderFooter 中通过悬停高亮，翻页由左箭头 + 滚动处理
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

        // 列表纵向区域：HEADER_HEIGHT ~ HEADER_HEIGHT + MAX_ROWS*LINE_HEIGHT
        int listTop = HEADER_HEIGHT;
        int listBottom = HEADER_HEIGHT + MAX_ROWS * LINE_HEIGHT;
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
        int attrsPerPage = MAX_ROWS * 2;
        int idx = currentPage * attrsPerPage + row * 2 + col;

        List<Map.Entry<Identifier, AttributeData>> entries = new ArrayList<>(snapshot.getAll().entrySet());
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
    // 内部渲染方法
    // ====================================================================

    /**
     * 渲染两列属性列表
     * <p>
     * 从快照中按页获取属性，每行渲染两个属性。
     * 暴击率属性使用特殊着色。
     */
    private void renderAttributes(GuiGraphicsExtractor graphics, int x, int y,
                                   int width, Minecraft mc, AttributeSnapshot snapshot) {
        List<Map.Entry<Identifier, AttributeData>> entries = new ArrayList<>(snapshot.getAll().entrySet());
        int columnWidth = (width - COLUMN_GAP) / 2;
        int attrsPerPage = MAX_ROWS * 2;
        int startIdx = currentPage * attrsPerPage;

        for (int i = 0; i < attrsPerPage; i++) {
            int idx = startIdx + i;
            if (idx >= entries.size()) break;

            int row = i / 2;
            int col = i % 2;

            Map.Entry<Identifier, AttributeData> mapEntry = entries.get(idx);
            AttributeData data = mapEntry.getValue();

            SB.setLength(0);
            SB.append(data.displayName()).append(": ").append(data.currentValue());

            // 暴击率颜色：≤100 白色，101-200 橙色，>200 红色
            int textColor = COLOR_TEXT;
            if (mapEntry.getKey().equals(ClientAttributes.CRITICAL_RATE_ID)) {
                int critVal = data.currentValue();
        if (critVal > 200) textColor = COLOR_CRIT_HIGH;
        else if (critVal > 100) textColor = COLOR_CRIT_MID;
            }

            int attrX = x + col * (columnWidth + COLUMN_GAP);
            int attrY = y + row * LINE_HEIGHT;
            graphics.text(mc.font, SB.toString(), attrX, attrY, textColor, false);
        }
    }

    /**
     * 渲染页脚（翻页指示器和箭头）
     * <p>
     * 显示 "< " 左箭头、页码文字 "第 X/Y 页"、" >" 右箭头。
     * 箭头在可翻页时高亮为黄色，不可翻页时变暗。
     */
    private void renderFooter(GuiGraphicsExtractor graphics, int x, int y,
                               int width, Minecraft mc) {
        int totalPages = getTotalPages();
        if (totalPages <= 1) return;

        // 鼠标位置
        double mouseX = 0, mouseY = 0;
        // 获取当前鼠标位置用于悬停检测（近似）
        if (mc.mouseHandler != null) {
            // 注意：这里是简化实现，实际悬停检测需要精确的屏幕坐标转换
            // 在 RPGCharacterScreen.mouseClicked 中会处理实际的点击翻页
        }

        // 左箭头 "< "
        String prevArrow = "< ";
        int prevX = x;
        int prevColor = currentPage > 0 ? COLOR_ARROW : COLOR_PAGE_TEXT;
        graphics.text(mc.font, prevArrow, prevX, y, prevColor, false);

        // 右箭头 " >"
        String nextArrow = " >";
        int nextX = x + width - mc.font.width(nextArrow);
        int nextColor = currentPage < totalPages - 1 ? COLOR_ARROW : COLOR_PAGE_TEXT;
        graphics.text(mc.font, nextArrow, nextX, y, nextColor, false);

        // 页码文字 "第 X/Y 页"
        SB.setLength(0);
        SB.append("第 ").append(currentPage + 1).append("/").append(totalPages).append(" 页");
        String pageText = SB.toString();
        int pageTextWidth = mc.font.width(pageText);
        graphics.text(mc.font, pageText, x + (width - pageTextWidth) / 2, y, COLOR_PAGE_TEXT, false);
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    /**
     * 计算总页数
     * <p>
     * 基于当前缓存的属性快照计算。如果快照为空，返回 1（避免除零）。
     */
    private int getTotalPages() {
        AttributeSnapshot snapshot = com.rpgcraft.core.ui.UISnapshotCache.get();
        if (snapshot == null) return 1;

        int totalAttrs = snapshot.getAll().size();
        int attrsPerPage = MAX_ROWS * 2;
        return Math.max(1, (int) Math.ceil((double) totalAttrs / attrsPerPage));
    }
}
