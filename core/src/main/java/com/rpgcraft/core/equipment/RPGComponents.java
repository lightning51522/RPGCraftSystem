package com.rpgcraft.core.equipment;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * RPG 共享 {@link DataComponentType} 注册中心
 * <p>
 * 注册存储在 {@code ItemStack} 上的自定义数据组件。本类是项目中<b>首个</b>自定义
 * DataComponent 的注册点。
 * <p>
 * 放在 core 模块（命名空间 {@code rpgcraftcore}），使 equipment / client 等只依赖 core 的
 * 模块都能读写这些组件（与 AttachmentType、ID 常量统一上提到 core 的做法一致）。
 * <p>
 * 在 {@code RPGCraftCore} 构造函数中通过 {@link #getDeferredRegister()} 接到 Mod 事件总线。
 */
public final class RPGComponents {

    /** 工程命名空间（与附件/数据保持一致）。 */
    public static final String NAMESPACE = "rpgcraftcore";

    private RPGComponents() {
    }

    /** DataComponentType 延迟注册器（vanilla 注册键 {@link Registries#DATA_COMPONENT_TYPE}）。 */
    public static final DeferredRegister<DataComponentType<?>> DEFERRED_REGISTER =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, NAMESPACE);

    /**
     * 装备稀有度组件：存储每件 {@code ItemStack} 的（动态随机生成的）稀有度等级。
     * <p>
     * 缺省（组件不存在）= {@link EquipmentRarity#GRAY}（基础最低级）。
     */
    public static final Supplier<DataComponentType<EquipmentRarity>> EQUIPMENT_RARITY =
            DEFERRED_REGISTER.register("equipment_rarity",
                    () -> DataComponentType.<EquipmentRarity>builder()
                            .persistent(EquipmentRarity.CODEC)
                            .networkSynchronized(EquipmentRarity.STREAM_CODEC)
                            .build());

    /**
     * 装备等级组件：存储每件 {@code ItemStack} 的等级（0~6，缺省 0）。
     * <p>
     * 通过铁砧用同物品 ID + 同等级的另一件装备合成升级（每次 +1，最高 6，无失败）。
     * 等级用星形后缀展示在装备名后（见 {@link EquipmentLevelStars}）。读取用
     * {@code stack.getOrDefault(EQUIPMENT_LEVEL.get(), 0)}。
     * <p>
     * Codec 仅在存档加载时校验 [0,6] 区间（仿 {@code MAX_STACK_SIZE} 的 intRange）；
     * 运行时写入需自行钳制到 6。
     */
    public static final Supplier<DataComponentType<Integer>> EQUIPMENT_LEVEL =
            DEFERRED_REGISTER.register("equipment_level",
                    () -> DataComponentType.<Integer>builder()
                            .persistent(net.minecraft.util.ExtraCodecs.intRange(0, 6))
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                            .build());

    public static DeferredRegister<DataComponentType<?>> getDeferredRegister() {
        return DEFERRED_REGISTER;
    }
}
