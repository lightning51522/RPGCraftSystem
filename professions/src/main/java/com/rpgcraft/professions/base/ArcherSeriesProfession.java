package com.rpgcraft.professions.base;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.CombatStats;
import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 弓箭手系列职业基类 —— 封装系列共享的属性公式。
 * <p>
 * 物理攻击 = {@code 力量×1.5 + 敏捷×1.5}。
 * 暴击使用默认公式（敏捷/5、精准/5）。
 * <p>
 * 叶子职业（弓箭手、神射手）继承本类。神射手额外覆写暴击率公式（敏捷/3）。
 */
public abstract class ArcherSeriesProfession extends AbstractProfession {

    protected ArcherSeriesProfession(Identifier id, String displayName, String description,
                                     ProfessionType type, @Nullable Identifier prerequisite, int maxLevel,
                                     Map<Identifier, Integer> baseBonuses, Map<Identifier, Integer> perLevel) {
        super(id, displayName, description, type, prerequisite, maxLevel, baseBonuses, perLevel);
    }

    @Override
    public int computePhysicalAttack(CombatStats s) {
        return (int) Math.round(s.strength() * 1.5 + s.agile() * 1.5);
    }

    @Override
    public List<Component> getFormulaTooltip() {
        return List.of(
                Component.literal("物理攻击 = 力量×1.5 + 敏捷×1.5"),
                Component.literal("魔法攻击 = 智力×2 + 力量"),
                Component.literal("物理防御 = 力量×2"),
                Component.literal("有效暴击率 = 暴击率 + 敏捷/5"),
                Component.literal("有效暴击伤害 = 暴击伤害 + (精准/5)×2")
        );
    }
}
