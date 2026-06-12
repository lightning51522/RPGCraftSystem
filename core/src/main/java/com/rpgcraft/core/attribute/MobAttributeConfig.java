package com.rpgcraft.core.attribute;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.RPGCraftCore;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 生物属性 JSON 配置加载器
 * <p>
 * 从 {@code data/rpgcraftcore/rpg/mob_attributes.json} 读取各生物类型的自定义属性配置，
 * 在服务端资源加载/重载时解析并存入内存 Map。
 * <p>
 * 通过 {@link AddServerReloadListenersEvent} 注册为自定义资源重载监听器，
 * 支持 {@code /reload} 命令热更新配置。
 * <p>
 * 本类与具体属性解耦：按 {@link Identifier} 通用存取，不硬编码任何游戏属性名
 * （生命除外，LIFE 由 core 提供）。具体属性的语义由消费方模块（如 combat）解释。
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class MobAttributeConfig {

    /**
     * 随机刷新权重分布
     *
     * @param levelWeights  等级 → 权重（不可变）
     * @param ratingWeights 评级枚举名 → 权重（不可变）
     */
    public record SpawnDistribution(
            Map<Integer, Double> levelWeights,
            Map<String, Double> ratingWeights
    ) {}

    /**
     * 单个生物类型的属性配置
     *
     * @param attackType        攻击伤害类型（physical/magic 等）
     * @param level             怪物等级（用于经验计算，默认 1）
     * @param baseExp           击杀基础经验（默认 100）
     * @param intrinsicBases    固有属性基础值映射（attrId → 基础值）
     * @param spawnDistribution 随机刷新权重分布（nullable，无 spawn 配置时为 null）
     */
    public record MobAttributes(
            AttackType attackType,
            int level, int baseExp,
            Map<Identifier, Integer> intrinsicBases,
            SpawnDistribution spawnDistribution
    ) {
        /** 获取生命值基础值（LIFE 由 core 提供，必然存在） */
        public int life() {
            return getIntrinsicBase(AttributeManager.LIFE_ID);
        }

        /** 按属性 ID 获取固有基础值（未配置返回 0） */
        public int getIntrinsicBase(Identifier id) {
            return intrinsicBases.getOrDefault(id, 0);
        }
    }

    /** 配置文件路径 */
    private static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/mob_attributes.json");

    /** 顶级元字段（非属性），旧格式解析时跳过 */
    private static final Set<String> META_FIELDS = Set.of(
            "attack_type", "level", "base_exp", "spawn");

    /** 生物类型ID → 属性配置 的映射，不可变快照 */
    private static volatile Map<Identifier, MobAttributes> configMap = Collections.emptyMap();

    private static final Gson GSON = new Gson();

    /**
     * 注册资源重载监听器（Game 事件总线）
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                try {
                    var resource = resourceManager.getResource(CONFIG_ID);
                    if (resource.isPresent()) {
                        try (var reader = resource.get().openAsReader()) {
                            return GSON.fromJson(reader, JsonObject.class);
                        }
                    }
                } catch (Exception e) {
                    RPGCraftCore.LOGGER.error("加载生物属性配置失败", e);
                }
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                Map<Identifier, MobAttributes> newMap = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    try {
                        // 跳过非对象值（如 _global_spawn 注释段等）
                        if (!entry.getValue().isJsonObject()) continue;
                        // 跳过以下划线开头的元数据键
                        if (entry.getKey().startsWith("_")) continue;

                        Identifier entityId = Identifier.parse(entry.getKey());
                        JsonObject attrs = entry.getValue().getAsJsonObject();

                        // 解析攻击类型，缺失时默认为 PHYSICAL（向后兼容）
                        AttackType attackType = AttackType.PHYSICAL;
                        if (attrs.has("attack_type")) {
                            try {
                                attackType = AttackType.valueOf(
                                        attrs.getAsJsonPrimitive("attack_type").getAsString().toUpperCase()
                                );
                            } catch (IllegalArgumentException e) {
                                RPGCraftCore.LOGGER.warn("未知的攻击类型: {}，使用默认 PHYSICAL",
                                        attrs.getAsJsonPrimitive("attack_type").getAsString());
                            }
                        }

                        // 解析等级和基础经验，缺失时使用默认值
                        int level = attrs.has("level") ? attrs.getAsJsonPrimitive("level").getAsInt() : 1;
                        int baseExp = attrs.has("base_exp") ? attrs.getAsJsonPrimitive("base_exp").getAsInt() : 100;

                        // 解析 spawn 分布配置（可选）
                        SpawnDistribution spawnDist = null;
                        if (attrs.has("spawn") && attrs.get("spawn").isJsonObject()) {
                            spawnDist = parseSpawnDistribution(attrs.getAsJsonObject("spawn"));
                        }

                        newMap.put(entityId, new MobAttributes(
                                attackType,
                                Math.max(1, level),
                                Math.max(0, baseExp),
                                parseIntrinsicBases(attrs),
                                spawnDist
                        ));
                    } catch (Exception e) {
                        RPGCraftCore.LOGGER.warn("解析生物属性配置失败: {} - {}", entry.getKey(), e.getMessage());
                    }
                }
                configMap = Collections.unmodifiableMap(newMap);
                RPGCraftCore.LOGGER.info("已加载 {} 种生物的自定义属性配置", newMap.size());
            }
        });
    }

    /**
     * 解析固有属性基础值
     * <p>
     * 支持两种 JSON 格式（向后兼容）：
     * <ol>
     *   <li><b>新格式</b>：{@code "intrinsic_bases": { "rpgcraftcore:life": 80, ... }}
     *       —— 按完整 Identifier 存储，对任意属性都成立。</li>
     *   <li><b>旧格式</b>：顶级数值字段 {@code "life": 80, "strength": 15, ...}
     *       —— 字段名即属性 path（namespace 固定 {@code rpgcraftcore}），通用规则，
     *       不硬编码任何具体属性名。</li>
     * </ol>
     * 若 {@code intrinsic_bases} 存在则优先使用，否则从顶级数值字段构建。
     *
     * @param attrs 生物属性 JSON 对象
     * @return 属性基础值映射（不可变）
     */
    private static Map<Identifier, Integer> parseIntrinsicBases(JsonObject attrs) {
        // 新格式：intrinsic_bases 对象（按完整 Identifier 存储，通用）
        if (attrs.has("intrinsic_bases") && attrs.get("intrinsic_bases").isJsonObject()) {
            Map<Identifier, Integer> bases = new LinkedHashMap<>();
            JsonObject basesObj = attrs.getAsJsonObject("intrinsic_bases");
            for (Map.Entry<String, JsonElement> entry : basesObj.entrySet()) {
                try {
                    Identifier attrId = Identifier.parse(entry.getKey());
                    int value = entry.getValue().getAsInt();
                    bases.put(attrId, value);
                } catch (Exception e) {
                    RPGCraftCore.LOGGER.warn("解析 intrinsic_bases 条目失败: {} - {}", entry.getKey(), e.getMessage());
                }
            }
            return Collections.unmodifiableMap(bases);
        }

        // 旧格式：顶级数值字段名即属性 path（namespace 固定 rpgcraftcore）
        // 通用规则，不硬编码任何具体属性名；跳过元字段和非数值字段
        Map<Identifier, Integer> bases = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : attrs.entrySet()) {
            String key = entry.getKey();
            if (META_FIELDS.contains(key)) continue;
            JsonElement val = entry.getValue();
            if (!val.isJsonPrimitive() || !val.getAsJsonPrimitive().isNumber()) continue;
            try {
                bases.put(
                        Identifier.fromNamespaceAndPath("rpgcraftcore", key),
                        val.getAsInt()
                );
            } catch (Exception e) {
                RPGCraftCore.LOGGER.warn("解析属性字段失败: {} - {}", key, e.getMessage());
            }
        }
        return Collections.unmodifiableMap(bases);
    }

    /**
     * 解析 spawn 分布配置
     *
     * @param spawnObj spawn JSON 对象
     * @return SpawnDistribution，字段可为空 Map
     */
    private static SpawnDistribution parseSpawnDistribution(JsonObject spawnObj) {
        Map<Integer, Double> levelWeights = new LinkedHashMap<>();
        Map<String, Double> ratingWeights = new LinkedHashMap<>();

        // 解析 level_weights: { "1": 60, "2": 20, ... }
        if (spawnObj.has("level_weights") && spawnObj.get("level_weights").isJsonObject()) {
            JsonObject lw = spawnObj.getAsJsonObject("level_weights");
            for (Map.Entry<String, JsonElement> lwEntry : lw.entrySet()) {
                try {
                    int lvl = Integer.parseInt(lwEntry.getKey());
                    double weight = lwEntry.getValue().getAsDouble();
                    if (weight > 0) {
                        levelWeights.put(lvl, weight);
                    }
                } catch (NumberFormatException e) {
                    RPGCraftCore.LOGGER.warn("spawn.level_weights 中无效的等级键: {}", lwEntry.getKey());
                }
            }
        }

        // 解析 rating_weights: { "NORMAL": 85, "STRONG": 10, ... }
        if (spawnObj.has("rating_weights") && spawnObj.get("rating_weights").isJsonObject()) {
            JsonObject rw = spawnObj.getAsJsonObject("rating_weights");
            for (Map.Entry<String, JsonElement> rwEntry : rw.entrySet()) {
                double weight = rwEntry.getValue().getAsDouble();
                if (weight > 0) {
                    ratingWeights.put(rwEntry.getKey(), weight);
                }
            }
        }

        if (levelWeights.isEmpty() && ratingWeights.isEmpty()) {
            return null;
        }

        return new SpawnDistribution(
                Collections.unmodifiableMap(levelWeights),
                Collections.unmodifiableMap(ratingWeights)
        );
    }

    /**
     * 查询指定生物类型的属性配置
     *
     * @param entityType 生物类型的 Identifier（如 minecraft:zombie）
     * @return 属性配置，未配置则返回 empty
     */
    public static Optional<MobAttributes> getConfig(Identifier entityType) {
        return Optional.ofNullable(configMap.get(entityType));
    }

    /**
     * 查询指定生物类型的随机刷新分布配置
     *
     * @param entityType 生物类型的 Identifier
     * @return 权重分布，未配置 spawn 段则返回 null
     */
    public static SpawnDistribution getSpawnDistribution(Identifier entityType) {
        return getConfig(entityType)
                .map(MobAttributes::spawnDistribution)
                .orElse(null);
    }
}
