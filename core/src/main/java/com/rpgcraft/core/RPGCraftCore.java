package com.rpgcraft.core;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.PacketHandler;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.preference.PlayerPreferences;
import com.rpgcraft.core.snapshot.DeathRestoreModeSavedData;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * RPGCraftCore 模组主类 —— 微内核协调器
 * <p>
 * 使用 {@code @Mod} 注解标记，FML 在加载模组时会自动实例化此类。
 * 构造函数中完成所有注册和事件总线的挂载。
 * <p>
 * 死亡/重生数据恢复委托给 {@link SnapshotCoordinator}，
 * 各子系统通过 {@link ISnapshotContributor} 接口注册自己的快照逻辑。
 */
@Mod(RPGCraftCore.MODID)
public class RPGCraftCore {

    /** 模组 ID，必须与 neoforge.mods.toml 中的 modId 一致 */
    public static final String MODID = "rpgcraftcore";

    /** SLF4J 日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组主构造函数 —— FML 自动调用
     * <p>
     * 初始化顺序：属性 → 快照贡献者注册
     * <p>
     * 职业、装备和等级模块由各自的插件模组自行初始化。
     *
     * @param modEventBus  Mod 事件总线，用于注册生命周期事件和延迟注册器
     * @param modContainer 模组容器，用于注册配置等扩展点
     */
    public RPGCraftCore(IEventBus modEventBus, ModContainer modContainer) {
        // 初始化属性注册中心和战斗计算器
        AttributeManager.init();

        // 注册快照贡献者
        // AttributeSnapshotContributor 由 attributes 模块自行注册
        // LevelSnapshotContributor 由 leveling 模块自行注册
        // EquipmentSnapshotContributor 由 equipment 模块自行注册
        // ProfessionSnapshotContributor 由 profession 模块自行注册
        // AttributePointsSnapshotContributor 由 attributepoints 模块自行注册

        // 注册属性附件类型回调（必须在 DeferredRegister 之前注册，确保先执行）
        modEventBus.addListener(AttributeManager::onRegisterAttachmentTypes);

        // 注册属性 AttachmentType（通过门面的便捷方法）
        AttributeManager.getDeferredRegister().register(modEventBus);

        // 注册 RPG 共享 DataComponentType（如装备稀有度组件，存于 ItemStack）
        com.rpgcraft.core.equipment.RPGComponents.getDeferredRegister().register(modEventBus);

        // 注册 RPG 共享创造模式标签页（如「RPG 宝石」标签，集中存放各模块的宝石物品）
        RpgCreativeTabs.getDeferredRegister().register(modEventBus);

        // 等级模块 AttachmentType 由 leveling 模块自行注册
        // 职业模块 AttachmentType 由 profession 模块自行注册
        // 装备模块 AttachmentType 由 equipment 模块自行注册
        // 属性点模块 AttachmentType 由 attributepoints 模块自行注册

        // 注册网络包处理器
        modEventBus.addListener(PacketHandler::register);

        // 将本类注册到 Game 事件总线，使 @SubscribeEvent 方法生效
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * 玩家登录回调 —— 全量同步属性到客户端（Game 事件总线）
     * <p>
     * 当玩家进入世界时，遍历所有自定义 RPG 属性发送给客户端，
     * 并恢复装备加成追踪数据。
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            EntityAttribute attr = (EntityAttribute) event.getEntity().getData(entry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(event.getEntity(), entry.getId(), attr);
        }

        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // 登录时从当前装备恢复修饰符追踪（由 equipment 模块通过 RPGSystems 提供）
            com.rpgcraft.core.registry.RPGSystems.getEquipmentSystem().restoreBonusTracking(serverPlayer);
            // 同步自定义 life 到原版生命条
            AttributeManager.syncVanillaHealth(serverPlayer);
            // 同步等级数据到客户端（由 leveling 模块通过 RPGSystems 处理）
            com.rpgcraft.core.registry.RPGSystems.getLevelSystem().syncToClient(serverPlayer);
            // 同步职业数据到客户端（由 profession 模块通过 RPGSystems 处理）
            com.rpgcraft.core.registry.RPGSystems.getProfessionSystem().syncToClient(serverPlayer);

            // 同步玩家偏好设置（HUD 开关）到客户端
            PlayerPreferences prefs = serverPlayer.getData(AttributeManager.PLAYER_PREFERENCES);
            if (!prefs.isHudEnabled()) {
                com.rpgcraft.core.registry.RPGSystems.getClientSystem().sendHudToggle(serverPlayer, false);
            }
        }
    }

    /**
     * 玩家断开连接回调 —— 清理残留的死亡快照（Game 事件总线）
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SnapshotCoordinator.cleanup(event.getEntity().getUUID());
    }

    /**
     * 服务端启动回调 —— 从存档恢复死亡恢复模式（Game 事件总线）
     * <p>
     * {@code /rpg deathmode} 的切换通过 {@link DeathRestoreModeSavedData} 持久化到存档；
     * 此处在服务端启动时把磁盘值同步回 {@link DeathRestoreMode} 的内存镜像，使设置跨重启保留。
     */
    @SubscribeEvent
    public void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        DeathRestoreModeSavedData.load(event.getServer());
    }

    /**
     * 玩家死亡回调 —— 兜底缓存属性快照（Game 事件总线）
     * <p>
     * 作为 {@link SnapshotCoordinator#captureIfDying} 的兜底，
     * 处理非生命归零导致的死亡（如 void、/kill 等）。
     */
    @SubscribeEvent
    public void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        SnapshotCoordinator.captureDeath(serverPlayer);
    }

    /**
     * 检测玩家生命是否归零并捕获快照
     * <p>
     * 保留为公共 API，供 CombatEventHandler 等调用。
     * 委托给 {@link SnapshotCoordinator#captureIfDying}。
     *
     * @param player 可能即将死亡的玩家
     */
    public static void checkAndSnapshotIfDying(net.minecraft.server.level.ServerPlayer player) {
        SnapshotCoordinator.captureIfDying(player);
    }

    /**
     * 玩家克隆回调 —— 从快照恢复数据（Game 事件总线）
     * <p>
     * 委托给 {@link SnapshotCoordinator#restoreOnClone}，
     * 各 {@link com.rpgcraft.core.snapshot.ISnapshotContributor} 自行处理恢复逻辑。
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        SnapshotCoordinator.restoreOnClone(serverPlayer);
    }

    /**
     * 玩家重生回调 —— 同步属性到客户端（Game 事件总线）
     * <p>
     * 委托给 {@link SnapshotCoordinator#syncOnRespawn}，
     * 各贡献者自行处理客户端同步。
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        SnapshotCoordinator.syncOnRespawn(serverPlayer);
    }
}
