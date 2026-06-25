package com.rpgcraft.professions.commoner;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 平民 —— 主职业树的唯一根
 * <p>
 * 无任何属性加成，是所有主职业的进阶起点。新玩家默认绑定本职业（见
 * {@code ProfessionData} 默认构造）。
 */
public class CommonerProfession extends AbstractProfession {

    public CommonerProfession() {
        super(
                ProfessionIds.COMMONER_ID,
                "平民",
                "无特殊加成的普通职业",
                ProfessionType.PRIMARY,
                null,
                20,
                java.util.Map.of(),
                java.util.Map.of()
        );
    }

    @Override
    public ItemStack getIconItem() {
        return new ItemStack(Items.WOODEN_HOE);
    }

    @Override
    public String getIconChar() {
        return "民";
    }
}
