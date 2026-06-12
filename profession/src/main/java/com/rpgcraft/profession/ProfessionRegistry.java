package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionRegistry;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 职业注册中心 —— 管理 {@link IProfession} 实例的注册和查询
 * <p>
 * 内部使用 {@code LinkedHashMap} 保持注册顺序，
 * 通过 {@link ProfessionManager#init()} 完成内置职业的注册。
 * <p>
 * 实现 {@link IProfessionRegistry} 接口（定义在 core 中），供第三方通过 SPI 扩展。
 */
public class ProfessionRegistry implements IProfessionRegistry {

    private final Map<Identifier, IProfession> professions = new LinkedHashMap<>();

    /**
     * 注册一个职业
     *
     * @param profession 职业实例
     */
    public void register(IProfession profession) {
        professions.put(profession.getId(), profession);
    }

    /**
     * 根据标识符获取职业
     *
     * @param id 职业标识符
     * @return 职业实例，不存在则返回 {@code null}
     */
    public IProfession getProfession(Identifier id) {
        return professions.get(id);
    }

    /**
     * 获取所有已注册的职业
     *
     * @return 不可变职业集合
     */
    public Collection<IProfession> getAllProfessions() {
        return Collections.unmodifiableCollection(professions.values());
    }

    /**
     * 获取默认职业（平民）
     *
     * @return 平民职业实例
     */
    public IProfession getDefault() {
        return professions.get(ProfessionManager.COMMONER_ID);
    }
}
