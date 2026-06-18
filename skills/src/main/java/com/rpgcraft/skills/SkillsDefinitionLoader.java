package com.rpgcraft.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.skill.api.ISkillProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
import java.util.ServiceLoader;

/**
 * 技能定义 JSON 加载器
 * <p>
 * 从 datapack 目录 {@code data/rpgcraftcore/rpg/skills/*.json} 扫描所有技能定义文件，
 * 每个文件代表一个技能，文件名（去 {@code .json}）即技能 ID 的 path，命名空间固定
 * {@code rpgcraftcore}。
 * <p>
 * 加载完成后重建 {@link SkillsRegistry}（先 clear 再灌入），追加 SPI 注册的第三方技能
 * （{@link ISkillProvider}，ServiceLoader），并向在线玩家推送最新技能状态
 * （实现 {@code /reload} 即时生效）。
 * <p>
 * 兜底与校验（违规项打 WARN 并跳过，不崩溃）：
 * <ul>
 *   <li>resource_cost / cooldown_ticks / damage_amount / range 负值重置为 0</li>
 *   <li>未知 attack_type 字符串按 PHYSICAL 处理</li>
 *   <li>animation_id 缺失时回退为 {@code rpgcraftskills:<skillPath>}（自动生成）</li>
 * </ul>
 *
 * @see JsonSkill
 * @see SkillsManager
 */
@EventBusSubscriber(modid = SkillsMod.MODID)
public class SkillsDefinitionLoader {

