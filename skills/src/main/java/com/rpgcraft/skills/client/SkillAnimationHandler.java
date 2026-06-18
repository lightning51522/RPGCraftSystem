package com.rpgcraft.skills.client;

import com.mojang.logging.LogUtils;
import com.rpgcraft.skills.SkillsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * 技能动画播放处理器（仅客户端）
 * <p>
 * 负责在 PAL（Player Animation Library）存在时，为玩家播放技能动画。
 * <p>
 * <h3>PAL 可选依赖设计</h3>
 * PAL 声明为 optional 依赖（mods.toml），modpack 玩家可能未安装。
 * 本类用反射检测 PAL 是否存在（{@link #isPalAvailable()}），
 * PAL 调用隔离到 {@link PalBridge}（仅当 PAL 存在时才类加载，避免 ClassNotFoundException）。
 * PAL 缺失时 {@link #playSkillAnimation} 静默跳过——技能伤害/冷却正常，仅无动画。
 * <p>
 * <h3>layer 注册时机</h3>
 * PAL 的动画 layer 工厂必须在客户端玩家构造前注册（见 {@link PalBridge#ensureRegistered()}）。
 * 本类监听 {@link FMLClientSetupEvent}（mod-loading 阶段，早于任何世界/玩家加载）触发注册，
 * 与 PAL 自身的初始化模式一致。
 * <p>
 * <h3>架构说明</h3>
 * 放在 skills 模块（而非统一的 client 模块）的原因：PAL 依赖只有 skills 模块引入，
 * client 模块不依赖 PAL。本类用 {@code @EventBusSubscriber(value = Dist.CLIENT)} 自动注册。
 *
 * @see PalBridge PAL 调用桥（隔离 PAL 类引用）
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = SkillsMod.MODID)
public class SkillAnimationHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** PAL 是否可用的缓存（首次检测后缓存，避免重复反射） */
    private static volatile Boolean palAvailable = null;

    /**
     * 客户端 setup：在玩家构造前注册 PAL layer 工厂
     * <p>
     * FMLClientSetupEvent 在 mod-loading 阶段触发，早于任何世界加载与玩家构造，
     * 是注册动画 layer 工厂的安全时机（PAL 自身也在此阶段注册）。
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (isPalAvailable()) {
            event.enqueueWork(PalBridge::ensureRegistered);
        }
    }

    /**
     * 检测 PAL 是否在运行时可用
     * <p>
     * 通过尝试加载 PAL 的核心类 {@code PlayerAnimationAccess} 判断。
     * 由于 PAL 是 optional 依赖，运行时可能缺失。
     */
    public static boolean isPalAvailable() {
        if (palAvailable != null) return palAvailable;
        try {
            Class.forName("com.zigythebird.playeranim.api.PlayerAnimationAccess", false,
                    SkillAnimationHandler.class.getClassLoader());
            palAvailable = true;
        } catch (ClassNotFoundException e) {
            palAvailable = false;
            LOGGER.info("PAL（Player Animation Library）未加载，技能动画功能禁用（技能伤害/冷却不受影响）");
        }
        return palAvailable;
    }

    /**
     * 播放技能动画
     * <p>
     * 由 {@link com.rpgcraft.skills.network.PlaySkillAnimationPacket#handle} 在客户端主线程调用。
     * PAL 不可用时静默跳过。
     *
     * @param player      客户端玩家
     * @param animationId 动画 ID（PAL 存在时对应 player_animations 资源）
     */
    public static void playSkillAnimation(Player player, Identifier animationId) {
        boolean available = isPalAvailable();
        LOGGER.info("[技能动画] 请求播放 id={}，PAL available={}", animationId, available);
        if (!available) return;
        try {
            // 委托给 PalBridge（仅 PAL 存在时才类加载，触发 PAL 类的加载）
            PalBridge.play(player, animationId);
        } catch (Throwable t) {
            LOGGER.warn("[技能动画] 播放失败（PAL 调用异常）: {} - {}", animationId, t.getMessage());
        }
    }
}
