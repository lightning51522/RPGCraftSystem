package com.rpgcraft.skills;

import com.mojang.logging.LogUtils;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import com.rpgcraft.skills.client.SkillKeyMappings;
import com.rpgcraft.skills.network.PlaySkillAnimationPacket;
import com.rpgcraft.skills.network.SyncPlayerSkillsPacket;
import com.rpgcraft.skills.snapshot.SkillSnapshotContributor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * RPG Skills 插件模组入口
 * <p>
 * 主动技能系统：按键释放 → 资源消耗 → 冷却 → PAL 玩家动画 → 走 vanilla hurt 造成 RPG 伤害。
 * 仅依赖 RPG Core（rpgcraftcore）与 PAL（playeranimator，仅客户端）。
 * <p>
 * 技能定义由 datapack JSON 驱动（{@code data/rpgcraftcore/rpg/skills/*.json}），
 * 由 {@link SkillsDefinitionLoader} 在服务端 reload 时加载。
 */
@Mod(SkillsMod.MODID)
public class SkillsMod {
    public static final String MODID = "rpgcraftskills";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SkillsMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Skills 模块初始化");

        // 初始化技能模块（注册附件类型 + RPGSystems 门面）
        SkillsManager.init();

        // 注册附件类型到 Mod 事件总线
        SkillsManager.getDeferredRegister().register(modEventBus);

        // 注册网络包
        modEventBus.addListener(this::registerNetwork);

        // 注册技能释放快捷键（Mod 事件总线）
        modEventBus.addListener(SkillKeyMappings::registerKeyMappings);

        // 注册快照贡献者（死亡/重生恢复冷却与已学技能）
        SnapshotCoordinator.registerContributor(new SkillSnapshotContributor());
    }

    /**
     * 网络包注册回调
     */
    private void registerNetwork(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        // 服务端→客户端：技能数据同步（冷却 + 已学）
        registrar.playToClient(
                SyncPlayerSkillsPacket.TYPE,
                SyncPlayerSkillsPacket.STREAM_CODEC,
                SyncPlayerSkillsPacket::handle
        );
        // 服务端→客户端：技能动画播放指令
        registrar.playToClient(
                PlaySkillAnimationPacket.TYPE,
                PlaySkillAnimationPacket.STREAM_CODEC,
                PlaySkillAnimationPacket::handle
        );
    }
}

