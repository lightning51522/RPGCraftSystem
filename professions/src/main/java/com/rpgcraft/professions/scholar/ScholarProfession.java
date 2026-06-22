package com.rpgcraft.professions.scholar;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 学者 —— 副职业树的根
 * <p>
 * 知识探索者，副职业进阶起点。图标为书本。本期未配置属性加成（待后续设计）。
 */
public class ScholarProfession extends AbstractProfession {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "scholar");

    public ScholarProfession() {
        super(
                ID,
                "学者",
                "知识探索者，副职业进阶的起点",
                ProfessionType.SECONDARY,
                null,
                20,
                java.util.Map.of(),
                java.util.Map.of()
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public String getIconChar() {
        return "学";
    }
}
