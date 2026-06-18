package com.rpgcraft.core.skill.api;

/**
 * 技能服务提供者接口（SPI）
 * <p>
 * 其他模组可实现此接口，通过 {@link java.util.ServiceLoader} 机制
 * 向 skills 模块注册自定义技能（除 datapack JSON 之外的代码定义方式）。
 * <p>
 * 使用方式：在 {@code META-INF/services/com.rpgcraft.core.skill.api.ISkillProvider}
 * 文件中写入实现类的全限定名。
 * <p>
 * 示例：
 * <pre>
 * public class MyModSkills implements ISkillProvider {
 *     public void registerSkills(ISkillRegistry registry) {
 *         registry.register(new MyCustomSkill());
 *     }
 * }
 * </pre>
 */
public interface ISkillProvider {

    /**
     * 注册自定义技能到技能注册中心
     *
     * @param registry 技能注册中心
     */
    void registerSkills(ISkillRegistry registry);
}
