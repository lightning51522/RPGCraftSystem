package com.rpgcraft.core.level;

import com.rpgcraft.core.network.SyncPlayerLevelPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 等级模块全局门面
 * <p>
 * 保留对 {@link PlayerLevelData} 附件的静态引用和注册器，
 * 遵循 {@link com.rpgcraft.core.attribute.AttributeManager} 的门面模式。
 */
public class LevelManager {

    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    /** PlayerLevelData 附件 Supplier */
    public static Supplier<AttachmentType<PlayerLevelData>> PLAYER_LEVEL;

    /**
     * 初始化等级模块
     * <p>
     * 创建 DeferredRegister 并注册 PlayerLevelData 附件。
     * 必须在 {@link #getDeferredRegister().register(modEventBus)} 之前调用。
     */
    public static void init() {
        deferredRegister = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_LEVEL = deferredRegister.register("player_level",
                () -> AttachmentType.builder(PlayerLevelData::new)
                        .serialize(PlayerLevelData.CODEC)
                        .build()
        );
    }

    /**
     * 获取底层 DeferredRegister，用于注册到 Mod 事件总线
     */
    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    /**
     * 同步玩家等级数据到客户端
     *
     * @param player 目标玩家（必须为 ServerPlayer）
     */
    public static void syncToClient(ServerPlayer player) {
        PlayerLevelData data = player.getData(PLAYER_LEVEL);
        SyncPlayerLevelPacket.sendToClient(player, data);
    }
}
