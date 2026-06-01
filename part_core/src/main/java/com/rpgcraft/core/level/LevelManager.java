package com.rpgcraft.core.level;

import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.level.api.ILevelCalculator;
import com.rpgcraft.core.level.api.ILevelProvider;
import com.rpgcraft.core.level.api.ILevelRegistry;
import com.rpgcraft.core.level.api.IMobAttributeScaler;
import com.rpgcraft.core.network.SyncPlayerLevelPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * 等级模块全局门面
 * <p>
 * 保留对 {@link ILevelRegistry}、{@link ILevelCalculator}、{@link IMobAttributeScaler}
 * 和 {@link PlayerLevelData}/{@link MobLevelData} 附件的静态引用，
 * 遵循 {@link com.rpgcraft.core.attribute.AttributeManager} 的门面模式。
 * <p>
 * 经验计算器和怪物属性缩放器均可通过 set 方法在运行时替换，
 * 允许子模组提供自定义实现。
 */
public class LevelManager {

    private static ILevelRegistry registry;
    private static ILevelCalculator calculator;
    private static IMobAttributeScaler mobScaler;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    /** PlayerLevelData 附件 Supplier */
    public static Supplier<AttachmentType<PlayerLevelData>> PLAYER_LEVEL;

    /** MobLevelData 附件 Supplier（非序列化） */
    public static Supplier<AttachmentType<MobLevelData>> MOB_LEVEL;

    /**
     * 初始化等级模块
     * <p>
     * 创建注册中心、默认经验计算器、默认怪物属性缩放器，
     * 注册附件类型，并通过 SPI 发现子模组提供的等级数据。
     * 必须在 {@link #getDeferredRegister().register(modEventBus)} 之前调用。
     */
    public static void init() {
        registry = new LevelConfig();
        calculator = new DefaultLevelCalculator();
        mobScaler = new DefaultMobAttributeScaler();

        deferredRegister = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_LEVEL = deferredRegister.register("player_level",
                () -> AttachmentType.builder(PlayerLevelData::new)
                        .serialize(PlayerLevelData.CODEC)
                        .build()
        );

        MOB_LEVEL = deferredRegister.register("mob_level",
                () -> AttachmentType.builder(MobLevelData::new)
                        .build()
        );

        // SPI：发现子模组注册的等级经验数据
        for (ILevelProvider provider : ServiceLoader.load(ILevelProvider.class)) {
            provider.registerLevelData(registry);
        }
    }

    public static ILevelRegistry getRegistry() {
        return registry;
    }

    public static ILevelCalculator getLevelCalculator() {
        return calculator;
    }

    public static void setLevelCalculator(ILevelCalculator newCalculator) {
        calculator = newCalculator;
    }

    public static IMobAttributeScaler getMobScaler() {
        return mobScaler;
    }

    /**
     * 替换怪物属性缩放器
     * <p>
     * 子模组可调用此方法注入自定义等级缩放公式。
     *
     * @param newScaler 新的缩放器实例
     */
    public static void setMobScaler(IMobAttributeScaler newScaler) {
        mobScaler = newScaler;
    }

    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    /**
     * 同步玩家等级数据到客户端
     */
    public static void syncToClient(ServerPlayer player) {
        PlayerLevelData data = player.getData(PLAYER_LEVEL);
        SyncPlayerLevelPacket.sendToClient(player, data);
    }
}
