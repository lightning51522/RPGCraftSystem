package com.rpgcraft.professions.warrior;

import com.rpgcraft.core.profession.api.ProfessionCombatContext;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionIds;
import com.rpgcraft.professions.base.WarriorSeriesProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 战士 —— 主职业，进阶自平民
 * <p>
 * 力量 +5，敏捷 -3；每级力量 +1。
 * 战斗特性：攻击有一定概率触发额外伤害（暴怒之力）。
 */
public class WarriorProfession extends WarriorSeriesProfession {

    /** 力量属性 ID */
    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    /** 敏捷属性 ID */
    private static final Identifier AGILE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");

    public WarriorProfession() {
        super(
                ProfessionIds.WARRIOR_ID,
                "战士",
                "力量提升，敏捷降低",
                ProfessionType.PRIMARY,
                ProfessionIds.COMMONER_ID,
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
     */
    @Override
    public void onAttack(ProfessionCombatContext ctx) {
        float chance = 0.005f * ctx.level();
        if (ctx.player().getRandom().nextFloat() < chance) {
            // TODO: 触发额外伤害
        }
    }
}
