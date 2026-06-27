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

/**
 * 区域注册表（内存态，双层存储）
 * <p>
 * 维护两类区域：
 * <ul>
 *   <li><b>静态区域</b>（{@link #staticRegions}）：由 datapack JSON 定义（含几何），reload 时整体替换</li>
 *   <li><b>运行时区域</b>（{@link #runtimeRegions}）：玩家通过 {@code setregion} 创建，由
 *       {@link RuntimeRegionSavedData} 持久化，reload 不影响</li>
 * </ul>
 * 查询返回两者合并视图。按维度分桶以便快速查询。
 * <p>
 * <h3>线程安全</h3>
 * 读路径（tick 查询、伤害计算）在主线程；写路径（reload / 命令）在主线程。
 * 用 volatile 引用保证更新后立即可见。每次写操作重建不可变实例（copy-on-write）。
 *
 * @see RegionsDefinitionLoader
 * @see RuntimeRegionSavedData
 */
public final class RegionsRegistry {

    /** 全局实例 */
    private static volatile RegionsRegistry instance = new RegionsRegistry(Map.of(), Map.of());

    /** 静态区域：datapack 定义（reload 替换） */
    private final Map<Identifier, Region> staticRegions;
    /** 运行时区域：玩家创建（SavedData 持久化） */
    private final Map<Identifier, Region> runtimeRegions;
    /** 合并视图：static + runtime（不可变） */
    private final Map<Identifier, Region> allRegions;
    /** 维度 → 该维度下的所有区域列表（合并视图，不可变） */
    private final Map<ResourceKey<Level>, List<Region>> byDimension;

    private RegionsRegistry(Map<Identifier, Region> staticRegions,
                             Map<Identifier, Region> runtimeRegions) {
        this.staticRegions = Collections.unmodifiableMap(new LinkedHashMap<>(staticRegions));
        this.runtimeRegions = Collections.unmodifiableMap(new LinkedHashMap<>(runtimeRegions));
        // 合并视图（runtime 优先，允许运行时区域覆盖同 ID 的静态区域）
        Map<Identifier, Region> merged = new LinkedHashMap<>(this.staticRegions);
        merged.putAll(this.runtimeRegions);
        this.allRegions = Collections.unmodifiableMap(merged);

        // 按维度分桶
        Map<ResourceKey<Level>, List<Region>> bucketed = new LinkedHashMap<>();
        for (Region r : allRegions.values()) {
            bucketed.computeIfAbsent(r.getDimension(), k -> new ArrayList<>()).add(r);
        }
        Map<ResourceKey<Level>, List<Region>> frozen = new LinkedHashMap<>();
        for (var e : bucketed.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        this.byDimension = Collections.unmodifiableMap(frozen);
    }

    // ==================================================================
    // 静态区域写 API（reload 时由 RegionsDefinitionLoader 调用）
    // ==================================================================

    /**
     * 替换静态区域（不影响运行时区域）
     * <p>
     * 由 {@link RegionsDefinitionLoader} 在 apply 阶段调用。
     *
     * @param regions 新的静态区域映射
     */
    public static void replaceDatapack(Map<Identifier, Region> regions) {
        instance = new RegionsRegistry(regions, instance.runtimeRegions);
    }

    // ==================================================================
    // 运行时区域写 API（命令 / SavedData 同步时调用）
    // ==================================================================

    /**
     * 增量添加一个运行时区域
     */
    public static void addRuntime(Region region) {
        Map<Identifier, Region> newRuntime = new LinkedHashMap<>(instance.runtimeRegions);
        newRuntime.put(region.getId(), region);
        instance = new RegionsRegistry(instance.staticRegions, newRuntime);
    }

    /**
     * 增量移除一个运行时区域
     *
     * @return true 若移除成功
     */
    public static boolean removeRuntime(Identifier regionId) {
        if (!instance.runtimeRegions.containsKey(regionId)) return false;
        Map<Identifier, Region> newRuntime = new LinkedHashMap<>(instance.runtimeRegions);
        newRuntime.remove(regionId);
        instance = new RegionsRegistry(instance.staticRegions, newRuntime);
        return true;
    }

    /**
     * 整体替换运行时区域（从 SavedData 恢复时调用）
     * <p>
     * 保留当前静态区域不变，仅替换运行时部分。
     */
    public static void replaceAllRuntime(Map<Identifier, Region> regions) {
        instance = new RegionsRegistry(instance.staticRegions, regions);
    }

    // ==================================================================
    // 查询 API（tick / 命令读取）
    // ==================================================================

    /** 获取当前实例 */
    public static RegionsRegistry get() {
        return instance;
    }

    /** 区域总数（static + runtime） */
    public int size() {
        return allRegions.size();
    }

    /** 按 ID 查询区域（static + runtime，可能为 null） */
    public Region get(Identifier id) {
        return allRegions.get(id);
    }

    /** 是否为运行时区域（非静态） */
    public boolean isRuntime(Identifier id) {
        return runtimeRegions.containsKey(id);
    }

    /** 所有区域（static + runtime，不可变） */
    public Iterable<Region> all() {
        return allRegions.values();
    }

    /** 所有静态区域（不可变） */
    public Iterable<Region> allStatic() {
        return staticRegions.values();
    }

    /** 所有运行时区域（不可变） */
    public Iterable<Region> allRuntime() {
        return runtimeRegions.values();
    }

    /**
     * 获取指定维度下的所有区域（static + runtime）
     *
     * @param dimension 维度 key
     * @return 区域列表（不可变，可能为空）
     */
    public List<Region> inDimension(ResourceKey<Level> dimension) {
        return byDimension.getOrDefault(dimension, List.of());
    }

    /**
     * 按「显示名或 ID」匹配区域（所有维度）
     * <p>
     * 用于 {@code findregion} 命令。匹配规则：显示名全等 / ID 完整形式 / ID path 任一命中。
     *
     * @param name 查询名（显示名或 ID）
     * @return 匹配的区域列表（新建可变列表，可能为空）
     */
    public List<Region> matchByName(String name) {
        List<Region> hits = new ArrayList<>();
        for (Region r : allRegions.values()) {
            if (matchesName(r, name)) {
                hits.add(r);
            }
        }
        return hits;
    }

    /** 判断区域是否匹配给定名称（显示名或 ID） */
    private static boolean matchesName(Region r, String name) {
        if (r.getName().equals(name)) return true;
        Identifier id = r.getId();
        if (id.toString().equals(name)) return true;
        return id.getPath().equals(name);
    }
}
