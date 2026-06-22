package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.AttributeSnapshot.AttributeData;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 综合属性面板插件 —— 角色界面中的攻击力/防御力展示区
 * <p>
 * 展示 3 个由一般属性派生的综合属性（不可加点）：
 * <ul>
 *   <li>物理攻击 = 力量×2 + 智力</li>
 *   <li>魔法攻击 = 智力×2 + 力量</li>
 *   <li>防御 = 力量×2</li>
 * </ul>
 * 这些属性<b>不作为真实属性注册</b>，由本插件从快照读取力量/智力后按公式动态计算
 * （公式镜像服务端 {@code DefaultDamageCalculator}，仅用于客户端展示）。
 * <p>
 * 布局（从上到下）：
 * <ol>
 *   <li>标题 "综合属性"（居中，黄色）+ 分隔线</li>
 *   <li>三行综合属性（两列布局：物理攻击 | 魔法攻击，第二行单独显示防御）</li>
 * </ol>
 * <p>
 * 排在 {@link AttributeListPlugin}（一般属性）<b>之前</b>，由 ClientMod 注册顺序保证。
 *
 * @see ICharacterScreenPlugin
 * @see AttributeListPlugin
 */
public class CompositeAttributePlugin implements ICharacterScreenPlugin {

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 每行高度 */
    private static final int LINE_HEIGHT = 12;
    /** 两列间距 */
    private static final int COLUMN_GAP = 8;
    /** 标题区域高度（标题 + 间距 + 分隔线 + 间距） */
    private static final int HEADER_HEIGHT = 20;
    /** 综合属性行数（物理攻击/魔法攻击同一行两列 + 防御单独一行 = 2 行） */
    private static final int COMPOSITE_ROWS = 2;
    /** 插件总高度 */
    private static final int PLUGIN_HEIGHT = HEADER_HEIGHT + COMPOSITE_ROWS * LINE_HEIGHT;

    // ====================================================================
    // 颜色常量（ARGB 格式）
    // ====================================================================

    /** 黄色标题（原版强调色，与 AttributeListPlugin 一致） */
    private static final int COLOR_TITLE = 0xFFFFE000;
    /** 白色文本 */
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** 灰色分隔线 */
    private static final int COLOR_SEPARATOR = 0xFF373737;

    /** 复用的 StringBuilder */
    private static final StringBuilder SB = new StringBuilder(64);

    /**
     * 获取插件高度（固定值）
     */
    @Override
    public int getHeight() {
        return PLUGIN_HEIGHT;
    }

    /**
     * 渲染综合属性面板
     * <p>
     * 从快照读取力量/智力，按公式动态计算 3 个综合属性并渲染。
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

        // 1. 标题 "综合属性"
        String title = "综合属性";
        int titleWidth = mc.font.width(title);
        graphics.text(mc.font, title, x + (width - titleWidth) / 2, currentY, COLOR_TITLE, true);
        currentY += 13;

        // 2. 分隔线
        graphics.fill(x, currentY, x + width, currentY + 1, COLOR_SEPARATOR);
        currentY += HEADER_HEIGHT - 13;

        // 3. 计算综合属性值
        int strength = snapshotValue(snapshot, ClientAttributes.STRENGTH_ID);
        int intelligence = snapshotValue(snapshot, ClientAttributes.INTELLIGENCE_ID);
        int physAttack = strength * 2 + intelligence;
        int magicAttack = intelligence * 2 + strength;
        int defense = strength * 2;

        // 4. 两列布局：第一行 物理攻击 | 魔法攻击；第二行 防御（占整行）
        int columnWidth = (width - COLUMN_GAP) / 2;

        // 第一行左列：物理攻击
        SB.setLength(0);
        SB.append("物理攻击: ").append(physAttack);
        graphics.text(mc.font, SB.toString(), x, currentY, COLOR_TEXT, false);

        // 第一行右列：魔法攻击
        SB.setLength(0);
        SB.append("魔法攻击: ").append(magicAttack);
        graphics.text(mc.font, SB.toString(), x + columnWidth + COLUMN_GAP, currentY, COLOR_TEXT, false);
        currentY += LINE_HEIGHT;

        // 第二行：防御（左列，与一般属性列表的视觉节奏一致）
        SB.setLength(0);
        SB.append("防御: ").append(defense);
        graphics.text(mc.font, SB.toString(), x, currentY, COLOR_TEXT, false);
    }

    /** 从快照读取属性值（未注册返回 0） */
    private static int snapshotValue(AttributeSnapshot snapshot, net.minecraft.resources.Identifier id) {
        AttributeData d = snapshot.get(id);
        return d != null ? d.currentValue() : 0;
    }
}
