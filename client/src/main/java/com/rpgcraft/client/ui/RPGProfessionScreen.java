package com.rpgcraft.client.ui;

import com.rpgcraft.core.network.ProfessionActionPacket;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.ui.ProfessionStateCache;
import com.rpgcraft.core.ui.ProfessionStateCache.ProfessionNode;
import com.rpgcraft.core.ui.ProfessionStateCache.ProfessionStateView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * 按快捷键 P（默认）打开。布局由<b>三个独立区域</b>组成：
 * <ul>
 *   <li><b>主职业窗</b>（可拖动浮窗）：横向显示主职业树（PRIMARY，根在左、子向右）</li>
 *   <li><b>副职业窗</b>（可拖动浮窗）：横向显示副职业树（SECONDARY，独立成树）</li>
 *   <li><b>详情区</b>（固定右侧）：显示选中职业的等级、经验、加成与操作按钮</li>
 * </ul>
 * 交互：
 * <ul>
 *   <li>拖动窗口<b>标题栏</b> → 移动该窗口位置（主/副窗各自独立）</li>
 *   <li>拖动窗口<b>内空白</b> → 平移该窗口内的画布（查看超出窗口的树节点）</li>
 *   <li>点击节点 → 选中（详情区显示）；详情按钮触发服务端权威操作</li>
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
    /** 详情面板宽度 */
    private static final int DETAIL_PANEL_WIDTH = 150;
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

    /** 详情面板内部行高相关 */
    private static final int TITLE_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 14;

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
    /** 节点选中高亮 */
    private static final int COLOR_NODE_SELECTED = 0xFF666688;
    /** 连接线 */
    private static final int COLOR_LINE_UNLOCKED = 0xFF888888;
    private static final int COLOR_LINE_LOCKED = 0xFF444444;
    /** 当前主职业金色框 */
    private static final int COLOR_GOLD = 0xFFFFD700;
    /** 按钮 */
    private static final int COLOR_BUTTON = 0xFF5555AA;
    private static final int COLOR_BUTTON_HOVER = 0xFFFFFF00;
    private static final int COLOR_BUTTON_DISABLED = 0xFF555555;

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

    /** 当前选中的职业节点 ID（null 表示未选中；主/副窗共享此详情） */
    private Identifier selectedNodeId = null;

    // ----------------------------------------------------------------
    // 两个可拖动浮窗
    // ----------------------------------------------------------------
    /** 主职业窗（显示 PRIMARY 树） */
    private FloatingWindow mainWindow;
    /** 副职业窗（显示 SECONDARY 树） */
    private FloatingWindow secondaryWindow;
    /** 详情区矩形（固定右侧） */
    private int detailX, detailY, detailW, detailH;
    /** 是否已完成首次初始化（窗口尺寸/位置 + 居中） */
    private boolean inited = false;

    /**
     * 一个可拖动浮窗的状态：窗口屏幕矩形 + 窗内画布平移 + 两级拖动模式。
     * <p>
     * 两级拖动靠鼠标按下位置区分：
     * <ul>
     *   <li>按在标题栏 → {@link #draggingWindow}，移动窗口 x/y</li>
     *   <li>按在窗内空白 → {@link #draggingCanvas}，平移 panX/panY</li>
     * </ul>
     * 同一时刻至多一个 dragging 标志为 true。
     */
    private static final class FloatingWindow {
        int x, y, w, h;
        float panX, panY;
        boolean draggingWindow;
        double dragStartMouseX, dragStartMouseY;
        int dragStartWinX, dragStartWinY;
        boolean draggingCanvas;

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
    }

    public RPGProfessionScreen() {
        super(Component.literal("职业"));
    }

    @Override
    public void removed() {
        mainWindow = null;
        secondaryWindow = null;
        inited = false;
        selectedNodeId = null;
        ProfessionStateCache.clear();
        super.removed();
    }

    // ====================================================================
    // 初始化窗口尺寸与位置（每次打开首次渲染时执行一次）
    // ====================================================================

    /**
     * 计算并设置两个浮窗的初始矩形 + 详情区矩形。
     * <p>
     * 布局：左侧上下两个树窗（主上、副下），右侧详情区。
     * <pre>
     * ┌─ 主职业 ──────┐  ┌─ 详情 ─┐
     * │              │  │        │
     * └──────────────┘  │        │
     * ┌─ 副职业 ──────┐  │        │
     * │              │  │        │
     * └──────────────┘  └────────┘
     * </pre>
     */
    private void initWindows() {
        int availWidth = this.width - 2 * MARGIN;
        int availHeight = this.height - TOP_PADDING - BOTTOM_PADDING;
        // 详情区宽度固定，树窗占剩余宽度（上限 TREE_WIN_MAX_WIDTH）
        int detailW = DETAIL_PANEL_WIDTH;
        // 树窗区域总宽 = 可用宽 - 详情宽 - GAP
        int treeAreaW = Math.max(TREE_WIN_MIN_WIDTH, availWidth - detailW - GAP);
        int treeW = Math.min(TREE_WIN_MAX_WIDTH, treeAreaW);
        // 树窗与详情并排：树窗左侧、详情右侧
        int treeX = MARGIN;
        detailX = this.width - MARGIN - detailW;
        detailY = TOP_PADDING;
        detailH = availHeight;
        this.detailW = detailW;
        this.detailH = detailH;
        // 两个树窗上下排列：主窗占上 55%、副窗占下 40%，留 GAP
        int treeAreaH = availHeight;
        int gap2 = GAP * 2; // 两个窗之间的间距
        int mainH = (treeAreaH - gap2) * 55 / 100;
        int secH = (treeAreaH - gap2) - mainH;
        int mainY = TOP_PADDING;
        int secY = mainY + mainH + gap2;
        mainWindow = new FloatingWindow(treeX, mainY, treeW, mainH);
        secondaryWindow = new FloatingWindow(treeX, secY, treeW, secH);
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
            // 首次拿到 state 后居中（若此时 state 还没到，会在下方 state!=null 分支里补做）
        }

        // 渲染主职业窗
        renderTreeWindow(graphics, state, mainWindow, "主职业",
                IProfession.ProfessionType.PRIMARY, mouseX, mouseY);
        // 渲染副职业窗
        renderTreeWindow(graphics, state, secondaryWindow, "副职业",
                IProfession.ProfessionType.SECONDARY, mouseX, mouseY);

        // 首次拿到非空 state 时，以当前主职业居中
        if (state != null && !centeredOnMain) {
            centerOnCurrentMain(state);
            centeredOnMain = true;
        }

        // 右侧详情区
        renderDetailPanel(graphics, state, detailX, detailY, detailH, mouseX, mouseY);
    }

    /** 标记：是否已完成"以当前主职业居中"（避免每次刷新跳回） */
    private boolean centeredOnMain = false;

    /**
     * 渲染一个职业树窗口：标题栏（可拖动） + 经验池行 + 窗内画布（scissor + pose 平移 + 横向树）。
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
        // 标题栏（略亮背景 + 标题文字 + 拖动提示）
        fillRounded(graphics, win.x, win.y, win.w, TITLE_BAR_HEIGHT, CORNER_RADIUS, COLOR_TITLE_BAR);
        graphics.text(this.font, title, win.x + CONTENT_MARGIN, win.y + 4, COLOR_TITLE, true);
        graphics.text(this.font, "≡", win.x + win.w - CONTENT_MARGIN - this.font.width("≡"),
                win.y + 4, COLOR_HINT, false);

        // 经验池行（仅主职业窗显示；副窗显示类型说明）
        String poolText;
        int poolColor;
        if (type == IProfession.ProfessionType.PRIMARY) {
            int pool = state != null ? state.pool() : 0;
            poolText = "可分配职业经验: " + pool;
            poolColor = pool > 0 ? COLOR_TITLE : COLOR_HINT;
        } else {
            poolText = "（副职业独立成树，可设为副职业）";
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
        // 节点（仅该类型）
        if (state != null) {
            for (ProfessionNode n : state.nodes()) {
                if (n.type() != type) continue;
                int[] p = pos.get(n.id());
                if (p == null) continue;
                renderNode(graphics, state, n, p[0], p[1], win, mouseX, mouseY);
            }
        } else {
            // state 未到，显示加载提示
            graphics.text(this.font, "正在加载...", win.contentOriginX() + CONTENT_MARGIN,
                    win.contentOriginY(), COLOR_HINT, false);
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();
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

    /** 渲染单个方形节点。hover 检测需用窗口的 panX/panY 把节点逻辑坐标转屏幕坐标 */
    private void renderNode(GuiGraphicsExtractor graphics, ProfessionStateView state,
                            ProfessionNode node, int x, int y,
                            FloatingWindow win, int mouseX, int mouseY) {
        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        boolean selected = node.id().equals(selectedNodeId);
        // 节点 x/y 是逻辑坐标（已被 pose 平移），命中检测需加 panX/panY
        boolean hover = isHover(mouseX, mouseY,
                x + (int) win.panX, y + (int) win.panY, NODE_SIZE, NODE_SIZE);

        if (isCurrent) {
            graphics.fill(x - GOLD_BORDER_PAD - 1, y - GOLD_BORDER_PAD - 1,
                    x + NODE_SIZE + GOLD_BORDER_PAD + 1, y + NODE_SIZE + GOLD_BORDER_PAD + 1, COLOR_GOLD);
        }
        int color;
        if (isSecondary) color = COLOR_NODE_SECONDARY;
        else if (unlocked) color = COLOR_NODE_UNLOCKED;
        else color = COLOR_NODE_LOCKED;
        graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, color);
        int frame = selected ? COLOR_NODE_SELECTED : (hover ? COLOR_HINT : COLOR_BORDER);
        graphics.fill(x, y, x + NODE_SIZE, y + 1, frame);
        graphics.fill(x, y + NODE_SIZE - 1, x + NODE_SIZE, y + NODE_SIZE, frame);
        graphics.fill(x, y, x + 1, y + NODE_SIZE, frame);
        graphics.fill(x + NODE_SIZE - 1, y, x + NODE_SIZE, y + NODE_SIZE, frame);
        String icon = NODE_ICONS.getOrDefault(node.id(), "?");
        int iconColor = unlocked ? COLOR_TEXT : COLOR_HINT;
        graphics.text(this.font, icon, x + (NODE_SIZE - this.font.width(icon)) / 2,
                y + ICON_TEXT_OFFSET_Y, iconColor, false);
    }

    // --------------------------------------------------------------------
    // 右侧详情区（固定，沿用旧实现）
    // --------------------------------------------------------------------

    private void renderDetailPanel(GuiGraphicsExtractor graphics, ProfessionStateView state,
                                   int x, int y, int panelHeight, int mouseX, int mouseY) {
        fillRounded(graphics, x - 1, y - 1, detailW + 2, panelHeight + 2, CORNER_RADIUS + 1, COLOR_BORDER);
        fillRounded(graphics, x, y, detailW, panelHeight, CORNER_RADIUS, COLOR_BG);
        drawCentered(graphics, "职业详情", x, detailW, y + 6, COLOR_TITLE, true);

        if (state == null) {
            graphics.text(this.font, "正在加载...", x + CONTENT_MARGIN, y + TITLE_HEIGHT, COLOR_HINT, false);
            return;
        }
        if (selectedNodeId == null) {
            graphics.text(this.font, "点击节点选择职业", x + CONTENT_MARGIN, y + TITLE_HEIGHT, COLOR_HINT, false);
            return;
        }
        ProfessionNode node = findNode(state, selectedNodeId);
        if (node == null) return;

        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        int level = state.levels().getOrDefault(node.id(), 0);
        int maxLevel = node.maxLevel(); // 来自服务端 per-profession getMaxLevel()，不再硬编码 20
        int contentW = detailW - 2 * CONTENT_MARGIN;

        int cy = y + TITLE_HEIGHT + 4;
        graphics.text(this.font, node.displayName(), x + CONTENT_MARGIN, cy, COLOR_TEXT, true);
        cy += 12;
        String typeLabel = node.type() == IProfession.ProfessionType.SECONDARY ? "[副职业]" : "[主职业]";
        graphics.text(this.font, typeLabel, x + CONTENT_MARGIN, cy, COLOR_HINT, false);
        cy += 11;
        String status;
        if (isCurrent) status = "[当前主职业]";
        else if (isSecondary) status = "[副职业" + (state.secondaryActive() ? "·已激活]" : "]");
        else if (unlocked) status = "[已解锁]";
        else status = "[未解锁]";
        graphics.text(this.font, status, x + CONTENT_MARGIN, cy, COLOR_HINT, false);
        cy += 11;
        graphics.text(this.font, node.description(), x + CONTENT_MARGIN, cy, COLOR_HINT, false);
        cy += 13;
        graphics.fill(x + CONTENT_MARGIN, cy, x + detailW - CONTENT_MARGIN, cy + 1, COLOR_BORDER);
        cy += 4;

        if (unlocked) {
            graphics.text(this.font, "等级: " + level + " / " + maxLevel, x + CONTENT_MARGIN, cy, COLOR_TEXT, false);
            cy += 12;
            if (level < maxLevel) {
                int cost = costForNextLevel(level, maxLevel);
                boolean canAfford = state.pool() >= cost;
                String costText = "下一级: " + cost + (canAfford ? " (可投入)" : " (不足)");
                graphics.text(this.font, costText, x + CONTENT_MARGIN, cy,
                        canAfford ? COLOR_TITLE : COLOR_HINT, false);
                cy += 12;
            } else {
                graphics.text(this.font, "已达满级", x + CONTENT_MARGIN, cy, COLOR_TITLE, false);
                cy += 12;
            }
            cy += 3;
        }
        int btnX = x + CONTENT_MARGIN;
        renderActionButtons(graphics, state, node, btnX, cy, contentW, mouseX, mouseY);
    }

    private void renderActionButtons(GuiGraphicsExtractor graphics, ProfessionStateView state,
                                     ProfessionNode node, int x, int y, int w, int mouseX, int mouseY) {
        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        int level = state.levels().getOrDefault(node.id(), 0);
        boolean isPrimaryType = node.type() == IProfession.ProfessionType.PRIMARY;
        boolean isSecondaryType = node.type() == IProfession.ProfessionType.SECONDARY;
        int maxLevel = node.maxLevel();

        boolean canInvest = unlocked && level < maxLevel && state.pool() >= costForNextLevel(level, maxLevel);
        boolean canAdvance = isPrimaryType && canAdvanceFrom(state, node);
        boolean canSwitchMain = isPrimaryType && !isCurrent && unlocked;
        boolean canSetSecondary = isSecondaryType && unlocked && !isSecondary;
        boolean hasSecondary = state.currentSecondary() != null;

        y += renderButton(graphics, mouseX, mouseY, x, y, w, "投入一级", canInvest);
        y += 2;
        y += renderButton(graphics, mouseX, mouseY, x, y, w, "进阶到此职业", canAdvance);
        y += 2;
        y += renderButton(graphics, mouseX, mouseY, x, y, w, "切换为主职业", canSwitchMain);
        y += 2;
        y += renderButton(graphics, mouseX, mouseY, x, y, w, "设为副职业", canSetSecondary);
        y += 2;
        String toggleLabel = state.secondaryActive() ? "关闭副职业加成" : "开启副职业加成";
        renderButton(graphics, mouseX, mouseY, x, y, w, toggleLabel, hasSecondary);
    }

    private int renderButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                             int x, int y, int w, String label, boolean enabled) {
        boolean hover = enabled && isHover(mouseX, mouseY, x, y, w, BUTTON_HEIGHT);
        int color = !enabled ? COLOR_BUTTON_DISABLED : (hover ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.fill(x, y, x + w, y + BUTTON_HEIGHT, color);
        graphics.text(this.font, label, x + (w - this.font.width(label)) / 2,
                y + 3, enabled ? COLOR_TEXT : COLOR_HINT, false);
        return BUTTON_HEIGHT;
    }

    // ====================================================================
    // 交互（两级拖动 + 节点选中 + 详情按钮）
    // ====================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isPick) {
        if (event.button() != 0) return super.mouseClicked(event, isPick);
        double mx = event.x();
        double my = event.y();
        ProfessionStateView state = ProfessionStateCache.get();

        // 优先级：主窗标题栏 → 副窗标题栏 → 详情按钮 → 节点 → 窗内空白(拖画布)
        // 1. 主窗标题栏（拖动窗口）
        if (mainWindow != null && mainWindow.isInTitleBar(mx, my)) {
            startDragWindow(mainWindow, mx, my);
            return true;
        }
        // 2. 副窗标题栏
        if (secondaryWindow != null && secondaryWindow.isInTitleBar(mx, my)) {
            startDragWindow(secondaryWindow, mx, my);
            return true;
        }
        // 3. 详情按钮
        if (state != null && selectedNodeId != null
                && mx >= detailX && mx < detailX + detailW) {
            ProfessionNode node = findNode(state, selectedNodeId);
            if (node != null) {
                String action = hitDetailButton(state, node, detailX, detailY, mx, my);
                if (action != null) {
                    sendAction(action, node.id());
                    return true;
                }
            }
        }
        // 4. 节点命中（先主窗后副窗）
        if (state != null && mainWindow != null) {
            Identifier hit = hitNodeInWindow(state, mainWindow,
                    IProfession.ProfessionType.PRIMARY, mx, my);
            if (hit != null) { selectedNodeId = hit; return true; }
        }
        if (state != null && secondaryWindow != null) {
            Identifier hit = hitNodeInWindow(state, secondaryWindow,
                    IProfession.ProfessionType.SECONDARY, mx, my);
            if (hit != null) { selectedNodeId = hit; return true; }
        }
        // 5. 窗内空白（拖动画布）：落在窗内但非标题栏、非节点
        if (mainWindow != null && mainWindow.contains(mx, my)) {
            startDragCanvas(mainWindow);
            return true;
        }
        if (secondaryWindow != null && secondaryWindow.contains(mx, my)) {
            startDragCanvas(secondaryWindow);
            return true;
        }
        return super.mouseClicked(event, isPick);
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

    /** 命中详情按钮，返回 action 名或 null */
    private String hitDetailButton(ProfessionStateView state, ProfessionNode node,
                                   int x, int y, double mx, double my) {
        int btnX = x + CONTENT_MARGIN;
        int btnW = detailW - 2 * CONTENT_MARGIN;
        int cy = y + TITLE_HEIGHT + 4 + 12 + 11 + 11 + 13 + 4;
        boolean unlocked = state.unlocked().contains(node.id());
        int level = state.levels().getOrDefault(node.id(), 0);
        int maxLevel = node.maxLevel();
        if (unlocked) {
            // 等级行(12) + 下一级消耗/已达满级行(12) + 间距(3)
            cy += 12 + 12 + 3;
        }
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isPrimaryType = node.type() == IProfession.ProfessionType.PRIMARY;
        boolean isSecondaryType = node.type() == IProfession.ProfessionType.SECONDARY;
        boolean canInvest = unlocked && level < maxLevel && state.pool() >= costForNextLevel(level, maxLevel);
        boolean canAdvance = isPrimaryType && canAdvanceFrom(state, node);
        boolean canSwitchMain = isPrimaryType && !isCurrent && unlocked;
        boolean canSetSecondary = isSecondaryType && unlocked && !node.id().equals(state.currentSecondary());
        boolean hasSecondary = state.currentSecondary() != null;
        String[] actions = {"invest", "advance", "switch_main", "set_secondary", "toggle_secondary"};
        boolean[] enabled = {canInvest, canAdvance, canSwitchMain, canSetSecondary, hasSecondary};
        for (int i = 0; i < actions.length; i++) {
            if (enabled[i] && isHover(mx, my, btnX, cy, btnW, BUTTON_HEIGHT)) {
                return actions[i];
            }
            cy += BUTTON_HEIGHT + 2;
        }
        return null;
    }

    private void sendAction(String action, Identifier professionId) {
        ProfessionActionPacket.Action act = switch (action) {
            case "invest" -> ProfessionActionPacket.Action.INVEST;
            case "advance" -> ProfessionActionPacket.Action.ADVANCE;
            case "switch_main" -> ProfessionActionPacket.Action.SWITCH_MAIN;
            case "set_secondary" -> ProfessionActionPacket.Action.SET_SECONDARY;
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
    // 业务判定（与服务端校验镜像，仅用于 UI 灰显）
    // ====================================================================

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

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int panelX, int panelWidth,
                              int y, int color, boolean shadow) {
        graphics.text(this.font, text, panelX + (panelWidth - this.font.width(text)) / 2, y, color, shadow);
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
