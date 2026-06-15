package com.rpgcraft.core.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 玩家等级提升事件
 * <p>
 * 在玩家等级上升时由 leveling 模块的 {@code ILevelSystem} 适配器触发
 * （{@code addExperience} 和 {@code setLevel} 两条路径都会触发）。
 * <p>
 * 投递在 NeoForge {@code EVENT_BUS}（Game 事件总线）上，任何模块可通过
 * {@code @SubscribeEvent} 监听。用于解耦"升级时触发副作用"的逻辑
 * （如属性点授予），避免各消费方各自轮询等级变化。
 * <p>
 * <b>仅在服务端触发</b>。
 *
 * @see com.rpgcraft.core.registry.ILevelSystem
 */
public class PlayerLevelUpEvent extends Event {

    private final ServerPlayer player;
    private final int oldLevel;
    private final int newLevel;

    /**
     * @param player   升级的玩家（服务端）
     * @param oldLevel 升级前等级
     * @param newLevel 升级后等级（&gt; oldLevel）
     */
    public PlayerLevelUpEvent(ServerPlayer player, int oldLevel, int newLevel) {
        this.player = player;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    /** 获取升级的玩家 */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** 获取升级前等级 */
    public int getOldLevel() {
        return oldLevel;
    }

    /** 获取升级后等级 */
    public int getNewLevel() {
        return newLevel;
    }

    /**
     * 本次升级跨越的等级数（= newLevel - oldLevel，处理单次 addExperience 连续升级的场景）
     */
    public int getLevelsGained() {
        return newLevel - oldLevel;
    }
}
