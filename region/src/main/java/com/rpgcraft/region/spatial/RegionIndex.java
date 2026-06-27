package com.rpgcraft.region.spatial;

import com.rpgcraft.region.RegionsRegistry;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.data.RegionPolygon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 按 chunk 索引区域的空间加速结构
 * <p>
 * 在加载区域时，遍历每个区域多边形的 XZ 包围盒，将其登记到所有覆盖的 chunk 桶中。
 * 运行期位置查询只需 O(1) 取 {@link ChunkPos.pack(long, long)} 查表，再对命中的少量
 * 区域做多边形 inside 精判，避免全量扫描。
 * <p>
 * <h3>为何不按维度分两张表</h3>
 * 每个区域已绑定维度，索引时仍按 chunk 全局分桶；查询时 {@link RegionLocator} 会先按
 * {@link Region#getDimension()} 过滤。由于不同维度的同坐标 chunk 罕有区域重叠，
 * 实践中命中的候选集已经很小。
 *
 * @see RegionLocator
 */
public final class RegionIndex {

    /** 当前索引实例（reload 时由 RegionsDefinitionLoader 触发重建） */
    private static volatile RegionIndex instance = new RegionIndex(new Long2ObjectOpenHashMap<>());

    /** chunk long key → 该 chunk 可能覆盖的区域列表 */
    private final Long2ObjectMap<List<Region>> byChunk;

    private RegionIndex(Long2ObjectMap<List<Region>> byChunk) {
        this.byChunk = byChunk;
    }

    /**
     * 从 {@link RegionsRegistry} 重建索引
     * <p>
     * 遍历每个区域，计算其 XZ 包围盒覆盖的所有 chunk（步长 16），登记到对应桶。
     */
    public static void rebuild() {
        Long2ObjectMap<List<Region>> map = new Long2ObjectOpenHashMap<>();
        for (Region region : RegionsRegistry.get().all()) {
            RegionPolygon poly = region.getPolygon();
            // chunk 坐标范围（整数除法向负无穷取整 = floor）
            int minCX = poly.getMinBoxX() >> 4;
            int maxCX = poly.getMaxBoxX() >> 4;
            int minCZ = poly.getMinBoxZ() >> 4;
            int maxCZ = poly.getMaxBoxZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    long key = ChunkPos.pack(cx, cz);
                    map.computeIfAbsent(key, k -> new ArrayList<>()).add(region);
                }
            }
        }
        // 冻结每个桶为不可变列表
        for (var entry : map.long2ObjectEntrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        instance = new RegionIndex(map);
    }

    /** 获取当前索引实例 */
    public static RegionIndex get() {
        return instance;
    }

    /**
     * 查询指定 chunk 可能覆盖的区域列表
     *
     * @param chunkX chunk X 坐标
     * @param chunkZ chunk Z 坐标
     * @return 区域列表（不可变，可能为空）
     */
    public List<Region> regionsAtChunk(int chunkX, int chunkZ) {
        return byChunk.getOrDefault(ChunkPos.pack(chunkX, chunkZ), Collections.emptyList());
    }

    /**
     * 查询指定维度下某 chunk 可能覆盖的区域列表（额外按维度过滤）
     *
     * @param dimension 维度 key
     * @param chunkX    chunk X 坐标
     * @param chunkZ    chunk Z 坐标
     * @return 区域列表（不可变，可能为空）
     */
    public List<Region> regionsAtChunk(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        List<Region> raw = byChunk.get(ChunkPos.pack(chunkX, chunkZ));
        if (raw == null) return Collections.emptyList();
        // 多数情况下同 chunk 内区域同维度，直接返回；否则按维度过滤
        boolean allSameDim = true;
        for (Region r : raw) {
            if (!r.getDimension().equals(dimension)) { allSameDim = false; break; }
        }
        return allSameDim ? raw : raw.stream().filter(r -> r.getDimension().equals(dimension)).toList();
    }
}
