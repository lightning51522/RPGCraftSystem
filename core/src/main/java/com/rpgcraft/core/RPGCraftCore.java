package com.rpgcraft.core;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.PacketHandler;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.snapshot.AttributeSnapshotContributor;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    // ====================================================================
    // 原版注册器（方块、物品、创造标签页）
    // 使用 DeferredRegister 延迟注册模式，在构造函数中统一挂载到 Mod 事件总线
    // ====================================================================

    /** 方块注册器 */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    /** 物品注册器 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    /** 创造模式标签页注册器 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** 示例方块 */
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));
    /** 示例方块对应的物品 */
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
    /** 示例食物物品 */
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", p -> p.food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
    /** 示例创造模式标签页 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcraftcore"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get());
            }).build());

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
        SnapshotCoordinator.registerContributor(new AttributeSnapshotContributor());
        // LevelSnapshotContributor 由 leveling 模块自行注册
        // EquipmentSnapshotContributor 由 equipment 模块自行注册
        // ProfessionSnapshotContributor 由 profession 模块自行注册

        // 注册通用初始化回调
        modEventBus.addListener(this::commonSetup);

        // 注册原版内容的延迟注册器
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // 注册属性 AttachmentType（通过门面的便捷方法）
        AttributeManager.getDeferredRegister().register(modEventBus);

        // 等级模块 AttachmentType 由 leveling 模块自行注册
        // 职业模块 AttachmentType 由 profession 模块自行注册
        // 装备模块 AttachmentType 由 equipment 模块自行注册

        // 注册网络包处理器
        modEventBus.addListener(PacketHandler::register);

        // 将本类注册到 Game 事件总线，使 @SubscribeEvent 方法生效
        NeoForge.EVENT_BUS.register(this);

        // 注册创造标签页内容回调
        modEventBus.addListener(this::addCreative);

        // 注册模组配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    /**
     * 通用初始化回调（Mod 事件总线）
     *
     * @param event 通用设置事件
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    /**
     * 创造标签页内容回调（Mod 事件总线）
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    /**
     * 服务器启动回调（Game 事件总线）
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
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
