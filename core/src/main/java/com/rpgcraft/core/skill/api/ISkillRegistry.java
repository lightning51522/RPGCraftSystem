package com.rpgcraft.core.skill.api;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * 技能注册中心接口
 * <p>
 * 由 skills 模块实现，管理所有已注册的技能定义。datapack 加载与
 * {@link ISkillProvider}（ServiceLoader）注册都通过此接口写入。
 *
 * @see com.rpgcraft.core.registry.ISkillSystem
 */
public interface ISkillRegistry {

    /**
     * 注册一个技能定义（同 ID 重复注册会覆盖前者并 WARN）
     */
    void register(ISkill skill);

    /**
     * 按 ID 查询技能（不存在返回 null）
     */
    @Nullable
    ISkill getById(Identifier id);

    /**
     * 获取所有已注册的技能（不可变视图）
     */
    Collection<ISkill> getAll();

    /**
     * 清空注册表（datapack reload 时调用，随后重新灌入）
     */
    void clear();
}
