package com.rpgcraft.core.client;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.level.LevelManager;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.ProfessionManager;
import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 背包界面属性面板 —— 在原版背包界面右侧注入属性面板
 * <p>
 * 使用 NeoForge {@link ScreenEvent} 事件系统，无需替换 InventoryScreen 或使用 Mixin：
 * <ul>
 *   <li>{@code ScreenEvent.Init.Post} — 注入 "属性" 切换按钮</li>
 *   <li>{@code ScreenEvent.Render.Post} — 渲染属性面板叠加层</li>
 *   <li>{@code ScreenEvent.MouseButtonPressed.Pre} — 拦截翻页箭头点击</li>
 * </ul>
 * <p>
 * 面板布局：背包右侧，两列属性，超出时翻页。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = "rpgcraftcore")
public class InventoryAttributePanel {

    // ====================================================================
    // 状态
    // ====================================================================

    /** 面板是否打开（跨背包开关记忆） */
    private static boolean panelOpen = false;

    /** 当前页码（从 0 开始） */
    private static int currentPage = 0;

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 标准背包 GUI 宽度 */
    private static final int INV_WIDTH = 176;
    /** 标准背包 GUI 高度 */
    private static final int INV_HEIGHT = 166;

    /** 面板宽度 */
    private static final int PANEL_WIDTH = 146;
    /** 面板内边距 */
    private static final int PANEL_MARGIN = 6;
    /** 每行高度 */
    private static final int LINE_HEIGHT = 12;
    /** 两列间距 */
    private static final int COLUMN_GAP = 8;
    /** 面板高度（与背包一致） */
    private static final int PANEL_HEIGHT = 166;
    /** 切换按钮宽度 */
    private static final int TAB_WIDTH = 28;
    /** 切换按钮高度 */
    private static final int TAB_HEIGHT = 24;
    /** 圆角半径 */
    private static final int CORNER_RADIUS = 3;

    // 头部区域高度：标题 + 等级 + 职业 + 分隔线
    private static final int HEADER_HEIGHT = 13 + LINE_HEIGHT * 2 + 4;
    // 页脚区域高度
    private static final int FOOTER_HEIGHT = 14;

    // 颜色常量（ARGB）
    private static final int COLOR_BG = 0xC0000000;         // 半透明黑色背景
    private static final int COLOR_BORDER = 0xFF555555;     // 灰色边框
    private static final int COLOR_TITLE = 0xFFFFFF00;      // 黄色标题
    private static final int COLOR_TEXT = 0xFFFFFFFF;       // 白色文本
    private static final int COLOR_SEPARATOR = 0xFF555555;  // 分隔线
    private static final int COLOR_PAGE_TEXT = 0xFFAAAAAA;  // 页码文字
    private static final int COLOR_ARROW = 0xFFFFFFFF;      // 翻页箭头
    private static final int COLOR_ARROW_HOVER = 0xFFFFFF00; // 翻页箭头（悬停）

    /** 复用的 StringBuilder */
    private static final StringBuilder SB = new StringBuilder(64);

    // ====================================================================
    // 位置计算
    // ====================================================================

    /**
     * 计算背包左上角 X 坐标
     */
    private static int getLeftPos(net.minecraft.client.gui.screens.Screen screen) {
        return (screen.width - INV_WIDTH) / 2;
    }

    /**
     * 计算背包左上角 Y 坐标
     */
    private static int getTopPos(net.minecraft.client.gui.screens.Screen screen) {
        return (screen.height - INV_HEIGHT) / 2;
    }

    // ====================================================================
    // 事件处理器
    // ====================================================================

    /**
     * 背包界面初始化后 —— 注入 "属性" 切换按钮
     */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 计算按钮位置
        int leftPos = getLeftPos(screen);
        int topPos = getTopPos(screen);
        int tabX = leftPos + INV_WIDTH + 2;
        int tabY = panelOpen ? topPos + PANEL_HEIGHT + 2 : topPos + 5;

        Button tabButton = Button.builder(
                Component.literal("属性"),
                btn -> togglePanel()
        ).bounds(tabX, tabY, TAB_WIDTH, TAB_HEIGHT).build();