    /** 技能定义文件所在目录（相对 datapack 根） */
    private static final String SKILLS_DIR = "rpg/skills";
    /** 技能命名空间（与其他 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";

    /** 当前服务器实例（供 /reload 后遍历在线玩家推送技能状态） */
    private static volatile MinecraftServer currentServer;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        currentServer = event.getServer();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        currentServer = null;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/skills"),
                new SimplePreparableReloadListener<Map<String, JsonObject>>() {
                    @Override
                    protected @NonNull Map<String, JsonObject> prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return scanSkillFiles(rm);
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
     * 扫描 {@code data/rpgcraftcore/rpg/skills/*.json}，返回 path → JSON
     * <p>
     * 多 pack 叠加时栈顶（列表最后）优先级最高，取第一个成功解析的 JsonObject。
     */
    private static Map<String, JsonObject> scanSkillFiles(ResourceManager rm) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        Map<Identifier, List<Resource>> stacks = rm.listResourceStacks(SKILLS_DIR,
                id -> id.getNamespace().equals(NAMESPACE) && id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, List<Resource>> entry : stacks.entrySet()) {
            Identifier fileId = entry.getKey();
            // path 形如 "rpg/skills/heavy_strike.json"，去掉目录前缀和后缀得到技能 path
            String path = fileId.getPath();
            String nameWithExt = path.substring(path.lastIndexOf('/') + 1);
            String skillPath = nameWithExt.substring(0, nameWithExt.length() - ".json".length());
            List<Resource> resources = entry.getValue();
            for (int i = resources.size() - 1; i >= 0; i--) {
                try (var reader = resources.get(i).openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed != null && parsed.isJsonObject()) {
                        result.put(skillPath, parsed.getAsJsonObject());
                        break;
                    }
                } catch (Exception e) {
                    SkillsMod.LOGGER.warn("解析技能定义失败: {} - {}: {}", fileId,
                            e.getClass().getSimpleName(), e.getMessage());
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // apply 阶段：解析 + 校验 + 重建注册表 + ServiceLoader + 同步在线玩家
    // ------------------------------------------------------------------

    private static void applyLoaded(Map<String, JsonObject> files) {
        SkillsRegistry registry = SkillsManager.getRegistry();
        registry.clear();

        // 1. 解析所有技能文件
        for (Map.Entry<String, JsonObject> entry : files.entrySet()) {
            String path = entry.getKey();
            Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
            try {
                JsonSkill skill = parseSkill(id, entry.getValue());
                if (skill != null) registry.register(skill);
            } catch (Exception e) {
                SkillsMod.LOGGER.warn("解析技能 {} 失败，已跳过: {}", id, e.getMessage());
            }
        }

        // 2. SPI 注册的第三方技能（每次重载都重新追加）
        for (ISkillProvider provider : ServiceLoader.load(ISkillProvider.class)) {
            provider.registerSkills(registry);
        }

        SkillsMod.LOGGER.info("已加载 {} 个技能定义", registry.getAll().size());

        // 3. 推送在线玩家（/reload 即时生效）
        pushSkillsStateToAllOnline();
    }

    /** /reload 后遍历在线玩家推送技能状态 */
    private static void pushSkillsStateToAllOnline() {
        MinecraftServer server = currentServer;
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                SkillsManager.syncToClient(player);
            } catch (Exception e) {
                SkillsMod.LOGGER.warn("推送技能状态给 {} 失败: {}",
                        player.getName().getString(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // 单个技能 JSON 解析
    // ------------------------------------------------------------------

    private static JsonSkill parseSkill(Identifier id, JsonObject json) {
        String name = parseString(json, "name", id.getPath());
        String description = parseString(json, "description", "");
        int resourceCost = parseNonNegativeInt(json, "resource_cost", 0);
        int cooldownTicks = parseNonNegativeInt(json, "cooldown_ticks", 0);
        int damageAmount = parseNonNegativeInt(json, "damage_amount", 0);
        AttackType attackType = parseAttackType(json);
        Identifier animationId = parseAnimationId(json, id);
        double range = parseNonNegativeDouble(json, "range", 4.0);
        return new JsonSkill(id, name, description, resourceCost, cooldownTicks, damageAmount,
                attackType, animationId, range);
    }

    private static AttackType parseAttackType(JsonObject json) {
        String raw = parseString(json, "attack_type", "PHYSICAL");
        try {
            return AttackType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            SkillsMod.LOGGER.warn("未知 attack_type 值: {}，默认按 PHYSICAL 处理", raw);
            return AttackType.PHYSICAL;
        }
    }

    /**
     * 解析 animation_id：缺失时回退为 {@code rpgcraftskills:<skillPath>}
     */
    private static Identifier parseAnimationId(JsonObject json, Identifier skillId) {
        String raw = parseString(json, "animation_id", null);
        if (raw == null || raw.isEmpty()) {
            // 自动生成：技能 path 作为动画文件名，命名空间 rpgcraftskills
            return Identifier.fromNamespaceAndPath("rpgcraftskills", skillId.getPath());
        }
        if (raw.contains(":")) return Identifier.parse(raw);
        return Identifier.fromNamespaceAndPath("rpgcraftskills", raw);
    }

    // ------------------------------------------------------------------
    // JSON 基础读取工具
    // ------------------------------------------------------------------

    private static String parseString(JsonObject json, String key, String def) {
        if (def == null) {
            // 允许返回 null 的变体（用于 animation_id 等可选字段）
            if (!json.has(key) || json.get(key).isJsonNull()) return null;
            if (json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isString()) {
                return json.get(key).getAsString();
            }
            return null;
        }
        if (json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsJsonPrimitive().isString()) {
            return json.get(key).getAsString();
        }
        return def;
    }

    private static int parseNonNegativeInt(JsonObject json, String key, int def) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            try {
                int v = json.getAsJsonPrimitive(key).getAsInt();
                if (v < 0) {
                    SkillsMod.LOGGER.warn("{} 不能为负（值={}），已重置为 0", key, v);
                    return 0;
                }
                return v;
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private static double parseNonNegativeDouble(JsonObject json, String key, double def) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            try {
                double v = json.getAsJsonPrimitive(key).getAsDouble();
                if (v < 0) {
                    SkillsMod.LOGGER.warn("{} 不能为负（值={}），已重置为 0", key, v);
                    return 0;
                }
                return v;
            } catch (Exception ignored) {
            }
        }
        return def;
    }
}
