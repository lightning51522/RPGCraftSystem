package com.rpgcraft.skills;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.skill.api.ISkill;
import net.minecraft.resources.Identifier;

/**
 * 由 datapack JSON 定义的技能实现。
 * <p>
 * 所有字段在构造时从 JSON 解析得到，运行时不可变。新增/修改技能只需增改
 * {@code data/rpgcraftcore/rpg/skills/*.json}，无需改动 Java 代码。
 * <p>
 * 由 {@link SkillsDefinitionLoader} 解析 JSON 后构造并注册到 {@link SkillsRegistry}。
 *
 * @see ISkill
 * @see SkillsDefinitionLoader
 */
public final class JsonSkill implements ISkill {

    private final Identifier id;
    private final String displayName;
    private final String description;
    private final int resourceCost;
    private final int cooldownTicks;
    private final int damageAmount;
    private final AttackType attackType;
    private final Identifier animationId;
    private final double range;

    /**
     * @param id           技能 ID（命名空间 rpgcraftcore，path 来自文件名）
     * @param displayName  显示名
     * @param description  描述
     * @param resourceCost 释放消耗的 skill_point 量
     * @param cooldownTicks 冷却 tick
     * @param damageAmount 单目标伤害值（扁平，进入 RPG 公式接受暴击/防御减免）
     * @param attackType   攻击类型（MVP 下实际生效仍由玩家手持武器解析，此字段供展示与预留）
     * @param animationId  PAL 动画资源 ID
     * @param range        命中范围（方块）
     */
    public JsonSkill(Identifier id, String displayName, String description,
                     int resourceCost, int cooldownTicks, int damageAmount,
                     AttackType attackType, Identifier animationId, double range) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.resourceCost = resourceCost;
        this.cooldownTicks = cooldownTicks;
        this.damageAmount = damageAmount;
        this.attackType = attackType;
        this.animationId = animationId;
        this.range = range;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getResourceCost() {
        return resourceCost;
    }

    @Override
    public int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public int getDamageAmount() {
        return damageAmount;
    }

    @Override
    public AttackType getAttackType() {
        return attackType;
    }

    @Override
    public Identifier getAnimationId() {
        return animationId;
    }

    @Override
    public double getRange() {
        return range;
    }
}
