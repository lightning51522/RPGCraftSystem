package com.rpgcraft.equipment;

import com.rpgcraft.equipment.snapshot.EquipmentSnapshotContributor;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * RPG Equipment 插件模组入口
 * <p>
 * 提供装备加成、武器攻击类型等功能。
 * 仅依赖 RPG Core（rpgcraftcore），不依赖其他插件模组。
 */
@Mod(EquipmentMod.MODID)
public class EquipmentMod {
    public static final String MODID = "rpgcraftequipment";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EquipmentMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Equipment 模块初始化");

        // 初始化装备模块注册中心和处理器
        EquipmentManager.init();

        // 注册装备附件类型到 Mod 事件总线
        EquipmentData.getAttachmentRegister().register(modEventBus);

        // 注册快照贡献者
        SnapshotCoordinator.registerContributor(new EquipmentSnapshotContributor());
    }
}
