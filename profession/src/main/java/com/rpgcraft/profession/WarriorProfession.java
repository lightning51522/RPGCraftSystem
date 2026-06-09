package com.rpgcraft.profession;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 战士职业 —— 增强力量，削弱敏捷
 * <p>
 * 力量 +5，敏捷 -3
 */
public class WarriorProfession implements IProfession {

    @Override
    public Identifier getId() {
        return ProfessionManager.WARRIOR_ID;
    }

    @Override
    public String getDisplayName() {
        return "战士";
    }

    @Override
    public String getDescription() {
        return "力量提升，敏捷降低";
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public Map<Identifier, Integer> getBonusMap() {
        return Map.of(
                AttributeManager.STRENGTH_ID, 5,
                AttributeManager.AGILE_ID, -3
        );
    }
}
