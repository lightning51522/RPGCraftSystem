package com.rpgcraft.client.ui;

import com.rpgcraft.core.network.ProfessionActionPacket;
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
import java.util.List;
import java.util.Map;

/**
 * 职业面板（独立 Screen）
 * <p>
 * 按快捷键 P（默认）打开。布局参考原版进度系统（advancements）风格：
 * <ul>
 *   <li>左侧为职业树画布，每个职业用一个<b>小方形节点</b>表示（内含单字符图标占位，
 *       后续替换为真实图标），父子职业之间用连接线相连</li>
 *   <li>当前主职业节点外围绘制<b>金色边框</b>高亮</li>
 *   <li>右侧为详情副界面，显示选中职业的等级、经验、加成与操作按钮</li>
 *   <li>顶部显示可分配职业经验池</li>
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

    private static final int TREE_PANEL_WIDTH = 230;
    private static final int DETAIL_PANEL_WIDTH = 190;
    private static final int PANEL_GAP = 8;
    private static final int TOP_PADDING = 20;
    private static final int BOTTOM_PADDING = 10;
    private static final int TITLE_HEIGHT = 20;
    private static final int CONTENT_MARGIN = 6;
    private static final int CORNER_RADIUS = 3;

    /** 原版进度系统风格：方形节点尺寸 */
    private static final int NODE_SIZE = 26;
    /** 节点图标字符（后续替换为真实图标） */
    private static final int ICON_TEXT_OFFSET_Y = 9;
    /** 节点层级间距（父子节点 Y 距离） */
    private static final int TIER_GAP_Y = 56;
    /** 同层节点最小水平间距 */
    private static final int SIBLING_GAP_X = 70;
    /** 当前主职业金色框的额外外扩（每边） */
    private static final int GOLD_BORDER_PAD = 2;

    // 详情面板
    private static final int BUTTON_HEIGHT = 14;

    // ====================================================================
    // 颜色（ARGB）
    // ====================================================================

    private static final int COLOR_BG = 0xC0000000;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_TITLE = 0xFFFFFF00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFFAAAAAA;
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

    /** 每个职业的图标字符（后续替换为真实图标资源） */
    private static final Map<Identifier, String> NODE_ICONS = new HashMap<>();

    static {
        NODE_ICONS.put(id("rpgcraftcore", "commoner"), "民");
        NODE_ICONS.put(id("rpgcraftcore", "warrior"), "战");
        NODE_ICONS.put(id("rpgcraftcore", "berserker"), "狂");
        NODE_ICONS.put(id("rpgcraftcore", "archer"), "弓");
        NODE_ICONS.put(id("rpgcraftcore", "marksman"), "神");
    }

    /** 当前选中的职业节点 ID（null 表示未选中） */
    private Identifier selectedNodeId = null;

    public RPGProfessionScreen() {
        super(Component.literal("职业"));
    }

    // ====================================================================
    // 渲染
    // ====================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ProfessionStateView state = ProfessionStateCache.get();
        int totalWidth = TREE_PANEL_WIDTH + PANEL_GAP + DETAIL_PANEL_WIDTH;
        int treeX = (this.width - totalWidth) / 2;
        int panelY = TOP_PADDING;
        int panelHeight = this.height - TOP_PADDING - BOTTOM_PADDING;
        int detailX = treeX + TREE_PANEL_WIDTH + PANEL_GAP;

        // 左侧树面板背景
        fillRounded(graphics, treeX - 1, panelY - 1, TREE_PANEL_WIDTH + 2, panelHeight + 2, CORNER_RADIUS + 1, COLOR_BORDER);
        fillRounded(graphics, treeX, panelY, TREE_PANEL_WIDTH, panelHeight, CORNER_RADIUS, COLOR_BG);

        // 标题
        drawCentered(graphics, "职业树", treeX, TREE_PANEL_WIDTH, panelY + 6, COLOR_TITLE, true);

        // 顶部经验池
        int pool = state != null ? state.pool() : 0;
        String poolText = "可分配职业经验: " + pool;
        graphics.text(this.font, poolText, treeX + CONTENT_MARGIN, panelY + TITLE_HEIGHT,
                pool > 0 ? COLOR_TITLE : COLOR_HINT, false);

        if (state == null) {
            renderCenteredText(graphics, "正在加载...", treeX, panelY + TITLE_HEIGHT * 2,
                    panelHeight - TITLE_HEIGHT * 2, TREE_PANEL_WIDTH);
            renderDetailPanel(graphics, state, detailX, panelY, panelHeight, mouseX, mouseY);
            return;
        }

        // 渲染职业树（方形节点 + 连接线）
        renderTree(graphics, state, treeX, panelY + TITLE_HEIGHT * 2, mouseX, mouseY);

        // 右侧详情副界面
        renderDetailPanel(graphics, state, detailX, panelY, panelHeight, mouseX, mouseY);
    }

    // --------------------------------------------------------------------
    // 职业树渲染
    // --------------------------------------------------------------------

    /** 计算树布局：按 prerequisite 构建层级（根/一级/二级...），每层节点水平等距居中分布。返回 节点ID → 左上角(x,y)。 */
    private Map<Identifier, int[]> computeLayout(ProfessionStateView state, int originX, int originY) {
        // 按 prerequisite 构建层级（BFS）
        List<ProfessionNode> roots = new ArrayList<>();
        Map<Identifier, List<ProfessionNode>> children = new HashMap<>();
        for (ProfessionNode n : state.nodes()) children.put(n.id(), new ArrayList<>());
        for (ProfessionNode n : state.nodes()) {
            if (n.prerequisite() == null) {
                roots.add(n);
            } else {
                children.computeIfAbsent(n.prerequisite(), k -> new ArrayList<>()).add(n);
            }
        }
        // 分层（保留注册顺序，使同层节点顺序稳定）
        List<List<ProfessionNode>> tiers = new ArrayList<>();
        tiers.add(roots);
        List<ProfessionNode> current = roots;
        while (!current.isEmpty()) {
            List<ProfessionNode> next = new ArrayList<>();
            for (ProfessionNode p : current) {
                next.addAll(children.getOrDefault(p.id(), new ArrayList<>()));
            }
            if (next.isEmpty()) break;
            tiers.add(next);
            current = next;
        }

        int treeContentWidth = TREE_PANEL_WIDTH - 2 * CONTENT_MARGIN;
        Map<Identifier, int[]> pos = new HashMap<>();
        for (int t = 0; t < tiers.size(); t++) {
            List<ProfessionNode> tier = tiers.get(t);
            int y = originY + t * TIER_GAP_Y;
            int slot = treeContentWidth / tier.size();
            for (int i = 0; i < tier.size(); i++) {
                int centerX = originX + CONTENT_MARGIN + slot * i + slot / 2;
                pos.put(tier.get(i).id(), new int[]{centerX - NODE_SIZE / 2, y});
            }
        }
        return pos;
    }

    /** 渲染整棵树：先画连接线，再画节点 */
    private void renderTree(GuiGraphicsExtractor graphics, ProfessionStateView state,
                            int originX, int originY, int mouseX, int mouseY) {
        Map<Identifier, int[]> pos = computeLayout(state, originX, originY);
        // 连接线（父底中 → 子顶中）
        for (ProfessionNode n : state.nodes()) {
            if (n.prerequisite() == null) continue;
            int[] parent = pos.get(n.prerequisite());
            int[] child = pos.get(n.id());
            if (parent == null || child == null) continue;
            boolean unlocked = state.unlocked().contains(n.id());
            int color = unlocked ? COLOR_LINE_UNLOCKED : COLOR_LINE_LOCKED;
            int x1 = parent[0] + NODE_SIZE / 2;
            int y1 = parent[1] + NODE_SIZE;
            int x2 = child[0] + NODE_SIZE / 2;
            int y2 = child[1];
            graphics.fill(x1, y1, x1 + 1, y2, color);     // 竖线（父正下方）
            graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color); // 横线
            graphics.fill(x2, y1, x2 + 1, y2, color);     // 竖线（到子）
        }
        // 节点
        for (ProfessionNode n : state.nodes()) {
            int[] p = pos.get(n.id());
            if (p == null) continue;
            renderNode(graphics, state, n, p[0], p[1], mouseX, mouseY);
        }
    }

    /** 渲染单个方形节点（含图标字符 + 状态边框） */
    private void renderNode(GuiGraphicsExtractor graphics, ProfessionStateView state,
                            ProfessionNode node, int x, int y, int mouseX, int mouseY) {
        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        boolean selected = node.id().equals(selectedNodeId);
        boolean hover = isHover(mouseX, mouseY, x, y, NODE_SIZE, NODE_SIZE);

        // 当前主职业：金色框（外扩）
        if (isCurrent) {
            graphics.fill(x - GOLD_BORDER_PAD - 1, y - GOLD_BORDER_PAD - 1,
                    x + NODE_SIZE + GOLD_BORDER_PAD + 1, y + NODE_SIZE + GOLD_BORDER_PAD + 1, COLOR_GOLD);
        }

        // 节点底色
        int color;
        if (isSecondary) color = COLOR_NODE_SECONDARY;
        else if (unlocked) color = COLOR_NODE_UNLOCKED;
        else color = COLOR_NODE_LOCKED;
        graphics.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, color);

        // 边框（选中 / 悬停）
        int frame = selected ? COLOR_NODE_SELECTED : (hover ? COLOR_HINT : COLOR_BORDER);
        graphics.fill(x, y, x + NODE_SIZE, y + 1, frame);                      // 上
        graphics.fill(x, y + NODE_SIZE - 1, x + NODE_SIZE, y + NODE_SIZE, frame); // 下
        graphics.fill(x, y, x + 1, y + NODE_SIZE, frame);                      // 左
        graphics.fill(x + NODE_SIZE - 1, y, x + NODE_SIZE, y + NODE_SIZE, frame); // 右

        // 图标字符（占位，后续替换为真实图标）
        String icon = NODE_ICONS.getOrDefault(node.id(), "?");
        int iconColor = unlocked ? COLOR_TEXT : COLOR_HINT;
        graphics.text(this.font, icon, x + (NODE_SIZE - this.font.width(icon)) / 2,
                y + ICON_TEXT_OFFSET_Y, iconColor, false);
    }

    // --------------------------------------------------------------------
    // 右侧详情副界面
    // --------------------------------------------------------------------

    private void renderDetailPanel(GuiGraphicsExtractor graphics, ProfessionStateView state,
                                   int x, int y, int panelHeight, int mouseX, int mouseY) {
        fillRounded(graphics, x - 1, y - 1, DETAIL_PANEL_WIDTH + 2, panelHeight + 2, CORNER_RADIUS + 1, COLOR_BORDER);
        fillRounded(graphics, x, y, DETAIL_PANEL_WIDTH, panelHeight, CORNER_RADIUS, COLOR_BG);

        drawCentered(graphics, "职业详情", x, DETAIL_PANEL_WIDTH, y + 6, COLOR_TITLE, true);

        if (state == null) {
            graphics.text(this.font, "正在加载...", x + CONTENT_MARGIN, y + TITLE_HEIGHT, COLOR_HINT, false);
            return;
        }
        if (selectedNodeId == null) {
            graphics.text(this.font, "点击左侧节点选择职业", x + CONTENT_MARGIN, y + TITLE_HEIGHT, COLOR_HINT, false);
            return;
        }
        ProfessionNode node = findNode(state, selectedNodeId);
        if (node == null) return;

        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        int level = state.levels().getOrDefault(node.id(), 0);
        int contentW = DETAIL_PANEL_WIDTH - 2 * CONTENT_MARGIN;

        int cy = y + TITLE_HEIGHT + 4;
        // 名称
        graphics.text(this.font, node.displayName(), x + CONTENT_MARGIN, cy, COLOR_TEXT, true);
        cy += 12;
        // 状态标签
        String status;
        if (isCurrent) status = "[当前主职业]";
        else if (isSecondary) status = "[副职业" + (state.secondaryActive() ? "·已激活]" : "]");
        else if (unlocked) status = "[已解锁]";
        else status = "[未解锁]";
        graphics.text(this.font, status, x + CONTENT_MARGIN, cy, COLOR_HINT, false);
        cy += 13;
        // 描述
        graphics.text(this.font, node.description(), x + CONTENT_MARGIN, cy, COLOR_HINT, false);
        cy += 13;
        // 分隔线
        graphics.fill(x + CONTENT_MARGIN, cy, x + DETAIL_PANEL_WIDTH - CONTENT_MARGIN, cy + 1, COLOR_BORDER);
        cy += 4;

        // 等级与经验
        if (unlocked) {
            graphics.text(this.font, "等级: " + level + " / 20", x + CONTENT_MARGIN, cy, COLOR_TEXT, false);
            cy += 12;
            if (level < 20) {
                int cost = costForNextLevel(level);
                boolean canAfford = state.pool() >= cost;
                String costText = "下一级消耗: " + cost + (canAfford ? " (可投入)" : " (经验不足)");
                graphics.text(this.font, costText, x + CONTENT_MARGIN, cy,
                        canAfford ? COLOR_TITLE : COLOR_HINT, false);
                cy += 12;
            } else {
                graphics.text(this.font, "已达满级", x + CONTENT_MARGIN, cy, COLOR_TITLE, false);
                cy += 12;
            }
            cy += 3;
        }

        // 操作按钮（垂直排列）
        int btnX = x + CONTENT_MARGIN;
        cy = renderActionButtons(graphics, state, node, btnX, cy, contentW, mouseX, mouseY);
    }

    /** 渲染操作按钮组，返回下一个可用 Y */
    private int renderActionButtons(GuiGraphicsExtractor graphics, ProfessionStateView state,
                                    ProfessionNode node, int x, int y, int w, int mouseX, int mouseY) {
        boolean unlocked = state.unlocked().contains(node.id());
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        int level = state.levels().getOrDefault(node.id(), 0);

        boolean canInvest = unlocked && level < 20 && state.pool() >= costForNextLevel(level);
        boolean canAdvance = canAdvanceFrom(state, node);
        boolean canSwitchMain = !isCurrent && unlocked;
        boolean canSetSecondary = unlocked && !isCurrent && !isSecondary;
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
        y += renderButton(graphics, mouseX, mouseY, x, y, w, toggleLabel, hasSecondary);
        return y;
    }

    /** 渲染单个按钮，返回其高度 */
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
    // 交互
    // ====================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isPick) {
        if (event.button() != 0) return super.mouseClicked(event, isPick);
        double mouseX = event.x();
        double mouseY = event.y();

        ProfessionStateView state = ProfessionStateCache.get();
        if (state == null) return super.mouseClicked(event, isPick);

        int totalWidth = TREE_PANEL_WIDTH + PANEL_GAP + DETAIL_PANEL_WIDTH;
        int treeX = (this.width - totalWidth) / 2;
        int panelY = TOP_PADDING;
        int detailX = treeX + TREE_PANEL_WIDTH + PANEL_GAP;

        // 1. 节点点击（选中）
        Identifier hit = hitNode(state, treeX, panelY + TITLE_HEIGHT * 2, mouseX, mouseY);
        if (hit != null) {
            selectedNodeId = hit;
            return true;
        }
        // 2. 详情按钮点击
        if (selectedNodeId != null && mouseX >= detailX && mouseX < detailX + DETAIL_PANEL_WIDTH) {
            ProfessionNode node = findNode(state, selectedNodeId);
            if (node != null) {
                String action = hitDetailButton(state, node, detailX, panelY, mouseX, mouseY);
                if (action != null) {
                    sendAction(action, node.id());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isPick);
    }

    /** 命中检测节点，返回节点 ID 或 null */
    private Identifier hitNode(ProfessionStateView state, int originX, int originY,
                               double mouseX, double mouseY) {
        Map<Identifier, int[]> pos = computeLayout(state, originX, originY);
        for (Map.Entry<Identifier, int[]> e : pos.entrySet()) {
            int[] p = e.getValue();
            if (isHover(mouseX, mouseY, p[0], p[1], NODE_SIZE, NODE_SIZE)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** 命中详情按钮，返回 action 名或 null */
    private String hitDetailButton(ProfessionStateView state, ProfessionNode node,
                                   int x, int y, double mouseX, double mouseY) {
        int btnX = x + CONTENT_MARGIN;
        int btnW = DETAIL_PANEL_WIDTH - 2 * CONTENT_MARGIN;
        int cy = y + TITLE_HEIGHT + 4 + 12 + 13 + 13 + 4; // 名称+状态+描述+分隔
        boolean unlocked = state.unlocked().contains(node.id());
        int level = state.levels().getOrDefault(node.id(), 0);
        if (unlocked) {
            cy += 12 + (level < 20 ? 12 : 12) + 3;
        }
        boolean isCurrent = node.id().equals(state.currentMain());
        boolean isSecondary = node.id().equals(state.currentSecondary());
        boolean canInvest = unlocked && level < 20 && state.pool() >= costForNextLevel(level);
        boolean canAdvance = canAdvanceFrom(state, node);
        boolean canSwitchMain = !isCurrent && unlocked;
        boolean canSetSecondary = unlocked && !isCurrent && !isSecondary;
        boolean hasSecondary = state.currentSecondary() != null;
        String[] actions = {"invest", "advance", "switch_main", "set_secondary", "toggle_secondary"};
        boolean[] enabled = {canInvest, canAdvance, canSwitchMain, canSetSecondary, hasSecondary};
        for (int i = 0; i < actions.length; i++) {
            if (enabled[i] && isHover(mouseX, mouseY, btnX, cy, btnW, BUTTON_HEIGHT)) {
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
    public void removed() {
        ProfessionStateCache.clear();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ====================================================================
    // 业务判定（与服务端校验镜像，仅用于 UI 灰显）
    // ====================================================================

    /** 职业经验表：round(50 × L^1.5)，L=1..19 */
    private static int costForNextLevel(int level) {
        if (level < 1 || level >= 20) return Integer.MAX_VALUE;
        return (int) Math.round(50 * Math.pow(level, 1.5));
    }

    private boolean canAdvanceFrom(ProfessionStateView state, ProfessionNode node) {
        if (node.prerequisite() == null) return false;
        if (state.unlocked().contains(node.id())) return false;
        if (!state.unlocked().contains(node.prerequisite())) return false;
        return state.levels().getOrDefault(node.prerequisite(), 0) >= 20;
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

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int panelX, int panelWidth,
                              int y, int color, boolean shadow) {
        graphics.text(this.font, text, panelX + (panelWidth - this.font.width(text)) / 2, y, color, shadow);
    }

    private void renderCenteredText(GuiGraphicsExtractor graphics, String text, int panelX,
                                    int contentStartY, int contentHeight, int panelWidth) {
        graphics.text(this.font, text,
                panelX + (panelWidth - this.font.width(text)) / 2,
                contentStartY + contentHeight / 2 - 4, COLOR_HINT, false);
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
