package com.rpgcraft.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * RPG 共享创造模式标签页注册中心
 * <p>
 * 注册跨模块共享的创造标签页。当前提供「RPG 宝石」标签页，集中存放所有宝石类物品
 * （equipment 模块的稀有度宝石、gemstone 模块的镶嵌宝石等），与原版物品分离。
 * <p>
 * <b>解耦设计</b>：标签页注册在 core（命名空间 {@code rpgcraftcore}），各模块通过
 * {@link #GEMSTONES_TAB_ID} 常量（一个 {@link Identifier}，非对象依赖）在各自的
 * {@code BuildCreativeModeTabContentsEvent} 监听器中判断并贡献物品。这样 equipment / gemstone
 * 模块只依赖 core 的常量字符串，互不依赖 —— 任一模块缺失，标签页仍正常（仅少几项内容）。
 * <p>
 * 在 {@code RPGCraftCore} 构造函数中通过 {@link #getDeferredRegister()} 接到 Mod 事件总线。
 */
public final class RpgCreativeTabs {

    /** 工程命名空间（与附件/数据组件统一）。 */
    public static final String NAMESPACE = "rpgcraftcore";

    /**
     * 「RPG 宝石」标签页的资源定位符。
     * <p>
     * 各模块的 {@code BuildCreativeModeTabContentsEvent} 监听器用
     * {@code event.getTabKey().location().equals(GEMSTONES_TAB_ID)} 判定是否为该标签页，
     * 以此把宝石物品贡献到这里（而非原版「工具与实用物品」标签）。
     */
    public static final Identifier GEMSTONES_TAB_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "gemstones");

    /**
     * 稀有度宝石物品 ID（equipment 模块），用作「RPG 宝石」标签页的图标。
     * <p>
     * core 不编译期依赖 equipment 模块，故通过此 ID 在运行时查物品注册表获取图标物品；
     * equipment 未加载时回退到钻石。
     */
    private static final Identifier RARITY_GEMSTONE_ICON_ID =
            Identifier.fromNamespaceAndPath("rpgcraftequipment", "rarity_gemstone");

    /** CreativeModeTab 延迟注册器（vanilla 注册键 {@link Registries#CREATIVE_MODE_TAB}）。 */
    public static final DeferredRegister<CreativeModeTab> DEFERRED_REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NAMESPACE);

    /**
     * 「RPG 宝石」创造标签页。
     * <p>
     * 图标用稀有度宝石（equipment 模块）。因 core 不编译期依赖 equipment，图标 Supplier 在运行时
     * 按物品 ID 查注册表获取；equipment 未加载时回退到钻石。内容由各模块的
     * {@code BuildCreativeModeTabContentsEvent} 监听器动态贡献（equipment 贡献稀有度宝石、
     * gemstone 贡献镶嵌宝石），本注册器不预先填充任何物品 —— 这样 core 不依赖任何具体宝石物品。
     */
    public static final Supplier<CreativeModeTab> GEMSTONES =
            DEFERRED_REGISTER.register("gemstones", () -> CreativeModeTab.builder()
                    .title(Component.translatable("creativetab.rpgcraftcore.gemstones"))
                    .icon(RpgCreativeTabs::gemstoneTabIcon)
                    .displayItems((parameters, output) -> {
                        // 内容由各模块的 BuildCreativeModeTabContentsEvent 监听器贡献，
                        // 这里留空：core 不依赖任何具体宝石物品。
                    })
                    .build());

    /**
     * 「RPG 宝石」标签页图标：优先用稀有度宝石，equipment 未加载时回退钻石。
     * <p>
     * 运行时按物品 ID 查 {@link BuiltInRegistries#ITEM}（用 {@code getOptional}，未注册返回空）。
     * 创造栏渲染时（图标 Supplier 调用时机）equipment 若加载，其物品必然已注册。
     */
    private static ItemStack gemstoneTabIcon() {
        Item item = BuiltInRegistries.ITEM.getOptional(RARITY_GEMSTONE_ICON_ID).orElse(Items.DIAMOND);
        return new ItemStack(item);
    }

    public static DeferredRegister<CreativeModeTab> getDeferredRegister() {
        return DEFERRED_REGISTER;
    }

    private RpgCreativeTabs() {
    }
}
