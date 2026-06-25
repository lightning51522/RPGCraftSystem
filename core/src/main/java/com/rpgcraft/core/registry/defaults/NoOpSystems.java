package com.rpgcraft.core.registry.defaults;

/**
 * 系统级 SPI 的 core 兜底（no-op）实现集合。
 * <p>
 * 当对应插件模块未加载时，{@link com.rpgcraft.core.registry.RPGSystems} 以这些实现
 * 预填充各系统槽（优先级 {@link com.rpgcraft.core.registry.RPGSystems#FALLBACK_PRIORITY}），
 * 保证 core 能独立运行不抛 {@link IllegalStateException}——与
 * {@code IDamageCalculator} 的透传兜底、{@code ExperienceCurveManager} 的默认曲线同一哲学。
 * <p>
 * 每个兜底实现都会在首次调用时记录一次 WARN 日志，提示"对应模块未加载"，
 * 避免"静默降级"掩盖部署问题。
 *
 * @see NoOpLevelSystem
 * @see NoOpEquipmentSystem
 * @see NoOpCombatSystem
 * @see NoOpAttackTypeResolver
 * @see NoOpMobDataProvider
 * @see NoOpClientSystem
 */
final class NoOpSystems {
    private NoOpSystems() {
    }
}
