package com.rpgcraft.profession;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.combat.RPGDamageEvent;
import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionCombatContext;
import com.rpgcraft.core.profession.api.ProfessionContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * 职业行为钩子中央调度器（从 {@link ProfessionManager} 抽离）。
 * <p>
 * 职责：把战斗事件（{@link RPGDamageEvent}）与生命周期事件转发给玩家当前生效的职业
 * （主职业 + 全部已激活副职业），依次调用对应钩子（{@code onAttack/onDamaged/onKill}、
 * {@code onLogin/onRespawn}）。
 * <p>
 * 战斗代码（{@code attributes} 模块）通过 {@link RPGEventBus} 发射事件，从不直接依赖职业模块
 * ——这是「插件互不依赖铁律」的关键解耦点，本类是消费侧。
 *
 * @apiNote 包私有；对外由 {@link ProfessionManager} 委托调用。
 */
final class ProfessionHookDispatcher {

    private ProfessionHookDispatcher() {
    }

    /**
     * 注册战斗事件监听器到 {@link RPGEventBus}。
     * <p>
     * 应在 mod 初始化阶段（{@link ProfessionManager#init()}）调用一次。
     * <ul>
     *   <li>Pre：攻击命中前，玩家作为攻击者触发 {@code onAttack}</li>
     *   <li>Post：伤害应用后，攻击者致命命中触发 {@code onKill}，被攻击者触发 {@code onDamaged}</li>
     * </ul>
     */
    static void registerCombatDispatchers() {
        // Pre：攻击命中前。仅处理玩家作为攻击者的 onAttack
        RPGEventBus.register(RPGDamageEvent.Pre.class, event -> {
            LivingEntity attacker = event.getAttacker();
            if (!(attacker instanceof ServerPlayer player)) return;
            dispatchAttack(player, event.getTarget(), event.getDamage(), event.getAttackType());
        });
        // Post：伤害应用后。区分攻击者（onKill）/ 被攻击者（onDamaged）
        RPGEventBus.register(RPGDamageEvent.Post.class, event -> {
            LivingEntity attacker = event.getAttacker();
            LivingEntity target = event.getTarget();
            if (attacker instanceof ServerPlayer atkPlayer) {
                // 攻击者：致命则触发 onKill，否则无额外钩子（onAttack 已在 Pre 阶段触发）
                if (event.isLethal()) {
                    dispatchKill(atkPlayer, target, event.getDamageDealt(), event.getAttackType());
                }
            }
            if (target instanceof ServerPlayer targetPlayer) {
                dispatchDamaged(targetPlayer, attacker, event.getDamageDealt(), event.getAttackType());
            }
        });
    }

    /**
     * 对玩家当前生效的职业（主 + 已激活副）依次触发指定钩子。
     * <p>
     * 参数化合并了 {@code dispatchAttack/dispatchDamaged/dispatchKill} 三处共有的
     * 「遍历主+副、逐个调用钩子」模式：传入钩子方法引用（{@code main / secondary} 是否作为攻击者）。
     */
    private static void forEachActiveProfession(ServerPlayer player,
                                                @Nullable LivingEntity opponent, int damage,
                                                AttackType attackType, boolean isAttacker,
                                                BiConsumer<IProfession, ProfessionCombatContext> hook) {
        ProfessionData data = ProfessionManager.getData(player);
        // 主职业
        IProfession main = ProfessionManager.getRegistry().getProfession(data.getProfessionId());
        if (main != null) {
            hook.accept(main, buildCombatCtx(player, main, opponent, damage, attackType, isAttacker));
        }
        // 已激活副职业
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = ProfessionManager.getRegistry().getProfession(secId);
            if (sec != null) {
                hook.accept(sec, buildCombatCtx(player, sec, opponent, damage, attackType, isAttacker));
            }
        }
    }

    /** Pre 阶段：玩家作为攻击者 → onAttack。注意 Pre 的 damage 是计算前值，可能随后被公式修改。 */
    private static void dispatchAttack(ServerPlayer player, LivingEntity target,
                                       int damage, AttackType attackType) {
        forEachActiveProfession(player, target, damage, attackType, true, IProfession::onAttack);
    }

    private static void dispatchDamaged(ServerPlayer player, @Nullable LivingEntity attacker,
                                        int damage, AttackType attackType) {
        forEachActiveProfession(player, attacker, damage, attackType, false, IProfession::onDamaged);
    }

    private static void dispatchKill(ServerPlayer player, LivingEntity victim,
                                     int damage, AttackType attackType) {
        forEachActiveProfession(player, victim, damage, attackType, true, IProfession::onKill);
    }

    private static ProfessionCombatContext buildCombatCtx(ServerPlayer player, IProfession prof,
                                                          @Nullable LivingEntity opponent, int damage,
                                                          AttackType attackType, boolean isAttacker) {
        return new ProfessionCombatContext(player, prof,
                ProfessionManager.getData(player).getProfessionLevel(prof.getId()),
                opponent, damage, attackType, isAttacker);
    }

    // ----------------------------------------------------------------
    // 生命周期钩子（onLogin / onRespawn）
    // ----------------------------------------------------------------

    /**
     * 触发玩家当前生效职业（主 + 已激活副）的 onLogin 钩子。
     * 由 {@code ProfessionLoginEventHandler} 在加成重建后调用。
     */
    static void notifyLoginHooks(ServerPlayer player) {
        forEachActiveProfessionLifecycle(player, IProfession::onLogin);
    }

    /**
     * 触发玩家当前生效职业（主 + 已激活副）的 onRespawn 钩子。
     * 由 {@code ProfessionSnapshotContributor} 在重生加成重建后调用。
     */
    static void notifyRespawnHooks(ServerPlayer player) {
        forEachActiveProfessionLifecycle(player, IProfession::onRespawn);
    }

    /**
     * 对玩家当前生效的职业（主 + 已激活副）依次触发指定生命周期钩子。
     * 与战斗钩子的差别仅在上下文类型（{@link ProfessionContext} 无战斗字段）。
     */
    private static void forEachActiveProfessionLifecycle(ServerPlayer player,
                                                         java.util.function.BiConsumer<IProfession, ProfessionContext> hook) {
        ProfessionData data = ProfessionManager.getData(player);
        IProfession main = ProfessionManager.getRegistry().getProfession(data.getProfessionId());
        if (main != null) {
            hook.accept(main, new ProfessionContext(player, main, data.getProfessionLevel(main.getId())));
        }
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = ProfessionManager.getRegistry().getProfession(secId);
            if (sec != null) {
                hook.accept(sec, new ProfessionContext(player, sec, data.getProfessionLevel(secId)));
            }
        }
    }
}
