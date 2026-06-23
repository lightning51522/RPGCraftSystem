package com.rpgcraft.core.ui;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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
     * @param activeSecondary  已激活副职业集合（每个独立激活，加成共存）
     * @param levels           职业 ID → 等级
     * @param unlocked         已解锁职业集合
     * @param nodes            职业树节点元数据（用于渲染，与玩家无关）
     */
    public record ProfessionStateView(
            int pool,
            Identifier currentMain,
            Set<Identifier> activeSecondary,
            Map<Identifier, Integer> levels,
            Set<Identifier> unlocked,
            List<ProfessionNode> nodes
    ) {
    }

    /**
     * 职业树节点元数据
     *
     * @param id            职业 ID
     * @param displayName   显示名
     * @param description   描述
     * @param prerequisites 前置职业 ID 列表（空列表表示树根；单前置职业含 1 个元素；复合职业含多个）
     * @param advanced      是否进阶职业（即 {@code !prerequisites.isEmpty()}）
     * @param type          职业类型（主职业 / 副职业 / 复合职业），决定节点归属哪棵树
     * @param maxLevel      该职业的等级上限
     * @param iconItem      节点物品图标（{@link IProfession#getIconItem()}），空物品表示回退到字符图标
     * @param iconChar      节点字符图标（{@link IProfession#getIconChar()}），物品图标为空时使用
     */
    public record ProfessionNode(
            Identifier id,
            String displayName,
            String description,
            List<Identifier> prerequisites,
            boolean advanced,
            IProfession.ProfessionType type,
            int maxLevel,
            ItemStack iconItem,
            String iconChar
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
