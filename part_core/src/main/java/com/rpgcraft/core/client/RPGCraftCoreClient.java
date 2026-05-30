package com.rpgcraft.core.client;

import com.rpgcraft.core.RPGCraftCore;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端入口类
 * <p>
 * 使用 {@code @Mod(dist = Dist.CLIENT)} 注解标记，确保此类仅在客户端（物理客户端）加载，
 * 不会在专用服务器上被实例化，因此可以安全地引用客户端专属类（如 Minecraft、Font 等）。
 * <p>
 * <b>职责：</b>
 * <ul>
 *   <li>注册模组的配置界面（Config Screen），允许玩家在游戏内修改配置</li>
 *   <li>通过 {@link EventBusSubscriber} 自动注册客户端生命周期事件的监听方法</li>
 * </ul>
 */
@Mod(value = RPGCraftCore.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = RPGCraftCore.MODID, value = Dist.CLIENT)
public class RPGCraftCoreClient {

    /**
     * 客户端模组构造函数
     * <p>
     * FML 在客户端加载模组时自动调用，注入 {@link ModContainer} 参数。
     *
     * @param container 模组容器，用于注册扩展点（如配置界面工厂）
     */
    public RPGCraftCoreClient(ModContainer container) {
        // 注册配置界面工厂，允许玩家通过 "Mods → 本模组 → Config" 打开配置界面
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * 客户端初始化回调
     * <p>
     * 在 FML 客户端设置阶段（FMLClientSetupEvent）触发，此时 Minecraft 实例已可用。
     * 用于执行仅需在客户端运行一次的初始化逻辑。
     *
     * @param event 客户端设置事件
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        RPGCraftCore.LOGGER.info("HELLO FROM CLIENT SETUP");
        RPGCraftCore.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
