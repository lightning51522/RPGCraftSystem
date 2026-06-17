package com.rpgcraft.profession;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionProvider;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * 职业定义 JSON 加载器 —— 框架与具体职业解耦的核心
 * <p>
 * 从 datapack 目录 {@code data/<ns>/rpg/professions/*.json} 扫描所有职业定义文件，
 * 每个文件代表一个职业，文件名（去 {@code .json}）即职业 ID 的 path，命名空间固定
 * {@code rpgcraftcore}。同时读取全局配置 {@code data/rpgcraftcore/rpg/profession_config.json}。
 * <p>
 * 加载完成后重建 {@link ProfessionRegistry}（先 clear 再灌入），并向在线玩家推送最新职业状态
 * （实现 {@code /reload} 即时生效）。
 * <p>
 * 兜底与占位规则：
 * <ul>
 *   <li><b>commoner 兜底</b>：若 datapack 未提供 {@code commoner.json}，代码注入一个空 commoner
 *       （primary，prerequisite=null，无加成），保证 {@code ProfessionData} 的
 *       "commoner 必解锁"不变量不致存档损坏。</li>
 *   <li><b>apprentice 占位副职业</b>：若 datapack 未提供任何 {@code type=secondary} 职业，
 *       代码注入一个真实注册的占位副职业 {@code rpgcraftcore:apprentice}（secondary，无加成，
 *       可被设为副职业/投入经验）。一旦 datapack 出现任意真实副职业 JSON，占位即移除。</li>
 * </ul>
 * 校验规则（违规项打 WARN 并跳过，不崩溃）：
 * <ul>
 *   <li>primary 职业的 prerequisite 链最终必须追溯到 commoner（循环检测）</li>
 *   <li>secondary 职业的 prerequisite 只能为 null 或指向其他 secondary（跨类型引用拒绝）</li>
 *   <li>跨类型 prerequisite（primary→secondary 或 secondary→primary）拒绝</li>
 * </ul>
 *
 * @see JsonProfession
 * @see ProfessionManager
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionDefinitionLoader {

    /** 职业定义文件所在目录（相对 datapack 根） */
    private static final String PROFESSIONS_DIR = "rpg/professions";
    /** 职业命名空间（与其它 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 全局配置文件 ID */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/profession_config.json");

    private static final Gson GSON = new Gson();

    /** 全局配置：是否允许从进阶职业切回基础职业 */
    private static volatile boolean allowDowngradeSwitch = false;
    /** 全局配置：职业默认等级上限（职业 JSON 未指定 max_level 时使用） */
    private static volatile int defaultMaxLevel = 20;

    public static boolean isAllowDowngradeSwitch() {
        return allowDowngradeSwitch;
    }

    public static int getDefaultMaxLevel() {
        return defaultMaxLevel;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 注入服务器实例到 ProfessionManager，供 /reload 后遍历在线玩家同步职业状态
        ProfessionManager.setCurrentServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ProfessionManager.setCurrentServer(null);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        // 用一个总的 prepare 阶段聚合「扫描职业目录 + 读取全局配置」，apply 阶段统一重建注册表
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/professions"),
                new SimplePreparableReloadListener<LoadedData>() {
                    @Override
                    protected @NonNull LoadedData prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        LoadedData data = new LoadedData();
                        data.professionFiles = scanProfessionFiles(rm);
                        data.config = readGlobalConfig(rm);
                        return data;
                    }

                    @Override
                    protected void apply(@NonNull LoadedData loaded, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        applyLoaded(loaded);
                    }
                });
    }

    // ------------------------------------------------------------------
    // prepare 阶段：扫描 + 解析
    // ------------------------------------------------------------------

    /** prepare 阶段聚合产物 */
    private static final class LoadedData {
        /** 文件名 path → 原始 JSON（按解析顺序，datapack 叠加时取栈顶第一个可解析对象） */
        Map<String, JsonObject> professionFiles;
        /** 全局配置原始 JSON */
        JsonObject config;
    }

    /** 扫描 {@code data/<ns>/rpg/professions/*.json}，返回 path → JSON */
    private static Map<String, JsonObject> scanProfessionFiles(ResourceManager rm) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        // listResourceStacks: 返回 ResourceLocation(id) → List<Resource>（多 pack 叠加）
        // 仅取命名空间为 rpgcraftcore 的文件，避免误收其他模组同名目录
        Map<Identifier, List<Resource>> stacks = rm.listResourceStacks(PROFESSIONS_DIR,
                id -> id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            Identifier fileId = entry.getKey();
            // path 形如 "rpg/professions/warrior.json"，去掉目录前缀和后缀得到职业 path
            String path = fileId.getPath();
            String nameWithExt = path.substring(path.lastIndexOf('/') + 1);
            String professionPath = nameWithExt.substring(0, nameWithExt.length() - ".json".length());
            // 多 pack 叠加时，栈顶（列表最后）优先级最高，取第一个成功解析的
            List<Resource> resources = entry.getValue();
            for (int i = resources.size() - 1; i >= 0; i--) {
                try (var reader = resources.get(i).openAsReader()) {
                    // 用 JsonParser.parseReader 解析为 JsonElement 再转 JsonObject，
                    // 比 GSON.fromJson(reader, JsonObject.class) 更稳健（后者在某些 Gson 配置下
                    // 对 JsonElement 子类目标类型会抛出消息为类名的异常）
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonObject()) {
                        result.put(professionPath, parsed.getAsJsonObject());
                        break;
                    }
                } catch (Exception e) {
                    ProfessionMod.LOGGER.warn("解析职业定义失败: {} - {}: {}", fileId,
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
        return result;
    }

    private static JsonObject readGlobalConfig(ResourceManager rm) {
        var resource = rm.getResource(CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("读取职业全局配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    // ------------------------------------------------------------------
    // apply 阶段：校验 + 兜底 + 占位 + 重建注册表 + 同步在线玩家
    // ------------------------------------------------------------------

    private static void applyLoaded(LoadedData loaded) {
        // 1. 解析全局配置
        applyGlobalConfig(loaded.config);

        // 2. 解析所有职业文件为 JsonProfession（先不校验跨职业引用）
        Map<Identifier, JsonProfession> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : loaded.professionFiles.entrySet()) {
            String path = entry.getKey();
            Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
            try {
                JsonProfession prof = parseProfession(id, entry.getValue());
                if (prof != null) parsed.put(id, prof);
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("解析职业 {} 失败，已跳过: {}", id, e.getMessage());
            }
        }

        // 3. 校验 prerequisite 跨类型 / 链追溯 / 循环
        Set<Identifier> valid = validateAndFilter(parsed);

        // 4. commoner 兜底：若没有合法 commoner，注入空 commoner
        if (!hasValidCommoner(parsed, valid)) {
            Identifier commonerId = ProfessionManager.COMMONER_ID;
            JsonProfession fallback = new JsonProfession(
                    commonerId, "平民", "无特殊加成的普通职业",
                    IProfession.ProfessionType.PRIMARY, null, defaultMaxLevel,
                    Map.of(), Map.of(), null);
            parsed.put(commonerId, fallback);
            valid.add(commonerId);
            ProfessionMod.LOGGER.warn("未找到合法的 commoner.json，已注入兜底平民职业");
        }

        // 5. apprentice 占位副职业：当且仅当没有任何合法 secondary 职业时注入
        boolean hasSecondary = valid.stream().anyMatch(id -> parsed.get(id).getType() == IProfession.ProfessionType.SECONDARY);
        if (!hasSecondary) {
            Identifier apprenticeId = ProfessionManager.APPRENTICE_ID;
            JsonProfession placeholder = new JsonProfession(
                    apprenticeId, "学徒（占位）", "暂无副职业时的占位，可通过 datapack 添加副职业替换",
                    IProfession.ProfessionType.SECONDARY, null, defaultMaxLevel,
                    Map.of(), Map.of(), null);
            parsed.put(apprenticeId, placeholder);
            valid.add(apprenticeId);
            ProfessionMod.LOGGER.info("未发现任何副职业定义，已注入占位副职业 apprentice");
        }

        // 6. 重建注册表
        ProfessionRegistry registry = ProfessionManager.getRegistry();
        registry.clear();
        for (Identifier id : valid) {
            registry.register(parsed.get(id));
        }
        // 6.1 SPI 注册的第三方职业（不在 datapack 体系内，每次重载都重新追加）
        // 注意：第三方职业可能引用 datapack 职业作为 prerequisite，故放在 datapack 之后
        for (IProfessionProvider provider : ServiceLoader.load(IProfessionProvider.class)) {
            provider.registerProfessions(registry);
        }

        ProfessionMod.LOGGER.info("已加载 {} 个职业定义（{} 个主职业，{} 个副职业）",
                registry.getAllProfessions().size(),
                registry.getAllProfessions().stream().filter(p -> p.getType() == IProfession.ProfessionType.PRIMARY).count(),
                registry.getAllProfessions().stream().filter(p -> p.getType() == IProfession.ProfessionType.SECONDARY).count());

        // 7. 推送在线玩家（/reload 即时生效）
        ProfessionManager.pushProfessionStateToAllOnline();
    }

    private static void applyGlobalConfig(JsonObject json) {
        allowDowngradeSwitch = parseBoolean(json, "allow_downgrade_switch", false);
        defaultMaxLevel = parseInt(json, "default_max_level", 20);
        if (defaultMaxLevel < 1) {
            ProfessionMod.LOGGER.warn("default_max_level 不能小于 1，已重置为 20");
            defaultMaxLevel = 20;
        }
    }

    /**
     * 校验所有已解析职业的 prerequisite 引用合法性。
     * <ul>
     *   <li>primary 的 prerequisite 链最终须追溯到 commoner（检测循环、断链、跨类型）</li>
     *   <li>secondary 的 prerequisite 须为 null 或指向其他 secondary</li>
     * </ul>
     * 返回通过校验的职业 ID 集合（违规者打 WARN 并排除）。
     */
    private static Set<Identifier> validateAndFilter(Map<Identifier, JsonProfession> parsed) {
        Set<Identifier> valid = new LinkedHashSet<>();
        for (Map.Entry<Identifier, JsonProfession> entry : parsed.entrySet()) {
            Identifier id = entry.getKey();
            JsonProfession prof = entry.getValue();
            Identifier prereq = prof.getPrerequisite();
            // 根职业（prerequisite=null）：primary 根只允许 commoner；secondary 根任意
            if (prereq == null) {
                if (prof.getType() == IProfession.ProfessionType.PRIMARY && !id.equals(ProfessionManager.COMMONER_ID)) {
                    ProfessionMod.LOGGER.warn("主职业 {} 的 prerequisite 为空但不是 commoner，已排除（主职业链根必须为 commoner）", id);
                    continue;
                }
                valid.add(id);
                continue;
            }
            // 非根：prerequisite 必须已解析
            JsonProfession prereqProf = parsed.get(prereq);
            if (prereqProf == null) {
                ProfessionMod.LOGGER.warn("职业 {} 的 prerequisite {} 不存在，已排除", id, prereq);
                continue;
            }
            // 跨类型引用拒绝
            if (prereqProf.getType() != prof.getType()) {
                ProfessionMod.LOGGER.warn("职业 {} 的 prerequisite {} 类型不匹配（{} → {}），已排除",
                        id, prereq, prof.getType(), prereqProf.getType());
                continue;
            }
            // 链追溯 + 循环检测
            if (!chainTerminatesAtRoot(parsed, id, prof.getType())) {
                ProfessionMod.LOGGER.warn("职业 {} 的 prerequisite 链存在循环或未正确终止于根，已排除", id);
                continue;
            }
            // 主职业额外校验：链最终须追溯到 commoner
            if (prof.getType() == IProfession.ProfessionType.PRIMARY
                    && !chainReaches(parsed, id, ProfessionManager.COMMONER_ID)) {
                ProfessionMod.LOGGER.warn("主职业 {} 的 prerequisite 链未追溯到 commoner，已排除", id);
                continue;
            }
            valid.add(id);
        }
        return valid;
    }

    /** 沿 prerequisite 链走，检测是否最终到达 null 根（无环） */
    private static boolean chainTerminatesAtRoot(Map<Identifier, JsonProfession> parsed,
                                                 Identifier start, IProfession.ProfessionType expectedType) {
        Set<Identifier> visited = new HashSet<>();
        Identifier cur = start;
        while (cur != null) {
            if (!visited.add(cur)) return false; // 环
            JsonProfession p = parsed.get(cur);
            if (p == null) return false; // 断链
            if (p.getType() != expectedType) return false; // 类型不一致
            cur = p.getPrerequisite();
        }
        return true; // 到达 null 根
    }

    /** 主职业专用：链是否最终到达指定根（commoner） */
    private static boolean chainReaches(Map<Identifier, JsonProfession> parsed,
                                        Identifier start, Identifier root) {
        Set<Identifier> visited = new HashSet<>();
        Identifier cur = start;
        while (cur != null) {
            if (!visited.add(cur)) return false;
            if (cur.equals(root)) return true;
            JsonProfession p = parsed.get(cur);
            if (p == null) return false;
            cur = p.getPrerequisite();
        }
        return false;
    }

    private static boolean hasValidCommoner(Map<Identifier, JsonProfession> parsed, Set<Identifier> valid) {
        Identifier commonerId = ProfessionManager.COMMONER_ID;
        if (!valid.contains(commonerId)) return false;
        JsonProfession commoner = parsed.get(commonerId);
        return commoner != null
                && commoner.getType() == IProfession.ProfessionType.PRIMARY
                && commoner.getPrerequisite() == null;
    }

    // ------------------------------------------------------------------
    // 单个职业 JSON 解析
    // ------------------------------------------------------------------

    private static JsonProfession parseProfession(Identifier id, JsonObject json) {
        String name = parseString(json, "name", id.getPath());
        String description = parseString(json, "description", "");
        IProfession.ProfessionType type = parseType(json);
        Identifier prerequisite = parsePrerequisite(json);
        int maxLevel = parseInt(json, "max_level", defaultMaxLevel);
        if (maxLevel < 1) {
            ProfessionMod.LOGGER.warn("职业 {} 的 max_level < 1，使用默认 {}", id, defaultMaxLevel);
            maxLevel = defaultMaxLevel;
        }
        Map<Identifier, Integer> bonuses = parseAttrMap(json, "bonuses");
        Map<Identifier, Integer> perLevel = parseAttrMap(json, "per_level");
        int[] expTable = parseExpTable(json, maxLevel);
        return new JsonProfession(id, name, description, type, prerequisite, maxLevel,
                bonuses, perLevel, expTable);
    }

    private static IProfession.ProfessionType parseType(JsonObject json) {
        if (!json.has("type") || !json.get("type").isJsonPrimitive()) {
            ProfessionMod.LOGGER.warn("职业缺少 type 字段（应为 \"primary\" 或 \"secondary\"），默认按 primary 处理");
            return IProfession.ProfessionType.PRIMARY;
        }
        String raw = json.getAsJsonPrimitive("type").getAsString();
        return switch (raw.toLowerCase()) {
            case "primary" -> IProfession.ProfessionType.PRIMARY;
            case "secondary" -> IProfession.ProfessionType.SECONDARY;
            default -> {
                ProfessionMod.LOGGER.warn("未知 type 值: {}，默认按 primary 处理", raw);
                yield IProfession.ProfessionType.PRIMARY;
            }
        };
    }

    private static Identifier parsePrerequisite(JsonObject json) {
        if (!json.has("prerequisite") || json.get("prerequisite").isJsonNull()) return null;
        JsonElement el = json.get("prerequisite");
        String raw;
        if (el.isJsonPrimitive() && ((JsonPrimitive) el).isString()) {
            raw = el.getAsString();
        } else {
            ProfessionMod.LOGGER.warn("prerequisite 字段非字符串，忽略");
            return null;
        }
        // 仅给 path 时补默认命名空间；给完整 ns:path 原样解析
        if (raw.contains(":")) return Identifier.parse(raw);
        return Identifier.fromNamespaceAndPath(NAMESPACE, raw);
    }

    /** 解析属性映射：{ "rpgcraftcore:strength": 5, ... } */
    private static Map<Identifier, Integer> parseAttrMap(JsonObject json, String field) {
        Map<Identifier, Integer> map = new LinkedHashMap<>();
        if (!json.has(field) || !json.get(field).isJsonObject()) return map;
        JsonObject obj = json.getAsJsonObject(field);
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
                Identifier attrId = entry.getKey().contains(":")
                        ? Identifier.parse(entry.getKey())
                        : Identifier.fromNamespaceAndPath(NAMESPACE, entry.getKey());
                int value = entry.getValue().getAsInt();
                map.put(attrId, value);
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("解析 {} 条目失败: {} - {}", field, entry.getKey(), e.getMessage());
            }
        }
        return map;
    }

    /** 解析专属经验表：长度应为 maxLevel-1；缺失则返回 null（用全局公式） */
    private static int[] parseExpTable(JsonObject json, int maxLevel) {
        if (!json.has("exp_table") || !json.get("exp_table").isJsonArray()) return null;
        JsonArray arr = json.getAsJsonArray("exp_table");
        int[] table = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            try {
                table[i] = arr.get(i).getAsInt();
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("exp_table[{}] 解析失败，置为 0", i);
                table[i] = 0;
            }
        }
        if (table.length != Math.max(0, maxLevel - 1)) {
            ProfessionMod.LOGGER.warn("exp_table 长度 {} 与 max_level-1={} 不符，仍按提供值使用",
                    table.length, Math.max(0, maxLevel - 1));
        }
        return table;
    }

    // ------------------------------------------------------------------
    // JSON 基础读取工具
    // ------------------------------------------------------------------

    private static boolean parseBoolean(JsonObject json, String key, boolean def) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            try {
                return json.getAsJsonPrimitive(key).getAsBoolean();
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private static int parseInt(JsonObject json, String key, int def) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            try {
                return json.getAsJsonPrimitive(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private static String parseString(JsonObject json, String key, String def) {
        if (json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isString()) {
            // 注意：必须用 json.get(key).getAsString()，不能用 json.getAsString()
            // 后者是把整个 JsonObject 当作 primitive 取值，会抛 UnsupportedOperationException("JsonObject")
            return json.get(key).getAsString();
        }
        return def;
    }
}
