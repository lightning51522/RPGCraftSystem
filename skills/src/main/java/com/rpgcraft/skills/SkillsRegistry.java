package com.rpgcraft.skills;

import com.rpgcraft.core.skill.api.ISkill;
import com.rpgcraft.core.skill.api.ISkillRegistry;
import com.rpgcraft.skills.SkillsMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能注册中心（{@link ISkillRegistry} 默认实现）
 * <p>
 * 由 {@link SkillsDefinitionLoader} 在 datapack reload 时灌入，由 {@link ISkillProvider}
 * （ServiceLoader）追加。同 ID 重复注册会覆盖前者并 WARN（与 ProfessionRegistry 一致）。
 */
public class SkillsRegistry implements ISkillRegistry {

    private final Map<Identifier, ISkill> skills = new LinkedHashMap<>();

    @Override
    public void register(ISkill skill) {
        Identifier id = skill.getId();
        if (skills.containsKey(id)) {
            SkillsMod.LOGGER.warn("技能 {} 重复注册，后者覆盖前者", id);
        }
        skills.put(id, skill);
    }

    @Override
    public @Nullable ISkill getById(Identifier id) {
        return skills.get(id);
    }

    @Override
    public Collection<ISkill> getAll() {
        return Collections.unmodifiableCollection(skills.values());
    }

    @Override
    public void clear() {
        skills.clear();
    }
}
