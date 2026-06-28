package com.rpgcraft.region.spatial;

import com.rpgcraft.region.BiomeCategoryRegistry;
import com.rpgcraft.region.BiomeRegionFeature;
import com.rpgcraft.region.RegionsRegistry;
import com.rpgcraft.region.data.BiomeCategory;
import com.rpgcraft.region.data.BiomeRegion;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.data.RegionView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域位置查询门面
 * <p>
 * 给定一个实体或坐标，返回它当前所处的所有区域。<b>结果合并两条来源</b>：
 * <ul>
 *   <li><b>几何区域</b>：委托 {@link RegionIndex} 做 chunk 索引粗筛，再对候选区域做多边形
 *       inside 精判（XZ 多边形 + Y 范围柱体）</li>
 *   <li><b>生物群系区域</b>：读取所在坐标的<b>已存储</b>原版生物群系（区块的生物群系列就是
 *       世界生成阶段的产物，已加载区块即拥有完整范围数据），经
 *       {@link BiomeCategoryRegistry} 归到某一类别，派生 {@link BiomeRegion}</li>
 * </ul>
 * 这样「世界生成与加载过程中的范围划分」天然满足——我们不预生成多边形，而是消费已加载
 * 区块的生物群系信息。客户端侧 / 区块未加载时不读生物群系，仅返回几何区域。
 * <p>
 * <h3>性能特征</h3>
 * <ul>
 *   <li>几何路径：O(1) chunk 查表 + 少量候选区域射线法 inside</li>
 *   <li>生物群系路径：{@code level.getBiome(pos)} O(1)（命中缓存）+ 反向索引 O(1) 查类别</li>
 *   <li>单个实体查询耗时可忽略，适合每 N tick 对所有在线玩家调用</li>
 * </ul>
 *
 * @see RegionIndex
 * @see BiomeCategoryRegistry
 */
public final class RegionLocator {

    private RegionLocator() {}

    /**
     * 查询实体当前所在的所有区域（几何 + 生物群系）
     *
     * @param entity 目标实体
     * @return 区域列表（新建列表，可能为空）
     */
    public static List<RegionView> regionsAt(Entity entity) {
        return regionsAt(entity.level(), entity.position());
    }

    /**
     * 查询指定世界下某坐标点所在的所有区域（几何 + 生物群系）
     *
     * @param level 世界（用于几何索引过滤维度 + 读生物群系）
     * @param pos   世界坐标
     * @return 区域列表（新建列表，可能为空）
     */
    public static List<RegionView> regionsAt(Level level, Vec3 pos) {
        return regionsAt(level, pos.x, pos.y, pos.z);
    }

    /**
     * 查询指定世界下某坐标点所在的所有区域（几何 + 生物群系）
     *
     * @param level 世界（用于几何索引过滤维度 + 读生物群系）
     * @param x     世界 X（浮点，取整后判定）
     * @param y     世界 Y
     * @param z     世界 Z（浮点，取整后判定）
     * @return 区域列表（新建列表，可能为空）
     */
    public static List<RegionView> regionsAt(Level level, double x, double y, double z) {
        int bx = floor(x);
        int by = floor(y);
        int bz = floor(z);
        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;
        ResourceKey<Level> dimension = level.dimension();

        // 1) 几何区域：chunk 查表 + 多边形精判（维度过滤）
        List<Region> geomCandidates = RegionIndex.get().regionsAtChunk(dimension, chunkX, chunkZ);
        List<RegionView> hits = null;
        for (Region r : geomCandidates) {
            if (r.getDimension().equals(dimension) && r.getPolygon().contains(bx, by, bz)) {
                if (hits == null) hits = new ArrayList<>(2);
                hits.add(r);
            }
        }

        // 2) 生物群系区域：仅在服务端读取（客户端无生物群系归类数据）
        if (!level.isClientSide()) {
            BiomeRegion biomeRegion = biomeRegionAt(level, dimension, bx, by, bz);
            if (biomeRegion != null) {
                if (hits == null) hits = new ArrayList<>(2);
                hits.add(biomeRegion);
            }
        }

        return hits != null ? hits : Collections.emptyList();
    }

