package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import com.rpgcraft.core.ui.RPGUIPlugins;
import com.rpgcraft.core.ui.UISnapshotCache;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    private static final int RIGHT_PANEL_WIDTH = 150;

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

    /** 每次滚动的像素距离 */
    private static final int SCROLL_STEP = 15;

    /** 滚动条宽度 */
    private static final int SCROLLBAR_WIDTH = 8;

    // ====================================================================
    // 精灵贴图（原版 + 自定义）
    // ====================================================================

    /** 原版滚动条滑块 */
    private static final Identifier SCROLLER_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "widget/scroller");
    /** 原版滚动条背景 */
    private static final Identifier SCROLLER_BACKGROUND_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "widget/scroller_background");
    /** 原版标题分隔线（32×2，水平平铺） */
    private static final Identifier HEADER_SEPARATOR =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/header_separator.png");

    // ====================================================================
    // 颜色常量（ARGB 格式）—— 原版风格灰阶
    // ====================================================================

    /** 黄色标题（原版强调色） */
    private static final int COLOR_TITLE = 0xFFFFE000;
    /** 灰色提示文本（加载中/无数据） */
    private static final int COLOR_HINT = 0xFFA0A0A0;

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
        // 右侧属性点面板仅在屏幕足够宽 *且* 属性点模块已加载时显示；
        // 模块未加载时不显示右侧面板（也不显示"模块未加载"提示），只居中显示左侧属性面板
        boolean showRightPanel = this.width >= MIN_WIDTH_FOR_RIGHT_PANEL
                && RPGSystems.hasAttributePointSystem();
        int totalWidth = showRightPanel ? (PANEL_WIDTH + PANEL_GAP + RIGHT_PANEL_WIDTH) : PANEL_WIDTH;
        int panelX = (this.width - totalWidth) / 2;
        int panelY = TOP_PADDING;
        int panelHeight = this.height - TOP_PADDING - BOTTOM_PADDING;

        // 3. 绘制面板背景（原版 menu_background 泥土纹理平铺 + 深色覆盖 + 斜面边框）
        Screen.extractMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND,
                panelX, panelY, 0.0F, 0.0F, PANEL_WIDTH, panelHeight);
        // menu_background 仅 25% 黑，叠一层 50% 黑提高可读性
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0x80000000);
        drawContainerBorder(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);

        // 4. 绘制标题 + 标题下原版分隔线
        String title = "角色信息";
        int titleWidth = this.font.width(title);
        graphics.text(this.font, title, panelX + (PANEL_WIDTH - titleWidth) / 2,
                panelY + 6, COLOR_TITLE, true);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HEADER_SEPARATOR,
                panelX, panelY + TITLE_HEIGHT - 2, 0.0F, 0.0F, PANEL_WIDTH, 2, 32, 2);

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

        // 10. 绘制滚动条指示器（内容超出可见区域时显示）—— 原版 scroller 精灵
        if (maxScroll > 0) {
            int scrollbarTrackHeight = maxVisibleHeight;
            int scrollbarThumbHeight = Math.max(20,
                    (int) ((double) maxVisibleHeight / totalHeight * scrollbarTrackHeight));
            int scrollbarY = contentStartY +
                    (maxScroll > 0
                            ? (int) ((double) this.scrollOffset / maxScroll * (scrollbarTrackHeight - scrollbarThumbHeight))
                            : 0);
            int scrollbarX = panelX + PANEL_WIDTH - SCROLLBAR_WIDTH - 2;
            // 背景轨道（全高）
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE,
                    scrollbarX, contentStartY, SCROLLBAR_WIDTH, maxVisibleHeight, -1);
            // 滑块
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE,
                    scrollbarX, scrollbarY, SCROLLBAR_WIDTH, scrollbarThumbHeight, -1);
        }

        // 11. 渲染右侧属性点面板（屏幕足够宽时显示）
        if (showRightPanel) {
            int rightX = panelX + PANEL_WIDTH + PANEL_GAP;
            Screen.extractMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND,
                    rightX, panelY, 0.0F, 0.0F, RIGHT_PANEL_WIDTH, panelHeight);
            graphics.fill(rightX, panelY, rightX + RIGHT_PANEL_WIDTH, panelY + panelHeight, 0x80000000);
            drawContainerBorder(graphics, rightX, panelY, RIGHT_PANEL_WIDTH, panelHeight);
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
        // 右侧属性点面板仅在屏幕足够宽 *且* 属性点模块已加载时显示；
        // 模块未加载时不显示右侧面板（也不显示"模块未加载"提示），只居中显示左侧属性面板
        boolean showRightPanel = this.width >= MIN_WIDTH_FOR_RIGHT_PANEL
                && RPGSystems.hasAttributePointSystem();
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
     * 经典原版容器斜面边框：1px 黑外框 + 上左白高光 + 下右深灰阴影。
     * <p>
     * 配色解码自 {@code textures/gui/container/inventory.png}（黑 0x000000、白 0xFFFFFF、
     * 深灰 0x555555）。在面板背景之上、内容之下绘制。
     */
    private static void drawContainerBorder(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int x1 = x + w, y1 = y + h;
        // 1px 黑外框（4 边）
        g.fill(x, y, x1, y + 1, 0xFF000000);
        g.fill(x, y1 - 1, x1, y1, 0xFF000000);
        g.fill(x, y + 1, x + 1, y1 - 1, 0xFF000000);
        g.fill(x1 - 1, y + 1, x1, y1 - 1, 0xFF000000);
        // 1px 白高光（顶 + 左，黑框内侧）
        g.fill(x + 1, y + 1, x1 - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y1 - 1, 0xFFFFFFFF);
        // 1px 深灰阴影（底 + 右，黑框内侧）
        g.fill(x + 1, y1 - 2, x1 - 1, y1 - 1, 0xFF555555);
        g.fill(x1 - 2, y + 1, x1 - 1, y1 - 1, 0xFF555555);
    }
}
