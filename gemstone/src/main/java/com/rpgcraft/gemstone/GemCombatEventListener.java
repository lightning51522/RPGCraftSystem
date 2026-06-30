package com.rpgcraft.gemstone;

import com.rpgcraft.core.equipment.GemInstance;
import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.event.RPGEvent;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.combat.RPGDamageEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 宝石战斗事件监听器
 * <p>
 * 向 core 的 {@link RPGEventBus} 注册 {@link RPGDamageEvent.Pre/Post} 监听器，在玩家造成伤害时
 * 遍历其装备上镶嵌的宝石，收集特效词条的 effect_id 并调用对应 {@link GemSpecialEffect}。
 * <p>
 * <b>接入 RPGEventBus 而非 NeoForge LivingDamageEvent</b>：项目战斗伤害统一走自定义 RPG 事件总线
 * （{@code CombatEventHandler} 在 {@code LivingDamageEvent.Pre} 内派发 {@code RPGDamageEvent}），
 * 直接监听原生事件会与战斗公式重复/冲突。本监听器只在玩家作为<b>攻击者</b>时触发（佩戴宝石
 * 影响的是攻击者的输出，如增伤/吸血）。
 * <p>
 * <b>当前状态</b>：{@link GemSpecialEffectRegistry} 无任何实现，{@code get} 恒返回 null，
 * 监听器空跑 —— 验证管线连通。后续注册特效实现后自动生效。
 *
 * @see GemSpecialEffect 特效接口
 * @see GemSpecialEffectRegistry 特效注册表
 */
public final class GemCombatEventListener {

    private GemCombatEventListener() {
        // 禁止实例化
    }

    /**
     * 注册战斗事件监听器到 RPGEventBus。
     * <p>
     * 由 {@link GemstoneManager#init()} 调用。使用默认优先级 {@link RPGEvent#PRIORITY_NORMAL}。
     */
    public static void register() {
        RPGEventBus.register(RPGDamageEvent.Pre.class, GemCombatEventListener::onDamagePre);
        RPGEventBus.register(RPGDamageEvent.Post.class, GemCombatEventListener::onDamagePost);
    }

    /** 伤害计算前：触发攻击者装备宝石的 Pre 特效（增减伤/取消）。 */
    private static void onDamagePre(RPGDamageEvent.Pre event) {
        // 仅处理有攻击者的战斗伤害（跳过环境伤害如摔落）
        if (!(event.getAttacker() instanceof ServerPlayer attacker)) return;
        collectSpecialEffects(attacker).forEach(effectId -> {
            GemSpecialEffect effect = GemSpecialEffectRegistry.get(effectId);
            if (effect != null) {
                try {
                    effect.onDamagePre(event, attacker);
                } catch (Exception e) {
                    GemstoneMod.LOGGER.error("宝石特效 [{}] onDamagePre 失败: {}",
                            effectId, e.getMessage(), e);
                }
            }
        });
    }

    /** 伤害结算后：触发攻击者装备宝石的 Post 特效（吸血/反伤/连击）。 */
    private static void onDamagePost(RPGDamageEvent.Post event) {
        if (!(event.getAttacker() instanceof ServerPlayer attacker)) return;
        collectSpecialEffects(attacker).forEach(effectId -> {
            GemSpecialEffect effect = GemSpecialEffectRegistry.get(effectId);
            if (effect != null) {
                try {
                    effect.onDamagePost(event, attacker);
                } catch (Exception e) {
                    GemstoneMod.LOGGER.error("宝石特效 [{}] onDamagePost 失败: {}",
                            effectId, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * 收集玩家装备上所有镶嵌宝石携带的特效词条 effect_id（去重）。
     * <p>
     * 遍历所有装备槽，读取 {@code EQUIPMENT_SOCKET} 组件（镶嵌的那颗宝石），对其中的特效词条
     * 收集 effect_id。使用 {@link LinkedHashSet} 去重并保持稳定顺序（同一 effect 多件装备触发
     * 只调用一次）。
     *
     * @param attacker 攻击者玩家
     * @return 特效 effect_id 集合（去重）
     */
    private static Set<Identifier> collectSpecialEffects(ServerPlayer attacker) {
        Set<Identifier> effectIds = new LinkedHashSet<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = attacker.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            GemInstance gem = stack.get(RPGComponents.EQUIPMENT_SOCKET.get());
            if (gem == null) continue;
            for (Identifier affixId : gem.affixIds()) {
                Identifier effectId = SocketGemConfig.getSpecialEffectId(affixId);
                if (effectId != null) {
                    effectIds.add(effectId);
                }
            }
        }
        return effectIds;
    }
}
