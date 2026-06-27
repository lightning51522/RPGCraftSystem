package com.rpgcraft.region.tick;

import com.rpgcraft.region.RegionMod;
import com.rpgcraft.region.RegionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 区域位置 Tick 检查器
 * <p>
 * 在每 Tick 末尾（{@link ServerTickEvent.Post}）以 N tick 节流频率检查所有在线玩家的
 * 位置，触发 {@link RegionManager#updatePlayerRegions} 应用 / 移除区域修饰符。
 * <p>
 * <h3>节流</h3>
 * 玩家位置变化通常是连续的（步行 / 飞行），每 {@link #CHECK_INTERVAL_TICKS} tick
 * （默认 10 tick = 0.5 秒）检查一次足够实时，且大幅降低开销。
 * <p>
 * <h3>登录处理</h3>
 * 玩家上线时 {@link com.rpgcraft.region.data.PlayerRegionState} 为空，首次 tick 检查
 * 会自动补齐当前所在区域的修饰符（无需单独的登录事件）。
 *
 * @see RegionManager#updatePlayerRegions
 */
@EventBusSubscriber(modid = RegionMod.MODID)
public class RegionTickHandler {

    /** 检查间隔（tick），10 tick ≈ 0.5 秒 */
    private static final int CHECK_INTERVAL_TICKS = 10;

    /** tick 计数器（服务端主线程，无需 volatile） */
    private static int tickCounter = 0;

    private RegionTickHandler() {}

    /**
     * 服务端 Tick 末尾回调：节流检查玩家区域
     *
     * @param event 服务端 Tick 事件（Post 阶段）
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 节流：每 CHECK_INTERVAL_TICKS tick 检查一次
        if (++tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                RegionManager.updatePlayerRegions(player);
            } catch (Exception e) {
                // 单个玩家异常不应中断整个循环
                RegionMod.LOGGER.warn("更新玩家 {} 的区域状态失败: {}",
                        player.getName().getString(), e.getMessage());
            }
        }
    }
}
