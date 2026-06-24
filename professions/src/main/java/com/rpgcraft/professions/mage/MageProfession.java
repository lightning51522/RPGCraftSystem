package com.rpgcraft.professions.mage;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.professions.sorcerer.SorcererProfession;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 法师 —— 主职业，进阶自术士
 * <p>
 * 智力 +6，法术穿透 +3；每级智力 +1、法术穿透 +1。
 * 法术造诣加深，开始能穿透法抗。
 */
public class MageProfession extends AbstractProfession {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "mage");

    /** 智力属性 ID */
    private static final Identifier INTELLIGENCE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");
    /** 法术穿透属性 ID */
    private static final Identifier MAGICAL_PENETRATE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "magical_penetrate");

    public MageProfession() {
        super(
                ID,
                "法师",
                "法术造诣加深，智力与法术穿透提升，进阶自术士",
                ProfessionType.PRIMARY,
                SorcererProfession.ID,
                20,
                java.util.Map.of(
                        INTELLIGENCE_ID, 6,
                        MAGICAL_PENETRATE_ID, 3
                ),
                java.util.Map.of(
                        INTELLIGENCE_ID, 1,
                        MAGICAL_PENETRATE_ID, 1
                )
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public String getIconChar() {
        return "法";
    }

    /**
     * 法师专属魔法攻击力公式：{@code 智力×3 + 力量}（强化智力权重）。
     * 默认公式为 {@code 智力×2 + 力量}，法师通过提高智力系数体现法术优势。
     */
    @Override
    public int computeMagicalAttack(int strength, int intelligence) {
        return (int) Math.round(intelligence * 3.0 + strength);
    }

    @Override
    public List<Component> getFormulaTooltip() {
        return List.of(
                Component.literal("物理攻击 = 力量×2 + 智力"),
                Component.literal("魔法攻击 = 智力×3 + 力量"),
                Component.literal("物理防御 = 力量×2"),
                Component.literal("有效暴击率 = 暴击率 + 敏捷/5"),
                Component.literal("有效暴击伤害 = 暴击伤害 + (精准/5)×2")
        );
    }
}
