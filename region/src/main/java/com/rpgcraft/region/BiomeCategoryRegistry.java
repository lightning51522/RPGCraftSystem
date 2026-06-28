package com.rpgcraft.region;

import com.rpgcraft.region.data.BiomeCategory;
import com.rpgcraft.region.data.BiomeRegion;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物群系类别注册表（内存态，{@code /reload} 时整体重建）
 * <p>
 * 维护两张表：
 * <ul>
 *   <li><b>正向</b>：{@code id → BiomeCategory}（供按 ID 反查类别）</li>
 *   <li><b>反向索引</b>：{@code ResourceKey<Biome> → 类别 id}（供按生物群系快速归类，O(1)）</li>
 * </ul>
 * 由 {@link BiomeCategoryLoader} 在服务端 reload 时灌入。查询路径：
 * {@link com.rpgcraft.region.spatial.RegionLocator} 读到生物群系 key →
 * {@link #categoryFor(ResourceKey)} → 取类别 → 派生 {@link BiomeRegion}。
 * <p>
 * <h3>线程安全</h3>
 * 读路径（tick 查询、伤害计算）在主线程；写路径（reload）在 reload 线程的 apply 阶段（主线程）。
 * 用 volatile 引用保证 reload 后立即可见，每次写操作重建不可变实例。
 *
 * @see BiomeCategoryLoader
 * @see BiomeCategory
 */
public final class BiomeCategoryRegistry {

    /**
     * 合成 ID 中可能出现的维度 path 小集合
     * <p>
     * 用于 {@link #viewForId} 反查：枚举维度前缀剥离出 category path。原版三维度封闭，
     * 模组自定义维度极少与生物群系区域绑定；未覆盖的维度路径返回 null（兜底，无显示名回退到 ID path）。
     */
    private static final List<ResourceKey<Level>> KNOWN_DIMENSIONS = List.of(
            Level.OVERWORLD, Level.NETHER, Level.END
    );

    /** 全局实例（加载时由 BiomeCategoryLoader 替换） */
    private static volatile BiomeCategoryRegistry instance =
            new BiomeCategoryRegistry(Map.of(), Map.of(), Map.of());

    /** id → BiomeCategory（不可变） */
    private final Map<Identifier, BiomeCategory> categories;
    /** 生物群系 key → 类别 id（不可变，反向索引） */
    private final Map<ResourceKey<Biome>, Identifier> biomeToCategory;
    /** 类别 id → 合成 BiomeRegion 模板（按 overworld 维度预算，反查显示名用） */
    private final Map<Identifier, BiomeRegion> overworldTemplates;

    private BiomeCategoryRegistry(Map<Identifier, BiomeCategory> categories,
                                  Map<ResourceKey<Biome>, Identifier> biomeToCategory,
                                  Map<Identifier, BiomeRegion> overworldTemplates) {
        this.categories = categories;
        this.biomeToCategory = biomeToCategory;
        this.overworldTemplates = overworldTemplates;
    }

    /**
     * 用新类别集合替换当前注册表，并重建反向索引
     *
     * @param categories 新的 id → BiomeCategory 映射
     */
    public static void replaceAll(Map<Identifier, BiomeCategory> categories) {
        Map<Identifier, BiomeCategory> catMap = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        // 重建反向索引：biome key → 类别 id（后写入的类别覆盖前者并 WARN 由 loader 保证）
        Map<ResourceKey<Biome>, Identifier> reverse = new LinkedHashMap<>();
        for (Map.Entry<Identifier, BiomeCategory> e : catMap.entrySet()) {
            for (ResourceKey<Biome> biome : e.getValue().biomes()) {
                reverse.put(biome, e.getKey());
            }
        }
        // 预算 overworld 维度的合成 BiomeRegion 模板，供 viewForId 反查显示名
        Map<Identifier, BiomeRegion> templates = new LinkedHashMap<>();
        for (BiomeCategory c : catMap.values()) {
            BiomeRegion tpl = new BiomeRegion(Level.OVERWORLD, c);
            templates.put(c.id(), tpl);
        }
        instance = new BiomeCategoryRegistry(
                catMap,
                Collections.unmodifiableMap(reverse),
                Collections.unmodifiableMap(templates)
        );
    }

    /** 获取当前注册表实例 */
    public static BiomeCategoryRegistry get() {
        return instance;
    }

    /** 类别总数 */
    public int size() {
        return categories.size();
    }

    /**
     * 按生物群系 key 查类别（O(1) 反向索引）
     *
     * @param biomeKey 生物群系 ResourceKey
     * @return 对应类别；无归类返回 null（兜底：无生物群系区域效果）
     */
    public BiomeCategory categoryFor(ResourceKey<Biome> biomeKey) {
        Identifier categoryId = biomeToCategory.get(biomeKey);
        return categoryId == null ? null : categories.get(categoryId);
    }

    /**
     * 按合成 BiomeRegion ID 反查区域视图
     * <p>
     * 供 {@link com.rpgcraft.region.RegionManager} 在玩家离开区域时取显示名：几何区域查
     * {@link RegionsRegistry}，miss 则查本方法（生物群系区域）。返回的视图以 overworld 维度
     * 合成（仅供取 {@code getName()}，维度不影响显示名与效果）。
     *
     * @param biomeRegionId 合成 ID（{@code rpgcraftregion:biome_<dim>_<category>}）
     * @return 对应 BiomeRegion；非生物群系 ID 返回 null
     */
    public BiomeRegion viewForId(Identifier biomeRegionId) {
        if (!biomeRegionId.getNamespace().equals(RegionMod.MODID)) return null;
        String path = biomeRegionId.getPath();
        // 合成 path = biome_<dim>_<category>。dim 属于固定小集合，按此逐维度重构造比对
        for (ResourceKey<Level> dim : KNOWN_DIMENSIONS) {
            String prefix = "biome_" + dim.identifier().getPath() + "_";
            if (path.startsWith(prefix)) {
                String categoryPath = path.substring(prefix.length());
                Identifier categoryId = Identifier.fromNamespaceAndPath(
                        BiomeCategoryLoader.NAMESPACE, categoryPath);
                BiomeRegion tpl = overworldTemplates.get(categoryId);
                if (tpl != null) return tpl;
            }
        }
        return null;
    }

    /** 所有类别（不可变） */
    public Iterable<BiomeCategory> all() {
        return categories.values();
    }
}
