package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 神射手职业 —— 弓箭手的进阶职业
 * <p>
 * 前置 = 弓箭手（需弓箭手满级 20 才能进阶）。进阶后叠加在弓箭手之上：
 * 神射手提供自身加成（独立 20 级），与弓箭手加成（已满级）共同生效。
 * <p>
 * 敏捷 base +6 / 每级 +1；暴击 base +3 / 每级 +1。
 */
public class MarksmanProfession implements IProfession {

    @Override
    public Identifier getId() {
        return ProfessionManager.MARKSMAN_ID;
    }

    @Override
    public String getDisplayName() {
        return "神射手";
    }

    @Override
    public String getDescription() {
        return "极致的敏捷与暴击，进阶自弓箭手";
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public Identifier getPrerequisite() {
        return ProfessionManager.ARCHER_ID;
    }

    @Override
    public Map<Identifier, Integer> getBonusMap() {
        return Map.of(
                ProfessionAttributes.AGILE_ID, 6,
                ProfessionAttributes.CRITICAL_ID, 3
        );
    }

    @Override
    public int getBonusPerLevel(Identifier attrId) {
        if (attrId.equals(ProfessionAttributes.AGILE_ID)) return 1;
        if (attrId.equals(ProfessionAttributes.CRITICAL_ID)) return 1;
        return 0;
    }
}
