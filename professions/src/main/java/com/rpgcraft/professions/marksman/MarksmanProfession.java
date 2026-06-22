package com.rpgcraft.professions.marksman;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.profession.ProfessionManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 神射手 —— 主职业，进阶自弓箭手（进阶树叶子）
 * <p>
 * 敏捷 +6，暴击率 +3；每级敏捷 +1、暴击率 +1。
 */
public class MarksmanProfession extends AbstractProfession {

    private static final Identifier AGILE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    private static final Identifier CRITICAL_RATE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");

    public MarksmanProfession() {
        super(
                ProfessionManager.MARKSMAN_ID,
                "神射手",
                "极致的敏捷与暴击，进阶自弓箭手",
                ProfessionType.PRIMARY,
                ProfessionManager.ARCHER_ID,
                20,
                java.util.Map.of(
                        AGILE_ID, 6,
                        CRITICAL_RATE_ID, 3
                ),
                java.util.Map.of(
                        AGILE_ID, 1,
                        CRITICAL_RATE_ID, 1
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.CROSSBOW);
    }

    @Override
    public String getIconChar() {
        return "神";
    }
}
