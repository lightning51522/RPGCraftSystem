package com.rpgcraft.core.registry;

import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端系统接口
 * <p>
 * 由 client 模块注册，提供客户端相关功能的服务端调用入口。
 * 核心用途：允许 core 中的命令系统向客户端发送 HUD 开关同步包，
 * 而不直接依赖 client 模块的网络包类。
 */
public interface IClientSystem {

    /**
     * 向指定玩家发送 HUD 开关同步包
     *
     * @param player  目标玩家
     * @param enabled HUD 开关状态
     */
    void sendHudToggle(ServerPlayer player, boolean enabled);
}
