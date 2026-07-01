package com.rpgcraft.gemstone;

import com.rpgcraft.core.RpgCreativeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 将镶嵌宝石加入 RPG 共享的「宝石」创造模式标签。
 * <p>
 * {@link BuildCreativeModeTabContentsEvent} 是 {@code IModBusEvent}，走 Mod 事件总线。
 * 本类不使用 {@code @EventBusSubscriber(bus = Bus.MOD)}（不同 NeoForge 版本该注解形式差异较大），
 * 而是改由 {@link GemstoneMod} 构造函数通过 {@code modEventBus.addListener(GemstoneCreativeTab::onBuildCreativeTab)}
 * 显式挂到 Mod 事件总线（与 equipment 模块的 {@code RarityGemstoneCreativeTab} 用法一致）。
 * <p>
 * 加入 core 注册的「RPG 宝石」标签（{@link RpgCreativeTabs#GEMSTONES_TAB_ID}），与原版物品分离。
 * 通过 {@code Identifier} 字符串常量匹配标签页（非对象依赖），gemstone 模块不依赖 equipment 模块。
 * <p>
 * <b>注意</b>：创造栏提供的是「裸」宝石物品载体（无 {@code GEM_INSTANCE} 组件）。
 * 宝石的稀有度与词条需通过 GM 指令 {@code /rpg gemstone givegem} 赋予，或在未来的获取途径中生成；
 * 裸物品镶嵌后不会有任何加成。
 */
public final class GemstoneCreativeTab {

    private GemstoneCreativeTab() {
    }

    /**
     * 监听创造标签构建事件，把西瓜电气石加入「RPG 宝石」标签。
     * <p>
     * 由 {@link GemstoneMod} 在 Mod 事件总线上注册。
     */
    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().identifier().equals(RpgCreativeTabs.GEMSTONES_TAB_ID)) {
            event.accept(GemstoneItems.WATERMELON_TOURMALINE.get());
            event.accept(GemstoneItems.RED_GARNET.get());
        }
    }
}
