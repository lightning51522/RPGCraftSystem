package com.rpgcraft.leveling;

import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.core.level.api.IMobAttributeScaler;
import com.rpgcraft.core.level.api.ILevelRegistry;
import com.rpgcraft.core.level.api.ILevelCalculator;
import com.rpgcraft.core.level.api.ILevelProvider;
import com.rpgcraft.leveling.network.SyncPlayerLevelPacket;
import com.rpgcraft.core.registry.ILevelSystem;
import com.rpgcraft.core.registry.IMobDataProvider;
import com.rpgcraft.core.registry.RPGSystems;
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
 *
 * @apiNote 内部 API — 第三方模组应通过 {@link RPGSystems} 门面访问等级系统功能，
 *          不应直接依赖此类。
 */
public class LevelManager {

    private static ILevelRegistry registry;
    private static ILevelCalculator calculator;
    private static IMobAttributeScaler mobScaler;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    /** PlayerLevelData 附件 Supplier */
    public static Supplier<AttachmentType<PlayerLevelData>> PLAYER_LEVEL;

    /** MobLevelData 附件 Supplier（序列化到实体 NBT，确保自定义怪物数据跨 chunk 重载持久化） */
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

        // 注入经验表注册中心到 PlayerLevelData（core 中的数据类）
        PlayerLevelData.setRegistry(registry);

        deferredRegister = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_LEVEL = deferredRegister.register("player_level",
                () -> AttachmentType.builder(PlayerLevelData::new)
                        .serialize(PlayerLevelData.CODEC)
                        .build()
        );

        MOB_LEVEL = deferredRegister.register("mob_level",
                () -> AttachmentType.builder(MobLevelData::new)
                        .serialize(MobLevelData.CODEC)
                        .build()
        );

        // SPI：发现子模组注册的等级经验数据
        for (ILevelProvider provider : ServiceLoader.load(ILevelProvider.class)) {
            provider.registerLevelData(registry);
        }

        // 注册附件供应商到 RPGSystems（供客户端代码访问等级数据）
        RPGSystems.registerPlayerLevelAttachment(PLAYER_LEVEL);

        // 注册到 RPGSystems 统一门面
        RPGSystems.registerLevelSystem(new ILevelSystem() {
            @Override
            public int getLevel(ServerPlayer player) {
                return player.getData(PLAYER_LEVEL).getLevel();
            }

            @Override
            public int getExperience(ServerPlayer player) {
                return player.getData(PLAYER_LEVEL).getExperience();
            }

            @Override
            public void setLevel(ServerPlayer player, int level) {
                int oldLevel = player.getData(PLAYER_LEVEL).getLevel();
                player.getData(PLAYER_LEVEL).setLevel(level);
                if (level > oldLevel) {
                    // 等级上升时触发升级事件（覆盖 /rpg setlevel 路径）
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new com.rpgcraft.core.event.PlayerLevelUpEvent(player, oldLevel, level));
                }
            }

            @Override
            public void setExperience(ServerPlayer player, int experience) {
                player.getData(PLAYER_LEVEL).setExperience(experience);
            }

            @Override
            public boolean addExperience(ServerPlayer player, int amount) {
                int oldLevel = player.getData(PLAYER_LEVEL).getLevel();
                boolean leveledUp = player.getData(PLAYER_LEVEL).addExperience(amount);
                if (amount > 0) {
                    // 广播经验获取事件（与打怪路径一致，供职业经验等同步副作用消费）
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new com.rpgcraft.core.event.PlayerExpGainEvent(player, amount));
                }
                if (leveledUp) {
                    int newLevel = player.getData(PLAYER_LEVEL).getLevel();
                    // 触发升级事件（携带连续升级的等级增量，供属性点等系统消费）
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new com.rpgcraft.core.event.PlayerLevelUpEvent(player, oldLevel, newLevel));
                }
                return leveledUp;
            }

            @Override
            public int getExpForNextLevel(ServerPlayer player) {
                return player.getData(PLAYER_LEVEL).getExpForNextLevel();
            }

            @Override
            public int getExpForLevel(int level) {
                return registry.getExpForLevel(level);
            }

            @Override
            public int getMaxLevel() {
                return registry.getMaxLevel();
            }

            @Override
            public void syncToClient(ServerPlayer player) {
                LevelManager.syncToClient(player);
            }
        });

        RPGSystems.registerMobDataProvider(new IMobDataProvider() {
            @Override
            public MobLevelData getMobLevelData(net.minecraft.world.entity.LivingEntity entity) {
                return entity.getData(MOB_LEVEL);
            }

            @Override
            public IMobAttributeScaler getScaler() {
                return mobScaler;
            }

            @Override
            public java.util.Optional<MobAttributeConfig.MobAttributes> getConfig(net.minecraft.resources.Identifier typeId) {
                return MobAttributeConfig.getConfig(typeId);
            }

            @Override
            public MobAttributeConfig.SpawnDistribution getSpawnDistribution(net.minecraft.resources.Identifier typeId) {
                return MobAttributeConfig.getSpawnDistribution(typeId);
            }
        });
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
