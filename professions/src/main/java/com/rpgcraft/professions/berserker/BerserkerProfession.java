package com.rpgcraft.professions.berserker;

import com.rpgcraft.core.profession.api.ProfessionCombatContext;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.profession.ProfessionManager;
import com.rpgcraft.professions.base.WarriorSeriesProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 狂战士 —— 主职业，进阶自战士（进阶树叶子）
 * <p>
 * 力量 +6，生命 +10；每级力量 +1、生命 +2。
 * 战斗特性：击杀后回复生命（嗜血）。
 */
public class BerserkerProfession extends WarriorSeriesProfession {

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
     */
    @Override
    public void onKill(ProfessionCombatContext ctx) {
        int healAmount = Math.max(1, ctx.level() / 2);
        // TODO: 通过 RPGHealEvent 或直接回复玩家 LIFE 属性
    }
}
