package com.rpgcraft.professions.warrior;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionCombatContext;
import com.rpgcraft.profession.ProfessionManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 战士 —— 主职业，进阶自平民
 * <p>
 * 力量 +5，敏捷 -3；每级力量 +1。
 * 战斗特性：攻击有一定概率触发额外伤害（暴怒之力）。
 */
public class WarriorProfession extends AbstractProfession {

    /** 力量属性 ID */
    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    /** 敏捷属性 ID */
    private static final Identifier AGILE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");

    public WarriorProfession() {
        super(
                ProfessionManager.WARRIOR_ID,
                "战士",
                "力量提升，敏捷降低",
                ProfessionType.PRIMARY,
                ProfessionManager.COMMONER_ID,
                20,
                java.util.Map.of(
                        STRENGTH_ID, 5,
                        AGILE_ID, -3
                ),
                java.util.Map.of(
                        STRENGTH_ID, 1
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    public String getIconChar() {
        return "战";
    }

    /**
     * 战士战斗钩子示例：每次攻击有一定概率追加额外伤害。
     * <p>
     * 概率随等级线性增长（1 级 1% → 20 级 10%）。
     * 实际额外伤害效果可通过修改目标属性或发射子事件实现，
     * 当前仅作为钩子调用的演示（具体效果由子类或后续实现补全）。
     */
    @Override
    public void onAttack(ProfessionCombatContext ctx) {
        // 概率 = 0.005 × level，1 级 0.5%，20 级 10%
        float chance = 0.005f * ctx.level();
        if (ctx.player().getRandom().nextFloat() < chance) {
            // TODO: 触发额外伤害（如对目标施加固定值伤害修饰符）
            // 当前保留钩子骨架，具体效果待效果系统接入
        }
    }

    /**
     * 战士专属物理攻击力公式：{@code 力量×3 + 智力}（强化力量权重）。
     * 默认公式为 {@code 力量×2 + 智力}，战士通过提高力量系数体现近战优势。
     */
    @Override
    public int computePhysicalAttack(int strength, int intelligence) {
        return strength * 3 + intelligence;
    }

    /**
     * 战士专属物理防御力公式：{@code 力量×3}（强化防御系数）。
     * 默认公式为 {@code 力量×2}，战士获得更高的物理防御派生。
     */
    @Override
    public int computePhysicalDefense(int strength, int intelligence) {
        return strength * 3;
    }
}
