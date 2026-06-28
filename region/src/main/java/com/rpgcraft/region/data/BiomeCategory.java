package com.rpgcraft.region.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.Element;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 生物群系类别（效果模板 + 生物群系列表）
 * <p>
 * 把若干<b>原版生物群系</b>归到同一类别，并赋予统一的主题效果（属性修饰符 + 元素伤害加成）。
 * 由 datapack 定义（{@code data/rpgcraftcore/rpg/biome_regions/*.json}），文件名（去 {@code .json}）
 * 即类别 ID 的 path，命名空间固定 {@code rpgcraftcore}。
 * <p>
 * <h3>与 {@link Region} / {@link EnvironmentType} 的关系</h3>
 * <ul>
 *   <li>{@link Region}：几何（多边形）+ 效果，查询靠多边形 inside</li>
 *   <li>{@link EnvironmentType}：纯效果模板，运行时套用到玩家构建的几何上</li>
 *   <li>{@code BiomeCategory}（本类）：效果模板 + 生物群系列表，查询时按「所在生物群系」归类，
 *       派生出无固定几何边界的 {@link BiomeRegion}</li>
 * </ul>
 * 三者共享同一套效果词汇（{@link AttributeMod} + {@code element_damage_bonus} 千分制）。
 * <p>
 * <h3>按「类别」而非「单个生物群系」触发</h3>
 * 同一类别内的相邻原版生物群系（如沙漠 → 沙滩归不同类）会平滑切换；而同一类别内
 * （如 plains → sunflower_plains 同属「草原」）不会触发虚假进出，避免区块边界噪声。
 *
 * @param id                 类别 ID（命名空间 {@code rpgcraftcore}）
 * @param displayName        显示名（如「沙漠」）
 * @param attributeMods      属性修饰符列表
 * @param elementDamageBonus 元素伤害倍率（千分制，1000=基准不变）
 * @param biomes             归入本类别的原版生物群系 key 列表
 */
public record BiomeCategory(
        Identifier id,
        String displayName,
        List<AttributeMod> attributeMods,
        Map<Element, Integer> elementDamageBonus,
        List<ResourceKey<Biome>> biomes
) {

    /**
     * DFU Codec（datapack JSON 解析）
     * <p>
     * JSON 示例（{@code data/rpgcraftcore/rpg/biome_regions/desert.json}）：
     * <pre>{@code
     * {
     *   "name": "沙漠",
     *   "biomes": ["minecraft:desert"],
     *   "attribute_mods": [
     *     { "attr": "rpgcraftcore:fire_resistance", "op": "ADDITION", "value": 15 }
     *   ],
     *   "element_damage_bonus": { "FIRE": 1200, "WATER": 800 }
     * }
     * }</pre>
     * {@code _id} 字段由加载器注入（文件名去 {@code .json}）。
     */
    public static final Codec<BiomeCategory> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("_id").forGetter(BiomeCategory::id),
                    Codec.STRING.fieldOf("name").forGetter(BiomeCategory::displayName),
                    AttributeMod.CODEC.listOf()
                            .optionalFieldOf("attribute_mods", List.of())
                            .forGetter(BiomeCategory::attributeMods),
                    Codec.unboundedMap(
                                    enumStringCodec(Element.class),
                                    Codec.INT
                            )
                            .optionalFieldOf("element_damage_bonus", Map.of())
                            .forGetter(BiomeCategory::elementDamageBonus),
                    ResourceKey.codec(Registries.BIOME).listOf()
                            .fieldOf("biomes")
                            .forGetter(BiomeCategory::biomes)
            ).apply(instance, BiomeCategory::new)
    );

    /**
     * 获取本类别需要施加的<b>全部</b>属性修饰符
     * <p>
     * 将显式 {@link #attributeMods} 与 {@link #elementDamageBonus}（转换为 {@code <element>_damage_bonus}
     * 的 ADDITION 修饰符）合并。换算逻辑与 {@link Region#allMods()} 完全一致。
     *
     * @return 合并后的修饰符列表（新建可变列表，调用方可安全修改）
     */
    public List<AttributeMod> allMods() {
        List<AttributeMod> result = new ArrayList<>(attributeMods);
        for (Map.Entry<Element, Integer> entry : elementDamageBonus.entrySet()) {
            Element e = entry.getKey();
            int bonus = entry.getValue();
            if (bonus == Element.DAMAGE_BONUS_BASE) continue; // 基准值，无需修饰
            Identifier damageBonusId = e.damageBonusId();
            if (damageBonusId == null) continue; // NONE 等无对应属性
            int addition = bonus - Element.DAMAGE_BONUS_BASE;
            result.add(new AttributeMod(damageBonusId, com.rpgcraft.core.attribute.api.Operation.ADDITION, addition));
        }
        return result;
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
