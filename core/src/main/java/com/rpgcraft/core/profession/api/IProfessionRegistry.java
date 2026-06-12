package com.rpgcraft.core.profession.api;

import net.minecraft.resources.Identifier;

import java.util.Collection;

/**
 * 职业注册中心接口
 * <p>
 * 管理职业实例的注册和查询。默认实现在 profession 模块中。
 * <p>
 * 第三方模组通过 {@link IProfessionProvider} SPI 获取此接口实例来注册自定义职业。
 */
public interface IProfessionRegistry {

    /**
     * 注册一个职业
     *
     * @param profession 职业实例
     */
    void register(IProfession profession);

    /**
     * 根据标识符获取职业
     *
     * @param id 职业标识符
     * @return 职业实例，不存在则返回 {@code null}
     */
    IProfession getProfession(Identifier id);

    /**
     * 获取所有已注册的职业
     *
     * @return 不可变职业集合
     */
    Collection<IProfession> getAllProfessions();

    /**
     * 获取默认职业（平民）
     *
     * @return 默认职业实例
     */
    IProfession getDefault();
}
