package com.rpgcraft.client.ui;

import com.rpgcraft.core.network.ProfessionActionPacket;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.ui.ProfessionStateCache;
import com.rpgcraft.core.ui.ProfessionStateCache.ProfessionNode;
import com.rpgcraft.core.ui.ProfessionStateCache.ProfessionStateView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 职业面板（独立 Screen）
 * <p>
 * 按快捷键 P（默认）打开。布局由<b>两个可拖动浮窗</b>组成：
 * <ul>
 *   <li><b>主职业窗</b>：横向显示主职业树（PRIMARY，根在左、子向右）</li>
 *   <li><b>副职业窗</b>：横向显示副职业树（SECONDARY，独立成树）</li>
 * </ul>
 * 取消了固定详情面板。职业信息改为<b>悬停节点时的气泡提示</b>；操作改为<b>节点下方 + 按钮与双击</b>。
 * <p>
 * 交互：
 * <ul>
 *   <li><b>悬停节点</b> → 鼠标位置显示职业气泡（名称/类型/状态/描述/等级/下一级消耗）</li>
 *   <li><b>节点下 + 按钮</b> → 仅在「可投入一级」的已解锁职业下显示，点击投入一级</li>
 *   <li><b>双击节点</b>：
 *     <ul>
 *       <li>已解锁、非当前主职业 → 直接切换为主职业</li>
 *       <li>可进阶、未解锁的主职业 → 弹确认框 → 进阶并切换</li>
 *       <li>已解锁副职业 → 切换激活状态（激活/取消，加成共存）</li>
 *     </ul>
 *   </li>
 *   <li><b>激活的副职业</b> → 节点外显示蓝色外框</li>
 *   <li><b>标题栏右上角 □/⊟ 按钮</b> → 最大化/还原该窗（铺满全屏，隐藏另一窗）</li>
 *   <li>拖动窗口<b>标题栏</b> → 移动该窗口位置（主/副窗各自独立）</li>
 *   <li>拖动窗口<b>内空白</b> → 平移该窗口内的画布（查看超出窗口的树节点）</li>
 *   <li>打开时自动以「当前主职业」节点居中到主职业窗</li>
 * </ul>
 * 数据来源 {@link ProfessionStateCache}，操作通过 {@link ProfessionActionPacket} 发服务端权威处理。
 *
 * @see ProfessionScreenOpener
 * @see ProfessionStateCache
 */
public class RPGProfessionScreen extends Screen {

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 树窗口最大宽度 */
    private static final int TREE_WIN_MAX_WIDTH = 360;
    /** 树窗口最小宽度 */
    private static final int TREE_WIN_MIN_WIDTH = 200;
    /** 屏幕两侧/区域间安全边距 */
    private static final int MARGIN = 10;
    /** 区域间距 */
    private static final int GAP = 8;
    private static final int TOP_PADDING = 20;
    private static final int BOTTOM_PADDING = 10;
    private static final int CONTENT_MARGIN = 6;

    /** 窗口标题栏高度（可拖动区域） */
    private static final int TITLE_BAR_HEIGHT = 16;
    /** 窗口内顶部经验池行高 */
    private static final int POOL_ROW_HEIGHT = 14;

    /** 方形节点尺寸 */
    private static final int NODE_SIZE = 26;
    /** 节点图标字符 Y 偏移 */
    private static final int ICON_TEXT_OFFSET_Y = 9;
    /** 物品图标在节点内的 X 偏移（物品 16px，在 26px 节点内居中 = (26-16)/2 = 5） */
    private static final int NODE_ICON_ITEM_X = 5;
    /** 物品图标在节点内的 Y 偏移（同上，居中 = 5） */
    private static final int NODE_ICON_ITEM_Y = 5;
    /** 横向树：层级间距（X 方向，父→子） */
    private static final int LEVEL_GAP_X = 90;
    /** 横向树：同层兄弟节点间距（Y 方向） */
    private static final int SIBLING_GAP_Y = 40;
    /** 当前主职业/激活副职业角标 —— 距节点外缘的间距（每边），贴近节点框 */
    private static final int CORNER_OFFSET = 1;
    /** 角标的臂长（水平/垂直段各画这么长） */
    private static final int CORNER_ARM = 5;
    /** 角标的线宽 */
    private static final int CORNER_THICK = 1;

    /** 节点下方 +（投入一级）按钮尺寸 —— 适配原版按钮 9-slice（3px 边框，14 仍可接受） */
    private static final int PLUS_BUTTON_SIZE = 14;
    /** 节点下方 + 按钮宽度 —— 加宽以容纳「Lv.NN +」文本（字体宽度约 36px，留余量到 40） */
    private static final int PLUS_BUTTON_WIDTH = 40;
    /**
     * 解锁副职业消耗的职业经验（与服务端 {@code ProfessionConfigLoader.secondaryUnlockCost} 默认值镜像）。
     * <p>
     * 仅用于客户端 UI 显示/灰显判定；实际扣费由服务端权威处理。
     * 服务端配置可改，客户端若未同步会显示偏差但不影响实际扣费正确性。
     */
    private static final int SECONDARY_UNLOCK_COST = 50000;
    /** 节点下升级按钮行与节点的垂直间距 */
    private static final int PLUS_BUTTON_GAP = 1;
    /** 标题栏右上角最大化按钮尺寸 */
    private static final int MAX_BUTTON_SIZE = 12;

    /** 双击判定时间窗口（毫秒） */
    private static final long DOUBLE_CLICK_MS = 300;

    // ====================================================================
    // 精灵贴图（原版）—— 主窗口用 menu_background 泥土平铺，标题用 HEADER_SEPARATOR 分隔线
    // ====================================================================

