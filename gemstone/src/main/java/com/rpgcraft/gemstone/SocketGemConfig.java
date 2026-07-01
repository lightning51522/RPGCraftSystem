package com.rpgcraft.gemstone;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import com.rpgcraft.core.equipment.EquipmentRarity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 镶嵌宝石词条配置加载器。
 * <p>
 * <h3>核心设计：属性词条 = 属性</h3>
 * 属性词条不再有独立的 affixId —— <b>词条 ID 直接就是 RPG 属性 ID（attributeId）</b>，
 * 消除了旧设计中 {@code affixId → attributeId} 的冗余双层映射。词条候选池由 core 的
 * {@link IAttributeRegistry#getAllEntries()} 运行时枚举，凡是
 * {@link IAttributeEntry#isAvailableAsAffix()} 为 {@code true} 的属性都是合法词条
 * （含第三方通过 {@code registerAttributeModule} 注入的属性 —— 这正是「通过 core 中转自动读取属性为
 * 词条来源」的入口，gemstone 与第三方属性模块零编译期依赖）。
 * <p>
 * 本配置 JSON 仅承载<b>数值表</b>，不再枚举词条列表：
 * <ul>
 *   <li><b>{@code _default}</b>：全局默认数值表（按宝石稀有度 gray→red）。所有未单独配置数值的属性
 *       词条都查它 —— 第三方属性<b>零配置</b>即可作词条，想精调再加专属条目。</li>
 *   <li><b>专属数值表</b>（可选，键为属性 ID）：为个别属性覆盖默认表，如
 *       {@code "rpgcraftcore:strength": { "values": { ... } }}。</li>
 * </ul>
 * <p>
 * <h3>特效词条（独立子系统）</h3>
 * 特效词条与属性词条共用 {@code GemInstance.affixIds} 列表，但走独立命名空间与独立查询路径
 * （{@link #specialAffixes}）。其 ID 不指向 RPG 属性，而是指向
 * {@link GemSpecialEffectRegistry} 注册的特效。本配置声明 affixId → effectId 的映射。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新，完全自管；客户端镜像加载
 * 由 {@link GemstoneClientEventHandler} 处理。
 *
 * @see SocketGemBonusContributor 读取本配置计算属性加成
 * @see AttributeManager#getRegistry() 属性词条候选池的来源（core 中转入口）
 * @see GemSpecialEffectRegistry  特效注册表（特效词条的 effect_id 在此查询实现）
 */
@EventBusSubscriber(modid = GemstoneMod.MODID)
public class SocketGemConfig {

    /** 工程命名空间（与其它 rpg 配置保持一致）。 */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 配置文件 ID。 */
    public static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/socket_gem_affixes.json");

    /** JSON 中全局默认数值表的键（保留键，非合法 Identifier，仅作配置占位）。 */
    public static final String DEFAULT_TABLE_KEY = "_default";

    private static final Gson GSON = new Gson();

    /** 词缀类型：属性词条（提供属性加成）或特效词条（提供战斗特殊效果）。 */
    public enum AffixType { ATTRIBUTE, SPECIAL }

    /**
     * 数值表：各宝石稀有度下的数值。属性词条与全局默认表共用此结构。
     *
     * @param values 各宝石稀有度下的数值（缺失按 0 处理）
     */
    public record AffixValues(Map<EquipmentRarity, Integer> values) {
        /** 查询某宝石稀有度下的数值（缺失返回 0）。 */
        public int getValue(EquipmentRarity rarity) {
            return values.getOrDefault(rarity, 0);
        }
    }

    /**
     * 各属性词条的专属数值表（attributeId → 数值表）。volatile 保证 reload 写入与读取线程可见性。
     * 包私有：供 {@link #applyConfig} 与同包单元测试写入。
     */
    static volatile Map<Identifier, AffixValues> attributeAffixes = Collections.emptyMap();

    /** 全局默认数值表（未单独配置数值的属性词条查它）。volatile 同上。 */
    static volatile AffixValues defaultValues = new AffixValues(Collections.emptyMap());

    /**
     * 各特效词条的映射（affixId → effectId）。volatile 保证 reload 写入与读取线程可见性。
     */
    static volatile Map<Identifier, Identifier> specialAffixes = Collections.emptyMap();

    // ----------------------------------------------------------------
    // 属性注册表访问（core 中转入口）
    // ----------------------------------------------------------------

    /**
     * 获取属性注册中心。gemstone 仅依赖 core，第三方属性通过此入口自动可见。
     * <p>
     * 属性模块未加载时返回 {@code null}（无任何属性注册），此时属性词条相关查询全部降级为「无候选」。
     */
    private static @Nullable IAttributeRegistry getRegistry() {
        return AttributeManager.getRegistry();
    }

    /**
     * 判断某 ID 是否为「可作词条的属性」。
     * <p>
     * 即该 ID 在属性注册表中存在且 {@link IAttributeEntry#isAvailableAsAffix()} 为 {@code true}。
     * 这是属性词条的唯一判定依据。
     */
    static boolean isAvailableAttribute(@Nullable Identifier attributeId) {
        if (attributeId == null) return false;
        IAttributeRegistry registry = getRegistry();
        if (registry == null) return false;
        IAttributeEntry entry = registry.getEntry(attributeId);
        return entry != null && entry.isAvailableAsAffix();
    }

    // ----------------------------------------------------------------
    // 词条类型与候选枚举
    // ----------------------------------------------------------------

    /**
     * 查询词条类型（属性 / 特效）；未定义返回 {@code null}。
     * <p>
     * 判定优先级：先查属性注册表（属性词条），再查特效映射（特效词条）。
     */
    public static @Nullable AffixType getAffixType(Identifier affixId) {
        if (isAvailableAttribute(affixId)) return AffixType.ATTRIBUTE;
        if (specialAffixes.containsKey(affixId)) return AffixType.SPECIAL;
        return null;
    }

    /** 是否为属性词条（= 可作词条的注册属性）。 */
    public static boolean isAttribute(Identifier affixId) {
        return isAvailableAttribute(affixId);
    }

    /** 是否为特效词条。 */
    public static boolean isSpecial(Identifier affixId) {
        return specialAffixes.containsKey(affixId);
    }

    /**
     * 查询属性词条在某宝石稀有度下的数值。
     * <p>
     * 先查该属性的专属数值表；缺失则回退到全局 {@code _default} 默认表；仍缺失返回 0。
     *
     * @param attributeId 属性 ID（须为可作词条的属性）
     * @param gemRarity   宝石稀有度
     * @return 数值；未配置返回 0
     */
    public static int getAttributeValue(Identifier attributeId, EquipmentRarity gemRarity) {
        AffixValues specific = attributeAffixes.get(attributeId);
        if (specific != null) {
            int v = specific.getValue(gemRarity);
            if (v != 0) return v; // 专属表显式配置优先（含显式 0 仍回退默认，避免歧义）
        }
        return defaultValues.getValue(gemRarity);
    }

    /** 查询特效词条对应的 effect_id（非特效词条返回 {@code null}）。 */
    public static @Nullable Identifier getSpecialEffectId(Identifier affixId) {
        return specialAffixes.get(affixId);
    }

    /**
     * 获取所有合法 affixId（供指令 Tab 补全）：可作词条的注册属性 ∪ 特效词条。
     * <p>
     * 属性词条由注册表运行时枚举（含第三方注入的属性），特效词条由 JSON 配置。
     */
    public static Set<Identifier> getAllAffixIds() {
        Set<Identifier> all = new HashSet<>();
        IAttributeRegistry registry = getRegistry();
        if (registry != null) {
            for (IAttributeEntry entry : registry.getAllEntries()) {
                if (entry.isAvailableAsAffix()) {
                    all.add(entry.getId());
                }
            }
        }
        all.addAll(specialAffixes.keySet());
        return Collections.unmodifiableSet(all);
    }

    // ----------------------------------------------------------------
    // 服务端 reload 监听器
    // ----------------------------------------------------------------

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/socket_gem_affixes"),
                new SimplePreparableReloadListener<JsonObject>() {
                    @Override
                    protected @NonNull JsonObject prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return readConfig(rm);
                    }

                    @Override
                    protected void apply(@NonNull JsonObject json, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        applyConfig(json, false);
                    }
                });
    }

    private static JsonObject readConfig(ResourceManager rm) {
        var resource = rm.getResource(CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                GemstoneMod.LOGGER.warn("读取镶嵌宝石词条配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    /**
     * 从已解析的 JSON 应用配置（供服务端 reload 监听器与客户端镜像加载共用）。
     * <p>
     * 解析规则：
     * <ul>
     *   <li>{@code _default} 键 → 全局默认数值表</li>
     *   <li>其它以 {@code namespace:path} 形式可解析为 Identifier 的键 → 该属性的专属数值表
     *       （属性是否真的可作词条仍由注册表 {@code isAvailableAsAffix} 决定，配置只提供数值）</li>
     *   <li>{@code type=special} 的条目 → 特效词条映射</li>
     * </ul>
     *
     * @param json   已解析的配置 JSON
     * @param client 是否客户端调用（仅用于日志区分）
     */
    public static void loadFromJson(JsonObject json, boolean client) {
        applyConfig(json, client);
    }

    private static void applyConfig(JsonObject json, boolean client) {
        if (json == null) json = new JsonObject();
        Map<Identifier, AffixValues> parsedAttr = new HashMap<>();
        Map<Identifier, Identifier> parsedSpecial = new HashMap<>();
        AffixValues parsedDefault = new AffixValues(Collections.emptyMap());

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("//")) continue; // 容忍文档注释键

            // 全局默认数值表
            if (DEFAULT_TABLE_KEY.equals(key)) {
                if (entry.getValue().isJsonObject()) {
                    parsedDefault = parseValues(entry.getValue().getAsJsonObject(), key);
                } else {
                    GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的值不是对象，已跳过", key);
                }
                continue;
            }

            Identifier affixId;
            try {
                affixId = Identifier.parse(key);
            } catch (Exception e) {
                GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：affixId {} 非法，已跳过", key);
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的值不是对象，已跳过", key);
                continue;
            }
            JsonObject defObj = entry.getValue().getAsJsonObject();

            // 特效词条：显式声明 type=special
            String type = defObj.has("type") ? defObj.get("type").getAsString() : "";
            if ("special".equals(type)) {
                Identifier effectId = parseEffectId(defObj, key);
                if (effectId != null) parsedSpecial.put(affixId, effectId);
                continue;
            }

            // 属性词条专属数值表（默认分支）：{ values: { 稀有度: 数值 } }
            // 注意：属性是否真的可作词条由注册表 isAvailableAsAffix 决定，此处只解析数值表。
            // 即便该属性当前未注册或不可作词条，配置也允许先存在（便于数据包独立维护）。
            if (defObj.has("values")) {
                AffixValues vals = parseValues(defObj, key);
                parsedAttr.put(affixId, vals);
            } else {
                GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 缺少 values 字段且非 special，已跳过", key);
            }
        }
        attributeAffixes = Collections.unmodifiableMap(parsedAttr);
        defaultValues = parsedDefault;
        specialAffixes = Collections.unmodifiableMap(parsedSpecial);
        GemstoneMod.LOGGER.info("镶嵌宝石词条配置已加载（{}）：{} 个专属属性数值表，{} 个特效词条，默认表{}",
                client ? "客户端镜像" : "服务端",
                parsedAttr.size(), parsedSpecial.size(),
                parsedDefault.values().isEmpty() ? "（空）" : "已就绪");
    }

    /** 解析 { values: { <稀有度>: <数值>, ... } } 为数值表。obj 须含 values 对象。 */
    private static AffixValues parseValues(JsonObject obj, String key) {
        Map<EquipmentRarity, Integer> values = new EnumMap<>(EquipmentRarity.class);
        if (obj.has("values") && obj.get("values").isJsonObject()) {
            for (Map.Entry<String, JsonElement> v : obj.get("values").getAsJsonObject().entrySet()) {
                if (v.getKey().startsWith("//")) continue;
                EquipmentRarity r = EquipmentRarity.fromName(v.getKey());
                if (r == EquipmentRarity.GRAY && !v.getKey().equalsIgnoreCase("gray")) {
                    // fromName 兜底 GRAY；只有真匹配 gray 才采纳，避免 typo 被静默吞为 GRAY
                    GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的 values 键 {} 无法识别为稀有度，已跳过",
                            key, v.getKey());
                    continue;
                }
                if (!v.getValue().isJsonPrimitive() || !v.getValue().getAsJsonPrimitive().isNumber()) {
                    GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的 values.{} 非数字，已跳过", key, v.getKey());
                    continue;
                }
                values.put(r, v.getValue().getAsInt());
            }
        } else if (obj.has("values")) {
            GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的 values 不是对象，已跳过", key);
        }
        return new AffixValues(values);
    }

    private static @Nullable Identifier parseEffectId(JsonObject obj, String key) {
        if (!obj.has("effect_id") || !obj.get("effect_id").isJsonPrimitive()) {
            GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 缺少 effect_id 字段，已跳过", key);
            return null;
        }
        try {
            return Identifier.parse(obj.get("effect_id").getAsString());
        } catch (Exception e) {
            GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的 effect_id 非法，已跳过", key);
            return null;
        }
    }
}
