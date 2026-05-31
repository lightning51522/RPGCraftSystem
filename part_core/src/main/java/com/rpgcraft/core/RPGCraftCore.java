package com.rpgcraft.core;

import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.PacketHandler;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;

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
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * RPGCraftCore 模组主类 —— 模组的入口点和核心注册中心
 * <p>
 * 使用 {@code @Mod} 注解标记，FML 在加载模组时会自动实例化此类。
 * 构造函数中完成所有注册和事件总线的挂载。
 * <p>
 * <b>注册的内容：</b>
 * <ul>
 *   <li>方块、物品、创造模式标签页（通过 DeferredRegister）</li>
 *   <li>玩家属性 AttachmentType（通过 {@link GenericEntityData#ATTRIBUTE_ATTACHMENT_TYPES}）</li>
 *   <li>网络包（通过 {@link PacketHandler#register}）</li>
 *   <li>配置文件（通过 ModContainer）</li>
 * </ul>
 * <p>
 * <b>事件监听：</b>
 * <ul>
 *   <li>Mod 事件总线（modEventBus）：{@link #commonSetup}、{@link PacketHandler#register}、{@link GenericEntityData} 注册</li>
 *   <li>Game 事件总线（{@link NeoForge#EVENT_BUS}）：{@link #onServerStarting}、{@link #onPlayerLogin}</li>
 * </ul>
 */
@Mod(RPGCraftCore.MODID)
public class RPGCraftCore {

    /** 模组 ID，必须与 neoforge.mods.toml 中的 modId 一致 */
    public static final String MODID = "rpgcraftcore";

    /** SLF4J 日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 死亡时属性快照缓存：UUID → AttributeSnapshot */
    private static final java.util.Map<java.util.UUID, AttributeSnapshot> deathSnapshot = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 检测玩家生命是否归零，如果是则立即缓存全属性快照
     * <p>
     * 在生命属性可能变为 0 的所有位置调用（指令设置、战斗伤害同步等），
     * 确保在游戏处理角色死亡之前捕获所有属性值。
     * 使用 {@code putIfAbsent} 避免覆盖已有的快照（只保存第一次归零时的值）。
     *
     * @param player 可能即将死亡的玩家
     */
    public static void checkAndSnapshotIfDying(net.minecraft.server.level.ServerPlayer player) {
        if (player.getData(GenericEntityData.LIFE).getValue() <= 0) {
            deathSnapshot.putIfAbsent(player.getUUID(), GenericEntityData.getRegistry().createSnapshot(player));
        }
    }

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
     * FML 识别参数类型并自动注入 {@link IEventBus}（Mod 事件总线）和 {@link ModContainer}。
     * 所有注册操作在此完成。
     *
     * @param modEventBus Mod 事件总线，用于注册生命周期事件和延迟注册器
     * @param modContainer 模组容器，用于注册配置等扩展点
     */
    public RPGCraftCore(IEventBus modEventBus, ModContainer modContainer) {
        // 初始化属性注册中心和战斗计算器
        GenericEntityData.init();

        // 注册通用初始化回调
        modEventBus.addListener(this::commonSetup);

        // 注册原版内容的延迟注册器
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // 注册属性 AttachmentType（通过注册中心的 DeferredRegister）
        GenericEntityData.getRegistry().getDeferredRegister().register(modEventBus);

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
     * <p>
     * 在模组加载的 FMLCommonSetupEvent 阶段触发，此时所有注册已完成。
     * 用于执行仅需运行一次的初始化逻辑。
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
     * <p>
     * 将示例方块物品添加到原版 "建筑方块" 创造标签页中。
     *
     * @param event 标签页内容构建事件
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    /**
     * 服务器启动回调（Game 事件总线）
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    /**
     * 玩家登录回调 —— 全量同步属性到客户端（Game 事件总线）
     * <p>
     * 当玩家进入世界时（无论创造模式还是生存模式），遍历所有自定义 RPG 属性，
     * 逐个通过 {@link SyncPlayerAttributePacket#sendToClient} 发送给该玩家的客户端。
     * <p>
     * 此同步确保客户端 HUD 在玩家进入世界后立即显示正确的属性值，
     * 而不需要等待属性发生变更才触发同步。
     *
     * @param event 玩家登录事件，通过 {@code event.getEntity()} 获取登录的玩家实例
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        for (IAttributeEntry entry : GenericEntityData.getRegistry().getAllEntries()) {
            EntityAttribute attr = (EntityAttribute) event.getEntity().getData(entry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(event.getEntity(), entry.getId(), attr);
        }
    }

    /**
     * 玩家断开连接回调 —— 清理残留的死亡快照（Game 事件总线）
     * <p>
     * 如果玩家死亡后直接断开（未点击重生），快照会留在内存中。
     * 在断开时清理，避免长期运行服务器上的内存泄漏。
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        deathSnapshot.remove(event.getEntity().getUUID());
    }

    /**
     * 玩家死亡回调 —— 兜底缓存属性快照（Game 事件总线）
     * <p>
     * 作为 {@link #checkAndSnapshotIfDying} 的兜底，处理非生命归零导致的死亡
     * （如 void、/kill 等）。使用 {@code putIfAbsent} 不覆盖已有的快照。
     */
    @SubscribeEvent
    public void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        deathSnapshot.putIfAbsent(serverPlayer.getUUID(), GenericEntityData.getRegistry().createSnapshot(serverPlayer));
    }

    /**
     * 玩家克隆回调 —— 从快照恢复属性数据（Game 事件总线）
     * <p>
     * 仅恢复服务端属性值，不在此处同步客户端。
     * 因为 Clone 事件在客户端创建新实体之前触发，此时发送的同步包会被旧实体接收后丢失。
     * 客户端同步改在 {@link #onPlayerRespawn} 中完成。
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        AttributeSnapshot snapshot = deathSnapshot.remove(serverPlayer.getUUID());
        if (snapshot == null) return;

        GenericEntityData.getRegistry().applySnapshot(serverPlayer, snapshot);
    }

    /**
     * 玩家重生回调 —— 同步属性到客户端（Game 事件总线）
     * <p>
     * PlayerRespawnEvent 在客户端已完成新实体创建之后触发，
     * 此时发送的同步包能被正确接收。
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;

        for (IAttributeEntry entry : GenericEntityData.getRegistry().getAllEntries()) {
            EntityAttribute attr = (EntityAttribute) serverPlayer.getData(entry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(serverPlayer, entry.getId(), attr);
        }
    }
}
