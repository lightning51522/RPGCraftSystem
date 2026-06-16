package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 狂战士职业 —— 战士的进阶职业
 * <p>
 * 前置 = 战士（需战士满级 20 才能进阶）。进阶后叠加在战士之上：
 * 狂战士提供自身加成（独立 20 级），与战士加成（已满级）共同生效。
 * <p>
 * 力量 base +6 / 每级 +1；生命 base +10 / 每级 +2。
 */
public class BerserkerProfession implements IProfession {

    @Override
    public Identifier getId() {
        return ProfessionManager.BERSERKER_ID;
    }

    @Override
    public String getDisplayName() {
        return "狂战士";
    }

    @Override
    public String getDescription() {
        return "极致的力量与生命，进阶自战士";
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public Identifier getPrerequisite() {
        return ProfessionManager.WARRIOR_ID;
    }

    @Override
    public Map<Identifier, Integer> getBonusMap() {
        return Map.of(
                ProfessionAttributes.STRENGTH_ID, 6,
                ProfessionAttributes.LIFE_ID, 10
        );
    }

    @Override
    public int getBonusPerLevel(Identifier attrId) {
        if (attrId.equals(ProfessionAttributes.STRENGTH_ID)) return 1;
        if (attrId.equals(ProfessionAttributes.LIFE_ID)) return 2;
        return 0;
    }
}
