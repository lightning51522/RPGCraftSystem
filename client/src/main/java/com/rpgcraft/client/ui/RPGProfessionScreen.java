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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
    private static final int CORNER_RADIUS = 3;

    /** 窗口标题栏高度（可拖动区域） */
    private static final int TITLE_BAR_HEIGHT = 16;
    /** 窗口内顶部经验池行高 */
    private static final int POOL_ROW_HEIGHT = 14;

    /** 方形节点尺寸 */
    private static final int NODE_SIZE = 26;
    /** 节点图标字符 Y 偏移 */
    private static final int ICON_TEXT_OFFSET_Y = 9;
    /** 横向树：层级间距（X 方向，父→子） */
    private static final int LEVEL_GAP_X = 90;
    /** 横向树：同层兄弟节点间距（Y 方向） */
    private static final int SIBLING_GAP_Y = 40;
    /** 当前主职业金色框的额外外扩（每边） */
    private static final int GOLD_BORDER_PAD = 2;
    /** 激活副职业蓝色框的额外外扩（每边，与金色一致） */
    private static final int SECONDARY_BORDER_PAD = 2;

    /** 节点下方 +（投入一级）按钮尺寸 */
    private static final int PLUS_BUTTON_SIZE = 12;
    /** 节点下 + 按钮与节点的垂直间距 */
    private static final int PLUS_BUTTON_GAP = 2;
    /** 标题栏右上角最大化按钮尺寸 */
    private static final int MAX_BUTTON_SIZE = 12;
    /** 节点上方等级徽章高度 */
    private static final int BADGE_HEIGHT = 9;
    /** 等级徽章文字 Y 偏移（相对徽章顶部） */
    private static final int BADGE_TEXT_OFFSET_Y = 1;
    /** 等级徽章左右内边距 */
    private static final int BADGE_PADDING_X = 2;

    /** 双击判定时间窗口（毫秒） */
    private static final long DOUBLE_CLICK_MS = 300;

    // ====================================================================
    // 颜色（ARGB）
    // ====================================================================

    private static final int COLOR_BG = 0xC0000000;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_TITLE = 0xFFFFFF00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFAAAAAA;
    /** 标题栏背景（略亮于窗口体） */
    private static final int COLOR_TITLE_BAR = 0xFF333355;
    /** 节点底色：已解锁 */
    private static final int COLOR_NODE_UNLOCKED = 0xFF2F4F2F;
    /** 节点底色：未解锁 */
    private static final int COLOR_NODE_LOCKED = 0xFF3A2A2A;
    /** 节点底色：副职业 */
    private static final int COLOR_NODE_SECONDARY = 0xFF4A2A4A;
    /** 连接线 */
    private static final int COLOR_LINE_UNLOCKED = 0xFF888888;
    private static final int COLOR_LINE_LOCKED = 0xFF444444;
    /** 当前主职业金色框 */
    private static final int COLOR_GOLD = 0xFFFFD700;
    /** 激活副职业蓝色框 */
    private static final int COLOR_SECONDARY_ACTIVE = 0xFF3A7BFF;
    /** 按钮 */
    private static final int COLOR_BUTTON = 0xFF5555AA;
    private static final int COLOR_BUTTON_HOVER = 0xFFFFFF00;

    /** 每个职业的图标字符 */
    private static final Map<Identifier, String> NODE_ICONS = new HashMap<>();

    static {
        NODE_ICONS.put(id("rpgcraftcore", "commoner"), "民");
        NODE_ICONS.put(id("rpgcraftcore", "warrior"), "战");
        NODE_ICONS.put(id("rpgcraftcore", "berserker"), "狂");
        NODE_ICONS.put(id("rpgcraftcore", "archer"), "弓");
        NODE_ICONS.put(id("rpgcraftcore", "marksman"), "神");
        NODE_ICONS.put(id("rpgcraftcore", "apprentice"), "徒");
    }

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
        ProfessionStateCache.clear();
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
        // 窗口背景
        fillRounded(graphics, win.x - 1, win.y - 1, win.w + 2, win.h + 2, CORNER_RADIUS + 1, COLOR_BORDER);
        fillRounded(graphics, win.x, win.y, win.w, win.h, CORNER_RADIUS, COLOR_BG);
        // 标题栏（略亮背景 + 标题文字 + 最大化按钮）
        fillRounded(graphics, win.x, win.y, win.w, TITLE_BAR_HEIGHT, CORNER_RADIUS, COLOR_TITLE_BAR);
        graphics.text(this.font, title, win.x + CONTENT_MARGIN, win.y + 4, COLOR_TITLE, true);
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

        // 外框：激活副职业（蓝）优先于当前主职业（金）—— 渲染用逻辑坐标 x/y
        if (isSecondaryActive) {
            graphics.fill(x - SECONDARY_BORDER_PAD - 1, y - SECONDARY_BORDER_PAD - 1,
                    x + NODE_SIZE + SECONDARY_BORDER_PAD + 1, y + NODE_SIZE + SECONDARY_BORDER_PAD + 1,
                    COLOR_SECONDARY_ACTIVE);
        } else if (isCurrent) {
            graphics.fill(x - GOLD_BORDER_PAD - 1, y - GOLD_BORDER_PAD - 1,
                    x + NODE_SIZE + GOLD_BORDER_PAD + 1, y + NODE_SIZE + GOLD_BORDER_PAD + 1, COLOR_GOLD);
        }
        int color;
        if (node.type() == IProfession.ProfessionType.SECONDARY) color = COLOR_NODE_SECONDARY;
        else if (unlocked) color = COLOR_NODE_UNLOCKED;
        else color = COLOR_NODE_LOCKED;
        graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, color);
        int frame = hover ? COLOR_TITLE : COLOR_BORDER;
        graphics.fill(x, y, x + NODE_SIZE, y + 1, frame);
        graphics.fill(x, y + NODE_SIZE - 1, x + NODE_SIZE, y + NODE_SIZE, frame);
        graphics.fill(x, y, x + 1, y + NODE_SIZE, frame);
        graphics.fill(x + NODE_SIZE - 1, y, x + NODE_SIZE, y + NODE_SIZE, frame);
        String icon = NODE_ICONS.getOrDefault(node.id(), "?");
        int iconColor = unlocked ? COLOR_TEXT : COLOR_HINT;
        graphics.text(this.font, icon, x + (NODE_SIZE - this.font.width(icon)) / 2,
                y + ICON_TEXT_OFFSET_Y, iconColor, false);

        // 节点上方等级徽章：仅已解锁职业显示当前等级
        if (unlocked) {
            renderLevelBadge(graphics, x, y, node, state);
        }

        // 节点下 + 按钮：仅在 canInvest 时显示
        // 渲染用逻辑坐标（节点下方），hover 检测用屏幕坐标
        if (canInvest(state, node)) {
            renderPlusButton(graphics, x, y, sx, sy, mouseX, mouseY);
        }
        return hover;
    }

    /**
     * 在节点正上方绘制等级徽章（蓝底白字数字）。
     * <p>
     * 渲染用逻辑坐标 {@code nodeX/nodeY}（与节点本体一致，靠 pose 平移）。
     * 徽章底部紧贴节点顶边，水平居中于节点。
     */
    private void renderLevelBadge(GuiGraphicsExtractor graphics, int nodeX, int nodeY,
                                  ProfessionNode node, ProfessionStateView state) {
        int level = state.levels().getOrDefault(node.id(), 0);
        String text = String.valueOf(level);
        int badgeW = this.font.width(text) + 2 * BADGE_PADDING_X;
        int badgeX = nodeX + (NODE_SIZE - badgeW) / 2;
        int badgeY = nodeY - BADGE_HEIGHT;
        graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + BADGE_HEIGHT, COLOR_BUTTON);
        graphics.text(this.font, text, badgeX + (badgeW - this.font.width(text)) / 2,
                badgeY + BADGE_TEXT_OFFSET_Y, COLOR_TEXT, false);
    }

    /**
     * 节点下方居中的小 + 按钮（投入一级）。
     *
     * @param nodeLx  节点逻辑坐标 X（渲染用，靠 pose 平移）
     * @param nodeLy  节点逻辑坐标 Y（渲染用）
     * @param nodeSx  节点屏幕坐标 X（hover 检测用）
     * @param nodeSy  节点屏幕坐标 Y（hover 检测用）
     */
    private void renderPlusButton(GuiGraphicsExtractor graphics, int nodeLx, int nodeLy,
                                  int nodeSx, int nodeSy, int mouseX, int mouseY) {
        // 渲染矩形（逻辑坐标）
        int lx = nodeLx + (NODE_SIZE - PLUS_BUTTON_SIZE) / 2;
        int ly = nodeLy + NODE_SIZE + PLUS_BUTTON_GAP;
        // 命中矩形（屏幕坐标）
        int hx = nodeSx + (NODE_SIZE - PLUS_BUTTON_SIZE) / 2;
        int hy = nodeSy + NODE_SIZE + PLUS_BUTTON_GAP;
        boolean hover = isHover(mouseX, mouseY, hx, hy, PLUS_BUTTON_SIZE, PLUS_BUTTON_SIZE);
        int color = hover ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
        graphics.fill(lx, ly, lx + PLUS_BUTTON_SIZE, ly + PLUS_BUTTON_SIZE, color);
        graphics.text(this.font, "+", lx + (PLUS_BUTTON_SIZE - this.font.width("+")) / 2,
                ly + 1, COLOR_TEXT, false);
    }

    /** 标题栏右上角最大化/还原按钮（□ 未最大化 / ⊟ 已最大化） */
    private void renderMaxButton(GuiGraphicsExtractor graphics, FloatingWindow win, int mouseX, int mouseY) {
        int bx = win.maxButtonX();
        int by = win.maxButtonY();
        boolean hover = isHover(mouseX, mouseY, bx, by, MAX_BUTTON_SIZE, MAX_BUTTON_SIZE);
        int color = hover ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
        graphics.fill(bx, by, bx + MAX_BUTTON_SIZE, by + MAX_BUTTON_SIZE, color);
        String glyph = win.maximized ? "⊟" : "□";
        graphics.text(this.font, glyph, bx + (MAX_BUTTON_SIZE - this.font.width(glyph)) / 2,
                by + 1, COLOR_TEXT, false);
    }

    /** 节点下 + 按钮的屏幕矩形（命中检测用） */
    private int[] plusButtonScreenRect(int nodeSx, int nodeSy) {
        int bx = nodeSx + (NODE_SIZE - PLUS_BUTTON_SIZE) / 2;
        int by = nodeSy + NODE_SIZE + PLUS_BUTTON_GAP;
        return new int[]{bx, by, PLUS_BUTTON_SIZE, PLUS_BUTTON_SIZE};
    }

    // --------------------------------------------------------------------
    // 悬停气泡构造
    // --------------------------------------------------------------------

    /**
     * 构造节点悬停气泡的文本行（参考 {@code AttributeListPlugin.getTooltip} 的多行拼装风格）。
     * 行序：名称（白） / 类型标签（灰） / 状态标签（灰） / 描述（灰） / 等级（白） / 下一级消耗（灰/黄）
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
        if (isCurrent) { status = "[当前主职业]"; statusColor = 0xFFD700; }
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
        }
        return lines;
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
            // 副职业：已解锁 → 切换激活状态
            if (unlocked) {
                sendAction("toggle_secondary", nodeId);
            }
        }
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
     * 命中某窗口内节点下方的 + 按钮，返回该节点 ID（仅 canInvest 的节点才画 + 按钮）。
     * 鼠标坐标需减去该窗口 panX/panY 转逻辑坐标，再比较 + 按钮屏幕矩形。
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

    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        g.fill(x + 1, y + 1, x + r, y + r, color);
        g.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        g.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        g.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
    }
}
