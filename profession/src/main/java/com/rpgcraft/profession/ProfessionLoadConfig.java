package com.rpgcraft.profession;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 职业加载开关配置加载器
 * <p>
 * 加载 {@code data/rpgcraftcore/rpg/profession_load.json}（归属 {@code rpgcraftprofessions}
 * 内容附属模块的专属配置），提供「每个职业是否显式启用」的查询。
 * <p>
 * 配置形式为 {@code ID → 布尔} 映射，未列出的职业默认启用（opt-out）：
 * <pre>
 * {
 *   "professions": {
 *     "rpgcraftcore:warrior": false,
 *     "rpgcraftcore:berserker": true
 *   }
 * }
 * </pre>
 * <p>
 * 本类只负责「显式启用」的读取；「有效启用」（含前置级联）由 {@link ProfessionAvailability}
 * 统一计算。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新；apply 末尾会
 * 清空 {@link ProfessionAvailability} 的缓存并向在线玩家重推职业状态。
 *
 * @see ProfessionAvailability
 * @see ProfessionConfigLoader
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionLoadConfig {

    /** 职业命名空间（与其它 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 加载开关配置文件 ID（虽放于 professions 内容模块，仍遵循统一 rpgcraftcore 数据命名空间约定） */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/profession_load.json");

    private static final Gson GSON = new Gson();

    /**
     * 显式启用映射（仅含 JSON 中显式列出的职业）。volatile 保证 reload 写入与读取线程可见性。
     * 包私有：供 {@link ProfessionLoadConfig#applyConfig} 与同包单元测试写入。
     */
    static volatile Map<Identifier, Boolean> explicitMap = Collections.emptyMap();

    /**
     * 查询某职业是否被显式启用（未在 JSON 列出者视为启用 —— opt-out 语义）。
     *
     * @param id 职业标识符
     * @return true 表示显式启用或缺省启用；false 表示显式关闭
     */
    public static boolean isExplicitlyEnabled(Identifier id) {
        return explicitMap.getOrDefault(id, Boolean.TRUE);
    }

    /**
     * 当前显式配置映射的不可变快照（供调试/命令展示）。
     */
    public static Map<Identifier, Boolean> snapshot() {
        return Collections.unmodifiableMap(explicitMap);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/profession_load"),
                new SimplePreparableReloadListener<JsonObject>() {
                    @Override
                    protected @NonNull JsonObject prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return readConfig(rm);
                    }

                    @Override
                    protected void apply(@NonNull JsonObject json, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        applyConfig(json);
                    }
                });
    }

    private static JsonObject readConfig(ResourceManager rm) {
        var resource = rm.getResource(CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("读取职业加载开关配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    private static void applyConfig(JsonObject json) {
        if (json == null) json = new JsonObject();
        Map<Identifier, Boolean> parsed = new HashMap<>();
        if (json.has("professions") && json.get("professions").isJsonObject()) {
            JsonObject entries = json.getAsJsonObject("professions");
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                String key = entry.getKey();
                // 容忍以 "//" 开头的文档注释键
                if (key.startsWith("//")) continue;
                Identifier id = Identifier.tryParse(key);
                if (id == null) {
                    ProfessionMod.LOGGER.warn("职业加载开关配置：无法解析的职业 ID \"{}\"，已跳过", key);
                    continue;
                }
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    ProfessionMod.LOGGER.warn("职业加载开关配置：{} 的值不是布尔，已跳过", id);
                    continue;
                }
                boolean enabled = entry.getValue().getAsBoolean();
                // 平民是主职业树根/默认主职业，恒为启用 —— 关闭会破坏「玩家始终有主职业」不变量
                if (!enabled && id.equals(com.rpgcraft.core.profession.api.ProfessionIds.COMMONER_ID)) {
                    ProfessionMod.LOGGER.warn("职业加载开关配置：{} 是树根/默认主职业，不允许关闭，已强制启用", id);
                    enabled = true;
                }
                parsed.put(id, enabled);
            }
        }

        explicitMap = parsed;

        ProfessionMod.LOGGER.info("职业加载开关配置已加载：显式条目 {} 项，其中关闭 {} 项",
                parsed.size(), parsed.values().stream().filter(b -> !b).count());

        // 配置变化后必须重算级联可用性并重推树
        ProfessionAvailability.invalidateCache();
        ProfessionManager.pushProfessionStateToAllOnline();
    }
}
