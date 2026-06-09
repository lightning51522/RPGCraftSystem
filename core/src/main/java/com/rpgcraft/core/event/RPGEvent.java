package com.rpgcraft.core.event;

/**
 * RPG 事件基类
 * <p>
 * 所有自定义 RPG 事件的基类，提供取消标志和优先级常量。
 * 事件由 {@link RPGEventBus} 分发给已注册的监听器。
 * <p>
 * <h3>优先级约定</h3>
 * <ul>
 *   <li>{@link #PRIORITY_FIRST} (0) — 最先执行，如无敌判定</li>
 *   <li>{@link #PRIORITY_EARLY} (100) — 早期处理，如闪避判定</li>
 *   <li>{@link #PRIORITY_NORMAL} (200) — 默认优先级</li>
 *   <li>{@link #PRIORITY_LATE} (300) — 晚期处理，如伤害日志</li>
 *   <li>{@link #PRIORITY_LAST} (400) — 最后执行，如统计</li>
 * </ul>
 * <p>
 * 子模块通过 {@link RPGEventBus#register(Class, RPGEventListener, int)} 注册监听器并指定优先级。
 * 优先级数值越小越先执行。相同优先级的监听器按注册顺序执行。
 */
public abstract class RPGEvent {

    /** 最先执行 — 如无敌判定、免疫检查 */
    public static final int PRIORITY_FIRST = 0;

    /** 早期处理 — 如闪避判定、护盾吸收 */
    public static final int PRIORITY_EARLY = 100;

    /** 默认优先级 */
    public static final int PRIORITY_NORMAL = 200;

    /** 晚期处理 — 如伤害日志、触发检查 */
    public static final int PRIORITY_LATE = 300;

    /** 最后执行 — 如统计、结算 */
    public static final int PRIORITY_LAST = 400;

    /** 是否已取消 */
    private boolean cancelled = false;

    /**
     * 取消此事件
     * <p>
     * 取消后，{@link RPGEventBus#post(RPGEvent)} 仍然会继续通知后续监听器，
     * 但核心模组应在事件发射点检查 {@link #isCancelled()} 来决定是否跳过默认行为。
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * 查询此事件是否已被取消
     *
     * @return true = 事件已取消，核心模组应跳过默认行为
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
