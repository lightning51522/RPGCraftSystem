package com.rpgcraft.profession;

import com.rpgcraft.core.event.PlayerExpGainEvent;
import com.rpgcraft.core.registry.RPGSystems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 职业经验池 —— 玩家经验获取处理
 * <p>
 * 玩家<b>每次获得等级经验</b>（打怪、命令等所有来源，不论是否升级）时，把等量的经验
 * 累积到玩家的「可分配职业经验池」，使职业经验与等级经验同步获得。玩家在职业面板把
 * 池中的经验投入某职业升级。
 * <p>
 * 监听 {@link PlayerExpGainEvent}（每次经验增量触发），而非仅升级触发的
 * {@code PlayerLevelUpEvent}，确保"加经验但不升级"时职业经验也同步增长。
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionLevelUpEventHandler {

    @SubscribeEvent
    public static void onExpGain(PlayerExpGainEvent event) {
        int exp = event.getExpGained();
        if (exp > 0) {
            // 等量累积到职业经验池（与等级经验同步）
            RPGSystems.getProfessionSystem().onPlayerLeveledUp(event.getPlayer(), exp);
        }
    }
}
