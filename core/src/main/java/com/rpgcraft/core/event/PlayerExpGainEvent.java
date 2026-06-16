package com.rpgcraft.core.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 玩家获得经验事件
 * <p>
 * 在玩家获得任意来源的等级经验时由 leveling 模块触发（打怪、命令等所有经验入口），
 * 投递在 NeoForge {@code EVENT_BUS}（Game 事件总线）上。
 * <p>
 * 与 {@link PlayerLevelUpEvent} 的区别：本事件在<b>每次经验增量</b>时触发（不论是否升级），
 * 用于"与等级经验同步"的副作用（如职业经验池累积）。{@link PlayerLevelUpEvent} 仅在等级
 * 实际上升时触发。
 * <p>
 * <b>仅在服务端触发</b>。事件携带原始经验增量（未经等级衰减的来源量）。
 *
 * @see com.rpgcraft.core.registry.ILevelSystem
 */
public class PlayerExpGainEvent extends Event {

    private final ServerPlayer player;
    private final int expGained;

    /**
     * @param player     获得经验的玩家（服务端）
     * @param expGained  本次经验增量（&gt; 0）
     */
    public PlayerExpGainEvent(ServerPlayer player, int expGained) {
        this.player = player;
        this.expGained = expGained;
    }

    /** 获得经验的玩家 */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** 本次经验增量 */
    public int getExpGained() {
        return expGained;
    }
}
