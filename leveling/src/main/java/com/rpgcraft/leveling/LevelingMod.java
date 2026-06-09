package com.rpgcraft.leveling;

import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import com.rpgcraft.leveling.network.SyncPlayerLevelPacket;
import com.rpgcraft.leveling.snapshot.LevelSnapshotContributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * RPG Leveling 插件模组入口
 * <p>
 * 提供等级系统、经验获取、怪物缩放等功能。
 * 仅依赖 RPG Core（rpgcraftcore），不依赖其他插件模组。
 */
@Mod(LevelingMod.MODID)
public class LevelingMod {
    public static final String MODID = "rpgcraftleveling";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LevelingMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Leveling 模块初始化");

        // 初始化等级模块（注册 RPGSystems 接口 + 附件类型）
        LevelManager.init();

        // 注册附件类型到 Mod 事件总线
        LevelManager.getDeferredRegister().register(modEventBus);

        // 注册等级同步网络包
        modEventBus.addListener(this::registerNetwork);

        // 注册快照贡献者
        SnapshotCoordinator.registerContributor(new LevelSnapshotContributor());
    }

    /**
     * 网络包注册回调
     */
    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                SyncPlayerLevelPacket.TYPE,
                SyncPlayerLevelPacket.STREAM_CODEC,
                SyncPlayerLevelPacket::handle
        );
    }
}
