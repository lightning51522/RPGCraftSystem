package com.rpgcraft.core.profession;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.attribute.AttributeManager;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 弓箭手职业 —— 增强敏捷，削弱力量
 * <p>
 * 敏捷 +5，力量 -3
 */
public class ArcherProfession implements IProfession {

    @Override
    public Identifier getId() {
        return ProfessionManager.ARCHER_ID;
    }

    @Override
    public String getDisplayName() {
        return "弓箭手";
    }

    @Override
    public String getDescription() {
        return "敏捷提升，力量降低";
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public Map<Identifier, Integer> getBonusMap() {
        return Map.of(
                AttributeManager.AGILE_ID, 5,
                AttributeManager.STRENGTH_ID, -3
        );
    }
}
