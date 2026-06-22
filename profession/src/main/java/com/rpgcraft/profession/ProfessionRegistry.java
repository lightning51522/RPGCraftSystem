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
 * 内部使用 {@code LinkedHashMap} 保持注册顺序。具体职业由 {@code professions} 子模块
 * （{@code rpgcraftprofessions}）在它的 {@code @Mod} 入口构造函数中通过 {@link #register} 注册，
 * NeoForge mod 加载顺序保证本注册中心先于 professions 模块初始化。
 * <p>
 * 实现 {@link IProfessionRegistry} 接口（定义在 core 中）。
 */
public class ProfessionRegistry implements IProfessionRegistry {

    private final Map<Identifier, IProfession> professions = new LinkedHashMap<>();
    /** 标记：professions 子模块是否已完成注册（首次成功 register 后置 true） */
    private volatile boolean loaded = false;

    /**
     * 注册一个职业
     * <p>
     * 由 professions 子模块在 @Mod 入口构造函数中调用。首次成功注册后置 loaded=true。
     *
     * @param profession 职业实例
     */
    public void register(IProfession profession) {
        professions.put(profession.getId(), profession);
        loaded = true;
    }

    /**
     * 清空所有已注册职业（仅供测试/调试使用）
     * <p>
     * 具体职业已改为 Java 类硬编码注册，运行时不再 clear 重建。
     */
    public void clear() {
        professions.clear();
        loaded = false;
    }

    /**
     * 是否已完成职业注册
     * <p>
     * professions 子模块首次调用 register 后返回 true。
     * 可用于判断 professions 模块是否正确加载。
     *
     * @return {@code true} 如果至少有一个职业已注册
     */
    public boolean isLoaded() {
        return loaded;
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
