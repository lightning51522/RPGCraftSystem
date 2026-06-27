package com.rpgcraft.region;

import com.rpgcraft.region.data.Region;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域注册表（内存态，{@code /reload} 时整体重建）
 * <p>
 * 维护 id → {@link Region} 的映射，并按维度分桶以便快速查询。
 * 由 {@link RegionsDefinitionLoader} 在服务端 reload 时灌入。
 * <p>
 * <h3>线程安全</h3>
 * 读路径（tick 查询、伤害计算）在服务端主线程；写路径（reload）在 reload 线程。
 * 用 {@link ConcurrentHashMap} + volatile 引用保证 reload 后所有线程立即看到新表。
 *
 * @see RegionsDefinitionLoader
 */
public final class RegionsRegistry {

    /** 全局实例（加载时由 RegionsDefinitionLoader 替换） */
    private static volatile RegionsRegistry instance = new RegionsRegistry(Map.of());

    /** id → Region（不可变快照） */
    private final Map<Identifier, Region> regions;
    /** 维度 → 该维度下的区域列表（不可变快照） */
    private final Map<ResourceKey<Level>, List<Region>> byDimension;

    private RegionsRegistry(Map<Identifier, Region> regions) {
        this.regions = regions;
        // 按维度分桶
        Map<ResourceKey<Level>, List<Region>> bucketed = new LinkedHashMap<>();
        for (Region r : regions.values()) {
            bucketed.computeIfAbsent(r.getDimension(), k -> new ArrayList<>()).add(r);
        }
        // 冻结每个桶为不可变列表
        Map<ResourceKey<Level>, List<Region>> frozen = new LinkedHashMap<>();
        for (var e : bucketed.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        this.byDimension = Collections.unmodifiableMap(frozen);
    }

    /**
     * 用新区域集合替换当前注册表
     * <p>
     * 由 {@link RegionsDefinitionLoader} 在 apply 阶段调用。
     *
     * @param regions 新的区域映射（会被内部复制为不可变）
     */
    public static void replaceAll(Map<Identifier, Region> regions) {
        instance = new RegionsRegistry(Collections.unmodifiableMap(new LinkedHashMap<>(regions)));
    }

    /** 获取当前注册表实例 */
    public static RegionsRegistry get() {
        return instance;
    }

    /** 区域总数 */
    public int size() {
        return regions.size();
    }

    /** 按 ID 查询区域（可能为 null） */
    public Region get(Identifier id) {
        return regions.get(id);
    }

    /** 所有区域（不可变） */
    public Iterable<Region> all() {
        return regions.values();
    }

    /**
     * 获取指定维度下的所有区域
     *
     * @param dimension 维度 key
     * @return 区域列表（不可变，可能为空）
     */
    public List<Region> inDimension(ResourceKey<Level> dimension) {
        return byDimension.getOrDefault(dimension, List.of());
    }
}
