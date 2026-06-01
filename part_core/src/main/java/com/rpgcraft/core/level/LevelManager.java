package com.rpgcraft.core.level;

import com.rpgcraft.core.level.api.ILevelCalculator;
import com.rpgcraft.core.level.api.ILevelProvider;
import com.rpgcraft.core.level.api.ILevelRegistry;
import com.rpgcraft.core.network.SyncPlayerLevelPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * 等级模块全局门面
 * <p>
 * 保留对 {@link ILevelRegistry}、{@link ILevelCalculator} 和 {@link PlayerLevelData} 附件的静态引用，
 * 遵循 {@link com.rpgcraft.core.attribute.AttributeManager} 的门面模式。
 * <p>
 * 经验计算器可通过 {@link #setLevelCalculator(ILevelCalculator)} 在运行时替换，
 * 允许子模组提供自定义经验公式。
 */
public class LevelManager {

    private static ILevelRegistry registry;
    private static ILevelCalculator calculator;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    /** PlayerLevelData 附件 Supplier */
    public static Supplier<AttachmentType<PlayerLevelData>> PLAYER_LEVEL;

    /**
     * 初始化等级模块
     * <p>
     * 创建注册中心、默认经验计算器，注册 PlayerLevelData 附件，
     * 并通过 SPI 发现子模组提供的等级数据。
     * 必须在 {@link #getDeferredRegister().register(modEventBus)} 之前调用。
     */
    public static void init() {
        registry = new LevelConfig();
        calculator = new DefaultLevelCalculator();

        deferredRegister = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_LEVEL = deferredRegister.register("player_level",
                () -> AttachmentType.builder(PlayerLevelData::new)
                        .serialize(PlayerLevelData.CODEC)
                        .build()
        );

        // SPI：发现子模组注册的等级经验数据
        for (ILevelProvider provider : ServiceLoader.load(ILevelProvider.class)) {
            provider.registerLevelData(registry);
        }
    }

    /**
     * 获取等级注册中心（经验表）
     */
    public static ILevelRegistry getRegistry() {
        return registry;
    }

    /**
     * 获取经验计算器
     */
    public static ILevelCalculator getLevelCalculator() {
        return calculator;
    }

    /**
     * 替换经验计算器
     * <p>
     * 子模组可调用此方法注入自定义经验公式。
     *
     * @param newCalculator 新的计算器实例
     */
    public static void setLevelCalculator(ILevelCalculator newCalculator) {
        calculator = newCalculator;
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
