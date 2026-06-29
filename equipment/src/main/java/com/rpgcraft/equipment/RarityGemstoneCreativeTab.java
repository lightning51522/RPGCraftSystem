package com.rpgcraft.equipment;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 将稀有度宝石加入原版创造模式标签。
 * <p>
 * {@link BuildCreativeModeTabContentsEvent} 是 {@code IModBusEvent}，走 Mod 事件总线。
 * 本类不使用 {@code @EventBusSubscriber(bus = Bus.MOD)}（不同 NeoForge 版本该注解形式差异较大），
 * 而是改由 {@link EquipmentMod} 构造函数通过 {@code modEventBus.addListener(RarityGemstoneCreativeTab::onBuildCreativeTab)}
 * 显式挂到 Mod 事件总线，与项目已有的 {@code modEventBus.addListener(...)} 用法一致（如 AttributePointsMod）。
 * <p>
 * 加入「工具与实用物品」标签，便于创造模式取用与游戏内测试。
 */
public final class RarityGemstoneCreativeTab {

    private RarityGemstoneCreativeTab() {
    }

    /**
     * 监听创造标签构建事件，把稀有度宝石加入「工具与实用物品」标签。
     * <p>
     * 由 {@link EquipmentMod} 在 Mod 事件总线上注册。
     */
    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(RPGItems.RARITY_GEMSTONE.get());
        }
    }
}
