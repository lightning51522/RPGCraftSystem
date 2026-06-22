package com.rpgcraft.core.profession.api;

import com.rpgcraft.core.attribute.AttackType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 职业战斗钩子参数
 * <p>
 * 封装传递给 {@link IProfession} 战斗回调（{@link IProfession#onAttack}、
 * {@link IProfession#onDamaged}、{@link IProfession#onKill}）的上下文。
 * <p>
 * 不可变 record。{@code isAttacker} 区分玩家是攻击者还是被攻击者：
 * <ul>
 *   <li>{@code true}：玩家是攻击者，{@link #opponent} 是被攻击目标，
 *       回调为 {@link IProfession#onAttack}（命中后）/ {@link IProfession#onKill}（击杀后）</li>
 *   <li>{@code false}：玩家是被攻击者，{@link #opponent} 是攻击者，
 *       回调为 {@link IProfession#onDamaged}</li>
 * </ul>
 * 对于环境伤害（摔落/溺水/火焰等），{@code opponent} 可能为 {@code null}。
 *
 * @param player     触发事件的玩家
 * @param profession 触发钩子的职业实例
 * @param level      玩家在本职业的当前等级
 * @param opponent   对手实体（攻击者场景为被攻击目标，被攻击场景为攻击者），环境伤害时为 null
 * @param damage     本次伤害数值（攻击场景：造成的伤害；被攻击场景：承受的伤害）
 * @param attackType 攻击类型（PHYSICAL/MAGIC）
 * @param isAttacker {@code true}=玩家是攻击者，{@code false}=玩家是被攻击者
 */
public record ProfessionCombatContext(
        ServerPlayer player,
        IProfession profession,
        int level,
        @Nullable LivingEntity opponent,
        int damage,
        AttackType attackType,
        boolean isAttacker
) {
}
