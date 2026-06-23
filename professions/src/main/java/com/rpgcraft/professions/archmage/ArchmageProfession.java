package com.rpgcraft.professions.archmage;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.professions.mage.MageProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 大法师 —— 主职业，进阶自法师（魔法系列进阶树叶子）
 * <p>
 * 智力 +7，暴击伤害 +5；每级智力 +1、暴击伤害 +1。
 * 魔法修习的巅峰，法术兼具威力与致命性。
 */
public class ArchmageProfession extends AbstractProfession {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "archmage");

    /** 智力属性 ID */
    private static final Identifier INTELLIGENCE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");
    /** 暴击伤害属性 ID */
    private static final Identifier CRITICAL_RATIO_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");

    public ArchmageProfession() {
        super(
                ID,
                "大法师",
                "魔法修习的巅峰，智力与暴击伤害提升，进阶自法师",
                ProfessionType.PRIMARY,
                MageProfession.ID,
                20,
                java.util.Map.of(
                        INTELLIGENCE_ID, 7,
                        CRITICAL_RATIO_ID, 5
                ),
                java.util.Map.of(
                        INTELLIGENCE_ID, 1,
                        CRITICAL_RATIO_ID, 1
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public String getIconChar() {
        return "大";
    }
}
