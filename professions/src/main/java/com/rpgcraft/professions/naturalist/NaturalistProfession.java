package com.rpgcraft.professions.naturalist;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 博物学家 —— 副职业，进阶自研究员（副职业树叶子）
 * <p>
 * 通晓万物的博学者，副职业进阶终点。图标为书本。本期未配置属性加成。
 */
public class NaturalistProfession extends AbstractProfession {

    public NaturalistProfession() {
        super(
                Identifier.fromNamespaceAndPath("rpgcraftcore", "naturalist"),
                "博物学家",
                "通晓万物的博学者，进阶自研究员",
                ProfessionType.SECONDARY,
                Identifier.fromNamespaceAndPath("rpgcraftcore", "researcher"),
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
        return "博";
    }
}
