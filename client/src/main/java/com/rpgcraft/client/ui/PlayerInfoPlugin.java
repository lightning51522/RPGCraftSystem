package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attributepoints.PlayerAttributePoints;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.ui.ICharacterScreenPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家信息插件 —— 角色界面中的等级与职业展示区
 * <p>
 * 显示玩家等级、经验值和职业信息。此插件直接从客户端玩家实体的 Attachment
 * 读取等级和职业数据，
 * 而非从 {@link AttributeSnapshot} 中读取。
 * <p>
 * 布局（从上到下）：
 * <ol>
 *   <li>标题 "玩家信息"（居中，黄色）</li>
 *   <li>等级行：{@code "等级: X (MAX)"} 或 {@code "等级: X  经验: current/next"}</li>
 *   <li>主职业行：{@code "职业: 名称 Lv.X"}（副职业不在此显示，避免多副职业时占用过多空间）</li>
 *   <li>可分配点数行：{@code "可分配点数: N"}（N &gt; 0 时绿色高亮，提示玩家分配）</li>
 *   <li>分隔线</li>
 * </ol>
 *
 * @see ICharacterScreenPlugin
 */
public class PlayerInfoPlugin implements ICharacterScreenPlugin {

    // ====================================================================
    // 布局常量
    // ====================================================================

    /** 标题行高度 */
    private static final int TITLE_HEIGHT = 13;

    /** 每行信息高度 */
    private static final int LINE_HEIGHT = 12;

    /** 分隔线上下方间距 */
    private static final int SEPARATOR_GAP = 2;

    // ====================================================================
    // 颜色常量（ARGB 格式）
    // ====================================================================

    /** 黄色标题（原版强调色） */
    private static final int COLOR_TITLE = 0xFFFFE000;
    /** 白色文本 */
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    /** 灰色分隔线（原版灰阶） */
    private static final int COLOR_SEPARATOR = 0xFF373737;
    /** 绿色高亮（有可分配点数时） */
    private static final int COLOR_HIGHLIGHT = 0xFF55FF55;
    /** 灰色提示（无可分配点数时） */
    private static final int COLOR_HINT = 0xFFA0A0A0;

    /** 复用的 StringBuilder */
    private static final StringBuilder SB = new StringBuilder(64);

    /**
     * 插件总高度（标题 + 等级 + 主职业 + 可分配点数 + 分隔线间距 + 1px 分隔线）。
     * <p>
     * 主职业经验池行仅当 {@code pool > 0} 时显示，故其高度动态。
     * 副职业不在此界面显示（可同时激活多个，全列会占用过多空间；详见职业面板 P 键）。
     */
    @Override
    public int getHeight() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        int extraLines = 0;
        if (player != null) {
            ProfessionData profData = player.getData(
                    RPGSystems.<ProfessionData>getPlayerProfessionAttachment().get());
            if (profData.getSkillPointPool() > 0) extraLines += 1;
        }
        return TITLE_HEIGHT + LINE_HEIGHT * (4 + extraLines) + SEPARATOR_GAP * 2 + 1;
    }

    /**
     * 渲染玩家信息区域
     * <p>
     * 从客户端玩家实体的 Attachment 读取等级和职业数据。
     * 虽然 {@code snapshot} 参数包含属性数据，但等级和职业信息
     * 不在属性快照中，需要直接访问客户端实体。
     *
     * @param graphics 图形上下文
     * @param x        渲染区域左上角 X
     * @param y        渲染区域左上角 Y
     * @param width    渲染区域宽度
     * @param snapshot 属性快照（本插件不使用，等级/职业数据从实体 Attachment 读取）
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y, int width, AttributeSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        int currentY = y;

        // 1. 标题 "玩家信息"
        String title = "玩家信息";
        int titleWidth = mc.font.width(title);
        graphics.text(mc.font, title, x + (width - titleWidth) / 2, currentY, COLOR_TITLE, true);
        currentY += TITLE_HEIGHT;

        // 2. 等级信息
        SB.setLength(0);
        PlayerLevelData levelData = player.getData(
                RPGSystems.<PlayerLevelData>getPlayerLevelAttachment().get());
        int expForNext = levelData.getExpForNextLevel();
        if (expForNext < 0) {
            SB.append("等级: ").append(levelData.getLevel()).append(" (MAX)");
        } else {
            SB.append("等级: ").append(levelData.getLevel())
                    .append("  经验: ").append(levelData.getExperience())
                    .append("/").append(expForNext);
        }
        graphics.text(mc.font, SB.toString(), x, currentY, COLOR_TEXT, false);
        currentY += LINE_HEIGHT;

        // 3. 职业信息（含当前主职业等级 + 副职业）
        ProfessionData profData = player.getData(
                RPGSystems.<ProfessionData>getPlayerProfessionAttachment().get());
        IProfession prof = RPGSystems.getProfessionSystem()
                .getProfessionById(profData.getProfessionId());
        int profLevel = profData.getProfessionLevel(profData.getProfessionId());
        String profText = "职业: " + (prof != null ? prof.getDisplayName() : "未知")
                + " Lv." + Math.max(1, profLevel);
        graphics.text(mc.font, profText, x, currentY, COLOR_TEXT, false);
        currentY += LINE_HEIGHT;
        // 注：副职业不在此界面显示 —— 副职业可同时激活多个，全列会占用过多空间且信息冗余。
        // 副职业详情请查看职业面板（按 P 键）。
        // 可分配职业经验池
        int profPool = profData.getSkillPointPool();
        if (profPool > 0) {
            String poolText = "职业经验: " + profPool;
            graphics.text(mc.font, poolText, x, currentY, COLOR_TITLE, false);
            currentY += LINE_HEIGHT;
        }

        // 4. 可分配属性点数（属性点模块加载时显示；N > 0 绿色高亮提示分配）
        if (RPGSystems.hasAttributePointSystem()) {
            PlayerAttributePoints points = player.getData(
                    RPGSystems.<PlayerAttributePoints>getPlayerAttributePointsAttachment().get());
            int available = points.getAvailablePoints();
            String pointsText = "可分配点数: " + available;
            int pointsColor = available > 0 ? COLOR_HIGHLIGHT : COLOR_HINT;
            graphics.text(mc.font, pointsText, x, currentY, pointsColor, false);
        }
        currentY += LINE_HEIGHT + SEPARATOR_GAP;

        // 5. 分隔线
        graphics.fill(x, currentY, x + width, currentY + 1, COLOR_SEPARATOR);
    }
}
