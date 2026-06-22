package com.rpgcraft.professions.apprentice;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.profession.ProfessionManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 学徒 —— 副职业占位
 * <p>
 * 当无任何具体副职业定义时的占位，保留副职业交互流程的可体验性。
 * 未来第三方可通过 {@code @Mod} 模块注册真正的副职业替换本占位。
 */
public class ApprenticeProfession extends AbstractProfession {

    public ApprenticeProfession() {
        super(
                ProfessionManager.APPRENTICE_ID,
                "学徒",
                "无特殊加成的副职业占位",
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
        return "徒";
    }
}
