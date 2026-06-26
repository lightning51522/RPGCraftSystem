package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.AttributeSnapshot.AttributeData;
import com.rpgcraft.core.profession.api.CombatStats;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionFormulas;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import com.rpgcraft.core.ui.ProfessionStateCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 综合属性面板插件 —— 角色界面中的攻击力/防御力/暴击展示区
 * <p>
 * 展示由一般属性派生的综合属性（不可加点）：
 * <ul>
 *   <li>物理攻击、魔法攻击、物理防御、有效暴击率、有效暴击伤害</li>
 * </ul>
 * 计算公式由<b>当前主职业</b>提供（{@link IProfession#computePhysicalAttack 等}）。
 * 暴击率/暴击伤害展示最终有效值；详细组成（基础+派生加成）在鼠标悬停时通过 tooltip 显示。
 * <p>
 * 若无法解析职业（缓存为空 / 职业模块未加载）：回退默认公式（镜像服务端）。
 * <p>
 * 布局（从上到下）：
 * <ol>
 *   <li>标题 "属性"（居中，黄色）+ 分隔线</li>
 *   <li>物理攻击 / 魔法攻击（两列）</li>
 *   <li>防御</li>
 *   <li>暴击率 / 暴击伤害（两列，悬停显示基础+派生拆分）</li>
 * </ol>
 * <p>
 * 排在 {@link AttributeListPlugin}（一般属性）<b>之前</b>，由 ClientMod 注册顺序保证。
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
    /** 综合属性行数（物攻/魔攻 + 防御 + 暴击率/暴击伤害 = 3 行） */
    private static final int COMPOSITE_ROWS = 3;
    /** 插件总高度 */
    private static final int PLUGIN_HEIGHT = HEADER_HEIGHT + COMPOSITE_ROWS * LINE_HEIGHT;

    /** 标题右侧 ? 详情图标字符 */
    private static final String INFO_ICON = "?";
    /** ? 图标颜色（灰色） */
    private static final int COLOR_INFO_ICON = 0xFF888888;
    /** 标题与 ? 图标之间的水平间距 */
    private static final int ICON_GAP = 2;
    /** ? 图标相对标题顶部的垂直偏移（与较高中文标题光学居中） */
    private static final int ICON_Y_OFFSET = 1;

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

    /** 上帧渲染的暴击率基础值（供 tooltip 读取） */
    private int lastCritRateBase;
    /** 上帧渲染的暴击率派生加成 */
    private int lastCritRateBonus;
    /** 上帧渲染的暴击伤害基础值 */
    private int lastCritRatioBase;
    /** 上帧渲染的暴击伤害派生加成 */
    private int lastCritRatioBonus;

    /** ? 图标区域（供 tooltip 碰撞检测），在 render 中更新 */
    private int infoIconX, infoIconY, infoIconW = 10, infoIconH = 10;

    /** 当前渲染宽度（供 tooltip 碰撞检测） */
    private int lastRenderWidth;

    /** 上帧渲染时解析的主职业（供 tooltip 复用） */
    private IProfession lastProfession;

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

        // 1. 解析主职业（用于公式驱动 + 标题 ? 图标判定）
        IProfession prof = resolveCurrentMainProfession();
        lastProfession = prof;

        // 2. 标题 "属性" + 右侧 ? 详情图标
        // 将「标题 + 间距 + ?」整体居中，避免 ? 出现时标题偏左、视觉重心右移。
        lastRenderWidth = width;
        String title = "属性";
        int titleWidth = mc.font.width(title);
        int iconWidth = prof != null ? mc.font.width(INFO_ICON) : 0;
        int blockWidth = titleWidth + (prof != null ? ICON_GAP + iconWidth : 0);
        int blockStartX = x + (width - blockWidth) / 2;
        graphics.text(mc.font, title, blockStartX, currentY, COLOR_TITLE, true);

        // ? 图标（紧贴标题右侧，灰色，供鼠标悬停查看公式详情）
        if (prof != null) {
            infoIconX = blockStartX - x + titleWidth + ICON_GAP;
            infoIconY = currentY - y + ICON_Y_OFFSET;
            graphics.text(mc.font, INFO_ICON, x + infoIconX, y + infoIconY, COLOR_INFO_ICON, false);
        }
        currentY += 13;

        // 3. 分隔线
        graphics.fill(x, currentY, x + width, currentY + 1, COLOR_SEPARATOR);
        currentY += HEADER_HEIGHT - 13;

        // 4. 构建全属性快照
        CombatStats s = new CombatStats(
                snapshotValue(snapshot, ClientAttributes.STRENGTH_ID),
                snapshotValue(snapshot, ClientAttributes.INTELLIGENCE_ID),
                snapshotValue(snapshot, ClientAttributes.AGILE_ID),
                snapshotValue(snapshot, ClientAttributes.PRECISION_ID),
                snapshotValue(snapshot, ClientAttributes.CRITICAL_RATE_ID),
                snapshotValue(snapshot, ClientAttributes.CRITICAL_RATIO_ID),
                snapshotValue(snapshot, ClientAttributes.FIXED_DAMAGE_ID),
                snapshotValue(snapshot, ClientAttributes.RESISTANCE_ID),
                snapshotValue(snapshot, ClientAttributes.PHYSICAL_PENETRATE_ID),
                snapshotValue(snapshot, ClientAttributes.MAGICAL_PENETRATE_ID)
        );

        // 5. 计算综合属性值
        int physAttack, magicAttack, defense, effectiveCritRate, effectiveCritDamage;
        if (prof != null) {
            physAttack = prof.computePhysicalAttack(s);
            magicAttack = prof.computeMagicalAttack(s);
            defense = prof.computePhysicalDefense(s);
            effectiveCritRate = prof.computeEffectiveCritRate(s);
            effectiveCritDamage = prof.computeEffectiveCritDamage(s);
        } else {
            // 无职业时回退默认公式：委托 core 共享公式（与战斗伤害计算同源，消除内联拷贝漂移）
            physAttack = ProfessionFormulas.physicalAttack(s);
            magicAttack = ProfessionFormulas.magicalAttack(s);
            defense = ProfessionFormulas.physicalDefense(s);
            effectiveCritRate = ProfessionFormulas.effectiveCritRate(s);
            effectiveCritDamage = ProfessionFormulas.effectiveCritDamage(s);
        }

        int columnWidth = (width - COLUMN_GAP) / 2;

        // 6. 第一行：物理攻击 | 魔法攻击
        SB.setLength(0);
        SB.append("物理攻击: ").append(physAttack);
        graphics.text(mc.font, SB.toString(), x, currentY, COLOR_TEXT, false);

        SB.setLength(0);
        SB.append("魔法攻击: ").append(magicAttack);
        graphics.text(mc.font, SB.toString(), x + columnWidth + COLUMN_GAP, currentY, COLOR_TEXT, false);
        currentY += LINE_HEIGHT;

        // 7. 第二行：防御（左列）
        SB.setLength(0);
        SB.append("防御: ").append(defense);
        graphics.text(mc.font, SB.toString(), x, currentY, COLOR_TEXT, false);
        currentY += LINE_HEIGHT;

        // 8. 第三行：暴击率（有效值）| 暴击伤害（有效值）
        //    详细组成通过鼠标悬停 tooltip 展示
        lastCritRateBonus = effectiveCritRate - s.critRate();
        lastCritRatioBonus = effectiveCritDamage - s.critRatio();
        lastCritRateBase = s.critRate();
        lastCritRatioBase = s.critRatio();

        SB.setLength(0);
        SB.append("暴击率: ").append(effectiveCritRate);
        graphics.text(mc.font, SB.toString(), x, currentY, COLOR_TEXT, false);

        SB.setLength(0);
        SB.append("暴击伤害: ").append(effectiveCritDamage);
        graphics.text(mc.font, SB.toString(), x + columnWidth + COLUMN_GAP, currentY, COLOR_TEXT, false);
    }

    /**
     * 鼠标悬停时显示 tooltip：? 图标 → 公式详情；暴击行 → 数值拆分。
     */
    @Override
    public List<Component> getTooltip(double relX, double relY, int width, AttributeSnapshot snapshot) {
        // 检测 ? 图标区域（公式详情）
        if (lastProfession != null
                && relX >= infoIconX && relX < infoIconX + infoIconW
                && relY >= infoIconY && relY < infoIconY + infoIconH) {
            return lastProfession.getFormulaTooltip();
        }

        int columnWidth = (width - COLUMN_GAP) / 2;
        int listTop = HEADER_HEIGHT;
        int row = (int) ((relY - listTop) / LINE_HEIGHT);

        // 仅暴击行（第 3 行）有 tooltip
        if (row != 2) return null;
        // 排除列间隙
        if (relX >= columnWidth && relX < columnWidth + COLUMN_GAP) return null;

        int col = relX < columnWidth ? 0 : 1;
        int critBonus = col == 0 ? lastCritRateBonus : lastCritRatioBonus;
        if (critBonus <= 0) return null;

        int base = col == 0 ? lastCritRateBase : lastCritRatioBase;
        String sourceName = col == 0 ? "敏捷" : "精准";
        List<Component> lines = new ArrayList<>(1);
        lines.add(Component.literal(base + "基础 + " + critBonus + sourceName));
        return lines;
    }

    /** 从快照读取属性值（未注册返回 0） */
    private static int snapshotValue(AttributeSnapshot snapshot, net.minecraft.resources.Identifier id) {
        AttributeData d = snapshot.get(id);
        return d != null ? d.currentValue() : 0;
    }

    /**
     * 从客户端缓存解析当前主职业实例。
     * <p>
     * 通过 {@link ProfessionStateCache} 读取当前主职业 ID，
     * 再通过 {@link RPGSystems#getProfessionSystem()} 查找职业实例。
     *
     * @return 主职业实例，若缓存为空 / 职业模块未加载 / ID 不匹配则返回 null
     */
    private static IProfession resolveCurrentMainProfession() {
        ProfessionStateCache.ProfessionStateView state = ProfessionStateCache.get();
        if (state == null) return null;
        if (!RPGSystems.hasProfessionSystem()) return null;
        Identifier mainId = state.currentMain();
        if (mainId == null) return null;
        return RPGSystems.getProfessionSystem().getProfessionById(mainId);
    }
}
