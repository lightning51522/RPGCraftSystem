package com.rpgcraft.region.data;

import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 区域查询结果的统一只读视图
 * <p>
 * 区域系统现在有两条来源不同的「区域」：
 * <ul>
 *   <li><b>几何区域</b>（{@link Region}）：由 XZ 多边形 + Y 范围定义，位置命中多边形即生效</li>
 *   <li><b>生物群系区域</b>（{@link com.rpgcraft.region.data.BiomeRegion}）：由所在区块存储的
 *       原版生物群系经 {@link com.rpgcraft.region.BiomeCategoryRegistry} 归类后派生，
 *       无固定几何边界</li>
 * </ul>
 * 两者对调用方（属性注入、进出提示）的语义一致——「给实体施加哪些属性修饰符」+「叫什么名字」。
 * 本接口抽出这两个调用方实际依赖的最小方法集，使两类区域可统一返回与处理。
 * <p>
 * <h3>调用方依赖</h3>
 * <ul>
 *   <li>{@link com.rpgcraft.region.RegionManager}（玩家路径）：依赖 {@link #getId()}（diff）、
 *       {@link #getName()}（进出提示）</li>
 *   <li>{@link com.rpgcraft.region.apply.NpcAttributeListener}（非玩家路径）：依赖
 *       {@link #allMods()}、{@link #getId()}</li>
 * </ul>
 *
 * @see Region
 * @see BiomeRegion
 */
public interface RegionView {

    /** 区域唯一标识符（用于玩家状态 diff 与修饰符 sourceId） */
    Identifier getId();

    /** 显示名（用于进出区域聊天提示，空则回退到 ID path） */
    String getName();

    /**
     * 该区域需要施加的全部属性修饰符（已合并元素伤害加成转出的 ADDITION 修饰符）
     *
     * @return 修饰符列表（不可变，每次调用返回同一实例）
     */
    List<AttributeMod> allMods();
}
