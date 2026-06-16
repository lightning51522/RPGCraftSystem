package com.rpgcraft.client.ui;

import com.rpgcraft.core.attributepoints.PlayerAttributePoints;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.AllocateAttributePointPacket;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.ui.AttributePointClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.List;
import java.util.function.Supplier;

/**
 * 属性点分配面板（角色界面右侧）
 * <p>
 * 在角色界面右侧并排显示，列出所有可分配属性及其已分配点数，每行带 {@code [-]} 和 {@code [+]}
 * 按钮可分别回收/分配 1 点。
 * <p>
 * <b>不是 {@code ICharacterScreenPlugin}</b>：插件系统是单列流式的，本面板是 {@link RPGCharacterScreen}
 * 直接协调的第二面板渲染器（独立于插件布局）。
 * <p>
 * <h3>布局（每行，从左到右）</h3>
 * <pre>
 * [属性名]          +N [-][+]
 * </pre>
 * <ul>
 *   <li>属性名靠左</li>
 *   <li>{@code +N}（已分配点数）右侧留出按钮区</li>
 *   <li>{@code [-]} 和 {@code [+]} 按钮在最右侧，各有固定宽度，互不重叠</li>
 * </ul>
 * <p>
 * <h3>数据源</h3>
 * <ul>
 *   <li>可分配属性列表：{@link AttributeManager#getRegistry()} 遍历过滤 {@code !shouldResetOnRespawn()}（客户端动态计算）</li>
 *   <li>已分配/可分配点数：客户端 {@link PlayerAttributePoints} 附件（服务端同步）</li>
 * </ul>
 * <p>
 * <h3>分配/回收流程</h3>
 * 点击 {@code [+]} / {@code [-]} → 发送 {@link AllocateAttributePointPacket}（core 网络包）到服务端 →
 * 服务端校验+应用+回推 {@code SyncPlayerAttributePointsPacket} + 全量属性快照
 * → 客户端附件 + UISnapshotCache 更新 → 下一帧左右两面板都重渲染（实时刷新）。
 *
 * @apiNote {@code AllocateAttributePointPacket} 放在 core 模块而非 attributepoints，
 *          使本类只需依赖 core（避免 client 对 attributepoints 的编译期依赖）。
 */
public class AttributePointPanel {

    // 布局常量
    private static final int LINE_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 20;
    private static final int CONTENT_MARGIN = 6;
    /** 单个按钮 [+] 或 [-] 占用的宽度 */
    private static final int BUTTON_WIDTH = 12;
    /** 两个按钮之间的间距 */
    private static final int BUTTON_GAP = 2;
    /** 按钮区（[+] + 间距 + [-]）的总宽度 */
    private static final int BUTTON_AREA_WIDTH = BUTTON_WIDTH * 2 + BUTTON_GAP;
    /** 已分配点数文本与按钮区之间保留的间距 */
    private static final int TEXT_BUTTON_GAP = 6;

    // 颜色（ARGB）
    private static final int COLOR_TITLE = 0xFFFFFF00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_AVAILABLE = 0xFF55FF55;
    private static final int COLOR_ALLOCATED = 0xFFAAAAFF;
    private static final int COLOR_BUTTON = 0xFF55FF55;
    private static final int COLOR_BUTTON_HOVER = 0xFFFFFF00;
    private static final int COLOR_BUTTON_DISABLED = 0xFF555555;
    private static final int COLOR_HINT = 0xFFAAAAAA;