        // 通过事件 API 添加可渲染组件
        event.addListener(tabButton);
    }

    /**
     * 背包界面渲染后 —— 绘制属性面板叠加层
     */
    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        if (!panelOpen) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        GuiGraphicsExtractor guiGraphics = event.getGuiGraphics();
        net.minecraft.client.gui.screens.Screen screen = event.getScreen();

        // 计算面板位置
        int panelX = getLeftPos(screen) + INV_WIDTH + 2;
        int panelY = getTopPos(screen);

        renderPanel(guiGraphics, panelX, panelY, mc.font, player, event.getMouseX(), event.getMouseY());
    }

    /**
     * 鼠标点击 —— 拦截翻页箭头点击
     */
    @SubscribeEvent
    static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        if (!panelOpen) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        net.minecraft.client.gui.screens.Screen screen = event.getScreen();
        int panelX = getLeftPos(screen) + INV_WIDTH + 2;
        int panelY = getTopPos(screen);

        // 计算翻页区域
        int totalPages = getTotalPages();
        if (totalPages <= 1) return;

        int footerY = panelY + PANEL_HEIGHT - PANEL_MARGIN - FOOTER_HEIGHT + 2;
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        // 检测左箭头点击 "< "
        String prevArrow = "< ";
        int prevX = panelX + PANEL_MARGIN;
        int prevWidth = mc.font.width(prevArrow);
        if (mouseX >= prevX && mouseX <= prevX + prevWidth &&
                mouseY >= footerY && mouseY <= footerY + LINE_HEIGHT) {
            if (currentPage > 0) {
                currentPage--;
                event.setCanceled(true);
            }
            return;
        }

        // 检测右箭头点击 " >"
        String nextArrow = " >";
        int nextX = panelX + PANEL_WIDTH - PANEL_MARGIN - mc.font.width(nextArrow);
        if (mouseX >= nextX && mouseX <= nextX + mc.font.width(nextArrow) &&
                mouseY >= footerY && mouseY <= footerY + LINE_HEIGHT) {
            if (currentPage < totalPages - 1) {
                currentPage++;
                event.setCanceled(true);
            }
        }
    }

    // ====================================================================
    // 面板渲染
    // ====================================================================

    /**
     * 渲染完整的属性面板
     */
    private static void renderPanel(GuiGraphicsExtractor g, int panelX, int panelY,
                                     net.minecraft.client.gui.Font font,
                                     Player player, double mouseX, double mouseY) {
        // 1. 背景
        renderBackground(g, panelX, panelY);

        int x = panelX + PANEL_MARGIN;
        int y = panelY + PANEL_MARGIN;

        // 2. 标题
        String title = "RPG属性";
        int titleWidth = font.width(title);
        g.text(font, title, panelX + (PANEL_WIDTH - titleWidth) / 2, y, COLOR_TITLE, true);
        y += 13;

        // 3. 等级信息
        PlayerLevelData levelData = player.getData(LevelManager.PLAYER_LEVEL);
        SB.setLength(0);
        int expForNext = levelData.getExpForNextLevel();
        if (expForNext < 0) {
            SB.append("等级: ").append(levelData.getLevel()).append(" (MAX)");
        } else {
            SB.append("等级: ").append(levelData.getLevel())
                    .append("  经验: ").append(levelData.getExperience())
                    .append("/").append(expForNext);
        }
        g.text(font, SB.toString(), x, y, COLOR_TEXT, false);
        y += LINE_HEIGHT;

        // 4. 职业信息
        ProfessionData profData = player.getData(ProfessionManager.PLAYER_PROFESSION);
        IProfession prof = ProfessionManager.getRegistry().getProfession(profData.getProfessionId());
        String profText = "职业: " + (prof != null ? prof.getDisplayName() : "未知");
        g.text(font, profText, x, y, COLOR_TEXT, false);
        y += LINE_HEIGHT + 2;

        // 5. 分隔线
        g.fill(x, y, panelX + PANEL_WIDTH - PANEL_MARGIN, y + 1, COLOR_SEPARATOR);
        y += 3;

        // 6. 属性列表（两列）
        renderAttributes(g, x, y, font, player);

        // 7. 页脚（翻页）
        renderFooter(g, panelX, panelY, font, mouseX, mouseY);
    }

    /**
     * 渲染面板背景（半透明黑色 + 灰色边框）
     */
    private static void renderBackground(GuiGraphicsExtractor g, int panelX, int panelY) {
        // 边框（比面板大一圈）
        fillRounded(g, panelX - 1, panelY - 1,
                PANEL_WIDTH + 2, PANEL_HEIGHT + 2, CORNER_RADIUS + 1, COLOR_BORDER);
        // 背景
        fillRounded(g, panelX, panelY,
                PANEL_WIDTH, PANEL_HEIGHT, CORNER_RADIUS, COLOR_BG);
    }

    /**
     * 渲染两列属性列表
     */
    private static void renderAttributes(GuiGraphicsExtractor g, int x, int y,
                                          net.minecraft.client.gui.Font font,
                                          Player player) {
        java.util.List<IAttributeEntry> entries = AttributeManager.getRegistry().getAllEntries();
        int columnWidth = (PANEL_WIDTH - 2 * PANEL_MARGIN - COLUMN_GAP) / 2;
        int attrsPerPage = getMaxRows() * 2;
        int startIdx = currentPage * attrsPerPage;

        for (int i = 0; i < attrsPerPage; i++) {
            int idx = startIdx + i;
            if (idx >= entries.size()) break;

            int row = i / 2;
            int col = i % 2;

            IAttributeEntry entry = entries.get(idx);
            IAttribute attr = player.getData(entry.getSupplier());

            SB.setLength(0);
            SB.append(entry.getDisplayName()).append(": ").append(attr.getValue());

            int attrX = x + col * (columnWidth + COLUMN_GAP);
            int attrY = y + row * LINE_HEIGHT;
            g.text(font, SB.toString(), attrX, attrY, COLOR_TEXT, false);
        }
    }

    /**
     * 渲染页脚（翻页指示器）
     */
    private static void renderFooter(GuiGraphicsExtractor g, int panelX, int panelY,
                                      net.minecraft.client.gui.Font font,
                                      double mouseX, double mouseY) {
        int totalPages = getTotalPages();
        int footerY = panelY + PANEL_HEIGHT - PANEL_MARGIN - FOOTER_HEIGHT + 2;

        if (totalPages <= 1) return;

        // 左箭头 "< "
        String prevArrow = "< ";
        int prevX = panelX + PANEL_MARGIN;
        boolean prevHover = mouseX >= prevX && mouseX <= prevX + font.width(prevArrow) &&
                mouseY >= footerY && mouseY <= footerY + LINE_HEIGHT;
        int prevColor = prevHover && currentPage > 0 ? COLOR_ARROW_HOVER :
                (currentPage > 0 ? COLOR_ARROW : COLOR_PAGE_TEXT);
        g.text(font, prevArrow, prevX, footerY, prevColor, false);

        // 右箭头 " >"
        String nextArrow = " >";
        int nextX = panelX + PANEL_WIDTH - PANEL_MARGIN - font.width(nextArrow);
        boolean nextHover = mouseX >= nextX && mouseX <= nextX + font.width(nextArrow) &&
                mouseY >= footerY && mouseY <= footerY + LINE_HEIGHT;
        int nextColor = nextHover && currentPage < totalPages - 1 ? COLOR_ARROW_HOVER :
                (currentPage < totalPages - 1 ? COLOR_ARROW : COLOR_PAGE_TEXT);
        g.text(font, nextArrow, nextX, footerY, nextColor, false);

        // 页码文字 "第 X/Y 页"
        SB.setLength(0);
        SB.append("第 ").append(currentPage + 1).append("/").append(totalPages).append(" 页");
        String pageText = SB.toString();
        int pageTextWidth = font.width(pageText);
        int pageTextX = panelX + (PANEL_WIDTH - pageTextWidth) / 2;
        g.text(font, pageText, pageTextX, footerY, COLOR_PAGE_TEXT, false);
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    /**
     * 切换面板开关状态
     */
    private static void togglePanel() {
        panelOpen = !panelOpen;
        // 关闭时重置页码
        if (!panelOpen) {
            currentPage = 0;
        }
        // 切换后需要重新初始化界面以更新按钮位置
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    /**
     * 计算每页最大行数
     */
    private static int getMaxRows() {
        int contentHeight = PANEL_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT - 2 * PANEL_MARGIN - 4;
        return Math.max(1, contentHeight / LINE_HEIGHT);
    }

    /**
     * 计算总页数
     */
    private static int getTotalPages() {
        java.util.List<IAttributeEntry> entries = AttributeManager.getRegistry().getAllEntries();
        int attrsPerPage = getMaxRows() * 2;
        return Math.max(1, (int) Math.ceil((double) entries.size() / attrsPerPage));
    }

    /**
     * 绘制圆角矩形填充
     * <p>
     * 与 {@link AttributeHudOverlay} 使用的相同算法。
     */
    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
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
