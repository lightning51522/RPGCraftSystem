package com.rpgcraft.core.equipment;

import com.rpgcraft.core.RPGCraftCore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * 装备变化事件监听器
 * <p>
 * 仅负责监听 {@link LivingEquipmentChangeEvent}，将处理委托到
 * {@link EquipmentManager#getHandler()}。
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class EquipmentEventHandler {

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        EquipmentManager.getHandler().onEquipmentChange(serverPlayer);
    }
}
