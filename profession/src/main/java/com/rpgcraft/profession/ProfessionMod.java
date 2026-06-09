package com.rpgcraft.profession;

import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import com.rpgcraft.profession.network.SyncPlayerProfessionPacket;
import com.rpgcraft.profession.snapshot.ProfessionSnapshotContributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * RPG Profession 插件模组入口
 * <p>
 * 提供职业系统、职业属性加成等功能。
 * 仅依赖 RPG Core（rpgcraftcore），不依赖其他插件模组。
 */
@Mod(ProfessionMod.MODID)
public class ProfessionMod {
    public static final String MODID = "rpgcraftprofession";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProfessionMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Profession 模块初始化");

        // 初始化职业模块（注册 RPGSystems 接口 + 附件类型 + 内置职业）
        ProfessionManager.init();

        // 注册附件类型到 Mod 事件总线
        ProfessionManager.getDeferredRegister().register(modEventBus);

        // 注册职业同步网络包
        modEventBus.addListener(this::registerNetwork);

        // 注册快照贡献者
        SnapshotCoordinator.registerContributor(new ProfessionSnapshotContributor());
    }

    /**
     * 网络包注册回调
     */
    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                SyncPlayerProfessionPacket.TYPE,
                SyncPlayerProfessionPacket.STREAM_CODEC,
                SyncPlayerProfessionPacket::handle
        );
    }
}
