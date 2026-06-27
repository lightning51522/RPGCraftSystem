package com.rpgcraft.region;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.spatial.RegionIndex;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时区域持久化存储
 * <p>
 * 持久化玩家通过 {@code setregion done} 创建的运行时区域（跨服务器重启保留）。
 * 采用 MC 26.1 的 {@link SavedDataType} + {@link Codec} 模式，挂载到
 * {@link MinecraftServer#getDataStorage()}（服务器全局，不绑定具体维度）。
 * <p>
 * <h3>与 {@link RegionsRegistry} 的协作</h3>
 * 本类负责持久化；内存中的查询走 {@link RegionsRegistry}（含 static + runtime 双层）。
 * 每次 {@link #addRegion} / {@link #removeRegion} 后同步更新 registry 和重建
 * {@link RegionIndex}（chunk 索引），保证 tick 查询立即生效。
 * <p>
 * <h3>reload 合并</h3>
 * {@code /reload} 触发 {@link RegionsDefinitionLoader} 重建 static 区域时，会从本类读取
 * 运行时区域合并进 registry（runtime 部分不受 reload 影响）。
 *
 * @see RegionsRegistry
 * @see RegionIndex
 */
public class RuntimeRegionSavedData extends SavedData {

    /** NBT 字段名 */
    private static final String KEY_REGIONS = "runtime_regions";

    /** 当前持久化的运行时区域列表 */
    private final List<Region> regions;

    /**
     * 序列化编解码器（Codec 模式）
     * <p>
     * 存储为 Region 列表，每个 Region 含完整几何 + 效果。
     */
    public static final Codec<RuntimeRegionSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Region.CODEC.listOf()
                            .optionalFieldOf(KEY_REGIONS, List.of())
                            .forGetter(data -> data.regions)
            ).apply(instance, RuntimeRegionSavedData::new)
    );

    /**
     * SavedData 类型标识
     */
    public static final SavedDataType<RuntimeRegionSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(RegionMod.MODID, "runtime_regions"),
            RuntimeRegionSavedData::new,
            CODEC
    );

    /** 默认构造（无运行时区域） */
    public RuntimeRegionSavedData() {
        this.regions = new ArrayList<>();
    }

    /** 反序列化构造 */
    public RuntimeRegionSavedData(List<Region> regions) {
        this.regions = new ArrayList<>(regions);
    }

    /**
     * 获取或创建服务器全局的持久化数据
     */
    public static RuntimeRegionSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** 当前所有运行时区域（只读视图） */
    public List<Region> getRegions() {
        return List.copyOf(regions);
    }

    /**
     * 添加一个运行时区域（持久化 + 同步 registry + 重建索引）
     *
     * @param server 服务端实例（用于 registry 同步）
     * @param region 待添加区域
     */
    public void addRegion(MinecraftServer server, Region region) {
        regions.add(region);
        setDirty();
        RegionsRegistry.get().addRuntime(region);
        RegionIndex.rebuild();
    }

    /**
     * 按 ID 移除运行时区域（持久化 + 同步 registry + 重建索引）
     *
     * @param server   服务端实例
     * @param regionId 区域 ID
     * @return true 若移除成功；false 若无此区域
     */
    public boolean removeRegion(MinecraftServer server, Identifier regionId) {
        boolean removed = regions.removeIf(r -> r.getId().equals(regionId));
        if (removed) {
            setDirty();
            RegionsRegistry.get().removeRuntime(regionId);
            RegionIndex.rebuild();
        }
        return removed;
    }

    /**
     * 将所有运行时区域同步到 registry（服务端启动 / reload 合并时调用）
     * <p>
     * 清空 registry 的 runtime 层后重新灌入本类持久化的区域。不触发 setDirty（仅同步内存）。
     */
    public void syncToRegistry() {
        Map<Identifier, Region> map = new LinkedHashMap<>();
        for (Region r : regions) {
            map.put(r.getId(), r);
        }
        RegionsRegistry.get().replaceAllRuntime(map);
    }
}
