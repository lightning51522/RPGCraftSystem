package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.AttributeSnapshot.AttributeData;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性抗性面板插件 —— 角色界面中的元素抗性展示区
 * <p>
 * 展示 7 个元素抗性属性（电/火/风/水/光/毒/暗），这些属性<b>不可加点</b>，
 * 默认值 0、上限 100，装备/职业加成生效。
 * <p>
 * 排在 {@link AttributeListPlugin}（一般属性）<b>之后</b>，由 ClientMod 注册顺序保证。
 * 一般属性列表的过滤条件（{@code isAllocatable || shouldResetOnRespawn}）排除了所有抗性属性
 *（两者皆 false），故抗性只在此栏显示，不与一般属性重复。
 * <p>
 * 布局（从上到下）：
 * <ol>
 *   <li>顶部分隔线（无标题；标题统一由 {@link CompositeAttributePlugin} 提供）</li>
 *   <li>两列抗性列表（4 行：前 3 行各 2 列 + 第 4 行 1 列 = 7 个）</li>
 * </ol>
 * <p>
 * 数值按元素主题配色，视觉直观：
 * 电=青、火=橙红、风=灰绿、水=天蓝、光=金黄、毒=草绿、暗=紫。
 * <p>
 * 数据来源：{@link AttributeSnapshot}（服务端自动同步，无需额外网络包）。
 *
 * @see ICharacterScreenPlugin
 * @see AttributeListPlugin
 */
public class ResistanceAttributePlugin implements ICharacterScreenPlugin {

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 每行高度（与 AttributeListPlugin / CompositeAttributePlugin 一致） */
    private static final int LINE_HEIGHT = 12;
    /** 两列间距 */
    private static final int COLUMN_GAP = 8;
    /** 顶部区域高度（分隔线上方间距 + 1px 分隔线，无标题） */
    private static final int HEADER_HEIGHT = 5;
    /** 抗性总行数：7 个抗性 = 3 行 × 2 列 + 1 行 × 1 列 = 4 行 */
    private static final int RESISTANCE_ROWS = 4;
    /** 插件总高度 */
    private static final int PLUGIN_HEIGHT = HEADER_HEIGHT + RESISTANCE_ROWS * LINE_HEIGHT;

    // ====================================================================
    // 颜色常量（ARGB 格式）
    // ====================================================================

    /** 灰色分隔线 */
    private static final int COLOR_SEPARATOR = 0xFF373737;

    // 元素主题色（数值文字）
    /** 电抗：青 */
    private static final int COLOR_ELECTRIC = 0xFF00E5FF;
    /** 火抗：橙红 */
    private static final int COLOR_FIRE = 0xFFFF6B35;
    /** 风抗：灰绿 */
    private static final int COLOR_WIND = 0xFF8FE388;
    /** 水抗：天蓝 */
    private static final int COLOR_WATER = 0xFF4FA8E0;
    /** 光抗：金黄 */
    private static final int COLOR_LIGHT = 0xFFFFE55C;
    /** 毒抗：草绿 */
    private static final int COLOR_POISON = 0xFF7CFC00;
    /** 暗抗：紫 */
    private static final int COLOR_DARK = 0xFFB366FF;

    /** 复用的 StringBuilder */
    private static final StringBuilder SB = new StringBuilder(32);

    /**
     * 单个抗性的展示元信息
     *
     * @param id       抗性属性 ID
     * @param label    显示名（不带「抗」字，渲染时统一拼接「X抗: 值」）
     * @param color    数值配色
     * @param desc     tooltip 说明
     */
    private record Resistance(Identifier id, String label, int color, String desc) {}

