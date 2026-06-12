package com.rpgcraft.core.profession.api;

/**
 * 职业服务提供者接口（SPI）
 * <p>
 * 其他模组可实现此接口，通过 {@link java.util.ServiceLoader} 机制
 * 向主模组注册自定义职业。
 * <p>
 * 使用方式：在 {@code META-INF/services/com.rpgcraft.core.profession.api.IProfessionProvider}
 * 文件中写入实现类的全限定名。
 * <p>
 * 示例：
 * <pre>
 * public class MyModProfessions implements IProfessionProvider {
 *     public void registerProfessions(IProfessionRegistry registry) {
 *         registry.register(new MyCustomProfession());
 *     }
 * }
 * </pre>
 */
public interface IProfessionProvider {

    /**
     * 注册自定义职业到职业注册中心
     *
     * @param registry 职业注册中心
     */
    void registerProfessions(IProfessionRegistry registry);
}
