package com.rpgcraft.profession;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 职业登录处理
 * <p>
 * 职业加成修饰符不参与序列化（{@code EntityAttribute.CODEC} 只存计算后的值），
 * 玩家重新登录后附件的 professionId/levels 仍在，但属性修饰符已丢失。
 * 此处理器在登录时从当前主职业与副职业的等级重新应用全部加成，并同步数据到客户端。
 * <p>
 * 与 {@code com.rpgcraft.attributepoints.AttributePointsLoginEventHandler} 对称。
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionLoginEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ProfessionManager.reapplyAllBonuses(player);
        ProfessionManager.syncToClient(player);
    }
}
