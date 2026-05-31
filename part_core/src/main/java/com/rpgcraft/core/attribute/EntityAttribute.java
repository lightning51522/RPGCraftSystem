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
 * <p>
 * <b>重要副作用提示：</b>{@link #setMaxValue(int)} 方法在修改上限时会自动将 currentValue
 * 钳制到新上限以内。在同时修改 maxValue 和 currentValue 的场景中（如装备加成应用），
 * 必须注意此副作用的影响顺序。
 */
public class EntityAttribute implements IAttribute {

    /** 属性显示名称（如"生命"、"力量"等），用于 HUD 和命令显示 */
    private String name;

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
     * 通过 {@link #setValue(int)} 修改时，会自动被夹紧（clamp）到 {@code [0, maxValue]} 范围内，
     * 保证数值不会出现负数或超过上限的非法状态。
     */
    private int currentValue;

    /**
     * 构造一个指定名称、当前值和最大值的属性
     * <p>
     * 适用于属性注册和存档数据恢复。
     *
     * @param name         属性显示名称
     * @param currentValue 属性当前值
     * @param maxValue     属性最大值
     */
    public EntityAttribute(String name, int currentValue, int maxValue) {
        this.name = name;
        this.maxValue = maxValue;
        this.currentValue = currentValue;
    }

    @Override
    public String getName() { return this.name; }

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
     * <b>副作用：</b>若当前值超过新的最大值，会自动将 {@link #currentValue} 钳制到新上限。
     * 这意味着调用此方法后 {@link #getValue()} 的返回值可能发生变化。
     * <p>
     * 此副作用在装备系统的资源属性（{@code equipmentAffectsMax=true}）处理中被依赖：
     * 脱下降低上限的装备时，通过此副作用自动将当前值钳制到新上限以内。
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
     * 数值会通过 {@link Math#clamp(long, int, int)} 被限制在 {@code [0, maxValue]} 范围内，
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
     * 将当前值直接设为最大值
     * <p>
     * 不通过 {@link #setValue} 的 clamp 逻辑（因为 maxValue 本身一定在合法范围内），
     * 直接赋值以简化路径。用于资源型属性（生命、技力、法力）重生时的恢复。
     */
    @Override
    public void fillMax() {
        currentValue = maxValue;
    }

    /**
     * MapCodec 序列化器，用于 AttachmentType 的存档读写
     * <p>
     * NeoForge 在保存/加载游戏存档时，会使用此 Codec 将 EntityAttribute 序列化为 NBT/JSON 格式。
     * 字段映射关系：
     * <ul>
     *   <li>{@code "name"} → {@link #getName()} 属性名称</li>
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
                    Codec.STRING.fieldOf("name").forGetter(EntityAttribute::getName),
                    Codec.INT.fieldOf("current").forGetter(EntityAttribute::getValue),
                    Codec.INT.fieldOf("max").forGetter(EntityAttribute::getMaxValue)
            ).apply(instance, EntityAttribute::new)
    );
}
