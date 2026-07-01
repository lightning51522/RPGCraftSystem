package com.rpgcraft.core.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 宝石实例（不可变值对象）
 * <p>
 * 表示一颗镶嵌宝石：其（宝石自身）稀有度 + 1~3 个词条标识符。词条<b>只存 affixId</b>，
 * 具体数值由宝石模块的配置按宝石稀有度查表（满足「同名词条在不同稀有度的宝石上数值不同」，
 * 且数值调整无需改代码、只改 JSON + {@code /reload}）。
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
 * @param rarity    宝石稀有度
 * @param affixIds  词条标识符列表（1~3 个，不可为空）
 */
public record GemInstance(EquipmentRarity rarity, List<Identifier> affixIds) {

    /** 词条数量下限。 */
    public static final int MIN_AFFIXES = 1;
    /** 词条数量上限。 */
    public static final int MAX_AFFIXES = 3;

    /** 空哨兵：用于缺省/兜底（GRAY + 单个占位 affixId）。 */
    public static final GemInstance EMPTY =
            new GemInstance(EquipmentRarity.GRAY, List.of(Identifier.parse("rpgcraftcore:empty")));

    /**
     * 紧凑构造器：防御性拷贝 + 词条数量校验（1~3 个）。
     *
     * @param rarity   宝石稀有度
     * @param affixIds 词条标识符列表
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

    /** 持久化编解码：{ rarity: "blue", affixes: ["rpgcraftcore:strength"] }（属性词条 ID = 属性 ID）。 */
    public static final Codec<GemInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    EquipmentRarity.CODEC.fieldOf("rarity").forGetter(GemInstance::rarity),
                    Identifier.CODEC.listOf(MIN_AFFIXES, MAX_AFFIXES)
                            .fieldOf("affixes").forGetter(GemInstance::affixIds)
            ).apply(instance, GemInstance::new));

    /** Identifier 列表的网络流编解码（buffer 类型为 ByteBuf，RegistryFriendlyByteBuf 是其子类故兼容）。 */
    private static final StreamCodec<ByteBuf, List<Identifier>> AFFIX_LIST_STREAM_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list());

    /** 网络流编解码：稀有度 ordinal + 词条列表。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, GemInstance> STREAM_CODEC =
            StreamCodec.of(
                    (buf, gem) -> {
                        buf.writeVarInt(gem.rarity.ordinal());
                        AFFIX_LIST_STREAM_CODEC.encode(buf, gem.affixIds);
                    },
                    buf -> {
                        EquipmentRarity rarity = EquipmentRarity.values()[buf.readVarInt()];
                        List<Identifier> affixes = new ArrayList<>(AFFIX_LIST_STREAM_CODEC.decode(buf));
                        return new GemInstance(rarity, affixes);
                    });
}
