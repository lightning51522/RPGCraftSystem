package com.rpgcraft.gemstone;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;

/**
 * RPG Gemstone 插件模组入口
 * <p>
 * 提供镶嵌宝石系统：每件装备可镶嵌 1 颗宝石，宝石带来属性词条加成（灰~紫）或额外特殊效果
 * （橙及以上）。宝石通过铁砧镶嵌（无失败），与 {@code rpgcraftequipment} 的稀有度宝石
 * （升级装备稀有度）是<b>完全不同的系统</b>。
 * <p>
 * <b>解耦设计</b>：本模块仅依赖 RPG Core（{@code rpgcraftcore}），通过 core 的两个扩展点
 * SPI 接入装备系统，与 equipment 模块零编译期依赖：
 * <ul>
 *   <li>{@code IEquipmentBonusContributor} — 为镶嵌宝石的装备注入词条加成</li>
 *   <li>{@code ITooltipImageContributor} — 为装备 tooltip 贡献镶嵌槽图像数据（由 client 渲染）</li>
 * </ul>
 * 战斗特殊效果通过 core 的 {@code RPGEventBus}（{@code RPGDamageEvent.Pre/Post}）接入，
 * 不直接监听 NeoForge 的 {@code LivingDamageEvent}，避免与战斗公式重复。
 * <p>
 * 删除本模块，equipment + client 仍正常工作（仅失去宝石功能）。
 */
@Mod(GemstoneMod.MODID)
public class GemstoneMod {
    public static final String MODID = "rpgcraftgemstone";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GemstoneMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Gemstone 模块初始化");

        // 初始化宝石模块：注册贡献者、战斗事件监听器
        GemstoneManager.init();

        // 注册占位宝石物品到 Mod 事件总线
        GemstoneItems.getDeferredRegister().register(modEventBus);

        // 注册西瓜电气石加入创造标签（BuildCreativeModeTabContentsEvent 走 Mod 事件总线）
        modEventBus.addListener(GemstoneCreativeTab::onBuildCreativeTab);
    }
}
