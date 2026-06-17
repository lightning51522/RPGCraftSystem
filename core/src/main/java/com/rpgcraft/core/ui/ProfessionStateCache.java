package com.rpgcraft.core.ui;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 职业面板客户端状态缓存（仅客户端运行时调用）
 * <p>
 * 缓存从服务端接收的完整职业状态（含职业树元数据、玩家进度），供职业面板
 * {@code RPGProfessionScreen} 渲染与交互。服务端在玩家打开面板/状态变化时通过
 * {@link com.rpgcraft.core.network.SyncProfessionStatePacket} 推送。
 * <p>
 * 使用 {@code volatile} 保证网络线程写入和渲染线程读取的可见性。
 *
 * @see com.rpgcraft.core.network.SyncProfessionStatePacket
 */
public final class ProfessionStateCache {

    private ProfessionStateCache() {
    }

    /**
     * 不可变职业状态视图（服务端推送，客户端缓存）
     *
     * @param pool             可分配职业经验池
     * @param currentMain      当前主职业 ID
     * @param currentSecondary 当前副职业 ID（可为 null）
     * @param secondaryActive  副职业加成开关
     * @param levels           职业 ID → 等级
     * @param unlocked         已解锁职业集合
     * @param nodes            职业树节点元数据（用于渲染，与玩家无关）
     */
    public record ProfessionStateView(
            int pool,
            Identifier currentMain,
            @Nullable Identifier currentSecondary,
            boolean secondaryActive,
            Map<Identifier, Integer> levels,
            Set<Identifier> unlocked,
            List<ProfessionNode> nodes
    ) {
    }

    /**
     * 职业树节点元数据
     *
     * @param id          职业 ID
     * @param displayName 显示名
     * @param description 描述
     * @param prerequisite 前置职业 ID（可为 null）
     * @param advanced    是否进阶职业
     * @param type        职业类型（主职业 / 副职业），决定节点归属哪棵树
     * @param maxLevel    该职业的等级上限（来自 JSON max_level / 全局 default_max_level）
     */
    public record ProfessionNode(
            Identifier id,
            String displayName,
            String description,
            @Nullable Identifier prerequisite,
            boolean advanced,
            IProfession.ProfessionType type,
            int maxLevel
    ) {
    }

    private static volatile @Nullable ProfessionStateView cached;

    public static void set(ProfessionStateView view) {
        cached = view;
    }

    public static @Nullable ProfessionStateView get() {
        return cached;
    }

    public static void clear() {
        cached = null;
    }
}
