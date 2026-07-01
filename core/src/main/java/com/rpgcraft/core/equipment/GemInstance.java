package com.rpgcraft.core.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 宝石实例（不可变值对象）
 * <p>
 * 表示一颗镶嵌宝石：其（宝石自身）稀有度 + 1~3 个词条标识符、可选的<b>自定义数值</b>，
 * 以及<b>宝石物品 ID</b>（用于镶嵌后还原 tooltip 图标）。
 * <p>
 * <b>数值来源</b>：词条默认按宝石稀有度查表（满足「同名词条在不同稀有度的宝石上数值不同」，
 * 且数值调整无需改代码、只改 JSON + {@code /reload}）。但若 {@link #customValues} 中存在该词条的条目，
 * 则<b>自定义数值优先</b> —— 由 {@code /rpg gemstone givegem} 命令指定，覆盖默认查表值。
 * {@link #customValues} 只记录「有自定义数值」的词条，未出现的走默认查表。
 * <p>
 * <b>宝石物品 ID</b>：记录这颗宝石对应的物品 ID（如 {@code rpgcraftgemstone:red_garnet}），供装备 tooltip
 * 在镶嵌后还原显示对应宝石的贴图图标。旧存档可能缺失此字段（{@code null}），渲染端应回退到默认宝石。
 * <p>
 * <b>两类用途</b>：
 * <ul>
 *   <li>装备的 {@code EQUIPMENT_SOCKET} 组件：记录该装备镶嵌的那颗宝石（每件装备 1 颗）</li>
 *   <li>宝石物品的 {@code GEM_INSTANCE} 组件：记录这颗宝石物品自身的稀有度与词条</li>
 * </ul>
 * 两者用同一类型，因数据结构完全一致。
 * <p>
 * 通过 {@link com.rpgcraft.core.equipment.RPGComponents} 注册的 DataComponentType 持久化与网络同步。
 *
 * @param rarity        宝石稀有度
 * @param affixIds      词条标识符列表（1~3 个，不可为空）
 * @param customValues  自定义数值覆盖表（affixId → 数值）；只记录有自定义数值的词条，
 *                      未出现的走默认查表。可为空 map（全部走默认）
 * @param gemItemId     宝石物品 ID（用于还原 tooltip 图标）；旧存档可能为 {@code null}
 */
public record GemInstance(EquipmentRarity rarity, List<Identifier> affixIds,
                          Map<Identifier, Integer> customValues, @Nullable Identifier gemItemId) {

    /** 词条数量下限。 */
    public static final int MIN_AFFIXES = 1;
    /** 词条数量上限。 */
    public static final int MAX_AFFIXES = 3;

    /** 空哨兵：用于缺省/兜底（GRAY + 单个占位 affixId）。 */
    public static final GemInstance EMPTY =
            new GemInstance(EquipmentRarity.GRAY, List.of(Identifier.parse("rpgcraftcore:empty")), Collections.emptyMap(), null);

    /**
     * 兼容旧调用的二元构造器：不带自定义数值与物品 ID（全部走默认查表，图标回退默认）。
     */
    public GemInstance(EquipmentRarity rarity, List<Identifier> affixIds) {
        this(rarity, affixIds, Collections.emptyMap(), null);
    }

    /**
     * 兼容旧调用的三元构造器：带自定义数值但不带物品 ID（图标回退默认）。
     */
    public GemInstance(EquipmentRarity rarity, List<Identifier> affixIds, Map<Identifier, Integer> customValues) {
        this(rarity, affixIds, customValues, null);
    }

    /**
     * 紧凑构造器：防御性拷贝 + 词条数量校验（1~3 个）。
     *
     * @param rarity        宝石稀有度
     * @param affixIds      词条标识符列表
     * @param customValues  自定义数值覆盖表（可为 null，按空 map 处理）
     * @param gemItemId     宝石物品 ID（可为 null）
     * @throws IllegalArgumentException 词条数量不在 [1,3] 区间
     */
    public GemInstance {
        if (affixIds == null || affixIds.size() < MIN_AFFIXES || affixIds.size() > MAX_AFFIXES) {
            throw new IllegalArgumentException(
                    "宝石实例的词条数量必须在 " + MIN_AFFIXES + "~" + MAX_AFFIXES + " 之间，实际: "
                            + (affixIds == null ? "null" : affixIds.size()));
        }
        if (rarity == null) {
            throw new IllegalArgumentException("宝石稀有度不能为 null");
        }
        // 防御性不可变拷贝
        affixIds = List.copyOf(affixIds);
        customValues = customValues == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(customValues));
    }

    /**
     * 查询某词条的自定义数值。
     *
     * @param affixId 词条 ID
     * @return 自定义数值；若该词条未指定自定义数值（走默认查表），返回 {@link java.util.Optional#empty()}
     */
    public java.util.OptionalInt customValueOf(Identifier affixId) {
        if (customValues != null && customValues.containsKey(affixId)) {
            return java.util.OptionalInt.of(customValues.get(affixId));
        }
        return java.util.OptionalInt.empty();
    }

    /**
     * 校验给定的词条数量是否合法（供命令层在构造前预检）。
     *
     * @param count 词条数量
     * @return true 合法
     */
    public static boolean isValidAffixCount(int count) {
        return count >= MIN_AFFIXES && count <= MAX_AFFIXES;
    }

    // ==================================================================
    // 序列化（供 DataComponentType 持久化 + 网络同步用）
    // ==================================================================

    /**
     * 持久化编解码：
     * <pre>{@code
     * { rarity: "blue", affixes: ["rpgcraftcore:strength"], values: { "rpgcraftcore:strength": 5 }, gem_item: "rpgcraftgemstone:red_garnet" }
     * }</pre>
     * {@code values} / {@code gem_item} 均为可选字段（向后兼容旧存档；缺省分别视作空 map / null）。
     * 属性词条 ID = 属性 ID。{@code values} 只记录有自定义数值的词条。
     */
    public static final Codec<GemInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    EquipmentRarity.CODEC.fieldOf("rarity").forGetter(GemInstance::rarity),
                    Identifier.CODEC.listOf(MIN_AFFIXES, MAX_AFFIXES)
                            .fieldOf("affixes").forGetter(GemInstance::affixIds),
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                            .optionalFieldOf("values", Collections.emptyMap())
                            .forGetter(GemInstance::customValues),
                    Identifier.CODEC.optionalFieldOf("gem_item")
                            .forGetter(gem -> java.util.Optional.ofNullable(gem.gemItemId))
            ).apply(instance, (rarity, affixes, values, gemItemId) ->
                    new GemInstance(rarity, affixes, values, gemItemId.orElse(null))));

    /** Identifier 列表的网络流编解码（buffer 类型为 ByteBuf，RegistryFriendlyByteBuf 是其子类故兼容）。 */
    private static final StreamCodec<ByteBuf, List<Identifier>> AFFIX_LIST_STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list());

    /** 自定义数值 map 的网络流编解码。 */
    private static final StreamCodec<ByteBuf, Map<Identifier, Integer>> CUSTOM_VALUES_STREAM_CODEC =
            ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.INT);

    /**
     * 网络流编解码：稀有度 ordinal + 词条列表 + 自定义数值 map + 可选宝石物品 ID。
     * <p>
     * 自定义数值 map 总是传输（空也写长度 0）；宝石物品 ID 用 boolean 标志位标记是否存在。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, GemInstance> STREAM_CODEC =
            StreamCodec.of(
                    (buf, gem) -> {
                        buf.writeVarInt(gem.rarity.ordinal());
                        AFFIX_LIST_STREAM_CODEC.encode(buf, gem.affixIds);
                        Map<Identifier, Integer> cv = gem.customValues == null ? Collections.emptyMap() : gem.customValues;
                        CUSTOM_VALUES_STREAM_CODEC.encode(buf, cv);
                        buf.writeBoolean(gem.gemItemId != null);
                        if (gem.gemItemId != null) {
                            Identifier.STREAM_CODEC.encode(buf, gem.gemItemId);
                        }
                    },
                    buf -> {
                        EquipmentRarity rarity = EquipmentRarity.values()[buf.readVarInt()];
                        List<Identifier> affixes = new ArrayList<>(AFFIX_LIST_STREAM_CODEC.decode(buf));
                        Map<Identifier, Integer> customValues = CUSTOM_VALUES_STREAM_CODEC.decode(buf);
                        Identifier gemItemId = buf.readBoolean() ? Identifier.STREAM_CODEC.decode(buf) : null;
                        return new GemInstance(rarity, affixes, customValues, gemItemId);
                    });
}
