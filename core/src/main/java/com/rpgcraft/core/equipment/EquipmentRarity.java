package com.rpgcraft.core.equipment;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 装备稀有度
 * <p>
 * 十个等级（从低到高，直接以颜色名命名）：灰 → 白 → 绿 → 蓝 → 紫 → 橙 → 粉 → 金 → 红 → 彩虹。
 * 每个等级对应一个 RGB 颜色（用于 tooltip 物品名与 {@code [等级名]} 标签着色）；
 * {@link #RAINBOW} 为动态彩虹色，颜色由客户端逐 tick 按 HSV 色相轮转（见
 * {@code EquipmentRarityColors}）。
 * <p>
 * 标签行直接显示枚举名（如 {@code [BLUE]}）。
 * <p>
 * <b>等级（tier）与加成缩放</b>：{@link #getTier()} 为序号（GRAY=0 … RAINBOW=9），
 * 每升一级该件装备的全部属性加成 +10%（{@link #getBonusMultiplier()} = {@code 1 + 0.1×tier}）。
 * <p>
 * 稀有度现为<b>动态、按物品实例</b>的属性：通过 {@code DataComponentType}（见
 * {@code RPGComponents.EQUIPMENT_RARITY}）存储在 {@code ItemStack} 上，在制作/进入世界时
 * 按概率随机生成。
 */
public enum EquipmentRarity {

    GRAY(0x9D9D9D),
    WHITE(0xFFFFFF),
    GREEN(0x4FFF4F),
    BLUE(0x3A7BFF),
    PURPLE(0xB026FF),
    ORANGE(0xFF8C00),
    PINK(0xFF69B4),
    GOLD(0xFFD700),
    RED(0xFF2A2A),
    /** 彩虹：颜色由客户端逐 tick 轮转，非静态 RGB */
    RAINBOW(0xFFFFFF, true);

    private final int color;
    private final boolean rainbow;

    EquipmentRarity(int color) {
        this(color, false);
    }

    EquipmentRarity(int color, boolean rainbow) {
        this.color = color;
        this.rainbow = rainbow;
    }

    /** 该等级的 RGB 颜色（{@link #RAINBOW} 返回基准白，实际颜色由客户端动画覆盖）。 */
    public int getColor() {
        return color;
    }

    /** 是否为彩虹等级（颜色需由客户端逐 tick 动态计算）。 */
    public boolean isRainbow() {
        return rainbow;
    }

    /** 等级序号：GRAY=0 … RAINBOW=9。用于稀有度加成缩放。 */
    public int getTier() {
        return ordinal();
    }

    /** 属性加成倍率：{@code 1 + 0.1 × tier}（GRAY=1.0×、RAINBOW=1.9×）。 */
    public double getBonusMultiplier() {
        return 1.0 + 0.1 * getTier();
    }

    /**
     * 根据名称查找稀有度（不区分大小写，按枚举名匹配）
     *
     * @param name 稀有度名称（如 "green"、"rainbow"）
     * @return 对应的稀有度，未匹配返回 {@link #GRAY}
     */
    public static EquipmentRarity fromName(String name) {
        if (name == null) return GRAY;
        for (EquipmentRarity r : values()) {
            if (r.name().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return GRAY;
    }

    // ==================================================================
    // 序列化（供 DataComponentType 持久化 + 网络同步用）
    // ==================================================================

    /** 按枚举名（小写）字符串编解码；未知名称容错为 {@link #GRAY}。 */
    public static final Codec<EquipmentRarity> CODEC = Codec.STRING
            .xmap(EquipmentRarity::fromName, r -> r.name().toLowerCase())
            .stable();

    /** 网络流编解码：按枚举 ordinal 序号写/读 var int（与项目其它包一致用 ByteBuf 实例方法）。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentRarity> STREAM_CODEC =
            StreamCodec.of(
                    (buf, r) -> buf.writeVarInt(r.ordinal()),
                    buf -> EquipmentRarity.values()[buf.readVarInt()]);
}

