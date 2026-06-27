package com.rpgcraft.region;

import com.mojang.logging.LogUtils;
import com.rpgcraft.region.apply.NpcAttributeListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * RPG Region 插件模组入口
 * <p>
 * 区域系统：由 XZ 多边形 + Y 范围构成的柱体区域，对区域内实体施加环境属性修饰符
 * （增益 / 减益）与元素伤害加成倍率。未被任何区域包含的位置视为「一般区域」，无影响。
 * <p>
 * <h3>数据驱动</h3>
 * 区域定义由 datapack JSON 驱动（{@code data/rpgcraftcore/rpg/regions/*.json}），
 * 由 {@link RegionsDefinitionLoader} 在服务端 reload 时加载，{@code /reload} 即时生效。
 * <p>
 * <h3>属性注入路径</h3>
 * <ul>
 *   <li>玩家：通过 RPG 属性附件，进 / 出区域时由 {@link RegionManager} add/remove 修饰符</li>
 *   <li>非玩家实体：监听 {@code GatherAttributeEvent}，构建快照时按位置即时注入</li>
 * </ul>
 * <p>
 * 仅依赖 RPG Core（rpgcraftcore）与默认属性模块（rpgcraftattributes，提供元素抗性 /
 * 伤害加成属性）。遵循微内核 + 插件架构。
 *
 * @see RegionsDefinitionLoader
 * @see RegionManager
 */
@Mod(RegionMod.MODID)
public class RegionMod {

    /** 模组 ID */
    public static final String MODID = "rpgcraftregion";
    /** 模组日志器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    public RegionMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Region 模块初始化");

        // 初始化区域模块（注册附件类型）
        RegionManager.init();

        // 注册附件类型到 Mod 事件总线
        RegionManager.getDeferredRegister().register(modEventBus);

        // 注册非玩家实体属性注入监听器（挂载到 core 的 RPGEventBus）
        NpcAttributeListener.register();

        // 注：tick 检查器（RegionTickHandler）和数据加载器（RegionsDefinitionLoader）
        // 通过 @EventBusSubscriber 自动注册，无需在此手动 addListener。
    }
}
