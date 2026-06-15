package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import com.rpgcraft.core.ui.RPGUIPlugins;
import com.rpgcraft.core.ui.UISnapshotCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * RPG 角色信息界面（独立 Screen）
 * <p>
 * 按快捷键 R（默认绑定）打开，使用插件流式布局渲染角色信息。
 * <p>
 * <h3>布局算法</h3>
 * <ol>
 *   <li>从 {@link RPGUIPlugins#getPlugins()} 获取所有已注册插件</li>
 *   <li>计算 totalHeight = sum(plugin.getHeight())</li>
 *   <li>按注册顺序从上到下流式排列，每个插件占 {@link ICharacterScreenPlugin#getHeight()} 像素高度</li>
 *   <li>内容超出可见区域时启用滚动（鼠标滚轮）</li>
 * </ol>
 * <p>
 * <h3>数据流</h3>
 * <pre>
 * UISnapshotCache.get() → AttributeSnapshot → 各插件 render()
 * </pre>
 * 若缓存为空，显示"正在加载..."提示。界面关闭时清除缓存。
 * <p>
 * <b>注意</b>：NeoForge 26.1.2 的 Screen API 使用 {@code extractRenderState} 替代传统 {@code render}，
 * 使用 {@code extractBackground} 替代 {@code renderBackground}，
 * 鼠标事件使用 {@link MouseButtonEvent} 事件对象。
 *
 * @see ICharacterScreenPlugin
 * @see RPGUIPlugins
 * @see UISnapshotCache
 */
public class RPGCharacterScreen extends Screen {

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 左侧主面板固定宽度（像素） */
    private static final int PANEL_WIDTH = 200;

    /** 右侧属性点面板宽度（像素） */
    private static final int RIGHT_PANEL_WIDTH = 130;

    /** 左右面板间距（像素） */
    private static final int PANEL_GAP = 8;

    /** 两面板并排显示的最小屏幕宽度（低于此值隐藏右侧面板） */
    private static final int MIN_WIDTH_FOR_RIGHT_PANEL = 360;

    /** 顶部内边距（标题区域上方） */
    private static final int TOP_PADDING = 20;

    /** 底部内边距 */
    private static final int BOTTOM_PADDING = 10;

    /** 标题行高度 */
    private static final int TITLE_HEIGHT = 20;

    /** 内容区域内边距 */
    private static final int CONTENT_MARGIN = 6;

    /** 圆角半径 */
    private static final int CORNER_RADIUS = 3;

    /** 每次滚动的像素距离 */
    private static final int SCROLL_STEP = 15;

    // ====================================================================
    // 颜色常量（ARGB 格式）
    // ====================================================================

    /** 半透明黑色背景 */
    private static final int COLOR_BG = 0xC0000000;
    /** 灰色边框 */
    private static final int COLOR_BORDER = 0xFF555555;
    /** 黄色标题 */
    private static final int COLOR_TITLE = 0xFFFFFF00;
    /** 灰色提示文本（加载中/无数据） */
    private static final int COLOR_HINT = 0xFFAAAAAA;
    /** 灰色滚动条 */
    private static final int COLOR_SCROLLBAR = 0xFF555555;

    // ====================================================================
    // 状态
    // ====================================================================

    /** 当前滚动偏移量（像素） */
    private int scrollOffset = 0;

    public RPGCharacterScreen() {
        super(Component.literal("角色信息"));
    }

    // ====================================================================
    // 渲染
    // ====================================================================

    /**
     * 渲染角色界面
     * <p>
     * NeoForge 26.1.2 使用 {@code extractRenderState} 替代传统 {@code render} 方法。
     * 框架在调用此方法之前已经通过 {@code extractBackground} 处理了背景渲染
     * （含 blur），因此此方法中<b>不能</b>再次调用 {@code extractBackground}，
     * 否则触发 {@code Can only blur once per frame} 异常。
     * <p>
     * 框架渲染管线（{@code Screen.extractRenderStateWithTooltipAndSubtitles}）：
     * <pre>
     * 1. extractBackground()  ← 框架调用，执行 blur
     * 2. fire ScreenEvent.Render.Background
     * 3. graphics.nextStratum()
     * 4. extractRenderState() ← 本方法
     * </pre>
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 注意：不调用 this.extractBackground()！框架已在调用此方法前完成背景渲染。
        // 再次调用会导致 IllegalStateException: Can only blur once per frame

        // 1. 计算左面板位置（以"左+间距+右"总宽居中，使右侧属性点面板能并排显示）
        boolean showRightPanel = this.width >= MIN_WIDTH_FOR_RIGHT_PANEL;
        int totalWidth = showRightPanel ? (PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH) : PANEL_WIDTH;
        int panelX = (this.width - totalWidth) / 2;
        int panelY = TOP_PADDING;
        int panelHeight = this.height - TOP_PADDING - BOTTOM_PADDING;

        // 3. 绘制面板背景（圆角矩形 + 边框）
        fillRounded(graphics, panelX - 1, panelY - 1, PANEL_WIDTH + 2, panelHeight + 2,
                CORNER_RADIUS + 1, COLOR_BORDER);
        fillRounded(graphics, panelX, panelY, PANEL_WIDTH, panelHeight,
                CORNER_RADIUS, COLOR_BG);

        // 4. 绘制标题
        String title = "角色信息";
        int titleWidth = this.font.width(title);
        graphics.text(this.font, title, panelX + (PANEL_WIDTH - titleWidth) / 2,
                panelY + 6, COLOR_TITLE, true);

        // 5. 获取快照和插件
        AttributeSnapshot snapshot = UISnapshotCache.get();
        List<ICharacterScreenPlugin> plugins = RPGUIPlugins.getPlugins();
        int contentStartY = panelY + TITLE_HEIGHT;

        // 6. 快照为空 → 显示加载提示
        if (snapshot == null) {
            renderCenteredText(graphics, "正在加载...", panelX, contentStartY, panelHeight - TITLE_HEIGHT);
            return;
        }

        // 7. 无插件 → 显示空状态
        if (plugins.isEmpty()) {
            renderCenteredText(graphics, "暂无角色数据", panelX, contentStartY, panelHeight - TITLE_HEIGHT);
            return;
        }

        // 8. 计算总高度和可见区域
        int totalHeight = 0;
        for (ICharacterScreenPlugin plugin : plugins) {
            totalHeight += plugin.getHeight();
        }

        int maxVisibleHeight = panelY + panelHeight - contentStartY;
        int maxScroll = Math.max(0, totalHeight - maxVisibleHeight);

        // 钳制滚动偏移在有效范围内
        if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
        }
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        }

        // 9. 渲染插件（流式布局）
        int contentX = panelX + CONTENT_MARGIN;
        int contentWidth = PANEL_WIDTH - 2 * CONTENT_MARGIN;
        int currentY = contentStartY - this.scrollOffset;

        // 鼠标悬停命中的 tooltip（一次只显示一个，取首个命中插件）
        List<Component> hoverTooltip = null;

        for (ICharacterScreenPlugin plugin : plugins) {
            int pluginTop = currentY;
            int pluginBottom = currentY + plugin.getHeight();
            int visibleTop = contentStartY;
            int visibleBottom = contentStartY + maxVisibleHeight;

            // 仅渲染与可见区域有交集的插件（跳过完全不可见的）
            if (pluginBottom > visibleTop && pluginTop < visibleBottom) {
                plugin.render(graphics, contentX, currentY, contentWidth, snapshot);

                // 悬停 tooltip 检测：鼠标落在插件矩形内时，询问插件是否有 tooltip
                if (hoverTooltip == null) {
                    double relX = mouseX - contentX;
                    double relY = mouseY - currentY;
                    if (relX >= 0 && relX < contentWidth && relY >= 0 && relY < plugin.getHeight()) {
                        List<Component> tip = plugin.getTooltip(relX, relY, contentWidth, snapshot);
                        if (tip != null && !tip.isEmpty()) {
                            hoverTooltip = tip;
                        }
                    }
                }
            }
            currentY += plugin.getHeight();
        }

        // 9.1 渲染悬停命中的 tooltip（原版风格气泡框，锚点为鼠标位置）
        if (hoverTooltip != null) {
            graphics.setComponentTooltipForNextFrame(this.font, hoverTooltip, mouseX, mouseY);
        }

        // 10. 绘制滚动条指示器（内容超出可见区域时显示）
        if (maxScroll > 0) {
            int scrollbarTrackHeight = maxVisibleHeight;
            int scrollbarThumbHeight = Math.max(20,
                    (int) ((double) maxVisibleHeight / totalHeight * scrollbarTrackHeight));
            int scrollbarY = contentStartY +
                    (maxScroll > 0
                            ? (int) ((double) this.scrollOffset / maxScroll * (scrollbarTrackHeight - scrollbarThumbHeight))
                            : 0);
            graphics.fill(
                    panelX + PANEL_WIDTH - 4, scrollbarY,
                    panelX + PANEL_WIDTH - 2, scrollbarY + scrollbarThumbHeight,
                    COLOR_SCROLLBAR
            );
        }

        // 11. 渲染右侧属性点面板（屏幕足够宽时显示）
        if (showRightPanel) {
            int rightX = panelX + PANEL_WIDTH + PANEL_GAP;
            fillRounded(graphics, rightX - 1, panelY - 1, RIGHT_PANEL_WIDTH + 2, panelHeight + 2,
                    CORNER_RADIUS + 1, COLOR_BORDER);
            fillRounded(graphics, rightX, panelY, RIGHT_PANEL_WIDTH, panelHeight,
                    CORNER_RADIUS, COLOR_BG);
            AttributePointPanel.render(graphics, rightX, panelY, RIGHT_PANEL_WIDTH, panelHeight,
                    mouseX, mouseY);
        }
    }

    // ====================================================================
    // 键盘交互
    // ====================================================================

    /**
     * 键盘按键回调
     * <p>
     * 当 Screen 打开时，Minecraft 将键盘事件路由到 {@code Screen.keyPressed(KeyEvent)}
     * 而非 {@code KeyMapping} 系统，因此关闭逻辑必须在此处直接检测 GLFW 按键。
     * <ul>
     *   <li>R 键 — 关闭界面（与打开界面的快捷键一致，实现 toggle 切换）</li>
     *   <li>ESC — 由父类 {@link Screen} 处理，默认关闭界面</li>
     * </ul>
     *
     * @param event 键盘事件（包含 key、scancode、modifiers）
     * @return true 如果事件被消费
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // ====================================================================
    // 鼠标交互
    // ====================================================================

    /**
     * 鼠标滚轮滚动回调
     * <p>
     * 调整滚动偏移量，步长为 {@value SCROLL_STEP} 像素。
     * 实际钳制在 {@link #extractRenderState} 中完成（因为最大偏移量依赖插件总高度）。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOffset -= (int) (scrollY * SCROLL_STEP);
        return true;
    }

    /**
     * 鼠标点击回调
     * <p>
     * NeoForge 26.1.2 使用 {@link MouseButtonEvent} 事件对象替代传统 (double, double, int) 参数。
     * 遍历所有插件，将鼠标坐标转换为相对于每个插件渲染区域的局部坐标，
     * 调用 {@link ICharacterScreenPlugin#mouseClicked} 处理交互。
     *
     * @param event 鼠标按钮事件（包含 x, y, button 信息）
     * @param isPick 如果为 true 表示选取操作
     * @return true 如果事件被消费
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isPick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // 与 extractRenderState 一致的面板定位（含右侧面板时的居中）
        boolean showRightPanel = this.width >= MIN_WIDTH_FOR_RIGHT_PANEL;
        int totalWidth = showRightPanel ? (PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH) : PANEL_WIDTH;
        int panelX = (this.width - totalWidth) / 2;
        int panelY = TOP_PADDING;
        int panelHeight = this.height - TOP_PADDING - BOTTOM_PADDING;

        // 先尝试右侧属性点面板的点击（[+] 按钮）
        if (showRightPanel) {
            int rightX = panelX + PANEL_WIDTH + PANEL_GAP;
            if (mouseX >= rightX && mouseX < rightX + RIGHT_PANEL_WIDTH
                    && mouseY >= panelY && mouseY < panelY + panelHeight) {
                if (AttributePointPanel.mouseClicked(rightX, panelY, RIGHT_PANEL_WIDTH, panelHeight,
                        mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        AttributeSnapshot snapshot = UISnapshotCache.get();
        if (snapshot == null) return false;

        List<ICharacterScreenPlugin> plugins = RPGUIPlugins.getPlugins();
        if (plugins.isEmpty()) return false;

        int contentStartY = panelY + TITLE_HEIGHT;
        int contentX = panelX + CONTENT_MARGIN;
        int contentWidth = PANEL_WIDTH - 2 * CONTENT_MARGIN;

        int currentY = contentStartY - this.scrollOffset;
        for (ICharacterScreenPlugin plugin : plugins) {
            // 将鼠标坐标转换为相对于插件渲染区域的局部坐标
            double relX = mouseX - contentX;
            double relY = mouseY - currentY;

            // 检查点击是否在插件区域内
            if (relY >= 0 && relY < plugin.getHeight() && relX >= 0 && relX < contentWidth) {
                if (plugin.mouseClicked(relX, relY, button)) {
                    return true;
                }
            }
            currentY += plugin.getHeight();
        }

        return super.mouseClicked(event, isPick);
    }

    // ====================================================================
    // 生命周期
    // ====================================================================

    /**
     * 界面关闭时清除快照缓存
     * <p>
     * 防止过期数据残留，下次打开时重新向服务端请求最新快照。
     */
    @Override
    public void removed() {
        UISnapshotCache.clear();
        super.removed();
    }

    /**
     * 此界面不暂停游戏
     *
     * @return {@code false}
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    /**
     * 在面板中央渲染居中提示文本
     *
     * @param graphics     图形上下文
     * @param text         提示文本
     * @param panelX       面板左上角 X
     * @param contentStartY 内容区域起始 Y
     * @param contentHeight 内容区域高度
     */
    private void renderCenteredText(GuiGraphicsExtractor graphics, String text,
                                    int panelX, int contentStartY, int contentHeight) {
        int textWidth = this.font.width(text);
        graphics.text(this.font, text,
                panelX + (PANEL_WIDTH - textWidth) / 2,
                contentStartY + contentHeight / 2 - 4,
                COLOR_HINT, false);
    }

    /**
     * 绘制圆角矩形填充
     * <p>
     * 使用圆角矩形填充算法：
     * 三条矩形（中间通栏 + 左右窄条）+ 四角方块补丁。
     *
     * @param g     图形上下文
     * @param x     左上角 X
     * @param y     左上角 Y
     * @param w     宽度
     * @param h     高度
     * @param r     圆角半径
     * @param color ARGB 颜色
     */
    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                    int r, int color) {
        // 中间通栏
        g.fill(x + r, y, x + w - r, y + h, color);
        // 左侧窄条
        g.fill(x, y + r, x + r, y + h - r, color);
        // 右侧窄条
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // 四角补丁
        g.fill(x + 1, y + 1, x + r, y + r, color);
        g.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        g.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        g.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
    }
}