    /** 7 个抗性的固定展示顺序（与 ClientAttributes 中的 ID 常量一一对应） */
    private static final List<Resistance> RESISTANCES = List.of(
            new Resistance(ClientAttributes.ELECTRIC_RESISTANCE_ID, "电抗", COLOR_ELECTRIC,
                    "减免受到的电属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 电抗/100)。不可加点，装备加成生效。"),
            new Resistance(ClientAttributes.FIRE_RESISTANCE_ID, "火抗", COLOR_FIRE,
                    "减免受到的火属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 火抗/100)。不可加点，装备加成生效。"),
            new Resistance(ClientAttributes.WIND_RESISTANCE_ID, "风抗", COLOR_WIND,
                    "减免受到的风属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 风抗/100)。不可加点，装备加成生效。"),
            new Resistance(ClientAttributes.WATER_RESISTANCE_ID, "水抗", COLOR_WATER,
                    "减免受到的水属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 水抗/100)。不可加点，装备加成生效。"),
            new Resistance(ClientAttributes.LIGHT_RESISTANCE_ID, "光抗", COLOR_LIGHT,
                    "减免受到的光属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 光抗/100)。不可加点，装备加成生效。"),
            new Resistance(ClientAttributes.POISON_RESISTANCE_ID, "毒抗", COLOR_POISON,
                    "减免受到的毒属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 毒抗/100)。不可加点，装备加成生效。"),
            new Resistance(ClientAttributes.DARK_RESISTANCE_ID, "暗抗", COLOR_DARK,
                    "减免受到的暗属性攻击伤害（百分比）。基础减伤后额外乘以 (1 - 暗抗/100)。不可加点，装备加成生效。")
    );

    /**
     * 获取插件高度（固定值）
     */
    @Override
    public int getHeight() {
        return PLUGIN_HEIGHT;
    }

    /**
     * 渲染属性抗性面板
     * <p>
     * 从快照读取 7 个抗性属性，按两列布局渲染（元素配色）。
     *
     * @param graphics 图形上下文
     * @param x        渲染区域左上角 X
     * @param y        渲染区域左上角 Y
     * @param width    渲染区域宽度
     * @param snapshot 属性快照
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y, int width, AttributeSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        int currentY = y;

        // 1. 分隔线（顶部，无标题）
        int separatorY = currentY + HEADER_HEIGHT - 1;
        graphics.fill(x, separatorY, x + width, separatorY + 1, COLOR_SEPARATOR);
        currentY += HEADER_HEIGHT;

        // 2. 两列抗性列表
        int columnWidth = (width - COLUMN_GAP) / 2;
        for (int i = 0; i < RESISTANCES.size(); i++) {
            Resistance r = RESISTANCES.get(i);
            int row = i / 2;
            int col = i % 2;
            int value = snapshotValue(snapshot, r.id());

            SB.setLength(0);
            SB.append(r.label()).append(": ").append(value);

            int attrX = x + col * (columnWidth + COLUMN_GAP);
            int attrY = currentY + row * LINE_HEIGHT;
            graphics.text(mc.font, SB.toString(), attrX, attrY, r.color(), false);
        }
    }

    /**
     * 鼠标悬停在抗性行上时返回该抗性的说明 tooltip
     * <p>
     * 碰撞检测与 {@link #render} 的两列网格布局对齐。
     *
     * @param relX 相对插件区域的鼠标 X
     * @param relY 相对插件区域的鼠标 Y
     * @return 说明文字行列表，或 {@code null}
     */
    @Override
    public List<Component> getTooltip(double relX, double relY, int width, AttributeSnapshot snapshot) {
        // 列表纵向区域：HEADER_HEIGHT ~ HEADER_HEIGHT + RESISTANCE_ROWS*LINE_HEIGHT
        int listTop = HEADER_HEIGHT;
        int listBottom = HEADER_HEIGHT + RESISTANCE_ROWS * LINE_HEIGHT;
        if (relY < listTop || relY >= listBottom) return null;

        // 两列横向区域
        int columnWidth = (width - COLUMN_GAP) / 2;
        int col;
        if (relX >= 0 && relX < columnWidth) {
            col = 0;
        } else if (relX >= columnWidth + COLUMN_GAP && relX < width) {
            col = 1;
        } else {
            return null; // 列间隙
        }

        int row = (int) ((relY - listTop) / LINE_HEIGHT);
        int idx = row * 2 + col;
        if (idx < 0 || idx >= RESISTANCES.size()) return null;

        Resistance r = RESISTANCES.get(idx);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(r.label()));
        for (String line : r.desc().split("\n")) {
            lines.add(Component.literal(line));
        }
        return lines;
    }

    /** 从快照读取属性值（未注册返回 0） */
    private static int snapshotValue(AttributeSnapshot snapshot, Identifier id) {
        AttributeData d = snapshot.get(id);
        return d != null ? d.currentValue() : 0;
    }
}
