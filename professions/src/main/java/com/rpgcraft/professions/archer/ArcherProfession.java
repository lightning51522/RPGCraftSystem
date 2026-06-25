package com.rpgcraft.professions.archer;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionIds;
import com.rpgcraft.professions.base.ArcherSeriesProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 弓箭手 —— 主职业，进阶自平民
 * <p>
 * 敏捷 +5，力量 -3；每级敏捷 +1。
 */
public class ArcherProfession extends ArcherSeriesProfession {

    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    private static final Identifier AGILE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");

    public ArcherProfession() {
        super(
                ProfessionIds.ARCHER_ID,
                "弓箭手",
                "敏捷提升，力量降低",
                ProfessionType.PRIMARY,
                ProfessionIds.COMMONER_ID,
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
}
