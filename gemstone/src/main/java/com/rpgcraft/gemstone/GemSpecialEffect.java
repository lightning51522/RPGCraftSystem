package com.rpgcraft.gemstone;

import com.rpgcraft.core.event.combat.RPGDamageEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 宝石特殊效果 SPI
 * <p>
 * 橙及以上稀有度的宝石除属性词条外，还可能携带特殊效果词条（如吸血、反伤、连击）。每类特效
 * 实现本接口并注册到 {@link GemSpecialEffectRegistry}，由 {@link GemCombatEventListener} 在战斗
 * 事件触发时按 effect_id 查询并调用。
 * <p>
 * <b>接入点</b>：通过 core 的 {@code RPGEventBus}（{@code RPGDamageEvent.Pre/Post}）接入，<b>不</b>
 * 直接监听 NeoForge 的 {@code LivingDamageEvent} —— 避免与战斗伤害公式重复/冲突（项目既定架构）。
 * <ul>
 *   <li>{@code onDamagePre}：伤害计算前触发，可增减伤、取消（如护盾吸收、免疫）</li>
 *   <li>{@code onDamagePost}：伤害结算后触发，用于触发型效果（如吸血、反伤、连击）</li>
 * </ul>
 * <p>
 * <b>当前状态（留接口）</b>：本次仅定义接口与注册表框架，不实现任何具体特效。
 * {@link GemCombatEventListener} 已注册到 RPGEventBus 并能正确收集装备上的特效词条 effect_id，
 * 验证管线连通；后续按 effect_id 注册 {@link GemSpecialEffect} 实现即可生效。
 *
 * @see GemSpecialEffectRegistry 特效注册表
 * @see GemCombatEventListener 战斗事件接入
 */
public interface GemSpecialEffect {

    /**
     * 获取特效的唯一标识符
     * <p>
     * 与 {@code socket_gem_affixes.json} 中特效词条的 {@code effect_id} 对应。
     *
     * @return 特效标识符
     */
    Identifier getId();

    /**
     * 伤害计算前触发（attacker 为佩戴宝石的玩家）。
     * <p>
     * 默认空实现。可在此增减伤（{@code event.setDamage}）或取消伤害（{@code event.cancel}）。
     *
     * @param event    伤害前事件
     * @param attacker 佩戴该宝石的玩家（攻击者）
     */
    default void onDamagePre(RPGDamageEvent.Pre event, ServerPlayer attacker) {
    }

    /**
     * 伤害结算后触发（attacker 为佩戴宝石的玩家）。
     * <p>
     * 默认空实现。可在此触发附加效果（吸血、反伤、连击等）。
     *
     * @param event    伤害后事件（伤害已应用到目标）
     * @param attacker 佩戴该宝石的玩家（攻击者）
     */
    default void onDamagePost(RPGDamageEvent.Post event, ServerPlayer attacker) {
    }
}
