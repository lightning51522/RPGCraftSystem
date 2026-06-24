package com.rpgcraft.professions.berserker;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.CombatStats;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionCombatContext;
import com.rpgcraft.profession.ProfessionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 狂战士 —— 主职业，进阶自战士（进阶树叶子）
 * <p>
 * 力量 +6，生命 +10；每级力量 +1、生命 +2。
 * 战斗特性：击杀后回复生命（嗜血）。
 */
public class BerserkerProfession extends AbstractProfession {

    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    private static final Identifier LIFE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "life");

    public BerserkerProfession() {
        super(
                ProfessionManager.BERSERKER_ID,
                "狂战士",
                "极致的力量与生命，进阶自战士",
                ProfessionType.PRIMARY,
                ProfessionManager.WARRIOR_ID,
                20,
                java.util.Map.of(
                        STRENGTH_ID, 6,
                        LIFE_ID, 10
                ),
                java.util.Map.of(
                        STRENGTH_ID, 1,
                        LIFE_ID, 2
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.DIAMOND_AXE);
    }

    @Override
    public String getIconChar() {
        return "狂";
    }

    /**
     * 狂战士击杀钩子：击杀后回复生命。
     * <p>
     * 回复量随等级线性增长（1 级回 1 点 → 20 级回 10 点）。
     * 当前保留钩子骨架，具体治疗逻辑待治疗系统接入。
     */
    @Override
    public void onKill(ProfessionCombatContext ctx) {
        int healAmount = Math.max(1, ctx.level() / 2);
        // TODO: 通过 RPGHealEvent 或直接回复玩家 LIFE 属性
        // 当前保留钩子骨架，具体效果待治疗系统接入
    }

    /**
     * 狂战士（战士系列）：物理攻击 = 力量×2.5 + 智力。
     */
    @Override
    public int computePhysicalAttack(CombatStats s) {
        return (int) Math.round(s.strength() * 2.5 + s.intelligence());
    }

    @Override
    public int computePhysicalDefense(CombatStats s) {
        return (int) Math.round(s.strength() * 2.5);
    }

    @Override
    public List<Component> getFormulaTooltip() {
        return List.of(
                Component.literal("物理攻击 = 力量×2.5 + 智力"),
                Component.literal("魔法攻击 = 智力×2 + 力量"),
                Component.literal("物理防御 = 力量×2.5"),
                Component.literal("有效暴击率 = 暴击率 + 敏捷/5"),
                Component.literal("有效暴击伤害 = 暴击伤害 + (精准/5)×2")
        );
    }
}
