package com.rpgcraft.attributepoints;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 属性点登录处理
 * <p>
 * 修饰符不参与序列化（{@code EntityAttribute.CODEC} 只存计算后的值），玩家重新登录后
 * 附件的 allocations 数据仍在（附件序列化保留），但对应的属性修饰符已丢失。
 * <p>
 * 此处理器在玩家登录时从附件的 allocations 重建所有修饰符，并同步点数数据到客户端。
 * 这与装备模块的 {@code restoreBonusTracking}、职业模块的 {@code applyProfessionBonuses}
 * 是对称的设计。
 */
@EventBusSubscriber(modid = AttributePointsMod.MODID)
public class AttributePointsLoginEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 登录时重建属性点修饰符（修饰符不持久化，需从 allocations 重新应用）
        AttributePointsManager.reapplyAllModifiers(player);
        AttributePointsManager.syncToClient(player);
        // 推送模块配置（如 allow_decrease），供客户端决定是否渲染 [-] 按钮
        AttributePointsConfig.syncToClient(player);
    }
}
