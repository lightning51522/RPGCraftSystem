package com.rpgcraft.coreattributes;

import com.rpgcraft.core.attributes.DefaultAttributeModule;
import com.rpgcraft.core.snapshot.AttributeSnapshotContributor;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import com.rpgcraft.core.registry.RPGSystems;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

/**
 * 默认属性模块入口
 * <p>
 * 注册全部 12 个 RPG 属性到系统。可通过第三方模块以
 * {@link RPGSystems#OVERRIDE_PRIORITY} 覆盖此默认实现。
 */
@Mod(AttributesMod.MODID)
public class AttributesMod {

    public static final String MODID = "rpgcraftattributes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AttributesMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Craft Core Attributes 模块初始化");

        // 注册默认属性模块（默认优先级，可被第三方覆盖）
        RPGSystems.registerAttributeModule(new DefaultAttributeModule(), RPGSystems.DEFAULT_PRIORITY);

        // 注册属性快照贡献者（死亡/重生时的属性保存与恢复）
        SnapshotCoordinator.registerContributor(new AttributeSnapshotContributor());
    }
}
