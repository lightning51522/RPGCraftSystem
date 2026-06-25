package com.rpgcraft.core.registry;

import com.rpgcraft.core.skill.api.ISkill;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * 技能系统接口
 * <p>
 * 由 skills 模块注册实现，提供技能查询、释放校验、冷却管理、客户端同步等能力。
 * 供其他模块（命令系统、客户端渲染、快照恢复）通过 {@link RPGSystems} 访问。
 * <p>
 * MVP 范围：主动技能基础闭环（按键释放 → 资源消耗 → 冷却 → 动画 → vanilla hurt 伤害）。
 *
 * @see RPGSystems#registerSkillSystem(ISkillSystem)
 * @see RPGSystems#getSkillSystem()
 */
public interface ISkillSystem {

    // ====================================================================
    // 基础查询
    // ====================================================================

    /**
     * 同步玩家技能数据到客户端（冷却 + 已学技能）
     */
    void syncToClient(ServerPlayer player);

    /**
     * 根据 ID 查询技能（不存在返回 null）
     */
    @Nullable
    ISkill getSkillById(Identifier id);

    /**
     * 获取所有已注册的技能
     */
    Collection<ISkill> getAllSkills();

    // ====================================================================
    // 释放校验与执行（canXxx / doXxx 配对，与 IProfessionSystem 一致）
    // ====================================================================

    /**
     * 是否可以释放某技能
     * <ul>
     *   <li>技能存在</li>
     *   <li>冷却已结束</li>
     *   <li>{@code skill_point} 当前值 ≥ 资源消耗</li>
     *   <li>已学习该技能（MVP 全部视为已学习）</li>
     * </ul>
     */
    boolean canCast(ServerPlayer player, Identifier skillId);

    /**
     * 释放技能。失败返回 false。
     * <p>
     * 执行 MVP 闭环：扣资源 → 启动冷却 → 播放动画包 → 对前方目标造成 vanilla hurt（由 CombatEventHandler 接管 RPG 公式）。
     */
    boolean cast(ServerPlayer player, Identifier skillId);

    // ====================================================================
    // 冷却查询（供 UI 与命令使用）
    // ====================================================================

    /**
     * 技能是否处于冷却中
     */
    boolean isOnCooldown(ServerPlayer player, Identifier skillId);

    /**
     * 技能剩余冷却 tick（0 表示可用）
     */
    long getRemainingCooldown(ServerPlayer player, Identifier skillId);

    /**
     * 重置玩家全部技能冷却（GM 调试）
     */
    void resetAllCooldowns(ServerPlayer player);
}
