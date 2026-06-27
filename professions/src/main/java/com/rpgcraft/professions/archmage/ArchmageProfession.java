package com.rpgcraft.professions.archmage;

import com.rpgcraft.core.attribute.AttributeIds;
import com.rpgcraft.core.profession.api.CombatStats;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.professions.base.MageSeriesProfession;
import com.rpgcraft.professions.mage.MageProfession;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 大法师 —— 主职业，进阶自法师（魔法系列进阶树叶子）
 * <p>
 * 智力 +7，暴击伤害 +5；每级智力 +1、暴击伤害 +1。
 * 魔法修习的巅峰，法术兼具威力与致命性。
 */
public class ArchmageProfession extends MageSeriesProfession {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "archmage");

    /** 智力属性 ID */
    private static final Identifier INTELLIGENCE_ID = AttributeIds.INTELLIGENCE_ID;
    /** 暴击伤害属性 ID */
    private static final Identifier CRITICAL_RATIO_ID = AttributeIds.CRITICAL_RATIO_ID;

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

    /**
     * 大法师专属：精准对暴击伤害的加成更高 —— {@code 暴击伤害 + (精准/3)×2}。
     */
    @Override
    public int computeEffectiveCritDamage(CombatStats s) {
        return (int) Math.round(s.critRatio() + (s.precision() / 3.0) * 2);
    }

    @Override
    public List<Component> getFormulaTooltip() {
        return List.of(
                Component.literal("物理攻击 = 力量×2 + 智力"),
                Component.literal("魔法攻击 = 智力×2 + 敏捷×0.5"),
                Component.literal("物理防御 = 力量×2"),
                Component.literal("有效暴击率 = 暴击率 + 敏捷/5"),
                Component.literal("有效暴击伤害 = 暴击伤害 + (精准/3)×2")
        );
    }
}