    /**
     * 渲染属性点面板
     *
     * @param graphics 图形上下文
     * @param x        面板左上角 X（绝对坐标）
     * @param y        面板左上角 Y
     * @param width    面板宽度
     * @param height   面板总高度
     * @param mouseX   鼠标 X（绝对坐标，用于 hover 高亮）
     * @param mouseY   鼠标 Y
     */
    public static void render(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 检测属性点系统是否可用（模块未加载则跳过）
        if (!RPGSystems.hasAttributePointSystem()) {
            graphics.text(mc.font, "属性点模块未加载", x + CONTENT_MARGIN, y + 8, COLOR_HINT, false);
            return;
        }

        PlayerAttributePoints points = getPlayerPoints();
        int available = points != null ? points.getAvailablePoints() : 0;
        List<IAttributeEntry> entries = AttributeManager.getRegistry().getAllEntries().stream()
                .filter(entry -> !entry.shouldResetOnRespawn())
                .toList();

        // 标题行
        graphics.text(mc.font, "属性点", x + CONTENT_MARGIN, y + 4, COLOR_TITLE, true);
        // 可用点数（右侧）
        String availText = "可用: " + available;
        int availColor = available > 0 ? COLOR_AVAILABLE : COLOR_HINT;
        graphics.text(mc.font, availText,
                x + width - CONTENT_MARGIN - mc.font.width(availText),
                y + 4, availColor, true);

        // 属性列表
        int listY = y + HEADER_HEIGHT;
        int line = 0;
        for (IAttributeEntry entry : entries) {
            int rowY = listY + line * LINE_HEIGHT;
            if (rowY + LINE_HEIGHT > y + height) break; // 超出面板底部，停止

            int allocated = points != null ? points.getAllocated(entry.getId()) : 0;
            renderRow(graphics, mc, x, rowY, width, entry.getDisplayName(), allocated,
                    available, allocated > 0, mouseX, mouseY);
            line++;
        }

        // 无可分配属性
        if (entries.isEmpty()) {
            graphics.text(mc.font, "无可分配属性", x + CONTENT_MARGIN, listY, COLOR_HINT, false);
        }
    }

    /**
     * 渲染单行：属性名 + 已分配点数 + [-][+] 按钮
     * <p>
     * 布局（从右到左计算锚点，避免重叠）：
     * <pre>
     * 右边界 - CONTENT_MARGIN = 最右
     *   [+] 按钮：右边界 - CONTENT_MARGIN - BUTTON_WIDTH .. 右边界 - CONTENT_MARGIN
     *   [-] 按钮：[+] 左边 - BUTTON_GAP - BUTTON_WIDTH .. [+] 左边 - BUTTON_GAP
     *   按钮区左边界 = [-] 左边
     *   +N 文本：右对齐到 按钮区左边界 - TEXT_BUTTON_GAP
     *   属性名：左对齐到 CONTENT_MARGIN
     * </pre>
     */
    private static void renderRow(GuiGraphicsExtractor graphics, Minecraft mc,
                                  int x, int rowY, int width,
                                  String name, int allocated, int available,
                                  boolean canDecrement, int mouseX, int mouseY) {
        // 配置 allow_decrease=false 时隐藏 [-] 按钮：[+] 直接右对齐到面板右边，
        // +N 文本右对齐到 [+] 左侧。allow_decrease=true 时保持原有 [+] [-] 双按钮布局。
        boolean allowDecrease = AttributePointClientConfig.isAllowDecrease();

        // [+] 按钮位置（最右）
        int plusX = x + width - CONTENT_MARGIN - BUTTON_WIDTH;
        // [-] 按钮位置（[+] 左侧）—— 仅 allow_decrease=true 时存在
        int minusX = plusX - BUTTON_GAP - BUTTON_WIDTH;
        // 按钮区左边界
        int buttonAreaLeft = allowDecrease ? minusX : plusX;
        // +N 文本右边界
        int allocRight = buttonAreaLeft - TEXT_BUTTON_GAP;

        // 属性名（左）
        graphics.text(mc.font, name, x + CONTENT_MARGIN, rowY, COLOR_TEXT, false);

        // 已分配点数 +N（右对齐到 allocRight）
        String allocStr = "+" + allocated;
        int allocColor = allocated > 0 ? COLOR_ALLOCATED : COLOR_HINT;
        graphics.text(mc.font, allocStr, allocRight - mc.font.width(allocStr), rowY, allocColor, false);

        // [-] 按钮（仅 allow_decrease=true 时渲染）
        if (allowDecrease) {
            renderButton(graphics, mc, "[-]", minusX, rowY, mouseX, mouseY,
                    canDecrement, COLOR_BUTTON, COLOR_BUTTON_HOVER, COLOR_BUTTON_DISABLED);
        }
        // [+] 按钮
        renderButton(graphics, mc, "[+]", plusX, rowY, mouseX, mouseY,
                available > 0, COLOR_BUTTON, COLOR_BUTTON_HOVER, COLOR_BUTTON_DISABLED);
    }

