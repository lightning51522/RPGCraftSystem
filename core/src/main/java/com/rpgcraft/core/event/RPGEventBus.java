package com.rpgcraft.core.event;

import com.rpgcraft.core.RPGCraftCore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * RPG 事件总线
 * <p>
 * 核心模组和子模块之间的事件通信通道。在关键计算点（伤害、治疗、升级等）
 * 发射事件，已注册的监听器按优先级顺序依次处理。
 * <p>
 * <h3>使用方式</h3>
 * <ul>
 *   <li>核心模组：在计算点调用 {@link #post(RPGEvent)} 发射事件</li>
 *   <li>子模块：通过 {@link #register(Class, RPGEventListener, int)} 注册监听器</li>
 * </ul>
 * <p>
 * <h3>线程安全</h3>
 * 使用 {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList} 保证线程安全。
 * 注册和发射可以并发进行（注册在 mod 初始化阶段，发射在游戏 tick 中）。
 * <p>
 * <h3>设计决策</h3>
 * <ul>
 *   <li>与 NeoForge 事件总线分离 — RPG 内部事件不干扰游戏事件</li>
 *   <li>监听器可取消事件（{@link RPGEvent#cancel()}），核心模组在发射点检查</li>
 *   <li>支持多监听器 — 不同子模块可同时订阅同一事件</li>
 *   <li>优先级排序 — 数值越小越先执行</li>
 * </ul>
 */
public class RPGEventBus {

    /**
     * 注册表：事件类型 Class → 监听器列表（含优先级）
     * <p>
     * CopyOnWriteArrayList 保证注册（mod 初始化）和遍历（游戏 tick）可并发进行。
     */
    private static final Map<Class<? extends RPGEvent>, List<ListenerEntry<?>>> listeners =
            new ConcurrentHashMap<>();

    /**
     * 注册事件监听器
     * <p>
     * 监听器会在 {@link #post(RPGEvent)} 时按优先级顺序被调用。
     * 相同优先级的监听器按注册顺序执行。
     *
     * @param eventType 事件类型的 Class 对象（如 {@code RPGDamageEvent.Pre.class}）
     * @param listener  监听器实现
     * @param priority  优先级（数值越小越先执行），使用 {@link RPGEvent} 中的常量
     * @param <T>       事件类型
     */
    public static <T extends RPGEvent> void register(Class<T> eventType,
                                                      RPGEventListener<T> listener,
                                                      int priority) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new ListenerEntry<>(listener, priority));
        RPGCraftCore.LOGGER.debug("RPG事件监听器已注册: {} (优先级 {})",
                eventType.getSimpleName(), priority);
    }

    /**
     * 注册事件监听器（使用默认优先级 {@link RPGEvent#PRIORITY_NORMAL}）
     *
     * @param eventType 事件类型的 Class 对象
     * @param listener  监听器实现
     * @param <T>       事件类型
     */
    public static <T extends RPGEvent> void register(Class<T> eventType,
                                                      RPGEventListener<T> listener) {
        register(eventType, listener, RPGEvent.PRIORITY_NORMAL);
    }

    /**
     * 发射事件
     * <p>
     * 按优先级从小到大依次调用已注册的监听器。
     * 即使事件被取消（{@link RPGEvent#cancel()}），后续监听器仍会被通知，
     * 以便它们执行清理或日志逻辑。核心模组应在发射后检查 {@link RPGEvent#isCancelled()}。
     *
     * @param event 事件实例
     * @param <T>   事件类型
     */
    @SuppressWarnings("unchecked")
    public static <T extends RPGEvent> void post(T event) {
        List<ListenerEntry<?>> entries = listeners.get(event.getClass());
        if (entries == null || entries.isEmpty()) return;

        // 按优先级排序（稳定排序，相同优先级保持注册顺序）
        // CopyOnWriteArrayList 的迭代器是快照，排序不修改原列表
        List<ListenerEntry<?>> sorted = entries.stream()
                .sorted((a, b) -> Integer.compare(a.priority, b.priority))
                .collect(Collectors.toList());

        for (ListenerEntry<?> entry : sorted) {
            try {
                ((ListenerEntry<T>) entry).listener.onEvent(event);
            } catch (Exception e) {
                RPGCraftCore.LOGGER.error("RPG事件监听器执行异常: {} - {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 移除指定事件类型的所有监听器
     * <p>
     * 主要用于测试清理，正常使用不需要手动移除。
     *
     * @param eventType 事件类型的 Class 对象
     */
    public static void unregisterAll(Class<? extends RPGEvent> eventType) {
        listeners.remove(eventType);
    }

    /**
     * 监听器注册条目（内部类，包装监听器和优先级）
     *
     * @param listener 监听器
     * @param priority 优先级
     * @param <T>      事件类型
     */
    private record ListenerEntry<T extends RPGEvent>(
            RPGEventListener<T> listener,
            int priority
    ) {}
}
