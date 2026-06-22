package com.rpgcraft.attributepoints;

import com.rpgcraft.attributepoints.network.SyncPlayerAttributePointsPacket;
import com.rpgcraft.attributepoints.snapshot.AttributePointsSnapshotContributor;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * RPG Attribute Points 插件模组入口
 * <p>
 * 提供属性点分配系统：玩家每升一级获得 1 个可自由分配的属性点，
 * 可分配到除 life/skill_point 之外的任意属性上。
 * <p>
 * 仅依赖 RPG Core（rpgcraftcore），不依赖其他插件模组。通过 {@link com.rpgcraft.core.event.PlayerLevelUpEvent}
 * 监听升级以授予点数，通过 core 的属性注册表动态发现可分配属性。
 */
@Mod(AttributePointsMod.MODID)
public class AttributePointsMod {
    public static final String MODID = "rpgcraftattributepoints";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AttributePointsMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Attribute Points 模块初始化");

        // 初始化属性点模块（注册 RPGSystems 接口 + 附件类型）
        AttributePointsManager.init();

        // 注册附件类型到 Mod 事件总线
        AttributePointsManager.getDeferredRegister().register(modEventBus);

        // 注册网络包
        modEventBus.addListener(this::registerNetwork);

        // 注册快照贡献者
        SnapshotCoordinator.registerContributor(new AttributePointsSnapshotContributor());
    }

    /**
     * 网络包注册回调
     */
    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // 服务端→客户端：点数数据同步（分配请求包 AllocateAttributePointPacket 由 core 的 PacketHandler 注册，
        // 放在 core 可避免 client 模块对 attributepoints 的编译期依赖）
        registrar.playToClient(
                SyncPlayerAttributePointsPacket.TYPE,
                SyncPlayerAttributePointsPacket.STREAM_CODEC,
                SyncPlayerAttributePointsPacket::handle
        );
    }
}
