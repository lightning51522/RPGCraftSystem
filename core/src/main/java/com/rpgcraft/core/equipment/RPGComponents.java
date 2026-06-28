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

    public static DeferredRegister<DataComponentType<?>> getDeferredRegister() {
        return DEFERRED_REGISTER;
    }
}
