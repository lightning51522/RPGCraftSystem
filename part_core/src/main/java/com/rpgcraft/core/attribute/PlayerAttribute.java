package com.rpgcraft.core.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家自定义属性的基础数据类
 * 包含当前值和最大值，并提供了序列化/反序列化所需的 Codec
 */
public class PlayerAttribute {
    // 最大值 (如果是 Integer.MAX_VALUE 代表无上限)
    private final int maxValue;
    // 当前值
    private int currentValue;

    public PlayerAttribute(int value) {
        this.maxValue = value;
        this.currentValue = value;
    }

    public PlayerAttribute(int currentValue, int maxValue) {
        this.maxValue = maxValue;
        this.currentValue = currentValue;
    }

    public PlayerAttribute() { this(100, 100); }

    public int getMaxValue() { return maxValue; }
    public int getValue() { return currentValue; }

    // 设置属性值，使用 Math.clamp 确保数值不会低于0或超过最大值
    public void setValue(int newVal) { currentValue = Math.clamp(newVal, 0, maxValue); }

    /**
     * 声明 MapCodec 用于 AttachmentType 的序列化。
     * NeoForge 会利用这个 Codec 将数据保存到存档文件中，并在需要时读取。
     * 字段名("current", "max")将作为存档中的 NBT/JSON 键名。
     */
    public static final MapCodec<PlayerAttribute> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("current").forGetter(PlayerAttribute::getValue),
                    Codec.INT.fieldOf("max").forGetter(PlayerAttribute::getMaxValue)
            ).apply(instance, PlayerAttribute::new)
    );
}
