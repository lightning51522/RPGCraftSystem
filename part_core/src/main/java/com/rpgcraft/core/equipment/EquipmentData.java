package com.rpgcraft.core.equipment;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 装备模块的注册入口
 * <p>
 * 管理装备加成追踪附件的注册。与属性模块的 {@code GenericEntityData} 分离，
 * 避免装备逻辑混入属性包。
 */
public class EquipmentData {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

    /**
     * 装备加成追踪附件：key=属性路径字符串, value=当前已应用的加成
     * <p>
     * 不序列化（登录/重生时从装备重新计算填充）。
     */
    public static final Supplier<AttachmentType<Map<String, EquipmentBonus>>> EQUIPMENT_BONUS =
            ATTACHMENTS.register(
                    "equipment_bonus",
                    () -> AttachmentType.builder(
                                    (java.util.function.Supplier<Map<String, EquipmentBonus>>) LinkedHashMap::new
                            )
                            .build()
            );

    public static DeferredRegister<AttachmentType<?>> getAttachmentRegister() {
        return ATTACHMENTS;
    }
}
