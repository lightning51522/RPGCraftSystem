package com.rpgcraft.core.event.combat;

/**
 * 治疗来源类型枚举
 * <p>
 * 定义模组中所有治疗来源的分类方式，
 * 用于 {@link RPGHealEvent} 中标识治疗的触发途径。
 * <p>
 * <b>已定义的类型：</b>
 * <ul>
 *   <li>{@link #VANILLA} —— 原版治疗（自然回复、药水等通过 LivingHealEvent 触发）</li>
 *   <li>{@link #CUSTOM} —— 模组自定义治疗（通过 CombatEventHandler.healEntity() API 触发）</li>
 * </ul>
 */
public enum HealSource {
    /** 原版治疗：饱食度自然回复、再生药水、治疗药水、信标效果等 */
    VANILLA,
    /** 模组自定义治疗：技能治疗、物品主动使用、吸血效果等 */
    CUSTOM;
}
