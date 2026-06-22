package com.rpgcraft.core.profession.api;

/**
 * 职业面板 tooltip 渲染上下文
 * <p>
 * 传递给 {@link IProfession#getTooltip(ProfessionTooltipContext)}，
 * 供职业类根据玩家解锁/等级/激活状态返回自定义的 tooltip 行。
 * <p>
 * 不可变 record。
 *
 * @param level              玩家在本职业的当前等级（未解锁时为 0）
 * @param maxLevel           本职业等级上限
 * @param unlocked           玩家是否已解锁本职业
 * @param isCurrent          是否为玩家当前主职业
 * @param isActiveSecondary  是否为玩家已激活的副职业
 */
public record ProfessionTooltipContext(
        int level,
        int maxLevel,
        boolean unlocked,
        boolean isCurrent,
        boolean isActiveSecondary
) {
}
