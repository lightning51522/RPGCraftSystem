package com.rpgcraft.professions.sorcerer;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionIds;
import com.rpgcraft.professions.base.MageSeriesProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 术士 —— 主职业，魔法系列树根，进阶自平民
 * <p>
 * 智力 +5，力量 -3；每级智力 +1。
 * 魔法修习的起点，以智换力。
 */
public class SorcererProfession extends MageSeriesProfession {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sorcerer");

    /** 智力属性 ID */
    private static final Identifier INTELLIGENCE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");
    /** 力量属性 ID */
    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");

    public SorcererProfession() {
        super(
                ID,
                "术士",
                "魔法修习的起点，智力提升而力量降低",
                ProfessionType.PRIMARY,
                ProfessionIds.COMMONER_ID,
                20,
                java.util.Map.of(
                        INTELLIGENCE_ID, 5,
                        STRENGTH_ID, -3
                ),
                java.util.Map.of(
                        INTELLIGENCE_ID, 1
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public String getIconChar() {
        return "术";
    }
}
