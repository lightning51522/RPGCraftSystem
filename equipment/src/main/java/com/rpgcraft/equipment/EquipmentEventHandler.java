package com.rpgcraft.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * 装备变化事件监听器（Game 事件总线）
 * <p>
 * 监听 {@link LivingEquipmentChangeEvent}，将处理逻辑委托到
 * {@link EquipmentManager#getHandler()} 返回的 {@link com.rpgcraft.equipment.api.IEquipmentHandler}。
 * <p>
 * <b>事件流：</b>
 * <pre>
 * 玩家装备变化 → NeoForge 触发 LivingEquipmentChangeEvent
 *   → 本类的 onEquipmentChange() 检查是否为 ServerPlayer
 *     → DefaultEquipmentHandler.onEquipmentChange()
 *       → calculateTotalBonus() 计算新总加成
 *       → 与旧加成对比计算差值
 *       → applyModifiers() 将差值应用到属性
 *       → 同步到客户端
 * </pre>
 * <p>
 * <b>为什么检查 {@code ServerPlayer}？</b>
 * {@link LivingEquipmentChangeEvent} 在客户端和服务端都会触发（例如客户端的装备栏动画），
 * 但属性修改和网络同步只能在服务端执行。客户端的属性值由 {@link com.rpgcraft.core.network.SyncPlayerAttributePacket} 同步。
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentEventHandler {

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        // 仅在服务端处理：属性修改和网络包发送必须在服务端执行
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        EquipmentManager.getHandler().onEquipmentChange(serverPlayer);
    }
}
