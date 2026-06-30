package com.rpgcraft.gemstone;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.equipment.EquipmentRarity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 镶嵌宝石词条配置加载器
 * <p>
 * 加载 {@code data/rpgcraftcore/rpg/socket_gem_affixes.json}：每条词条定义一个 affixId，含
 * 类型（{@code attribute} 属性词条 / {@code special} 特效词条）及对应的属性/特效配置。
 * <p>
 * <b>属性词条</b>：{@code attribute} 指向目标 RPG 属性 ID，{@code values} 为各宝石稀有度下
 * 的数值表（满足「同名词条在不同稀有度宝石上数值不同」）。数值为占位，待设计 —— 后续直接改
 * JSON + {@code /reload} 即可调整，无需改代码。
 * <p>
 * <b>特效词条</b>：{@code effect_id} 指向 {@link GemSpecialEffectRegistry} 注册的特效 ID。
 * 本配置仅声明映射，特效实现由宝石模块（或第三方）按 ID 注册。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新。本配置<b>完全自管</b>
 * （不走 {@code IEquipmentSystem} 门面），客户端镜像加载由本模块的客户端 reload 监听器处理。
 *
 * @see SocketGemBonusContributor 读取本配置计算属性加成
 * @see GemSpecialEffectRegistry  特效注册表（特效词条的 effect_id 在此查询实现）
 */
@EventBusSubscriber(modid = GemstoneMod.MODID)
public class SocketGemConfig {

    /** 工程命名空间（与其它 rpg 配置保持一致）。 */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 配置文件 ID。 */
    public static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/socket_gem_affixes.json");

    private static final Gson GSON = new Gson();

    /** 词缀类型：属性词条（提供属性加成）或特效词条（提供战斗特殊效果）。 */
    public enum AffixType { ATTRIBUTE, SPECIAL }

    /**
     * 属性词条定义：affixId → {目标属性 ID, 各宝石稀有度下的数值}。
     *
     * @param attributeId 目标 RPG 属性 ID（如 strength）
     * @param values      各宝石稀有度下的数值（缺失按 0 处理）
     */
    public record AttributeAffixDef(Identifier attributeId, Map<EquipmentRarity, Integer> values) {
        /** 查询某宝石稀有度下的数值（缺失返回 0）。 */
        public int getValue(EquipmentRarity rarity) {
            return values.getOrDefault(rarity, 0);
        }
    }

    /**
     * 各词条定义（属性词条）。volatile 保证 reload 写入与读取线程可见性。
     * 包私有：供 {@link #applyConfig} 与同包单元测试写入。
     */
    static volatile Map<Identifier, AttributeAffixDef> attributeAffixes = Collections.emptyMap();

    /**
     * 各特效词条的映射（affixId → effectId）。volatile 保证 reload 写入与读取线程可见性。
     */
    static volatile Map<Identifier, Identifier> specialAffixes = Collections.emptyMap();

    /** 查询词条类型（属性 / 特效）；未定义返回 {@code null}。 */
    public static AffixType getAffixType(Identifier affixId) {
        if (attributeAffixes.containsKey(affixId)) return AffixType.ATTRIBUTE;
        if (specialAffixes.containsKey(affixId)) return AffixType.SPECIAL;
        return null;
    }

    /** 是否为属性词条。 */
    public static boolean isAttribute(Identifier affixId) {
        return attributeAffixes.containsKey(affixId);
    }

    /** 是否为特效词条。 */
    public static boolean isSpecial(Identifier affixId) {
        return specialAffixes.containsKey(affixId);
    }

    /** 查询属性词条定义（非属性词条返回 {@code null}）。 */
    public static AttributeAffixDef getAttributeAffix(Identifier affixId) {
        return attributeAffixes.get(affixId);
    }

    /**
     * 查询属性词条在某宝石稀有度下的数值。
     *
     * @param affixId   词条 ID（须为属性词条）
     * @param gemRarity 宝石稀有度
     * @return 数值；非属性词条或未配置返回 0
     */
    public static int getAttributeValue(Identifier affixId, EquipmentRarity gemRarity) {
        AttributeAffixDef def = attributeAffixes.get(affixId);
        return def == null ? 0 : def.getValue(gemRarity);
    }

    /** 查询特效词条对应的 effect_id（非特效词条返回 {@code null}）。 */
    public static Identifier getSpecialEffectId(Identifier affixId) {
        return specialAffixes.get(affixId);
    }

    /** 获取所有已定义的 affixId（供指令 Tab 补全）。 */
    public static java.util.Set<Identifier> getAllAffixIds() {
        java.util.Set<Identifier> all = new java.util.HashSet<>(attributeAffixes.keySet());
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
     *
     * @param json   已解析的配置 JSON
     * @param client 是否客户端调用（仅用于日志区分）
     */
    public static void loadFromJson(JsonObject json, boolean client) {
        applyConfig(json, client);
    }

    private static void applyConfig(JsonObject json, boolean client) {
        if (json == null) json = new JsonObject();
        Map<Identifier, AttributeAffixDef> parsedAttr = new HashMap<>();
        Map<Identifier, Identifier> parsedSpecial = new HashMap<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("//")) continue; // 容忍文档注释键
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
            String type = defObj.has("type") ? defObj.get("type").getAsString() : "";
            switch (type) {
                case "attribute" -> {
                    AttributeAffixDef def = parseAttributeDef(defObj, key);
                    if (def != null) parsedAttr.put(affixId, def);
                }
                case "special" -> {
                    Identifier effectId = parseEffectId(defObj, key);
                    if (effectId != null) parsedSpecial.put(affixId, effectId);
                }
                default -> GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的 type '{}' 未知（须为 attribute/special），已跳过",
                        key, type);
            }
        }
        attributeAffixes = Collections.unmodifiableMap(parsedAttr);
        specialAffixes = Collections.unmodifiableMap(parsedSpecial);
        GemstoneMod.LOGGER.info("镶嵌宝石词条配置已加载（{}）：{} 个属性词条，{} 个特效词条",
                client ? "客户端镜像" : "服务端", parsedAttr.size(), parsedSpecial.size());
    }

    private static AttributeAffixDef parseAttributeDef(JsonObject obj, String key) {
        if (!obj.has("attribute") || !obj.get("attribute").isJsonPrimitive()) {
            GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 缺少 attribute 字段，已跳过", key);
            return null;
        }
        Identifier attrId;
        try {
            attrId = Identifier.parse(obj.get("attribute").getAsString());
        } catch (Exception e) {
            GemstoneMod.LOGGER.warn("镶嵌宝石词条配置：{} 的 attribute 非法，已跳过", key);
            return null;
        }
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
        }
        return new AttributeAffixDef(attrId, values);
    }

    private static Identifier parseEffectId(JsonObject obj, String key) {
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
