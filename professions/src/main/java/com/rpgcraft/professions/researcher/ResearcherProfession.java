package com.rpgcraft.professions.researcher;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.professions.scholar.ScholarProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 研究员 —— 副职业，进阶自学者
 * <p>
 * 系统化的研究者，在学者基础上深入学术。图标为书本。本期未配置属性加成。
 */
public class ResearcherProfession extends AbstractProfession {

    public ResearcherProfession() {
        super(
                Identifier.fromNamespaceAndPath("rpgcraftcore", "researcher"),
                "研究员",
                "系统化的研究者，进阶自学者",
                ProfessionType.SECONDARY,
                ScholarProfession.ID,
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
        return "研";
    }
}
