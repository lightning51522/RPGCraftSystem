package com.rpgcraft.core.attribute;

import com.rpgcraft.core.RPGCraftCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 属性快照缓存 Tick 清理器
 * <p>
 * 在每 Tick 末尾（{@link ServerTickEvent.Post}）调用
 * {@link AttributeSnapshotManager#tickCleanup}，
 * 清理脏标记集合和过期缓存条目。
 * <p>
 * 注册在 NeoForge GAME 事件总线上，仅服务端触发。
 *
 * @see AttributeSnapshotManager#tickCleanup
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class SnapshotTickHandler {

    private SnapshotTickHandler() {
    } // 禁止实例化

    /**
     * 服务端 Tick 末尾回调：清理快照缓存
     *
     * @param event 服务端 Tick 事件（Post 阶段）
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        AttributeSnapshotManager.tickCleanup(event.getServer().overworld());
    }
}
