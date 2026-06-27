package com.rpgcraft.region;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.spatial.RegionIndex;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域定义 JSON 加载器
 * <p>
 * 从 datapack 目录 {@code data/rpgcraftcore/rpg/regions/*.json} 扫描所有区域定义，
 * 每个文件代表一个区域，文件名（去 {@code .json}）即区域 ID 的 path，命名空间固定
 * {@code rpgcraftcore}。
 * <p>
 * 加载完成后重建 {@link RegionsRegistry}（先 clear 再灌入），实现 {@code /reload}
 * 即时生效。多 pack 叠加时栈顶（列表最后）优先级最高。
 * <p>
 * 兜底与校验（违规项打 WARN 并跳过该区域，不崩溃）：
 * <ul>
 *   <li>JSON 解析失败 / Codec 解码失败 → 跳过</li>
 *   <li>多边形顶点 &lt; 3 → 跳过</li>
 *   <li>同 ID 重复定义 → 后者覆盖前者并 WARN</li>
 * </ul>
 *
 * @see Region
 * @see RegionsRegistry
 */
@EventBusSubscriber(modid = RegionMod.MODID)
public class RegionsDefinitionLoader {

    /** 区域定义文件所在目录（相对 datapack 根） */
    private static final String REGIONS_DIR = "rpg/regions";
    /** 区域命名空间（与其他 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";

    private static final Gson GSON = new Gson();

    /** 当前服务器实例（供 /reload 后重建空间索引） */
    private static volatile MinecraftServer currentServer;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        currentServer = event.getServer();
        // 服务器启动后从 SavedData 恢复运行时区域到 registry，并重建索引
        // （reload 时拿不到 server，故运行时区域恢复在此处完成，而非 applyLoaded）
        RuntimeRegionSavedData savedData = RuntimeRegionSavedData.get(currentServer);
        savedData.syncToRegistry();
        RegionIndex.rebuild();
        RegionMod.LOGGER.info("已从存档恢复 {} 个运行时区域", savedData.getRegions().size());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        currentServer = null;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/regions"),
                new SimplePreparableReloadListener<Map<String, JsonObject>>() {
                    @Override
                    protected @NonNull Map<String, JsonObject> prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return scanRegionFiles(rm);
                    }

                    @Override
                    protected void apply(@NonNull Map<String, JsonObject> files, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        applyLoaded(files);
                    }
                });
    }

    // ------------------------------------------------------------------
    // prepare 阶段：扫描 + 解析
    // ------------------------------------------------------------------

    /**
     * 扫描 {@code data/rpgcraftcore/rpg/regions/*.json}，返回 path → JSON
     * <p>
     * 多 pack 叠加时栈顶（列表最后）优先级最高，取第一个成功解析的 JsonObject。
     */
    private static Map<String, JsonObject> scanRegionFiles(ResourceManager rm) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        Map<Identifier, List<Resource>> stacks = rm.listResourceStacks(REGIONS_DIR,
                id -> id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            Identifier fileId = entry.getKey();
            String path = fileId.getPath();
            String nameWithExt = path.substring(path.lastIndexOf('/') + 1);
            String regionPath = nameWithExt.substring(0, nameWithExt.length() - ".json".length());
            List<Resource> resources = entry.getValue();
            for (int i = resources.size() - 1; i >= 0; i--) {
                try (var reader = resources.get(i).openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonObject()) {
                        result.put(regionPath, parsed.getAsJsonObject());
                        break;
                    }
                } catch (Exception e) {
                    RegionMod.LOGGER.warn("解析区域定义失败: {} - {}: {}", fileId,
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // apply 阶段：解析 + 校验 + 重建注册表
    // ------------------------------------------------------------------

    private static void applyLoaded(Map<String, JsonObject> files) {
        Map<Identifier, Region> regions = new LinkedHashMap<>();

        for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
            String path = entry.getKey();
            Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
            try {
                Region region = parseRegion(id, entry.getValue());
                if (region != null) {
                    if (regions.containsKey(id)) {
                        RegionMod.LOGGER.warn("区域 {} 重复定义，后者覆盖前者", id);
                    }
                    regions.put(id, region);
                }
            } catch (Exception e) {
                RegionMod.LOGGER.warn("解析区域 {} 失败，已跳过: {}", id, e.getMessage());
            }
        }

        // 重建静态区域注册表（不影响运行时区域：replaceDatapack 只换 static 层）
        RegionsRegistry.replaceDatapack(regions);

        // 重建空间索引（含 static + runtime；runtime 层由 ServerStartedEvent 从 SavedData 恢复）
        RegionIndex.rebuild();

        RegionMod.LOGGER.info("已加载 {} 个静态区域定义", regions.size());
    }

    /**
     * 用 DFU Codec 解析单个区域 JSON
     * <p>
     * Region.CODEC 期望字段名为 {@code _id}，此处将 ID 注入 JSON 后解码。
     */
    private static Region parseRegion(Identifier id, JsonObject json) {
        // 注入 ID 供 Codec 读取（Codec 用 "_id" 字段避免与文件名脱节）
        JsonObject withId = GSON.fromJson(json, JsonObject.class);
        withId.addProperty("_id", id.toString());

        // Codec 解码：JSON → Region
        var result = Region.CODEC.parse(JsonOps.INSTANCE, withId);
        if (result.error().isPresent()) {
            RegionMod.LOGGER.warn("区域 {} Codec 解码失败: {}", id, result.error().get().message());
            return null;
        }
        // error() 已确认不存在；result() 理论上仍可能为空（部分结果），orElseThrow 自文档化
        Region region = result.result().orElseThrow(() -> new IllegalStateException(
                "区域 " + id + " 解码无错误但结果为空（部分结果）"));

        // 校验多边形
        if (region.getPolygon().vertexCount() < 3) {
            RegionMod.LOGGER.warn("区域 {} 多边形顶点数 < 3（实际 {}），已跳过",
                    id, region.getPolygon().vertexCount());
            return null;
        }

        return region;
    }
}
