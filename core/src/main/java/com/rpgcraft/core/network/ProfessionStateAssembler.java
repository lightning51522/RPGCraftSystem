package com.rpgcraft.core.network;

import com.rpgcraft.core.ui.ProfessionStateCache;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * 职业状态组装器 —— core 与 profession 模块之间的解耦回调
 * <p>
 * 由于 core 的 {@link RequestProfessionStatePacket} 需要组装完整职业状态（含职业树元数据），
 * 而这些数据归 profession 模块管理（core 不依赖 profession），故采用回调注册模式：
 * profession 模块在初始化时 {@link #register(Assembler)} 注册一个组装器实现，
 * core 侧网络包通过 {@link #get()} 反射式获取并调用。
 * <p>
 * 这与 {@code RPGSystems} 的子系统注册是同一模式，只是此处仅为网络包服务、不暴露给广泛消费方。
 */
public final class ProfessionStateAssembler {

    private ProfessionStateAssembler() {
    }

    /** 组装器接口：由 profession 模块实现 */
    public interface Assembler {
        ProfessionStateCache.ProfessionStateView build(ServerPlayer player);
    }

    private static volatile @Nullable Assembler registered;

    public static void register(Assembler assembler) {
        registered = assembler;
    }

    public static @Nullable Assembler get() {
        return registered;
    }
}
