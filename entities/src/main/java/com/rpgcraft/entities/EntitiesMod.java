package com.rpgcraft.entities;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * RPG Entities 插件模组入口
 * <p>
 * 本模块存放由 <b>BlockBench</b> 制作、经 <b>GeckoLib</b> 驱动模型与动画的自定义生物。
 * 模块本身是一个可选插件（与 skills / region 同级），仅在玩家需要 RPG 自定义生物时安装；
 * 但 GeckoLib 对本模块是<b>必需依赖</b>（mods.toml 声明 {@code type="required"}）——
 * 模块的存在意义即承载 GeckoLib 动画生物，GeckoLib 缺失则模块无意义。
 * <p>
 * <h3>动画库分工（项目硬约束）</h3>
 * <ul>
 *   <li><b>GeckoLib</b> —— 仅用于<b>生物（自定义实体）</b>的模型与动画（本模块）</li>
 *   <li><b>PAL (Player Animation Library)</b> —— 仅用于<b>玩家</b>动画（skills 模块技能动画）</li>
 * </ul>
 * 二者职责互斥，<b>严禁混用</b>：生物动画不得用 PAL，玩家动画不得用 GeckoLib。
 * <p>
 * <h3>添加一个 BlockBench 生物</h3>
 * 参见 {@code entities/CLAUDE.md} 的「如何添加一个 BlockBench 生物」流程：
 * <ol>
 *   <li>在 BlockBench 中建模 / 绑定骨骼 / 制作动画，导出
 *       {@code geometry.json}（geo）、{@code animation.json}（animations）、贴图 png</li>
 *   <li>资源放入 {@code assets/rpgcraftentities/}：
 *       geo / animations / textures/entity 各目录</li>
 *   <li>编写 {@code GeoEntity} 子类 + {@code GeoEntityRenderer}，
 *       资源 id 约定 {@code rpgcraftentities:<生物id>}</li>
 *   <li>在下方 {@link #ENTITY_TYPES} DeferredRegister 注册实体类型</li>
 *   <li>（可选）若需 RPG 属性 / 等级，复用 core 的
 *       {@code GatherAttributeEvent} / {@code IMobDataProvider}，不新增 RPGSystems 接口</li>
 * </ol>
 * <p>
 * 仅依赖 RPG Core（rpgcraftcore）与 GeckoLib（com.geckolib）。遵循微内核 + 插件架构。
 *
 * @see EntitiesMod#ENTITY_TYPES
 */
@Mod(EntitiesMod.MODID)
public class EntitiesMod {

    /** 模组 ID */
    public static final String MODID = "rpgcraftentities";
    /** 模组日志器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 自定义生物实体类型注册表。
     * <p>
     * 添加新生物时在此 put 一个 {@code Supplier<EntityType<...>>}，
     * 构造器中调用 {@code ENTITY_TYPES.register(modEventBus)} 完成注册。
     * 骨架阶段为空注册表，确保模块可独立编译 / 加载。
     */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public EntitiesMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Entities 模块初始化（GeckoLib 动画生物）");

        // 注册实体类型到 Mod 事件总线（骨架阶段为空，添加首个生物时填充）
        ENTITY_TYPES.register(modEventBus);

        // 注：GeckoLib 5.x 不再需要显式调用 GeckoLib.initialize()，
        // 作为 required 依赖由 mods.toml 保证加载顺序。具体生物的渲染器
        // 通过 RegisterRenderersEvent 在客户端侧注册，见 CLAUDE.md 流程。
    }
}
