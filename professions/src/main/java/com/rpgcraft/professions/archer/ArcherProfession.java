package com.rpgcraft.professions.archer;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.CombatStats;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.profession.ProfessionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 弓箭手 —— 主职业，进阶自平民
 * <p>
 * 敏捷 +5，力量 -3；每级敏捷 +1。
 */
public class ArcherProfession extends AbstractProfession {

    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    private static final Identifier AGILE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");

    public ArcherProfession() {
        super(
                ProfessionManager.ARCHER_ID,
                "弓箭手",
                "敏捷提升，力量降低",
                ProfessionType.PRIMARY,
                ProfessionManager.COMMONER_ID,
                20,
                java.util.Map.of(
                        AGILE_ID, 5,
                        STRENGTH_ID, -3
                ),
                java.util.Map.of(
                        AGILE_ID, 1
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.BOW);
    }

    @Override
    public String getIconChar() {
        return "弓";
    }

    /**
     * 弓箭手（射手系列）：物理攻击 = 力量×1.5 + 敏捷×1.5。
     */
    @Override
    public int computePhysicalAttack(CombatStats s) {
        return (int) Math.round(s.strength() * 1.5 + s.agile() * 1.5);
    }

    @Override
    public List<Component> getFormulaTooltip() {
        return List.of(
                Component.literal("物理攻击 = 力量×1.5 + 敏捷×1.5"),
                Component.literal("魔法攻击 = 智力×2 + 力量"),
                Component.literal("物理防御 = 力量×2"),
                Component.literal("有效暴击率 = 暴击率 + 敏捷/5"),
                Component.literal("有效暴击伤害 = 暴击伤害 + (精准/5)×2")
        );
    }
}