    /**
     * 查询指定维度下某坐标点所在的所有<b>几何</b>区域
     * <p>
     * 兼容旧调用方的纯几何重载（不带 Level，无法读生物群系）。优先使用
     * {@link #regionsAt(Level, double, double, double)} 以获得几何 + 生物群系完整结果。
     *
     * @param dimension 维度 key
     * @param x         世界 X（浮点，取整后判定）
     * @param y         世界 Y
     * @param z         世界 Z（浮点，取整后判定）
     * @return 几何区域列表（新建列表，可能为空）
     */
    public static List<RegionView> regionsAt(ResourceKey<Level> dimension, double x, double y, double z) {
        int bx = floor(x);
        int by = floor(y);
        int bz = floor(z);
        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;

        List<Region> candidates = RegionIndex.get().regionsAtChunk(dimension, chunkX, chunkZ);
        if (candidates.isEmpty()) return Collections.emptyList();

        List<RegionView> hits = null;
        for (Region r : candidates) {
            if (r.getDimension().equals(dimension) && r.getPolygon().contains(bx, by, bz)) {
                if (hits == null) hits = new ArrayList<>(2);
                hits.add(r);
            }
        }
        return hits != null ? hits : Collections.emptyList();
    }

    /**
     * 按 ID 反查区域视图（几何区域优先，miss 则查生物群系区域）
     * <p>
     * 供 {@link com.rpgcraft.region.RegionManager} 在玩家离开区域时取显示名：几何区域查
     * {@link RegionsRegistry}，miss 则查生物群系类别注册表。
     *
     * @param id 区域 ID（几何 ID 或合成 BiomeRegion ID）
     * @return 对应区域视图；不存在返回 null
     */
    @Nullable
    public static RegionView regionViewById(Identifier id) {
        Region geom = RegionsRegistry.get().get(id);
        if (geom != null) return geom;
        return BiomeCategoryRegistry.get().viewForId(id);
    }

    /**
     * 读取指定方块坐标的存储生物群系，归类后派生 {@link BiomeRegion}
     * <p>
     * 仅在服务端调用（调用方已保证）。{@code level.getBiome} 返回 {@code Holder<Biome>}，
     * 其 {@code unwrapKey()} 在注册表生物群系上返回 {@code ResourceKey<Biome>}（自定义非注册表
     * 生物群系返回 empty，此时兜底为无生物群系区域）。区块未加载时 MC 会返回「Void」占位生物群系，
     * 其 key 不在任何类别，故自然兜底为 null。
     *
     * @return 派生的 BiomeRegion；该坐标生物群系未归类返回 null
     */
    @Nullable
    private static BiomeRegion biomeRegionAt(Level level, ResourceKey<Level> dimension,
                                             int bx, int by, int bz) {
        // 全局开关关闭时直接跳过（不读生物群系、不查类别表），由调用方返回空 → 仅几何区域生效
        if (!BiomeRegionFeature.isEnabled()) return null;
        var biomeOpt = level.getBiome(MAIN_THREAD_POS.set(bx, by, bz)).unwrapKey();
        if (biomeOpt.isEmpty()) return null; // 非注册表生物群系，兜底
        ResourceKey<Biome> biomeKey = biomeOpt.get();
        BiomeCategory category = BiomeCategoryRegistry.get().categoryFor(biomeKey);
        if (category == null) return null; // 未归类，兜底
        return new BiomeRegion(dimension, category);
    }

    /** 向负无穷取整（Math.floor 的 int 版本） */
    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /**
     * 可复用的 MutableBlockPos（仅主线程调用，避免每次查询 new）
     * <p>
     * RegionLocator 仅在服务端主线程被 {@code RegionTickHandler}（玩家）与
     * {@code GatherAttributeEvent}（非玩家，主线程）调用，故无并发问题。
     */
    private static final BlockPos.MutableBlockPos MAIN_THREAD_POS = new BlockPos.MutableBlockPos();
}
