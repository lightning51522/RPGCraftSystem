package com.rpgcraft.attributepoints;

import com.rpgcraft.core.event.PlayerLevelUpEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 属性点升级监听器
 * <p>
 * 监听 {@link PlayerLevelUpEvent}（由 leveling 模块在 {@code addExperience}/{@code setLevel} 时触发），
 * 每升一级授予 1 个可分配属性点。使用 {@link PlayerLevelUpEvent#getLevelsGained()} 处理单次经验
 * 获取导致的连续升级（一次授予多点）。
 */
@EventBusSubscriber(modid = AttributePointsMod.MODID)
public class AttributePointsLevelUpEventHandler {

    @SubscribeEvent
    public static void onLevelUp(PlayerLevelUpEvent event) {
        int gained = event.getLevelsGained();
        if (gained > 0) {
            // 每升一级 +1 属性点
            AttributePointsManager.grantPoints(event.getPlayer(), gained);
        }
    }
}
