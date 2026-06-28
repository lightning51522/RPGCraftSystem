package com.rpgcraft.region.data;

import com.rpgcraft.region.RegionMod;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 生物群系区域（{@link RegionView} 的轻量派生实现）
 * <p>
 * 由「维度 + 生物群系类别」构建——<b>不是</b>按单个原版生物群系，而是按 {@link BiomeCategory}，
 * 使玩家在同一类别内的相邻生物群系（如 plains ↔ sunflower_plains 同属「草原」）之间移动时
 * 合成 ID 保持稳定，不触发虚假进出。
 * <p>
 * <h3>合成 ID 约定</h3>
 * {@code rpgcraftregion:biome_<dimPath>_<categoryPath>}
 * <p>
 * 含维度 path 是为避免跨维度 ID 冲突（如下界 / 末地同一类别在不同维度应独立计算进出）。
 * 这与几何区域的 sourceId 生成（{@link com.rpgcraft.region.RegionManager#modifierSourceId}）
 * 完全兼容：冒号→下划线后仍是合法单段 Identifier，离开时按 sourceId 精确移除。
 * <p>
 * <h3>为何不存几何</h3>
 * 生物群系是按列、不规则、由世界生成器计算并存储在已加载区块里的。本类不持有 XZ 多边形，
 * 边界由「所在区块的生物群系」天然定义——区块的生物群系列本身就是世界生成阶段的产物，
 * 已加载区块即拥有完整范围数据。查询时 {@link com.rpgcraft.region.spatial.RegionLocator}
 * 读取生物群系并归到本类的 {@link BiomeCategory}。
 *
 * @see BiomeCategory
 * @see RegionView
 */
public final class BiomeRegion implements RegionView {

    /** 合成 ID 的 path 前缀，与 {@code region_<...>}（几何区域）并列，便于调试与反查 */
    private static final String ID_PREFIX = "biome_";

    private final Identifier id;
    private final String displayName;
    private final List<AttributeMod> mods;

    /**
     * 由「维度 + 类别」构造
     *
     * @param dimension 维度（参与合成 ID，避免跨维度冲突）
     * @param category  生物群系类别（提供显示名与效果）
     */
    public BiomeRegion(ResourceKey<Level> dimension, BiomeCategory category) {
        this.id = Identifier.fromNamespaceAndPath(RegionMod.MODID,
                ID_PREFIX + dimension.identifier().getPath() + "_" + category.id().getPath());
        this.displayName = category.displayName();
        // 冻结一次，allMods() 后续无重算开销
        this.mods = List.copyOf(category.allMods());
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public String getName() {
        return displayName;
    }

    @Override
    public List<AttributeMod> allMods() {
        return mods;
    }
}
