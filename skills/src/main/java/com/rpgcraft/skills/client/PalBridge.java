package com.rpgcraft.skills.client;

import com.mojang.logging.LogUtils;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.enums.PlayState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

/**
 * PAL 调用桥（仅客户端）
 * <p>
 * <b>设计目的</b>：隔离所有 PAL 类的直接引用到此类。
 * {@link SkillAnimationHandler} 通过反射检测 PAL 可用后才调用本类方法，
 * 确保本类不会被提前类加载（避免 PAL 缺失时 ClassNotFoundException）。
 * <p>
 * <b>注册时机</b>：layer 工厂通过 {@link #ensureRegistered()} 注册，该方法必须在
 * 客户端 setup 阶段（{@code FMLClientSetupEvent}）调用一次，确保注册早于任何玩家构建。
 * PAL 自身也采用同样的 {@link PlayerAnimationFactory.ANIMATION_DATA_FACTORY} 工厂注册模式
 * （见 PlayerAnimLibMod.init），工厂在客户端玩家构造时由 PAL 自动实例化并加入动画栈。
 * <p>
 * <b>动画触发</b>：通过 {@link PlayerAnimationAccess#getPlayerAnimationLayer} 取回
 * 该玩家对应 layerId 的 controller（由工厂注册时自动存入 playerAssociated map），
 * 调 {@link PlayerAnimationController#triggerAnimation} 播放。
 * <p>
 * <b>注意</b>：本类的所有方法只能在 {@link SkillAnimationHandler#isPalAvailable()}
 * 返回 true 后调用，否则会因 PAL 类缺失而崩溃。
 */
final class PalBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 技能动画 layer 的 ID（用于从 playerAssociated map 取回 controller） */
    private static final Identifier LAYER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftskills", "skill_anim");

    /** 技能动画 layer 的优先级（数值越大越优先覆盖；1000 为 cosmetic 级） */
    private static final int LAYER_PRIORITY = 1000;

    /** 是否已注册 layer 工厂（防止重复注册） */
    private static volatile boolean registered = false;

    private PalBridge() {
    }

    /**
     * 注册技能动画 layer 工厂（幂等）
     * <p>
     * 必须在客户端 setup 阶段调用，保证早于玩家构造。
     * PAL 在每个客户端玩家构造时回调工厂，为该玩家创建 controller 并加入动画栈，
     * 同时以 {@link #LAYER_ID} 为 key 存入 playerAssociated map，供后续取回。
     */
    static void ensureRegistered() {
        if (registered) return;
        registered = true;
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID, LAYER_PRIORITY,
                avatar -> new PlayerAnimationController(avatar,
                        (controller, state, animSetter) -> PlayState.STOP)
        );
        LOGGER.info("[技能动画] PAL layer 工厂已注册 layerId={}", LAYER_ID);
    }

    /**
     * 播放技能动画
     *
     * @param player      客户端玩家
     * @param animationId 动画 ID（对应 player_animations 资源内部的 name）
     */
    static void play(Player player, Identifier animationId) {
        if (!(player instanceof Avatar avatar)) {
            LOGGER.warn("[技能动画] player 不是 Avatar，跳过：{}", player);
            return;
        }

        IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(avatar, LAYER_ID);
        if (!(layer instanceof PlayerAnimationController controller)) {
            LOGGER.warn("[技能动画] layer 未注册或类型不符（layer={}），跳过 id={}",
                    layer, animationId);
            return;
        }

        boolean ok = controller.triggerAnimation(animationId);
        if (ok) {
            LOGGER.info("[技能动画] 触发动画 id={}", animationId);
        } else {
            LOGGER.warn("[技能动画] PAL 找不到动画 id={}（检查 player_animations JSON 键名）", animationId);
        }
    }
}
