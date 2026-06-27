package com.rpgcraft.region.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.Element;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 环境类型模板（纯效果，无几何）
 * <p>
 * 定义一类环境效果的「模板」：属性修饰符（如火抗 +20）和元素伤害倍率（如火伤 +30%），
 * 但<b>不含几何范围</b>。由 datapack 定义（{@code data/rpgcraftcore/rpg/environments/*.json}），
 * 供运行时创建的区域（{@code setregion}/{@code addregion}）套用。
 * <p>
 * <h3>与 {@link Region} 的关系</h3>
 * {@link Region} 是「几何 + 效果」的完整值对象；{@code EnvironmentType} 是「纯效果」模板。
 * 运行时区域的创建流程：玩家用 {@code setregion init} + {@code addregion} 构建几何，
 * 用 {@code setregion done} 定稿时调用 {@link #createRegion} 把模板效果套用到几何上，
 * 生成完整 {@link Region}。
 * <p>
 * <h3>命令中的「环境 ID」</h3>
 * {@code setregion <ID> <NAME> <SIZE> init} 中的 {@code ID} 即环境类型 ID（如 {@code volcano}），
 * 必须是已注册的环境类型。{@code ID} 可用完整形式 {@code rpgcraftcore:volcano} 或 path {@code volcano}。
 *
 * @param id                 环境类型 ID（命名空间 {@code rpgcraftcore}）
 * @param displayName        显示名（如「火山」）
 * @param attributeMods      属性修饰符列表（火抗 +20 等）
 * @param elementDamageBonus 元素伤害倍率（千分制，1000=基准不变）
 */
public record EnvironmentType(
        Identifier id,
        String displayName,
        List<AttributeMod> attributeMods,
        Map<Element, Integer> elementDamageBonus
) {

    /**
     * DFU Codec（datapack JSON 解析）
     * <p>
     * JSON 示例（{@code data/rpgcraftcore/rpg/environments/volcano.json}）：
     * <pre>{@code
     * {
     *   "name": "火山",
     *   "attribute_mods": [
     *     { "attr": "rpgcraftcore:fire_resistance", "op": "ADDITION", "value": 20 }
     *   ],
     *   "element_damage_bonus": { "FIRE": 1300 }
     * }
     * }</pre>
     * {@code _id} 字段由加载器注入（文件名去 {@code .json}）。
     */
    public static final Codec<EnvironmentType> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("_id").forGetter(EnvironmentType::id),
                    Codec.STRING.fieldOf("name").forGetter(EnvironmentType::displayName),
                    AttributeMod.CODEC.listOf()
                            .optionalFieldOf("attribute_mods", List.of())
                            .forGetter(EnvironmentType::attributeMods),
                    Codec.unboundedMap(
                                    enumStringCodec(Element.class),
                                    Codec.INT
                            )
                            .optionalFieldOf("element_damage_bonus", Map.of())
                            .forGetter(EnvironmentType::elementDamageBonus)
            ).apply(instance, EnvironmentType::new)
    );

    /**
     * 将模板效果套用到给定几何，生成完整 {@link Region}
     * <p>
     * 运行时区域定稿（{@code setregion done}）时调用：用本模板的效果 + 玩家构建的几何
     * 生成一个可注册的完整区域。区域 ID 由调用方指定（通常基于区域 NAME 生成）。
     *
     * @param regionId  区域 ID（运行时区域的 ID，命名空间 {@code rpgcraftregion}）
     * @param name      区域显示名（草稿 NAME）
     * @param dimension 区域维度
     * @param polygon   区域几何（由草稿凹包构建）
     * @return 套用本模板效果的完整区域
     */
    public Region createRegion(Identifier regionId, String name,
                                ResourceKey<Level> dimension, RegionPolygon polygon) {
        return new Region(regionId, name, dimension, polygon, attributeMods, elementDamageBonus);
    }

    /**
     * 枚举字符串 Codec 工具：按 name() 序列化 / 反序列化
     * <p>
     * DFU 9.x 无 {@code stringComapableEnum}，此处手写等价实现（与 {@link Region} 一致）。
     */
    private static <E extends Enum<E>> Codec<E> enumStringCodec(Class<E> enumClass) {
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
