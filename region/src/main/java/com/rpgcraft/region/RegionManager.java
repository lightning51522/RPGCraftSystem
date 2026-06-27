package com.rpgcraft.region;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeModifier;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.region.data.AttributeMod;
import com.rpgcraft.region.data.PlayerRegionState;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.spatial.RegionLocator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 区域模块全局门面
 * <p>
 * 管理 {@link PlayerRegionState} 附件注册，并提供玩家区域修饰符的应用 / 移除逻辑。
 * <p>
 * <h3>修饰符来源 ID</h3>
 * 每个区域对每个属性施加的修饰符来源 ID 形如
 * {@code rpgcraftregion:region_<attrNs>_<attrPath>}（以区域 ID 为前缀），
 * 确保离开区域时能按 sourceId 精确移除本区域施加的所有修饰符。
 *
 * @see PlayerRegionState
 * @see RegionLocator
 */
public final class RegionManager {

    private static final String MODIFIER_PREFIX = "region_";

    private static DeferredRegister<AttachmentType<?>> deferredRegister;
    private static Supplier<AttachmentType<PlayerRegionState>> PLAYER_REGION_STATE;

    /**
     * 初始化区域模块：注册附件类型。
     * <p>
     * 由 {@link RegionMod} 构造函数调用，必须在 {@code getDeferredRegister().register(modEventBus)}
     * 之前执行。
     */
    public static void init() {
        deferredRegister = DeferredRegister.create(
                NeoForgeRegistries.ATTACHMENT_TYPES, RegionMod.MODID);

        PLAYER_REGION_STATE = deferredRegister.register("player_region_state",
                () -> AttachmentType.<PlayerRegionState>builder(() -> new PlayerRegionState())
                        .serialize(PlayerRegionState.CODEC)
                        .build()
        );
    }

    /** 获取附件 DeferredRegister（供主类注册到 Mod 事件总线） */
    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    /** 获取 PlayerRegionState 附件 Supplier */
    public static Supplier<AttachmentType<PlayerRegionState>> getPlayerRegionStateSupplier() {
        return PLAYER_REGION_STATE;
    }

    /**
     * 检查玩家位置变化，应用 / 移除区域修饰符
     * <p>
     * 流程：
     * <ol>
     *   <li>查询玩家当前所在的所有区域</li>
     *   <li>与 {@link PlayerRegionState} 记录的上次区域集合做 diff</li>
     *   <li>对「新进入」的区域：应用其全部修饰符</li>
     *   <li>对「已离开」的区域：移除其全部修饰符</li>
     *   <li>更新 {@link PlayerRegionState} 为当前集合</li>
     * </ol>
     * 若新旧集合相同则直接返回（零开销）。
     *
     * @param player 服务端玩家
     */
    public static void updatePlayerRegions(ServerPlayer player) {
        List<Region> current = RegionLocator.regionsAt(player);
        Set<Identifier> currentIds = new LinkedHashSet<>();
        for (Region r : current) {
            currentIds.add(r.getId());
        }

        PlayerRegionState state = player.getData(PLAYER_REGION_STATE.get());
        Set<Identifier> previousIds = state.getRegionIds();

        // 集合相同则跳过
        if (previousIds.equals(currentIds)) return;

        // 计算差集
        Set<Identifier> entered = new LinkedHashSet<>(currentIds);
        entered.removeAll(previousIds);
        Set<Identifier> left = new HashSet<>(previousIds);
        left.removeAll(currentIds);

        // 应用进入区域的修饰符
        for (Region r : current) {
            if (entered.contains(r.getId())) {
                applyRegionModifiers(player, r);
            }
        }
        // 移除离开区域的修饰符
        for (Identifier leftId : left) {
            removeRegionModifiers(player, leftId);
        }

        // 区域进出提示（受玩家偏好开关控制）
        if (player.getData(AttributeManager.PLAYER_PREFERENCES.get()).isRegionNotifyEnabled()) {
            // 进入提示（按 current 中的实际 Region 对象取显示名）
            for (Region r : current) {
                if (entered.contains(r.getId())) {
                    player.sendSystemMessage(Component.translatable("rpgcraft.region.entered", regionDisplayName(r)));
                }
            }
            // 离开提示（left 只有 ID，需查 registry 取显示名）
            for (Identifier leftId : left) {
                Region leftRegion = RegionsRegistry.get().get(leftId);
                player.sendSystemMessage(Component.translatable("rpgcraft.region.left", regionDisplayName(leftRegion)));
            }
        }

        // 更新状态
        state.set(currentIds);
    }

    /** 区域显示名（空则用 ID path；null 时返回本地化的"未知区域"） */
    private static Component regionDisplayName(Region r) {
        if (r == null) return Component.translatable("rpgcraft.region.unknown");
        return Component.literal(r.getName().isEmpty() ? r.getId().getPath() : r.getName());
    }

    /**
     * 应用单个区域的全部属性修饰符到玩家
     */
    private static void applyRegionModifiers(ServerPlayer player, Region region) {
        for (AttributeMod mod : region.allMods()) {
            applyModifier(player, region.getId(), mod);
        }
    }

    /**
     * 移除单个区域施加的全部属性修饰符
     * <p>
     * 按 sourceId 前缀移除。由于一个区域可能施加多个属性的修饰符，遍历所有已注册属性
     * 逐个 removeModifier（removeModifier 按 sourceId 批量移除，未命中的属性无副作用）。
     */
    private static void removeRegionModifiers(ServerPlayer player, Identifier regionId) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            IAttribute attr = player.getData(entry.getSupplier());
            attr.removeModifier(modifierSourceId(regionId, entry.getId()));
        }
    }

    /**
     * 应用单条修饰符
     * <p>
     * 若目标属性未注册（modid 卸载），{@link AttributeManager#getTypeById} 返回 null，
     * 此处静默跳过（遵循优雅降级原则）。
     */
    private static void applyModifier(ServerPlayer player, Identifier regionId, AttributeMod mod) {
        AttachmentType<EntityAttribute> type = AttributeManager.getTypeById(mod.attr());
        if (type == null) return; // 属性未注册，跳过
        EntityAttribute attr = player.getData(type);
        Identifier sourceId = modifierSourceId(regionId, mod.attr());
        // 先移除再添加，避免同 sourceId 重复累加
        attr.removeModifier(sourceId);
        attr.addModifier(AttributeModifier.of(sourceId, mod.op(), mod.value()));
    }

    /**
     * 生成区域修饰符的来源标识符
     * <p>
     * 格式：{@code rpgcraftregion:region_<regionId>_<attrId>}，区域 ID 和属性 ID 的
     * 冒号用下划线替换，确保 sourceId 是合法的单段 Identifier。
     * <p>
     * 公开供 {@link com.rpgcraft.region.apply.NpcAttributeListener} 复用，
     * 保证玩家附件路径与非玩家快照路径的 sourceId 一致。
     *
     * @param regionId 区域 ID
     * @param attrId   属性 ID
     * @return 修饰符来源标识符
     */
    public static Identifier modifierSourceId(Identifier regionId, Identifier attrId) {
        return Identifier.fromNamespaceAndPath(RegionMod.MODID,
                MODIFIER_PREFIX + regionId.getNamespace() + "_" + regionId.getPath()
                        + "_" + attrId.getNamespace() + "_" + attrId.getPath());
    }
}
