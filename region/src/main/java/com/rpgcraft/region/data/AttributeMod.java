package com.rpgcraft.region.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.api.Operation;
import net.minecraft.resources.Identifier;

/**
 * 区域对单个属性的修饰符定义
 * <p>
 * 描述「在区域内时，对该属性施加什么修饰」。例如火山区域：
 * <ul>
 *   <li>火抗 +20 → {@code (FIRE_RESISTANCE_ID, ADDITION, 20)}</li>
 *   <li>水抗 -10 → {@code (WATER_RESISTANCE_ID, ADDITION, -10)}</li>
 * </ul>
 * <p>
 * 这是一个<b>定义</b>（来自 JSON），与运行时的 {@link com.rpgcraft.core.attribute.api.IAttributeModifier}
 * 不同：定义只存 attr/op/value，运行时会绑定 sourceId 后包装成修饰符注入到实体上。
 * <p>
 * {@link Operation} 决定修饰符如何参与管线计算：
 * <ul>
 *   <li>{@link Operation#ADDITION}：直接加到属性基础值（区域最常用，如 +20 火抗）</li>
 *   <li>{@link Operation#MULTIPLY_BASE}：基于加算后的值百分比乘算</li>
 *   <li>{@link Operation#MULTIPLY_TOTAL}：对总值百分比乘算</li>
 * </ul>
 *
 * @param attr 属性标识符（如 {@code rpgcraftcore:fire_resistance}）
 * @param op   操作类型
 * @param value 修饰数值（ADDITION 为绝对值，乘算为百分比）
 */
public record AttributeMod(
        Identifier attr,
        Operation op,
        int value
) {

    /** DFU Codec */
    public static final Codec<AttributeMod> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("attr").forGetter(AttributeMod::attr),
                    enumCodec(Operation.class)
                            .optionalFieldOf("op", Operation.ADDITION)
                            .forGetter(AttributeMod::op),
                    Codec.INT.fieldOf("value").forGetter(AttributeMod::value)
            ).apply(instance, AttributeMod::new)
    );

    /**
     * 枚举 Codec 工具：按 name() 序列化 / 反序列化，解析失败返回错误
     * <p>
     * DFU 9.x 无 {@code stringComapableEnum}，此处手写等价实现。
     */
    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> enumClass) {
        return Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(Enum.valueOf(enumClass, name));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "未知 " + enumClass.getSimpleName() + " 值: " + name);
                    }
                },
                e -> DataResult.success(e.name())
        );
    }
}