    /**
     * 渲染单个按钮（含 hover 高亮 + 禁用态）
     */
    private static void renderButton(GuiGraphicsExtractor graphics, Minecraft mc,
                                     String text, int btnX, int rowY,
                                     int mouseX, int mouseY, boolean enabled,
                                     int colorNormal, int colorHover, int colorDisabled) {
        int btnY = rowY - 1;
        boolean hover = enabled && isHover(mouseX, mouseY, btnX, btnY, BUTTON_WIDTH, LINE_HEIGHT);
        int color;
        if (!enabled) {
            color = colorDisabled;
        } else if (hover) {
            color = colorHover;
        } else {
            color = colorNormal;
        }
        int textWidth = mc.font.width(text);
        graphics.text(mc.font, text, btnX + (BUTTON_WIDTH - textWidth) / 2, rowY, color, false);
    }

    /**
     * 处理面板内的鼠标点击
     *
     * @param x       面板左上角 X（绝对坐标）
     * @param y       面板左上角 Y
     * @param width   面板宽度
     * @param height  面板总高度
     * @param mouseX  鼠标 X（绝对坐标）
     * @param mouseY  鼠标 Y
     * @param button  鼠标按钮
     * @return true 如果点击被消费
     */
    public static boolean mouseClicked(int x, int y, int width, int height,
                                       double mouseX, double mouseY, int button) {
        if (button != 0) return false; // 仅左键
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;
        if (!RPGSystems.hasAttributePointSystem()) return false;

        PlayerAttributePoints points = getPlayerPoints();
        if (points == null) return false;

        List<IAttributeEntry> entries = AttributeManager.getRegistry().getAllEntries().stream()
                .filter(entry -> !entry.shouldResetOnRespawn())
                .toList();
        // 按钮位置（与 renderRow 一致）
        int plusX = x + width - CONTENT_MARGIN - BUTTON_WIDTH;
        int minusX = plusX - BUTTON_GAP - BUTTON_WIDTH;

        int listY = y + HEADER_HEIGHT;
        int line = 0;
        for (IAttributeEntry entry : entries) {
            int rowY = listY + line * LINE_HEIGHT;
            if (rowY + LINE_HEIGHT > y + height) break;

            int btnY = rowY - 1;
            Identifier attrId = entry.getId();

            // [+] 命中（需要可分配点数 > 0）
            if (points.getAvailablePoints() > 0
                    && isHover(mouseX, mouseY, plusX, btnY, BUTTON_WIDTH, LINE_HEIGHT)) {
                mc.getConnection().send(new AllocateAttributePointPacket(attrId, 1, true));
                return true;
            }
            // [-] 命中（需要已分配 > 0，且配置允许减少）
            if (AttributePointClientConfig.isAllowDecrease()
                    && points.getAllocated(attrId) > 0
                    && isHover(mouseX, mouseY, minusX, btnY, BUTTON_WIDTH, LINE_HEIGHT)) {
                mc.getConnection().send(new AllocateAttributePointPacket(attrId, 1, false));
                return true;
            }
            line++;
        }
        return false;
    }

    /**
     * 判断鼠标是否落在矩形区域内
     */
    private static boolean isHover(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * 获取客户端玩家的属性点附件数据
     */
    @SuppressWarnings("unchecked")
    private static PlayerAttributePoints getPlayerPoints() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        try {
            Supplier<AttachmentType<PlayerAttributePoints>> supplier =
                    RPGSystems.getPlayerAttributePointsAttachment();
            return mc.player.getData(supplier.get());
        } catch (IllegalStateException e) {
            // 附件未注册（模块未完全初始化）
            return null;
        }
    }
}
