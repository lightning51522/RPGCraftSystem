package com.rpgcraft.core.event;

/**
 * RPG 事件监听器函数接口
 * <p>
 * 子模块通过实现此接口来响应 RPG 事件。
 * 使用 {@link RPGEventBus#register(Class, RPGEventListener, int)} 注册。
 * <p>
 * 示例：
 * <pre>{@code
 * RPGEventBus.register(RPGDamageEvent.Pre.class, event -> {
 *     // 闪避逻辑
 *     if (shouldDodge(event.getTarget())) {
 *         event.cancel();
 *     }
 * }, RPGEvent.PRIORITY_EARLY);
 * }</pre>
 *
 * @param <T> 监听的事件类型
 */
@FunctionalInterface
public interface RPGEventListener<T extends RPGEvent> {

    /**
     * 处理 RPG 事件
     *
     * @param event 事件实例，可通过 getter 读取/修改数据
     */
    void onEvent(T event);
}
