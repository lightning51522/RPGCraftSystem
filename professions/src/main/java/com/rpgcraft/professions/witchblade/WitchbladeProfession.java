package com.rpgcraft.professions.witchblade;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.profession.ProfessionManager;
import com.rpgcraft.professions.mage.MageProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 魔剑士 —— 复合职业，进阶自狂战士 + 法师（双前置均需达满级）
 * <p>
 * 融合物理与法术：同时拥有力量与智力的成长。复合职业在职业面板中单独成树，
 * 通过标题栏 ⇌ 按钮切换查看。解锁后成为当前主职业，复用主职业的属性修饰符管线。
 * <p>
 * 前置要求：狂战士 (berserker) 与法师 (mage) 均 {@link #getMaxLevel() 达满级}。
 * 注意法师是术士系列的中间层，复合职业不要求前置系列达最顶级。
 */
public class WitchbladeProfession extends AbstractProfession {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "witchblade");

    /** 力量属性 ID */
    private static final Identifier STRENGTH_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    /** 智力属性 ID */
    private static final Identifier INTELLIGENCE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");

    public WitchbladeProfession() {
        super(
                ID,
                "魔剑士",
                "融合物理与法术的复合之路，需狂战士与法师皆达满级",
                ProfessionType.COMPOUND,
                // 复合职业无单前置；多前置通过下方 getPrerequisites() 覆写表达
                null,
                20,
                java.util.Map.of(
                        STRENGTH_ID, 4,
                        INTELLIGENCE_ID, 4
                ),
                java.util.Map.of(
                        STRENGTH_ID, 1,
                        INTELLIGENCE_ID, 1
                )
        );
    }

    /**
     * 复合职业双前置：狂战士 + 法师，均需达满级。
     */
    @Override
    public java.util.Set<Identifier> getPrerequisites() {
        return java.util.Set.of(
                ProfessionManager.BERSERKER_ID,
                MageProfession.ID
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public String getIconChar() {
        return "魔";
    }
}
