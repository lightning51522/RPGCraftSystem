package com.rpgcraft.core.profession.api;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 职业接口 —— 定义 RPG 职业的核心契约
 * <p>
 * 每个职业提供一组属性加成（可为正值或负值），通过 {@link #getBonusMap()} 返回。
 * {@link #isPrimary()} 区分主职业和副职业（副职业为预留功能）。
 * <p>
 * 内置实现见 {@code com.rpgcraft.profession.CommonerProfession}、
 * {@code com.rpgcraft.profession.WarriorProfession}、
 * {@code com.rpgcraft.profession.ArcherProfession}。
 * <p>
 * 子模组可通过 {@link IProfessionProvider} SPI 注册自定义职业。
 */
public interface IProfession {

    /**
     * 获取职业的唯一标识符
     *
     * @return 职业标识符，如 {@code rpgcraftcore:warrior}
     */
    Identifier getId();

    /**
     * 获取职业的显示名称
     *
     * @return 显示名称，如 "战士"
     */
    String getDisplayName();

    /**
     * 获取职业的描述文本
     *
     * @return 描述，如 "力量提升，敏捷降低"
     */
    String getDescription();

    /**
     * 判断是否为主职业
     * <p>
     * 主职业返回 {@code true}，副职业（预留）返回 {@code false}。
     *
     * @return {@code true} 表示主职业
     */
    boolean isPrimary();

    /**
     * 获取职业提供的属性加成映射
     * <p>
     * 键为属性标识符（如 {@code rpgcraftcore:strength}），
     * 值为加成值（正数为增益，负数为减益）。
     * 返回的 Map 应为不可变映射。
     *
     * @return 属性加成映射
     */
    Map<Identifier, Integer> getBonusMap();
}
