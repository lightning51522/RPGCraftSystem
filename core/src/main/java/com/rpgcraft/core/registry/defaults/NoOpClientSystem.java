package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.registry.IClientSystem;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link IClientSystem} 的 no-op 兜底实现。
 * <p>
 * 无 client 模块时由 core 预填充：HUD 开关同步为 no-op（服务端无客户端可同步时不发送）。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getClientSystem()
 */
public final class NoOpClientSystem implements IClientSystem {

    @Override
    public void sendHudToggle(ServerPlayer player, boolean enabled) {
        // 无客户端模块：不发送 HUD 同步包
    }
}