    /** 原版按钮精灵（9-slice 200×20，自动缩放到任意尺寸） */
    private static final Identifier BUTTON_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "widget/button_highlighted");
    /** 原版按钮禁用态精灵（经验不足时） */
    private static final Identifier BUTTON_DISABLED_SPRITE =
            Identifier.fromNamespaceAndPath("minecraft", "widget/button_disabled");
    /** 原版 advancement 节点框（26×26 stretch，正好等于 NODE_SIZE，1:1 无缩放） */
    private static final Identifier TASK_FRAME_OBTAINED =
            Identifier.fromNamespaceAndPath("minecraft", "advancements/task_frame_obtained");
    private static final Identifier TASK_FRAME_UNOBTAINED =
            Identifier.fromNamespaceAndPath("minecraft", "advancements/task_frame_unobtained");
    /** 原版标题分隔线（32×2，水平平铺） */
    private static final Identifier HEADER_SEPARATOR =
            Identifier.fromNamespaceAndPath("minecraft", "textures/gui/header_separator.png");

    // ====================================================================
    // 颜色（ARGB）—— 原版风格灰阶调色板
    // ====================================================================

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFA0A0A0;
    /** 强调色（原版黄） */
    private static final int COLOR_TITLE = 0xFFFFE000;
    /** 连接线 */
    private static final int COLOR_LINE_UNLOCKED = 0xFF555555;
    private static final int COLOR_LINE_LOCKED = 0xFF373737;
    /** 当前主职业绿色角标（与激活副职业的蓝色区分） */
    private static final int COLOR_MAIN_CURRENT = 0xFF55FF55;
    /** 激活副职业蓝色角标 */
    private static final int COLOR_SECONDARY_ACTIVE = 0xFF3A7BFF;

    /** 每个职业的图标字符（fallback，优先用 {@link ProfessionNode#iconItem()} 的物品图标） */
    // 注：图标现由服务端通过 ProfessionNode.iconItem / iconChar 推送（源自 IProfession 实现），
    // 客户端不再硬编码。

    // ----------------------------------------------------------------
    // 两个可拖动浮窗
    // ----------------------------------------------------------------
    /** 主职业窗（显示 PRIMARY 树） */
    private FloatingWindow mainWindow;
    /** 副职业窗（显示 SECONDARY 树） */
    private FloatingWindow secondaryWindow;
    /** 是否已完成首次初始化（窗口尺寸/位置 + 居中） */
    private boolean inited = false;
    /** 标记：是否已完成"以当前主职业居中"（避免每次刷新跳回） */
    private boolean centeredOnMain = false;

    /** 双击判定：上次点击时间与节点 ID */
    private long lastClickTime = 0L;
    private Identifier lastClickNodeId = null;
    /** 双击判定：本次点击是否已被识别为双击（避免重复触发） */
    private boolean lastClickWasDouble = false;

    /**
     * 一个可拖动浮窗的状态：窗口屏幕矩形 + 窗内画布平移 + 两级拖动模式 + 最大化标志。
     * <p>
     * 两级拖动靠鼠标按下位置区分：
     * <ul>
     *   <li>按在标题栏 → {@link #draggingWindow}，移动窗口 x/y</li>
     *   <li>按在窗内空白 → {@link #draggingCanvas}，平移 panX/panY</li>
     * </ul>
     * 同一时刻至多一个 dragging 标志为 true。
     * <p>
     * {@code maximized} 为 true 时，{@link #applyMaximizedLayout()} 会把该窗铺满全屏、隐藏另一窗。
     * 切回 false 时由 {@link #initWindows()} 恢复双窗布局。
     */
    private static final class FloatingWindow {
        int x, y, w, h;
        float panX, panY;
        boolean draggingWindow;
        double dragStartMouseX, dragStartMouseY;
        int dragStartWinX, dragStartWinY;
        boolean draggingCanvas;
        /** 是否处于最大化状态 */
        boolean maximized = false;

        FloatingWindow(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
        /** 标题栏矩形（屏幕坐标） */
        int titleBarY() { return y; }
        int titleBarH() { return TITLE_BAR_HEIGHT; }
        /** 窗口内容区（标题栏下方 + 经验池行下方）起点 Y */
        int contentOriginY() { return y + TITLE_BAR_HEIGHT + POOL_ROW_HEIGHT; }
        /** 窗口内容区起点 X */
        int contentOriginX() { return x; }
        /** 鼠标是否落在窗口整个矩形内 */
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
        /** 鼠标是否落在标题栏 */
        boolean isInTitleBar(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + TITLE_BAR_HEIGHT;
        }
        /** 最大化按钮的屏幕矩形（标题栏右上角） */
        int maxButtonX() { return x + w - CONTENT_MARGIN - MAX_BUTTON_SIZE; }
        int maxButtonY() { return y + (TITLE_BAR_HEIGHT - MAX_BUTTON_SIZE) / 2; }
    }

    public RPGProfessionScreen() {
        super(Component.literal("职业"));
    }

    @Override
    public void removed() {
        mainWindow = null;
        secondaryWindow = null;
        inited = false;
        centeredOnMain = false;
        lastClickTime = 0L;
        lastClickNodeId = null;
        // 注意：不清空 ProfessionStateCache。
        // 缓存由服务端推送的全局状态，与 Screen 生命周期无关；切到确认框等子 Screen 时本 Screen 会被
        // removed()，若清空缓存则切回时会因 state==null 显示「加载中」白屏。缓存会在下次服务端推送时
        // 自然覆盖更新，无需手动清理。
        super.removed();
    }

    // ====================================================================
    // 初始化窗口尺寸与位置（每次打开首次渲染时执行一次）
    // ====================================================================

    /**
     * 计算并设置两个浮窗的初始矩形（水平居中、上下排列：主上、副下）。
     * <pre>
     *         ┌─ 主职业 ────────┐
     *         │                │
     *         └────────────────┘
     *         ┌─ 副职业 ────────┐
     *         │                │
     *         └────────────────┘
     * </pre>
     */
    private void initWindows() {
        int[] mainRect = defaultMainRect();
        int[] secRect = defaultSecondaryRect();
        mainWindow = new FloatingWindow(mainRect[0], mainRect[1], mainRect[2], mainRect[3]);
        secondaryWindow = new FloatingWindow(secRect[0], secRect[1], secRect[2], secRect[3]);
    }

    /** 主职业窗默认矩形 [x, y, w, h]（水平居中、上 55%） */
    private int[] defaultMainRect() {
        int availWidth = this.width - 2 * MARGIN;
        int availHeight = this.height - TOP_PADDING - BOTTOM_PADDING;
        int treeW = treeWindowWidth();
        int treeX = (this.width - treeW) / 2;
        int gap2 = GAP * 2;
        int mainH = (availHeight - gap2) * 55 / 100;
        return new int[]{treeX, TOP_PADDING, treeW, mainH};
    }

    /** 副职业窗默认矩形 [x, y, w, h]（水平居中、下 40%） */
    private int[] defaultSecondaryRect() {
        int availHeight = this.height - TOP_PADDING - BOTTOM_PADDING;
        int treeW = treeWindowWidth();
        int treeX = (this.width - treeW) / 2;
        int gap2 = GAP * 2;
        int mainH = (availHeight - gap2) * 55 / 100;
        int secH = (availHeight - gap2) - mainH;
        int secY = TOP_PADDING + mainH + gap2;
        return new int[]{treeX, secY, treeW, secH};
    }

    /** 树窗宽度：不超过 TREE_WIN_MAX_WIDTH，不小于 TREE_WIN_MIN_WIDTH */
    private int treeWindowWidth() {
        int availWidth = this.width - 2 * MARGIN;
        return Math.min(TREE_WIN_MAX_WIDTH, Math.max(TREE_WIN_MIN_WIDTH, availWidth));
    }

    /**
     * 每帧根据当前 {@code maximized} 状态重算两个窗的矩形：
     * <ul>
     *   <li>最大化的窗 → 铺满全屏可用区</li>
     *   <li>非最大化的窗 → 恢复双窗默认布局（水平居中）</li>
     * </ul>
     * 这样点「还原」(maximized: true→false) 后，下一帧自动恢复双窗布局。
     */
    private void applyMaximizedLayout() {
        int maxH = this.height - TOP_PADDING - BOTTOM_PADDING;
        if (mainWindow != null) {
            if (mainWindow.maximized) {
                int w = Math.min(TREE_WIN_MAX_WIDTH, this.width - 2 * MARGIN);
                mainWindow.x = (this.width - w) / 2;
                mainWindow.y = TOP_PADDING;
                mainWindow.w = w;
                mainWindow.h = maxH;
            } else {
                int[] r = defaultMainRect();
                mainWindow.x = r[0]; mainWindow.y = r[1]; mainWindow.w = r[2]; mainWindow.h = r[3];
            }
        }
        if (secondaryWindow != null) {
            if (secondaryWindow.maximized) {
                int w = Math.min(TREE_WIN_MAX_WIDTH, this.width - 2 * MARGIN);
                secondaryWindow.x = (this.width - w) / 2;
                secondaryWindow.y = TOP_PADDING;
                secondaryWindow.w = w;
                secondaryWindow.h = maxH;
            } else {
                int[] r = defaultSecondaryRect();
                secondaryWindow.x = r[0]; secondaryWindow.y = r[1]; secondaryWindow.w = r[2]; secondaryWindow.h = r[3];
            }
        }
    }

    // ====================================================================
    // 渲染
    // ====================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfessionStateView state = ProfessionStateCache.get();
        if (!inited) {
            initWindows();
            inited = true;
        }
        applyMaximizedLayout();

        // 根据最大化状态决定渲染哪些窗：
        // 任一窗最大化 → 只渲染该最大化窗；否则两个窗都渲染
        boolean mainMax = mainWindow != null && mainWindow.maximized;
        boolean secMax = secondaryWindow != null && secondaryWindow.maximized;

        if (mainMax && mainWindow != null) {
            renderTreeWindow(graphics, state, mainWindow, "主职业",
                    IProfession.ProfessionType.PRIMARY, mouseX, mouseY);
        } else if (secMax && secondaryWindow != null) {
            renderTreeWindow(graphics, state, secondaryWindow, "副职业",
                    IProfession.ProfessionType.SECONDARY, mouseX, mouseY);
        } else {
            if (mainWindow != null) {
                renderTreeWindow(graphics, state, mainWindow, "主职业",
                        IProfession.ProfessionType.PRIMARY, mouseX, mouseY);
            }
            if (secondaryWindow != null) {
                renderTreeWindow(graphics, state, secondaryWindow, "副职业",
                        IProfession.ProfessionType.SECONDARY, mouseX, mouseY);
            }
        }

        // 首次拿到非空 state 时，以当前主职业居中
        if (state != null && !centeredOnMain) {
            centerOnCurrentMain(state);
            centeredOnMain = true;
        }
    }

    /**
     * 渲染一个职业树窗口：标题栏（可拖动 + 最大化按钮） + 经验池行 +
     * 窗内画布（scissor + pose 平移 + 横向树 + 节点下 + 按钮 + 悬停气泡）。
     *
     * @param win    目标窗口
     * @param title  标题栏文字（"主职业"/"副职业"）
     * @param type   只渲染该类型的节点
     */
    private void renderTreeWindow(GuiGraphicsExtractor graphics, ProfessionStateView state,
                                  FloatingWindow win, String title,
                                  IProfession.ProfessionType type, int mouseX, int mouseY) {
        // 窗口背景：原版 menu_background 泥土纹理平铺（INWORLD_MENU_BACKGROUND 是 private，
        // 用公开的 MENU_BACKGROUND；视觉差异仅模糊度，可接受）
        Screen.extractMenuBackgroundTexture(graphics, Screen.MENU_BACKGROUND,
                win.x, win.y, 0.0F, 0.0F, win.w, win.h);
        // 深色覆盖：menu_background 仅 25% 黑，叠一层 50% 黑提高可读性
        graphics.fill(win.x, win.y, win.x + win.w, win.y + win.h, 0x80000000);
        // 经典原版容器斜面边框
        drawContainerBorder(graphics, win.x, win.y, win.w, win.h);
        // 标题文字 + 标题栏底部原版分隔线（2px）
        graphics.text(this.font, title, win.x + CONTENT_MARGIN, win.y + 4, COLOR_TITLE, true);
        graphics.blit(RenderPipelines.GUI_TEXTURED, HEADER_SEPARATOR,
                win.x, win.y + TITLE_BAR_HEIGHT - 2, 0.0F, 0.0F, win.w, 2, 32, 2);
        renderMaxButton(graphics, win, mouseX, mouseY);

        // 经验池行（仅主职业窗显示；副窗显示类型说明）
        String poolText;
        int poolColor;
        if (type == IProfession.ProfessionType.PRIMARY) {
            int pool = state != null ? state.pool() : 0;
            poolText = "可分配职业经验: " + pool;
            poolColor = pool > 0 ? COLOR_TITLE : COLOR_HINT;
        } else {
            poolText = "（副职业独立成树，双击激活/取消，加成共存）";
            poolColor = COLOR_HINT;
        }
        graphics.text(this.font, poolText, win.x + CONTENT_MARGIN,
                win.y + TITLE_BAR_HEIGHT + 2, poolColor, false);

        // 计算该窗口内该类型节点的横向布局（逻辑坐标，origin = 窗内容区起点）
        Map<Identifier, int[]> pos = computeLayoutForType(state, type,
                win.contentOriginX(), win.contentOriginY());

        // scissor 裁剪窗内内容区（标题栏+经验池行下方到窗口底部）
        int scissorY0 = win.contentOriginY() - 4;
        graphics.enableScissor(win.x, scissorY0, win.x + win.w, win.y + win.h);
        // pose 平移窗内画布
        graphics.pose().pushMatrix();
        graphics.pose().translate(win.panX, win.panY);

        // 横向连接线（仅该类型、且父子均在该窗口内）
        for (ProfessionNode n : state != null ? state.nodes() : java.util.Collections.<ProfessionNode>emptyList()) {
            if (n.type() != type || n.prerequisite() == null) continue;
            int[] parent = pos.get(n.prerequisite());
            int[] child = pos.get(n.id());
            if (parent == null || child == null) continue;
            boolean unlocked = state.unlocked().contains(n.id());
            int color = unlocked ? COLOR_LINE_UNLOCKED : COLOR_LINE_LOCKED;
            int x1 = parent[0] + NODE_SIZE;
            int y1 = parent[1] + NODE_SIZE / 2;
            int x2 = child[0];
            int y2 = child[1] + NODE_SIZE / 2;
            int midX = (x1 + x2) / 2;
            graphics.fill(x1, y1, midX + 1, y1 + 1, color);
            graphics.fill(midX, Math.min(y1, y2), midX + 1, Math.max(y1, y2) + 1, color);
            graphics.fill(midX, y2, x2 + 1, y2 + 1, color);
        }
        // 节点（仅该类型）+ 节点下 + 按钮
        ProfessionNode hoveredNode = null;
        if (state != null) {
            for (ProfessionNode n : state.nodes()) {
                if (n.type() != type) continue;
                int[] p = pos.get(n.id());
                if (p == null) continue;
                boolean isHovered = renderNode(graphics, state, n, p[0], p[1], win, mouseX, mouseY);
                if (isHovered) hoveredNode = n;
            }
        } else {
            // state 未到，显示加载提示
            graphics.text(this.font, "正在加载...", win.contentOriginX() + CONTENT_MARGIN,
                    win.contentOriginY(), COLOR_HINT, false);
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        // 悬停气泡：在 scissor 结束后触发，让 tooltip 由 vanilla 在顶层渲染（不被窗内裁剪）
        if (hoveredNode != null && state != null) {
            graphics.setComponentTooltipForNextFrame(this.font,
                    buildProfessionTooltip(hoveredNode, state), mouseX, mouseY);
        }
    }

    // --------------------------------------------------------------------
    // 横向树布局（按类型分组，单窗口）
    // --------------------------------------------------------------------

    /**
     * 计算指定类型职业在该窗口内的横向布局。
     * 返回 节点ID → 左上角逻辑坐标(x,y)（相对于窗口内容区起点，渲染时叠加 pose 平移）。
     */
    private Map<Identifier, int[]> computeLayoutForType(ProfessionStateView state,
                                                        IProfession.ProfessionType type,
                                                        int originX, int originY) {
        Map<Identifier, int[]> pos = new LinkedHashMap<>();
        if (state == null) return pos;
        // 构建该类型的父子关系
        List<ProfessionNode> roots = new ArrayList<>();
        Map<Identifier, List<ProfessionNode>> children = new HashMap<>();
        for (ProfessionNode n : state.nodes()) {
            if (n.type() == type) children.put(n.id(), new ArrayList<>());
        }
        for (ProfessionNode n : state.nodes()) {
            if (n.type() != type) continue;
            if (n.prerequisite() == null) {
                roots.add(n);
            } else {
                children.computeIfAbsent(n.prerequisite(), k -> new ArrayList<>()).add(n);
            }
        }
        if (roots.isEmpty()) return pos;
        // 统计叶子总数 → 决定 Y 跨度
        int leafCount = 0;
        for (ProfessionNode root : roots) leafCount += countLeaves(root, children);
        int sectionHeight = Math.max(1, leafCount) * SIBLING_GAP_Y;
        int yTop = originY;
        int yBottom = originY + sectionHeight;
        // 多根并排，各占其叶子数对应的 Y 段
        int cursor = yTop;
        for (ProfessionNode root : roots) {
            int leaves = Math.max(1, countLeaves(root, children));
            int segH = leaves * SIBLING_GAP_Y;
            layoutSubtreeHorizontal(root, children, originX, cursor, cursor + segH, 0, pos);
            cursor += segH;
        }
        return pos;
    }

    /** 递归横向布局子树：节点中心 Y = (yTop+yBottom)/2，X = originX + depth×LEVEL_GAP_X */
    private void layoutSubtreeHorizontal(ProfessionNode node,
                                         Map<Identifier, List<ProfessionNode>> children,
                                         int originX, int yTop, int yBottom, int depth,
                                         Map<Identifier, int[]> outPos) {
        int centerX = originX + CONTENT_MARGIN + depth * LEVEL_GAP_X;
        int centerY = (yTop + yBottom) / 2;
        outPos.put(node.id(), new int[]{centerX, centerY - NODE_SIZE / 2});
        List<ProfessionNode> kids = children.getOrDefault(node.id(), new ArrayList<>());
        if (kids.isEmpty()) return;
        int segH = (yBottom - yTop) / kids.size();
        for (int i = 0; i < kids.size(); i++) {
            int childYTop = yTop + i * segH;
            int childYBottom = (i == kids.size() - 1) ? yBottom : childYTop + segH;
            layoutSubtreeHorizontal(kids.get(i), children, originX, childYTop, childYBottom, depth + 1, outPos);
        }
    }

    /** 统计子树叶子数 */
    private int countLeaves(ProfessionNode node, Map<Identifier, List<ProfessionNode>> children) {
        List<ProfessionNode> kids = children.getOrDefault(node.id(), new ArrayList<>());
        if (kids.isEmpty()) return 1;
        int sum = 0;
        for (ProfessionNode k : kids) sum += countLeaves(k, children);
        return sum;
    }

    /**
     * 渲染单个方形节点 + 节点下 + 按钮。
     * <p>
     * <b>坐标系统</b>：本方法在 {@code pose().translate(panX, panY)} 上下文内调用，
     * 节点的逻辑坐标 {@code x/y} 已被 pose 平移。因此：
     * <ul>
     *   <li><b>渲染</b>（fill/text）必须用逻辑坐标 {@code x/y}，靠 pose 平移 —— 不能再加 panX/panY</li>
     *   <li><b>命中检测</b>用屏幕坐标 {@code sx/sy}（= x + panX），因为 mouseX/mouseY 是屏幕坐标</li>
     * </ul>
     *
     * @return 该节点当前是否处于鼠标悬停状态（用于触发气泡）
     */
    private boolean renderNode(GuiGraphicsExtractor graphics, ProfessionStateView state,
                               ProfessionNode node, int x, int y,
                               FloatingWindow win, int mouseX, int mouseY) {
        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondaryActive = state.activeSecondary().contains(node.id());
        // 屏幕坐标仅用于 hover 检测（mouseX/Y 是屏幕坐标）
        int sx = x + (int) win.panX;
        int sy = y + (int) win.panY;
        boolean hover = isHover(mouseX, mouseY, sx, sy, NODE_SIZE, NODE_SIZE);

        // 外框角标：激活副职业（蓝）优先于当前主职业（绿）—— 在节点四角绘制 L 形角标
        // （不使用填充色块，避免大面积遮挡相邻节点；主/副用不同颜色区分）
        if (isSecondaryActive) {
            drawCornerMarkers(graphics, x, y, NODE_SIZE, COLOR_SECONDARY_ACTIVE);
        } else if (isCurrent) {
            drawCornerMarkers(graphics, x, y, NODE_SIZE, COLOR_MAIN_CURRENT);
        }
        // 节点框：原版 advancement task_frame（26×26，1:1 无缩放）
        // obtained = 已解锁/当前/激活副职业（亮框）；unobtained = 未解锁（暗框）
        boolean obtained = unlocked || isCurrent || isSecondaryActive;
        Identifier frameSprite = obtained ? TASK_FRAME_OBTAINED : TASK_FRAME_UNOBTAINED;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frameSprite, x, y, NODE_SIZE, NODE_SIZE);
        // 图标：优先物品图标（fakeItem，原版 advancement 节点偏移 +8,+5），否则回退字符图标
        // 图标数据由服务端通过 ProfessionNode 推送（源自 IProfession.getIconItem / getIconChar）
        ItemStack itemIcon = node.iconItem();
        if (itemIcon != null && !itemIcon.isEmpty()) {
            graphics.fakeItem(itemIcon, x + NODE_ICON_ITEM_X, y + NODE_ICON_ITEM_Y);
        } else {
            String icon = node.iconChar() != null && !node.iconChar().isEmpty() ? node.iconChar() : "?";
            int iconColor = unlocked ? COLOR_TEXT : COLOR_HINT;
            graphics.text(this.font, icon, x + (NODE_SIZE - this.font.width(icon)) / 2,
                    y + ICON_TEXT_OFFSET_Y, iconColor, false);
        }

        // 节点下行：等级数字 + 升级按钮（同一行，避免上方徽章遮挡下一行节点）
        renderNodeFooter(graphics, x, y, sx, sy, node, state, mouseX, mouseY);
        return hover;
    }

    /**
     * 节点正下方一行：显示等级 + 升级按钮。
     * <p>
     * 取代旧的「节点上方徽章 + 节点下方按钮」分离布局 —— 同一行避免上方徽章遮挡上一节点。
     * <ul>
     *   <li><b>可投入时</b>：一个加宽到节点宽度的按钮，内显「Lv.N +」（等级与升级同按钮，避免互相遮挡）</li>
     *   <li><b>满级时</b>：仅显示「Lv.MAX」文本（无按钮）</li>
     *   <li>仅已解锁职业显示；未解锁无此行</li>
     * </ul>
     * 渲染用逻辑坐标 {@code nodeLx/nodeLy}（靠 pose 平移）；命中检测用屏幕坐标 {@code nodeSx/nodeSy}。
     */
    private void renderNodeFooter(GuiGraphicsExtractor graphics,
                                  int nodeLx, int nodeLy, int nodeSx, int nodeSy,
                                  ProfessionNode node, ProfessionStateView state,
                                  int mouseX, int mouseY) {
        boolean unlocked = state.unlocked().contains(node.id());
        if (!unlocked) return;
        int level = state.levels().getOrDefault(node.id(), 0);
        int maxLevel = node.maxLevel();
        boolean atMax = level >= maxLevel;

        int rowY = nodeLy + NODE_SIZE + PLUS_BUTTON_GAP;        // 逻辑行 Y
        int rowSY = nodeSy + NODE_SIZE + PLUS_BUTTON_GAP;       // 屏幕行 Y

        if (!atMax) {
            // 可升级：加宽按钮显示「Lv.N +」，按钮水平居中于节点下方
            // canInvest 时高亮可点；经验不足时禁用态（button_disabled），仍显示等级
            boolean canInvest = canInvest(state, node);
            int btnW = PLUS_BUTTON_WIDTH;
            // 按钮左上角：水平居中于节点（节点中心 = nodeLx + NODE_SIZE/2）
            int lx = nodeLx + (NODE_SIZE - btnW) / 2;
            int hx = nodeSx + (NODE_SIZE - btnW) / 2;
            boolean hover = canInvest && isHover(mouseX, mouseY, hx, rowSY, btnW, PLUS_BUTTON_SIZE);
            Identifier sprite = !canInvest ? BUTTON_DISABLED_SPRITE
                    : (hover ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, lx, rowY, btnW, PLUS_BUTTON_SIZE, -1);
            // 文本「Lv.N +」整体水平居中于按钮
            String label = "Lv." + level + " +";
            graphics.text(this.font, label, lx + (btnW - this.font.width(label)) / 2,
                    rowY + centeredGlyphY(PLUS_BUTTON_SIZE),
                    canInvest ? COLOR_TEXT : COLOR_HINT, false);
        } else {
            // 满级：仅文本「Lv.MAX」，水平居中于节点
            String label = "Lv.MAX";
            graphics.text(this.font, label, nodeLx + (NODE_SIZE - this.font.width(label)) / 2,
                    rowY + centeredGlyphY(PLUS_BUTTON_SIZE), COLOR_TITLE, false);
        }
    }

    /** 标题栏右上角最大化/还原按钮（□ 未最大化 / ⊟ 已最大化）—— 原版按钮精灵 */
    private void renderMaxButton(GuiGraphicsExtractor graphics, FloatingWindow win, int mouseX, int mouseY) {
        int bx = win.maxButtonX();
        int by = win.maxButtonY();
        boolean hover = isHover(mouseX, mouseY, bx, by, MAX_BUTTON_SIZE, MAX_BUTTON_SIZE);
        blitButton(graphics, bx, by, MAX_BUTTON_SIZE, MAX_BUTTON_SIZE, hover);
        String glyph = win.maximized ? "⊟" : "□";
        graphics.text(this.font, glyph, bx + (MAX_BUTTON_SIZE - this.font.width(glyph)) / 2,
                by + centeredGlyphY(MAX_BUTTON_SIZE), COLOR_TEXT, false);
    }

    /** 绘制原版 9-slice 按钮：hover 时用高亮态精灵 */
    private void blitButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean hover) {
        Identifier sprite = hover ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, -1);
    }

    /**
     * 原版文字垂直居中公式：textY 偏移（相对矩形顶部）。
     * <p>
     * 原版用 {@code (top + bottom - lineHeight) / 2 + 1}，lineHeight = 9。
     * 简化为 {@code (height - 9) / 2 + 1}。
     */
    private static int centeredGlyphY(int height) {
        return (height - 9) / 2 + 1;
    }

    /**
     * 在矩形（节点）四个角绘制 L 形角标，用于标识当前主职业/已激活副职业。
     * <p>
     * 替代旧的整圈填充色块 —— 角标更轻量，不会大面积遮挡相邻节点。
     * 每个角的拐角点位于节点外缘 {@link #CORNER_OFFSET} 处，两条短臂向节点方向（向内）延伸
     * {@link #CORNER_ARM} 长，构成开口朝向节点中心的 L 形，整体呈"取景框"效果：
     * <pre>
     *    ┘                └
     *     ┐            ┌
     *       节点矩形
     *     └            ┘
     *    ┐                ┌
     * </pre>
     * 注：上图拐角在外、臂向内（实际臂较长时拐角几乎贴节点四角）。
     *
     * @param x     节点左上角逻辑 X
     * @param y     节点左上角逻辑 Y
     * @param size  节点边长
     * @param color 角标颜色（ARGB）
     */
    private static void drawCornerMarkers(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
        int off = CORNER_OFFSET;
        int arm = CORNER_ARM;
        int thick = CORNER_THICK;
        // 每个角的拐角点位于节点外缘 `off` 处；水平段和垂直段均从拐角点向节点方向（向内）延伸 `arm` 长
        // 左上角（拐角在节点左上方外缘，臂向右、向下伸向节点）
        graphics.fill(x - off - thick, y - off - thick,         x - off + arm,             y - off,             color); // 水平段（向右）
        graphics.fill(x - off - thick, y - off - thick,         x - off,                   y - off + arm,        color); // 垂直段（向下）
        // 右上角（拐角在节点右上方外缘，臂向左、向下）
        graphics.fill(x + size + off - arm, y - off - thick,    x + size + off + thick,    y - off,              color);
        graphics.fill(x + size + off,       y - off - thick,    x + size + off + thick,    y - off + arm,        color);
        // 左下角（拐角在节点左下方外缘，臂向右、向上）
        graphics.fill(x - off - thick,      y + size + off,     x - off + arm,             y + size + off + thick, color);
        graphics.fill(x - off - thick,      y + size + off - arm, x - off,                 y + size + off + thick, color);
        // 右下角（拐角在节点右下方外缘，臂向左、向上）
        graphics.fill(x + size + off - arm, y + size + off,     x + size + off + thick,    y + size + off + thick, color);
        graphics.fill(x + size + off,       y + size + off - arm, x + size + off + thick,  y + size + off + thick, color);
    }

    /** 节点下升级按钮的屏幕矩形（水平居中于节点，命中检测用） */
    private int[] plusButtonScreenRect(int nodeSx, int nodeSy) {
        int bx = nodeSx + (NODE_SIZE - PLUS_BUTTON_WIDTH) / 2;
        int by = nodeSy + NODE_SIZE + PLUS_BUTTON_GAP;
        return new int[]{bx, by, PLUS_BUTTON_WIDTH, PLUS_BUTTON_SIZE};
    }

    // --------------------------------------------------------------------
    // 悬停气泡构造
    // --------------------------------------------------------------------

    /**
     * 构造节点悬停气泡的文本行（参考 {@code AttributeListPlugin.getTooltip} 的多行拼装风格）。
     * 行序：名称（白） / 类型标签（灰） / 状态标签（灰/绿/蓝） / 描述（灰） /
     * 等级（白） / 下一级消耗（灰/黄） / 属性加成（灰标题+绿/红数值） / 自定义 tooltip
     */
    private List<Component> buildProfessionTooltip(ProfessionNode node, ProfessionStateView state) {
        List<Component> lines = new ArrayList<>();
        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondaryActive = state.activeSecondary().contains(node.id());
        int level = state.levels().getOrDefault(node.id(), 0);
        int maxLevel = node.maxLevel();

        lines.add(Component.literal(node.displayName()).withStyle(s -> s.withColor(0xFFFFFF)));
        String typeLabel = node.type() == IProfession.ProfessionType.SECONDARY ? "[副职业]" : "[主职业]";
        lines.add(Component.literal(typeLabel).withStyle(s -> s.withColor(0xAAAAAA)));

        String status;
        int statusColor;
        if (isCurrent) { status = "[当前主职业]"; statusColor = 0x55FF55; }
        else if (isSecondaryActive) { status = "[副职业·已激活]"; statusColor = 0x3A7BFF; }
        else if (unlocked) { status = "[已解锁]"; statusColor = 0x55FF55; }
        else { status = "[未解锁]"; statusColor = 0xAAAAAA; }
        final int statusColorFinal = statusColor;
        lines.add(Component.literal(status).withStyle(s -> s.withColor(statusColorFinal)));

        // 描述可能含换行，按行拆分
        String desc = node.description();
        if (desc != null && !desc.isEmpty()) {
            for (String line : desc.split("\n")) {
                lines.add(Component.literal(line).withStyle(s -> s.withColor(0xAAAAAA)));
            }
        }

        if (unlocked) {
            lines.add(Component.literal("等级: " + level + " / " + maxLevel)
                    .withStyle(s -> s.withColor(0xFFFFFF)));
            if (level < maxLevel) {
                int cost = costForNextLevel(level, maxLevel);
                boolean canAfford = state.pool() >= cost;
                String costText = "下一级: " + cost + (canAfford ? " (可投入)" : " (不足)");
                lines.add(Component.literal(costText).withStyle(s -> s.withColor(canAfford ? 0xFFFF00 : 0xAAAAAA)));
            } else {
                lines.add(Component.literal("已达满级").withStyle(s -> s.withColor(0xFFFF00)));
            }
        } else if (node.type() == IProfession.ProfessionType.SECONDARY) {
            // 未解锁副职业：显示解锁条件
            int cost = SECONDARY_UNLOCK_COST;
            boolean canAfford = state.pool() >= cost;
            boolean prereqOk = true;
            String prereqHint = "";
            if (node.prerequisite() != null) {
                ProfessionNode prereqNode = findNode(state, node.prerequisite());
                String prereqName = prereqNode != null ? prereqNode.displayName() : node.prerequisite().toString();
                int prereqMax = prereqNode != null ? prereqNode.maxLevel() : 20;
                int prereqLvl = state.levels().getOrDefault(node.prerequisite(), 0);
                if (!state.unlocked().contains(node.prerequisite())) {
                    prereqOk = false;
                    prereqHint = "需先解锁前置: " + prereqName;
                } else if (prereqLvl < prereqMax) {
                    prereqOk = false;
                    prereqHint = "前置 " + prereqName + " 需达满级 (" + prereqLvl + "/" + prereqMax + ")";
                }
            }
            if (!prereqOk) {
                lines.add(Component.literal(prereqHint).withStyle(s -> s.withColor(0xFF8080)));
            }
            String costText = "解锁消耗: " + cost + (canAfford ? " 经验" : " 经验 (不足)");
            lines.add(Component.literal(costText).withStyle(s -> s.withColor(canAfford ? 0xFFFF00 : 0xAAAAAA)));
            if (canUnlockSecondary(state, node)) {
                lines.add(Component.literal("双击解锁").withStyle(s -> s.withColor(0x55FF55)));
            }
        }

        // 属性加成详情 + 职业类自定义 tooltip 行（通过 IProfession 接口拿到）
        try {
            com.rpgcraft.core.registry.IProfessionSystem sys = com.rpgcraft.core.registry.RPGSystems.getProfessionSystem();
            if (sys != null) {
                IProfession prof = sys.getProfessionById(node.id());
                if (prof != null) {
                    // —— 属性加成展示 ——
                    // 已解锁：按当前等级计算实际加成；未解锁：按 1 级展示基础加成（让玩家预览职业特性）
                    appendBonusLines(lines, prof, unlocked ? Math.max(1, level) : 1);
                    // —— 自定义 tooltip ——
                    com.rpgcraft.core.profession.api.ProfessionTooltipContext ctx =
                            new com.rpgcraft.core.profession.api.ProfessionTooltipContext(
                                    level, maxLevel, unlocked, isCurrent, isSecondaryActive);
                    List<Component> custom = prof.getTooltip(ctx);
                    if (custom != null && !custom.isEmpty()) {
                        lines.addAll(custom);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 客户端 fallback：tooltip 扩展失败不应影响面板渲染
        }
        return lines;
    }

    /**
     * 向 tooltip 追加职业属性加成详情行。
     * <p>
     * 展示规则：
     * <ul>
     *   <li>标题行「属性加成:」（灰色小标签）</li>
     *   <li>每条加成一行：{@code 属性名: ±当前值 (+M/级)}
     *       —— 正值绿色、负值红色、每级增量灰色（增量为 0 时省略）</li>
     *   <li>空加成职业：显示「无属性加成」（灰色）</li>
     * </ul>
     * 数值按 {@link IProfession#getBonusAtLevel} 计算（已计入基础值 + 每级增量）。
     *
     * @param lines   待追加的 tooltip 行列表
     * @param prof    职业实例
     * @param atLevel 用于计算加成的等级（已解锁用当前等级，未解锁用 1）
     */
    private void appendBonusLines(List<Component> lines, IProfession prof, int atLevel) {
        java.util.Map<Identifier, Integer> baseBonus = prof.getBaseBonusMap();
        if (baseBonus == null || baseBonus.isEmpty()) {
            lines.add(Component.literal("无属性加成").withStyle(s -> s.withColor(0xAAAAAA)));
            return;
        }
        lines.add(Component.literal("属性加成:").withStyle(s -> s.withColor(0xAAAAAA)));
        com.rpgcraft.core.attribute.api.IAttributeRegistry attrReg =
                com.rpgcraft.core.attribute.AttributeManager.getRegistry();
        for (java.util.Map.Entry<Identifier, Integer> entry : baseBonus.entrySet()) {
            Identifier attrId = entry.getKey();
            // 属性显示名：优先从注册中心取，失败回退到 ID path
            String attrName;
            com.rpgcraft.core.attribute.api.IAttributeEntry attrEntry = attrReg.getEntry(attrId);
            if (attrEntry != null) {
                attrName = attrEntry.getDisplayName();
            } else {
                String path = attrId.getPath();
                attrName = path.substring(0, 1).toUpperCase() + path.substring(1);
            }
            int currentBonus = prof.getBonusAtLevel(attrId, atLevel);
            int perLevel = prof.getBonusPerLevel(attrId);
            // 数值颜色：正绿/负红/0 灰
            int valueColor = currentBonus > 0 ? 0x55FF55 : currentBonus < 0 ? 0xFF8080 : 0xAAAAAA;
            String sign = currentBonus >= 0 ? "+" : "";
            StringBuilder sb = new StringBuilder("  ").append(attrName).append(": ")
                    .append(sign).append(currentBonus);
            if (perLevel != 0) {
                String perSign = perLevel >= 0 ? "+" : "";
                sb.append(" (").append(perSign).append(perLevel).append("/级)");
            }
            lines.add(Component.literal(sb.toString()).withStyle(s -> s.withColor(valueColor)));
        }
    }

    // ====================================================================
    // 交互
    // ====================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isPick) {
        if (event.button() != 0) return super.mouseClicked(event, isPick);
        double mx = event.x();
        double my = event.y();
        ProfessionStateView state = ProfessionStateCache.get();
        long now = System.currentTimeMillis();

        // 可见窗列表（顺序 = 处理优先级）：
        // 任一窗最大化 → 只剩该最大化窗；否则两个窗都在（主窗在前）
        for (FloatingWindow win : visibleWindows()) {
            IProfession.ProfessionType type = (win == mainWindow)
                    ? IProfession.ProfessionType.PRIMARY
                    : IProfession.ProfessionType.SECONDARY;

            // 1. 最大化/还原按钮（标题栏右上角）
            if (isHover(mx, my, win.maxButtonX(), win.maxButtonY(), MAX_BUTTON_SIZE, MAX_BUTTON_SIZE)) {
                toggleMaximize(win);
                return true;
            }
            // 2. + 按钮（投入一级）
            if (state != null) {
                Identifier investId = hitPlusButtonInWindow(state, win, type, mx, my);
                if (investId != null) {
                    sendAction("invest", investId);
                    return true;
                }
            }
            // 3. 节点点击（单/双击判定）
            if (state != null) {
                Identifier hit = hitNodeInWindow(state, win, type, mx, my);
                if (hit != null) {
                    handleNodeClick(hit, state, now);
                    return true;
                }
            }
            // 4. 标题栏拖动
            if (win.isInTitleBar(mx, my)) {
                startDragWindow(win, mx, my);
                return true;
            }
            // 5. 窗内空白 → 拖动画布
            if (win.contains(mx, my)) {
                startDragCanvas(win);
                return true;
            }
        }
        return super.mouseClicked(event, isPick);
    }

    /**
     * 当前可见、应响应点击的窗口列表（已按优先级排序）。
     * <p>
     * 任一窗最大化 → 只返回该最大化窗；否则返回 [主窗, 副窗]。
     */
    private List<FloatingWindow> visibleWindows() {
        List<FloatingWindow> out = new ArrayList<>(2);
        if (mainWindow != null && mainWindow.maximized) {
            out.add(mainWindow);
            return out;
        }
        if (secondaryWindow != null && secondaryWindow.maximized) {
            out.add(secondaryWindow);
            return out;
        }
        if (mainWindow != null) out.add(mainWindow);
        if (secondaryWindow != null) out.add(secondaryWindow);
        return out;
    }

    /**
     * 处理节点点击：单/双击判定。
     * <p>
     * 同一节点在 {@link #DOUBLE_CLICK_MS} 内的第二次点击 = 双击 → 触发操作；
     * 否则仅记录单击时间，等待可能的第二次点击。
     */
    private void handleNodeClick(Identifier nodeId, ProfessionStateView state, long now) {
        boolean isDouble = lastClickNodeId != null
                && nodeId.equals(lastClickNodeId)
                && (now - lastClickTime) < DOUBLE_CLICK_MS
                && !lastClickWasDouble;
        if (isDouble) {
            lastClickWasDouble = true;
            handleDoubleClick(nodeId, state);
        } else {
            lastClickWasDouble = false;
        }
        lastClickNodeId = nodeId;
        lastClickTime = now;
    }

    /** 双击节点 → 按节点类型/状态 dispatch 操作 */
    private void handleDoubleClick(Identifier nodeId, ProfessionStateView state) {
        ProfessionNode node = findNode(state, nodeId);
        if (node == null) return;
        boolean unlocked = state.unlocked().contains(nodeId);
        boolean isCurrent = nodeId.equals(state.currentMain());

        if (node.type() == IProfession.ProfessionType.PRIMARY) {
            if (!unlocked) {
                // 可进阶、未解锁 → 弹确认框
                if (canAdvanceFrom(state, node)) {
                    openAdvanceConfirm(node);
                }
            } else if (!isCurrent) {
                // 已解锁、非当前 → 直接切换为主职业
                sendAction("switch_main", nodeId);
            }
            // 已解锁且是当前 → 无操作
        } else {
            // 副职业：未解锁 → 可解锁则弹确认框；已解锁 → 切换激活状态
            if (!unlocked) {
                if (canUnlockSecondary(state, node)) {
                    openUnlockSecondaryConfirm(node);
                }
            } else {
                sendAction("toggle_secondary", nodeId);
            }
        }
    }

    /** 打开副职业解锁确认对话框 */
    private void openUnlockSecondaryConfirm(ProfessionNode node) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = this;
        ConfirmScreen confirm = new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        sendAction("unlock_secondary", node.id());
                    }
                    mc.setScreen(parent);
                },
                Component.literal("解锁 " + node.displayName() + "?"),
                Component.literal("将消耗 " + SECONDARY_UNLOCK_COST + " 职业经验解锁此副职业"),
                Component.literal("解锁"),
                Component.literal("取消")
        );
        mc.setScreen(confirm);
    }

    /** 打开进阶确认对话框 */
    private void openAdvanceConfirm(ProfessionNode node) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = this;
        ConfirmScreen confirm = new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        sendAction("advance", node.id());
                    }
                    mc.setScreen(parent);
                },
                Component.literal("进阶到 " + node.displayName() + "?"),
                Component.literal("进阶后将切换当前主职业，此操作不可撤销"),
                Component.literal("进阶"),
                Component.literal("取消")
        );
        mc.setScreen(confirm);
    }

    private void toggleMaximize(FloatingWindow win) {
        win.maximized = !win.maximized;
        // 互斥：最大化一个窗时取消另一个的最大化
        if (win.maximized) {
            if (win == mainWindow && secondaryWindow != null) secondaryWindow.maximized = false;
            if (win == secondaryWindow && mainWindow != null) mainWindow.maximized = false;
        }
        applyMaximizedLayout();
    }

    private void startDragWindow(FloatingWindow win, double mx, double my) {
        win.draggingWindow = true;
        win.draggingCanvas = false;
        win.dragStartMouseX = mx;
        win.dragStartMouseY = my;
        win.dragStartWinX = win.x;
        win.dragStartWinY = win.y;
    }

    private void startDragCanvas(FloatingWindow win) {
        win.draggingCanvas = true;
        win.draggingWindow = false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0) return super.mouseDragged(event, dx, dy);
        boolean handled = false;
        if (mainWindow != null && mainWindow.draggingWindow) {
            mainWindow.x = clamp(mainWindow.dragStartWinX + (int) (event.x() - mainWindow.dragStartMouseX),
                    0, Math.max(0, this.width - mainWindow.w));
            mainWindow.y = clamp(mainWindow.dragStartWinY + (int) (event.y() - mainWindow.dragStartMouseY),
                    0, Math.max(0, this.height - mainWindow.h));
            handled = true;
        }
        if (mainWindow != null && mainWindow.draggingCanvas) {
            mainWindow.panX += (float) dx;
            mainWindow.panY += (float) dy;
            handled = true;
        }
        if (secondaryWindow != null && secondaryWindow.draggingWindow) {
            secondaryWindow.x = clamp(secondaryWindow.dragStartWinX + (int) (event.x() - secondaryWindow.dragStartMouseX),
                    0, Math.max(0, this.width - secondaryWindow.w));
            secondaryWindow.y = clamp(secondaryWindow.dragStartWinY + (int) (event.y() - secondaryWindow.dragStartMouseY),
                    0, Math.max(0, this.height - secondaryWindow.h));
            handled = true;
        }
        if (secondaryWindow != null && secondaryWindow.draggingCanvas) {
            secondaryWindow.panX += (float) dx;
            secondaryWindow.panY += (float) dy;
            handled = true;
        }
        return handled || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (mainWindow != null) {
                mainWindow.draggingWindow = false;
                mainWindow.draggingCanvas = false;
            }
            if (secondaryWindow != null) {
                secondaryWindow.draggingWindow = false;
                secondaryWindow.draggingCanvas = false;
            }
        }
        return super.mouseReleased(event);
    }

    /** 命中检测某窗口内某类型的节点。鼠标坐标需减去该窗口 panX/panY 转逻辑坐标 */
    private Identifier hitNodeInWindow(ProfessionStateView state, FloatingWindow win,
                                       IProfession.ProfessionType type, double mx, double my) {
        Map<Identifier, int[]> pos = computeLayoutForType(state, type,
                win.contentOriginX(), win.contentOriginY());
        double lx = mx - win.panX;
        double ly = my - win.panY;
        for (Map.Entry<Identifier, int[]> e : pos.entrySet()) {
            int[] p = e.getValue();
            if (lx >= p[0] && lx < p[0] + NODE_SIZE && ly >= p[1] && ly < p[1] + NODE_SIZE) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * 命中某窗口内节点下方的升级按钮，返回该节点 ID。
     * <p>
     * 按钮宽度 = 节点宽度，命中矩形覆盖整个按钮行。仅 {@code canInvest} 的节点会触发投入
     *（经验不足时按钮显示禁用态但不响应点击）。
     */
    private Identifier hitPlusButtonInWindow(ProfessionStateView state, FloatingWindow win,
                                             IProfession.ProfessionType type, double mx, double my) {
        Map<Identifier, int[]> pos = computeLayoutForType(state, type,
                win.contentOriginX(), win.contentOriginY());
        for (Map.Entry<Identifier, int[]> e : pos.entrySet()) {
            ProfessionNode node = findNode(state, e.getKey());
            if (node == null || !canInvest(state, node)) continue;
            // 节点屏幕坐标 = 逻辑坐标 + panX/panY
            int nodeSx = e.getValue()[0] + (int) win.panX;
            int nodeSy = e.getValue()[1] + (int) win.panY;
            int[] r = plusButtonScreenRect(nodeSx, nodeSy);
            if (isHover(mx, my, r[0], r[1], r[2], r[3])) {
                return e.getKey();
            }
        }
        return null;
    }

    private void sendAction(String action, Identifier professionId) {
        ProfessionActionPacket.Action act = switch (action) {
            case "invest" -> ProfessionActionPacket.Action.INVEST;
            case "advance" -> ProfessionActionPacket.Action.ADVANCE;
            case "switch_main" -> ProfessionActionPacket.Action.SWITCH_MAIN;
            case "toggle_secondary" -> ProfessionActionPacket.Action.TOGGLE_SECONDARY;
            case "unlock_secondary" -> ProfessionActionPacket.Action.UNLOCK_SECONDARY;
            default -> null;
        };
        if (act == null) return;
        Minecraft.getInstance().getConnection().send(new ProfessionActionPacket(act, professionId));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_P) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --------------------------------------------------------------------
    // 打开时居中：以当前主职业节点落在主窗可见区中心
    // --------------------------------------------------------------------

    private void centerOnCurrentMain(ProfessionStateView state) {
        if (mainWindow == null) return;
        Identifier mainId = state.currentMain();
        if (mainId == null) return;
        Map<Identifier, int[]> pos = computeLayoutForType(state, IProfession.ProfessionType.PRIMARY,
                mainWindow.contentOriginX(), mainWindow.contentOriginY());
        int[] p = pos.get(mainId);
        if (p == null) return;
        int nodeCenterX = p[0] + NODE_SIZE / 2;
        int nodeCenterY = p[1] + NODE_SIZE / 2;
        int visibleCenterX = mainWindow.x + mainWindow.w / 2;
        int visibleCenterY = mainWindow.contentOriginY() + (mainWindow.h - TITLE_BAR_HEIGHT - POOL_ROW_HEIGHT) / 2;
        mainWindow.panX = visibleCenterX - nodeCenterX;
        mainWindow.panY = visibleCenterY - nodeCenterY;
    }

    // ====================================================================
    // 业务判定（与服务端校验镜像，仅用于 UI 灰显/显隐）
    // ====================================================================

    /** 节点是否可投入一级（已解锁、未满级、池足够） */
    private boolean canInvest(ProfessionStateView state, ProfessionNode node) {
        if (!state.unlocked().contains(node.id())) return false;
        int level = state.levels().getOrDefault(node.id(), 0);
        if (level < 1 || level >= node.maxLevel()) return false;
        return state.pool() >= costForNextLevel(level, node.maxLevel());
    }

    /** 升下一级所需经验（与服务端公式镜像，仅用于 UI 显示）。委托 {@link ExpFormula} 统一公式 */
    private static int costForNextLevel(int level, int maxLevel) {
        if (level < 1 || level >= maxLevel) return Integer.MAX_VALUE;
        return com.rpgcraft.core.level.ExpFormula.expForNextLevel(level);
    }

    private boolean canAdvanceFrom(ProfessionStateView state, ProfessionNode node) {
        if (node.prerequisite() == null) return false;
        if (state.unlocked().contains(node.id())) return false;
        if (!state.unlocked().contains(node.prerequisite())) return false;
        // 前置职业需达到其自身满级（每职业 maxLevel 可不同，取前置节点的 maxLevel）
        ProfessionNode prereqNode = findNode(state, node.prerequisite());
        int prereqMax = prereqNode != null ? prereqNode.maxLevel() : 20;
        return state.levels().getOrDefault(node.prerequisite(), 0) >= prereqMax;
    }

    /**
     * 副职业节点是否可解锁（与服务端 {@code canUnlockSecondary} 镜像，仅用于 UI 显示/交互）。
     * <p>
     * 规则：
     * <ul>
     *   <li>必须是 SECONDARY 类型且未解锁</li>
     *   <li>基础副职业（prerequisite=null）：池 ≥ 解锁消耗</li>
     *   <li>非基础副职业：前置须已解锁且达其满级，池 ≥ 解锁消耗</li>
     * </ul>
     */
    private boolean canUnlockSecondary(ProfessionStateView state, ProfessionNode node) {
        if (node.type() != IProfession.ProfessionType.SECONDARY) return false;
        if (state.unlocked().contains(node.id())) return false;
        if (state.pool() < SECONDARY_UNLOCK_COST) return false;
        if (node.prerequisite() != null) {
            if (!state.unlocked().contains(node.prerequisite())) return false;
            ProfessionNode prereqNode = findNode(state, node.prerequisite());
            int prereqMax = prereqNode != null ? prereqNode.maxLevel() : 20;
            if (state.levels().getOrDefault(node.prerequisite(), 0) < prereqMax) return false;
        }
        return true;
    }

    private ProfessionNode findNode(ProfessionStateView state, Identifier id) {
        for (ProfessionNode n : state.nodes()) {
            if (n.id().equals(id)) return n;
        }
        return null;
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private static Identifier id(String ns, String path) {
        return Identifier.fromNamespaceAndPath(ns, path);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean isHover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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
