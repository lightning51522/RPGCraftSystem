package com.rpgcraft.core.profession.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * 职业生命周期钩子参数
 * <p>
 * 封装传递给 {@link IProfession} 生命周期回调（{@link IProfession#onLevelUp}、
 * {@link IProfession#onAdvance}、{@link IProfession#onActivate}、
 * {@link IProfession#onDeactivate}、{@link IProfession#onLogin}、
 * {@link IProfession#onRespawn}）的上下文。
 * <p>
 * 不可变 record。钩子实现可通过本对象拿到触发事件的玩家、本职业实例、当前等级。
 *
 * @param player     触发事件的玩家
 * @param profession 触发钩子的职业实例（即被回调的职业自身）
 * @param level      玩家在本职业的当前等级（钩子触发时的快照）
 */
public record ProfessionContext(
        ServerPlayer player,
        IProfession profession,
        int level
) {
}
