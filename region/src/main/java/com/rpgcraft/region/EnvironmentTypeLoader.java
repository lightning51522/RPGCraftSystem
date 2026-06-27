package com.rpgcraft.region;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.rpgcraft.region.data.EnvironmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境类型 JSON 加载器
 * <p>
 * 从 datapack 目录 {@code data/rpgcraftcore/rpg/environments/*.json} 扫描所有环境类型定义，
 * 每个文件代表一种环境类型（如「火山」），文件名（去 {@code .json}）即环境类型 ID 的 path，
 * 命名空间固定 {@code rpgcraftcore}。
 * <p>
 * 加载完成后重建 {@link EnvironmentTypeRegistry}（先 clear 再灌入），实现 {@code /reload}
 * 即时生效。多 pack 叠加时栈顶优先。
 * <p>
 * 兜底与校验（违规项打 WARN 并跳过该环境类型，不崩溃）：
 * <ul>
 *   <li>JSON 解析失败 / Codec 解码失败 → 跳过</li>
 *   <li>同 ID 重复定义 → 后者覆盖前者并 WARN</li>
 * </ul>
 *
 * @see EnvironmentType
 * @see EnvironmentTypeRegistry
 */
@EventBusSubscriber(modid = RegionMod.MODID)
public class EnvironmentTypeLoader {

    /** 环境类型定义文件所在目录（相对 datapack 根） */
    private static final String ENVIRONMENTS_DIR = "rpg/environments";
    /** 环境类型命名空间（与其他 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";

    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/environments"),
                new SimplePreparableReloadListener<Map<String, JsonObject>>() {
                    @Override
                    protected @NonNull Map<String, JsonObject> prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return scanEnvironmentFiles(rm);
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
     * 扫描 {@code data/rpgcraftcore/rpg/environments/*.json}，返回 path → JSON
     */
    private static Map<String, JsonObject> scanEnvironmentFiles(ResourceManager rm) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        Map<Identifier, List<Resource>> stacks = rm.listResourceStacks(ENVIRONMENTS_DIR,
                id -> id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            Identifier fileId = entry.getKey();
            String path = fileId.getPath();
            String nameWithExt = path.substring(path.lastIndexOf('/') + 1);
            String envPath = nameWithExt.substring(0, nameWithExt.length() - ".json".length());
            List<Resource> resources = entry.getValue();
            for (int i = resources.size() - 1; i >= 0; i--) {
                try (var reader = resources.get(i).openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonObject()) {
                        result.put(envPath, parsed.getAsJsonObject());
                        break;
                    }
                } catch (Exception e) {
                    RegionMod.LOGGER.warn("解析环境类型定义失败: {} - {}: {}", fileId,
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
        Map<Identifier, EnvironmentType> types = new LinkedHashMap<>();

        for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
            String path = entry.getKey();
            Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
            try {
                EnvironmentType type = parseEnvironmentType(id, entry.getValue());
                if (type != null) {
                    if (types.containsKey(id)) {
                        RegionMod.LOGGER.warn("环境类型 {} 重复定义，后者覆盖前者", id);
                    }
                    types.put(id, type);
                }
            } catch (Exception e) {
                RegionMod.LOGGER.warn("解析环境类型 {} 失败，已跳过: {}", id, e.getMessage());
            }
        }

        EnvironmentTypeRegistry.replaceAll(types);
        RegionMod.LOGGER.info("已加载 {} 个环境类型定义", types.size());
    }

    /**
     * 用 DFU Codec 解析单个环境类型 JSON
     */
    private static EnvironmentType parseEnvironmentType(Identifier id, JsonObject json) {
        JsonObject withId = GSON.fromJson(json, JsonObject.class);
        withId.addProperty("_id", id.toString());

        var result = EnvironmentType.CODEC.parse(JsonOps.INSTANCE, withId);
        if (result.error().isPresent()) {
            RegionMod.LOGGER.warn("环境类型 {} Codec 解码失败: {}", id, result.error().get().message());
            return null;
        }
        // error() 已确认不存在；orElseThrow 自文档化（result() 理论上仍可能为空的部分结果）
        return result.result().orElseThrow(() -> new IllegalStateException(
                "环境类型 " + id + " 解码无错误但结果为空（部分结果）"));
    }
}
