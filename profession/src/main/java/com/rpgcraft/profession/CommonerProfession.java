package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 平民职业 —— 新角色的默认职业，无属性加成
 */
public class CommonerProfession implements IProfession {

    @Override
    public Identifier getId() {
        return ProfessionManager.COMMONER_ID;
    }

    @Override
    public String getDisplayName() {
        return "平民";
    }

    @Override
    public String getDescription() {
        return "无特殊加成的普通职业";
    }

    @Override
    public boolean isPrimary() {
        return true;
    }

    @Override
    public Map<Identifier, Integer> getBonusMap() {
        return Map.of();
    }
}
