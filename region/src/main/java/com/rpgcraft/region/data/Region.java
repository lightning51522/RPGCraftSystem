package com.rpgcraft.region.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.Element;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

/**
 * 区域定义（不可变值对象）
 * <p>
 * 一个区域由以下要素定义：
 * <ul>
 *   <li>{@link #id}：唯一标识符（命名空间 {@code rpgcraftcore}，path = 文件名去 {@code .json}）</li>
 *   <li>{@link #name}：显示名（如「火山」）</li>
 *   <li>{@link #dimension}：限定维度（如主世界 {@link Level#OVERWORLD}）</li>
 *   <li>{@link #polygon}：XZ 多边形 + Y 范围构成的柱体几何</li>
 *   <li>{@link #attributeMods}：属性修饰符列表（区域内实体的属性增益/减益）</li>
 *   <li>{@link #elementDamageBonus}：元素伤害加成倍率（千分制，作用于输出端攻击者）</li>
 * </ul>
 * <p>
 * <h3>作用语义</h3>
 * 区域内的实体（玩家和非玩家 {@link net.minecraft.world.entity.LivingEntity}）会获得：
 * <ul>
 *   <li><b>属性修饰符</b>：通过 RPG 属性管线叠加（如火抗 +20、水抗 -10）</li>
 *   <li><b>元素伤害加成</b>：攻击者造成的对应元素伤害乘以 {@code bonus/1000}
 *       （如火山区域 FIRE=1300 表示火伤 +30%）</li>
 * </ul>
 * 未被任何区域包含的位置视为「一般区域」，无任何属性影响。
 * <p>
 * <h3>元素伤害加成的两种表达</h3>
 * {@code element_damage_bonus} 配置项会被转换为对 {@code <element>_damage_bonus} 属性的
 * ADDITION 修饰符（如 FIRE=1300 → {@code fire_damage_bonus} +300，因为默认值 1000）。
 * 这样元素增伤统一走属性管线，无需单独的伤害事件监听。
 *
 * @see RegionPolygon
 * @see AttributeMod
 * @see RegionView
 */
public final class Region implements RegionView {

    /**
     * DFU Codec（用于 datapack JSON 解析）
     * <p>
     * JSON 示例见 {@code data/rpgcraftcore/rpg/regions/volcano.json}。
     * 缺失字段兜底：dimension 默认 overworld，attribute_mods / element_damage_bonus 默认空。
     */
    public static final Codec<Region> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("_id").forGetter(Region::getId),
                    Codec.STRING.fieldOf("name").forGetter(Region::getName),
                    ResourceKey.codec(Registries.DIMENSION)
                            .optionalFieldOf("dimension", Level.OVERWORLD)
                            .forGetter(Region::getDimension),
                    RegionPolygon.CODEC.fieldOf("bounds").forGetter(Region::getPolygon),
                    AttributeMod.CODEC.listOf()
                            .optionalFieldOf("attribute_mods", List.of())
                            .forGetter(Region::getAttributeMods),
                    // element_damage_bonus: { "FIRE": 1300, "WATER": 700 } → 转为 ADDITION 修饰符
                    Codec.unboundedMap(
                                    enumStringCodec(Element.class),
                                    Codec.INT
                            )
                            .optionalFieldOf("element_damage_bonus", Map.of())
                            .forGetter(Region::getElementDamageBonus)
            ).apply(instance, Region::new)
    );

    private final Identifier id;
    private final String name;
    private final ResourceKey<Level> dimension;
    private final RegionPolygon polygon;
    private final List<AttributeMod> attributeMods;
    private final Map<Element, Integer> elementDamageBonus;

    /**
     * @param id                 区域 ID
     * @param name               显示名
     * @param dimension          维度
     * @param polygon            几何
     * @param attributeMods      属性修饰符
     * @param elementDamageBonus 元素伤害倍率（千分制，1000=基准不变）
     */
    public Region(Identifier id, String name, ResourceKey<Level> dimension,
                  RegionPolygon polygon, List<AttributeMod> attributeMods,
                  Map<Element, Integer> elementDamageBonus) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.polygon = polygon;
        this.attributeMods = List.copyOf(attributeMods);
        this.elementDamageBonus = Map.copyOf(elementDamageBonus);
    }

    /** 区域 ID */
    public Identifier getId() { return id; }

    /** 显示名 */
    public String getName() { return name; }

    /** 维度 */
    public ResourceKey<Level> getDimension() { return dimension; }

    /** 几何 */
    public RegionPolygon getPolygon() { return polygon; }

    /** 属性修饰符列表（不可变） */
    public List<AttributeMod> getAttributeMods() { return attributeMods; }

    /** 元素伤害倍率（不可变，千分制） */
    public Map<Element, Integer> getElementDamageBonus() { return elementDamageBonus; }

    /**
     * 获取该区域需要施加的<b>全部</b>属性修饰符
     * <p>
     * 将 {@link #attributeMods}（显式声明的）与 {@link #elementDamageBonus}（转换为
     * {@code <element>_damage_bonus} 的 ADDITION 修饰符）合并。元素倍率 1000（基准）时
     * 跳过（避免添加值为 0 的无用修饰符）。
     *
     * @return 合并后的完整修饰符列表（每次调用新建，调用方可安全修改）
     */
    public List<AttributeMod> allMods() {
        List<AttributeMod> result = new java.util.ArrayList<>(attributeMods);
        for (Map.Entry<Element, Integer> entry : elementDamageBonus.entrySet()) {
            Element e = entry.getKey();
            int bonus = entry.getValue();
            if (bonus == Element.DAMAGE_BONUS_BASE) continue; // 基准值，无需修饰
            Identifier damageBonusId = e.damageBonusId();
            if (damageBonusId == null) continue; // NONE 等无对应属性
            // ADDITION 修饰值 = 配置值 - 默认值（DAMAGE_BONUS_BASE）
            // 例：FIRE=1300 → fire_damage_bonus +300（在 1000 基础上 +300）
            int addition = bonus - Element.DAMAGE_BONUS_BASE;
            result.add(new AttributeMod(damageBonusId, com.rpgcraft.core.attribute.api.Operation.ADDITION, addition));
        }
        return result;
    }

    /**
     * 枚举字符串 Codec 工具：按 name() 序列化 / 反序列化
     * <p>
     * DFU 9.x 无 {@code stringComapableEnum}，此处手写等价实现。
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
