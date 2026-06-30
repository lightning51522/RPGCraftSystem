package com.rpgcraft.equipment;

import com.rpgcraft.core.RpgCreativeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 将稀有度宝石加入 RPG 共享的「宝石」创造模式标签。
 * <p>
 * {@link BuildCreativeModeTabContentsEvent} 是 {@code IModBusEvent}，走 Mod 事件总线。
 * 本类不使用 {@code @EventBusSubscriber(bus = Bus.MOD)}（不同 NeoForge 版本该注解形式差异较大），
 * 而是改由 {@link EquipmentMod} 构造函数通过 {@code modEventBus.addListener(RarityGemstoneCreativeTab::onBuildCreativeTab)}
 * 显式挂到 Mod 事件总线，与项目已有的 {@code modEventBus.addListener(...)} 用法一致（如 AttributePointsMod）。
 * <p>
 * 加入 core 注册的「RPG 宝石」标签（{@link RpgCreativeTabs#GEMSTONES_TAB_ID}），与原版物品分离。
 * 通过 {@code Identifier} 字符串常量匹配标签页（非对象依赖），equipment 模块不依赖 gemstone 模块。
 */
public final class RarityGemstoneCreativeTab {

    private RarityGemstoneCreativeTab() {
    }

    /**
     * 监听创造标签构建事件，把稀有度宝石加入「RPG 宝石」标签。
     * <p>
     * 由 {@link EquipmentMod} 在 Mod 事件总线上注册。
     */
    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().identifier().equals(RpgCreativeTabs.GEMSTONES_TAB_ID)) {
            event.accept(RPGItems.RARITY_GEMSTONE.get());
        }
    }
}
