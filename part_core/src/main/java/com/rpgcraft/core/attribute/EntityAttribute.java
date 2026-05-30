package com.rpgcraft.core.attribute;

import java.lang.Math;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.api.IAttribute;

/**
 * {@link IAttribute} 的默认实现
 * <p>
 * 每个 RPG 属性（如生命、力量、暴击率等）都由一个 EntityAttribute 实例表示。
 * 包含两个核心字段：
 * <ul>
 *   <li>{@link #currentValue} —— 属性当前值，可通过 {@link #setValue(int)} 修改</li>
 *   <li>{@link #maxValue} —— 属性上限值，可通过 {@link #setMaxValue(int)} 修改</li>
 * </ul>
 * <p>
 * 该类同时提供了 {@link #CODEC} 用于 NeoForge AttachmentType 的存档序列化。
 * 网络传输的序列化则在 {@link com.rpgcraft.core.network.SyncPlayerAttributePacket} 中单独处理。
 */
public class EntityAttribute implements IAttribute {

    /**
     * 属性上限值
     * <p>
     * 当值为 {@link Integer#MAX_VALUE} 时，表示该属性无上限（如力量、魔力等成长型属性）。
     * 有上限的属性（如生命 100、暴击率 100）在此字段中存储具体最大值。
     * 可通过 {@link #setMaxValue(int)} 动态修改。
     */
    private int maxValue;

    /**
     * 属性当前值
     * <p>
     * 通过 {@link #setValue(int)} 修改时，会自动被夹紧（clamp）到 [0, maxValue] 范围内，
     * 保证数值不会出现负数或超过上限的非法状态。
     */
    private int currentValue;

    /**
     * 构造一个当前值等于最大值的属性（满值构造）
     * <p>
     * 适用于初始化场景，属性创建时即为满状态。
     *
     * @param value 属性的初始值和最大值
     */
    public EntityAttribute(int value) {
        this.maxValue = value;
        this.currentValue = value;
    }

    /**
     * 构造一个指定当前值和最大值的属性
     * <p>
     * 适用于从存档数据恢复或网络包反序列化时重建属性实例。
     *
     * @param currentValue 属性当前值
     * @param maxValue     属性最大值
     */
    public EntityAttribute(int currentValue, int maxValue) {
        this.maxValue = maxValue;
        this.currentValue = currentValue;
    }

    /**
     * 获取属性最大值
     *
     * @return 属性上限值
     */
    @Override
    public int getMaxValue() {
        return maxValue;
    }

    /**
     * 设置属性最大值
     * <p>
     * 修改后若当前值超过新的最大值，会自动被夹紧。
     *
     * @param max 新的最大值
     */
    @Override
    public void setMaxValue(int max) {
        this.maxValue = max;
        if (currentValue > maxValue) {
            currentValue = maxValue;
        }
    }

    /**
     * 获取属性当前值
     *
     * @return 属性当前值
     */
    @Override
    public int getValue() {
        return currentValue;
    }

    /**
     * 设置属性当前值
     * <p>
     * 数值会通过 {@link Math#clamp(long, int, int)} 被限制在 [0, maxValue] 范围内，
     * 无需调用者手动校验边界。
     *
     * @param newVal 期望设置的新值
     */
    @Override
    public void setValue(int newVal) {
        currentValue = Math.clamp(newVal, 0, maxValue);
    }

    /**
     * 是否有上限
     *
     * @return {@code true} 如果 maxValue &lt; Integer.MAX_VALUE
     */
    @Override
    public boolean hasMaxValue() {
        return maxValue < Integer.MAX_VALUE;
    }

    /**
     * MapCodec 序列化器，用于 AttachmentType 的存档读写
     * <p>
     * NeoForge 在保存/加载游戏存档时，会使用此 Codec 将 EntityAttribute 序列化为 NBT/JSON 格式。
     * 字段映射关系：
     * <ul>
     *   <li>{@code "current"} → {@link #getValue()} 当前值</li>
     *   <li>{@code "max"} → {@link #getMaxValue()} 最大值</li>
     * </ul>
     * <p>
     * <b>注意：</b>此 Codec 仅用于存档序列化，网络传输使用的是
     * {@link com.rpgcraft.core.network.SyncPlayerAttributePacket#STREAM_CODEC}（StreamCodec），
     * 两者不可混用。
     */
    public static final MapCodec<EntityAttribute> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("current").forGetter(EntityAttribute::getValue),
                    Codec.INT.fieldOf("max").forGetter(EntityAttribute::getMaxValue)
            ).apply(instance, EntityAttribute::new)
    );
}
