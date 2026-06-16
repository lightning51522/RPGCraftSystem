package com.rpgcraft.client;

import com.rpgcraft.client.network.OpenCharacterScreenPacket;
import com.rpgcraft.client.ui.AttributeListPlugin;
import com.rpgcraft.client.ui.CharacterScreenOpener;
import com.rpgcraft.client.ui.PlayerInfoPlugin;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.registry.IClientSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.ui.RPGUIPlugins;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * RPG Client 插件模组入口（仅客户端）
 * <p>
 * 提供 HUD 渲染、角色界面、装备提示等功能。
 * 仅依赖 RPG Core（rpgcraftcore），不依赖其他插件模组。
 * <p>
 * 本模块同时承载 Core 模组的客户端功能（配置界面、网络包注册），
 * 因为 NeoForge 26.1.2 要求 {@code @Mod} 入口点与模组 ID 属于同一个模块。
 * 原先的 {@code RPGCraftCoreClient} 类已合并到此类中。
 */
@Mod(ClientMod.MODID)
public class ClientMod {
    public static final String MODID = "rpgcraftclient";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClientMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Client 模块初始化");

        // 注册客户端相关网络包（怪物信息查询/同步、HUD 开关）
        modEventBus.addListener(ClientMod::registerPackets);

        // 注册角色界面快捷键（Mod 事件总线）
        modEventBus.addListener(CharacterScreenOpener::registerKeyMapping);

        // 注册角色界面快捷键 tick 检测（Game 事件总线）
        NeoForge.EVENT_BUS.addListener(CharacterScreenOpener::onClientTick);

        // 注册角色界面 UI 插件（按显示顺序：玩家信息在上，属性列表在下）
        RPGUIPlugins.register(new PlayerInfoPlugin());
        RPGUIPlugins.register(new AttributeListPlugin());

        // 注册客户端系统到 RPGSystems（供 core 命令系统发送 HUD 同步包）
        RPGSystems.registerClientSystem(new IClientSystem() {
            @Override
            public void sendHudToggle(net.minecraft.server.level.ServerPlayer player, boolean enabled) {
                player.connection.send(new ToggleHudPacket(enabled));
            }
        });

        // 为 Core 模组注册配置界面（原先在 RPGCraftCoreClient 中）
        // Client 模块是 Core 模组的客户端扩展提供者，
        // 通过 ModList 查找 Core 的 ModContainer 并注册配置界面工厂。
        net.neoforged.fml.ModList.get().getModContainerById(RPGCraftCore.MODID)
                .ifPresent(coreContainer ->
                        coreContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
    }

    /**
     * 注册客户端相关网络包
     * <p>
     * 这三个网络包从 core 迁移到 client 模块，因为它们的客户端处理器
     * 引用了客户端渲染类（{@link AttributeHudOverlay}）。
     *
     * @param event NeoForge 网络包注册事件
     */
    private static void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // 注册怪物信息查询包（客户端 → 服务端）
        registrar.playToServer(
                QueryMobInfoPacket.TYPE,
                QueryMobInfoPacket.STREAM_CODEC,
                QueryMobInfoPacket::handle
        );

        // 注册怪物信息回复包（服务端 → 客户端）
        registrar.playToClient(
                SyncMobInfoPacket.TYPE,
                SyncMobInfoPacket.STREAM_CODEC,
                SyncMobInfoPacket::handle
        );

        // 注册 HUD 开关同步包（服务端 → 客户端）
        registrar.playToClient(
                ToggleHudPacket.TYPE,
                ToggleHudPacket.STREAM_CODEC,
                ToggleHudPacket::handle
        );

        // 注册打开角色界面信号包（服务端 → 客户端）
        // 配合 /rpg character 命令使用，服务端发送快照后通知客户端打开界面
        registrar.playToClient(
                OpenCharacterScreenPacket.TYPE,
                OpenCharacterScreenPacket.STREAM_CODEC,
                OpenCharacterScreenPacket::handle
        );
    }
}
