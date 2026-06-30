package com.rpgcraft.gemstone;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宝石特殊效果注册表
 * <p>
 * 管理 {@link GemSpecialEffect} 注册表，供 {@link GemCombatEventListener} 按 effect_id 查询实现。
 * 使用 {@link ConcurrentHashMap} 保证线程安全（注册在模组初始化阶段，查询在战斗事件线程）。
 * <p>
 * <b>当前状态（留接口）</b>：本次不注册任何特效实现，注册表为空。{@link GemCombatEventListener}
 * 查询时 {@link #get(Identifier)} 恒返回 {@code null}，监听器空跑 —— 仅验证管线连通。
 * 后续实现具体特效（如吸血、反伤）时，调用 {@link #register(GemSpecialEffect)} 注册即可。
 *
 * @see GemSpecialEffect 特效接口
 * @see GemCombatEventListener 战斗事件接入（按 effect_id 查询本注册表）
 */
public final class GemSpecialEffectRegistry {

    /** 已注册的特效，按 effect_id 索引。 */
    private static final Map<Identifier, GemSpecialEffect> effects = new ConcurrentHashMap<>();

    private GemSpecialEffectRegistry() {
        // 禁止实例化
    }

    /**
     * 注册宝石特殊效果
     * <p>
     * 在模组初始化阶段调用。重复注册同一 effect_id 会覆盖并输出 WARN。
     *
     * @param effect 特效实现
     */
    public static void register(GemSpecialEffect effect) {
        Identifier id = effect.getId();
        if (effects.putIfAbsent(id, effect) != null) {
            GemstoneMod.LOGGER.warn("宝石特效 {} 已注册，新注册覆盖旧实现", id);
            effects.put(id, effect);
        }
    }

    /**
     * 按 effect_id 查询特效实现。
     *
     * @param effectId 特效标识符（与 socket_gem_affixes.json 的 effect_id 对应）
     * @return 特效实现；未注册返回 {@code null}
     */
    public static @Nullable GemSpecialEffect get(Identifier effectId) {
        return effects.get(effectId);
    }

    /** 获取所有已注册的特效（不可变视图）。 */
    public static Map<Identifier, GemSpecialEffect> getAll() {
        return Collections.unmodifiableMap(effects);
    }
}
