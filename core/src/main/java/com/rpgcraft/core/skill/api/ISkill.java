package com.rpgcraft.core.skill.api;

import com.rpgcraft.core.attribute.AttackType;
import net.minecraft.resources.Identifier;

/**
 * 技能定义接口
 * <p>
 * 由 skills 模块从 datapack JSON 加载实现。定义一个主动技能的全部静态属性：
 * 资源消耗、冷却、伤害、攻击类型、动画引用、作用范围。
 * <p>
 * MVP 范围：仅主动技能的扁平数值定义；不含学习条件、buff、连招等。
 *
 * @see com.rpgcraft.core.registry.ISkillSystem
 */
public interface ISkill {

    /**
     * 技能唯一标识（命名空间 rpgcraftcore，path 来自 datapack 文件名）
     */
    Identifier getId();

    /**
     * 显示名称（datapack 定义）
     */
    String getDisplayName();

    /**
     * 描述文本（datapack 定义）
     */
    String getDescription();

    /**
     * 释放消耗的资源量（从 skill_point 属性扣除）
     */
    int getResourceCost();

    /**
     * 冷却时长（tick）
     */
    int getCooldownTicks();

    /**
     * 对单目标造成的伤害值（扁平，会进入 RPG 公式接受暴击/防御减免）
     */
    int getDamageAmount();

    /**
     * 技能攻击类型
     * <p>
     * 注意：MVP 下实际生效的攻击类型仍由 {@code CombatEventHandler} 根据玩家手持武器解析
     * （走 vanilla {@code target.hurt()} 管线）。此字段供技能面板展示与后续直接伤害路径预留。
     */
    AttackType getAttackType();

    /**
     * PAL 动画资源 ID（命名空间通常是 rpgcraftskills，path 对应 player_animations/*.json）
     */
    Identifier getAnimationId();

    /**
     * 命中范围（方块，玩家前方 AABB 检索半径）
     */
    double getRange();
}
