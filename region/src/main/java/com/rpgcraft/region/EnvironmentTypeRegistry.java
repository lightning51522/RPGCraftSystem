package com.rpgcraft.region;

import com.rpgcraft.region.data.EnvironmentType;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 环境类型注册表（内存态，{@code /reload} 时整体重建）
 * <p>
 * 维护 id → {@link EnvironmentType} 的映射。由 {@link EnvironmentTypeLoader} 在服务端
 * reload 时灌入。供 {@code setregion} 命令校验环境 ID、{@code setregion done} 定稿时
 * 套用效果。
 * <p>
 * <h3>线程安全</h3>
 * 读路径（命令校验）在主线程；写路径（reload）在 reload 线程的 apply 阶段（主线程）。
 * 用 volatile 引用保证 reload 后立即可见。
 *
 * @see EnvironmentTypeLoader
 */
public final class EnvironmentTypeRegistry {

    /** 全局实例（加载时由 EnvironmentTypeLoader 替换） */
    private static volatile EnvironmentTypeRegistry instance = new EnvironmentTypeRegistry(Map.of());

    /** id → EnvironmentType（不可变快照） */
    private final Map<Identifier, EnvironmentType> types;

    private EnvironmentTypeRegistry(Map<Identifier, EnvironmentType> types) {
        this.types = types;
    }

    /**
     * 用新环境类型集合替换当前注册表
     */
    public static void replaceAll(Map<Identifier, EnvironmentType> types) {
        instance = new EnvironmentTypeRegistry(
                Collections.unmodifiableMap(new LinkedHashMap<>(types)));
    }

    /** 获取当前注册表实例 */
    public static EnvironmentTypeRegistry get() {
        return instance;
    }

    /** 环境类型总数 */
    public int size() {
        return types.size();
    }

    /** 按 ID 查询（可能为 null） */
    public EnvironmentType get(Identifier id) {
        return types.get(id);
    }

    /**
     * 按名称查询环境类型（显示名或 ID 任一匹配）
     * <p>
     * 用于 {@code setregion <ID>} 命令的 ID 参数解析。匹配规则：
     * <ul>
     *   <li>ID 完整形式（{@code ns:path}）全等，<b>或</b></li>
     *   <li>ID path 部分（不含命名空间）全等，<b>或</b></li>
     *   <li>显示名全等</li>
     * </ul>
     * 任一命中即返回。
     *
     * @param idOrName 环境 ID（path 或完整形式）或显示名
     * @return 匹配的环境类型；无匹配返回 null
     */
    public EnvironmentType match(String idOrName) {
        for (EnvironmentType t : types.values()) {
            Identifier id = t.id();
            if (id.toString().equals(idOrName) || id.getPath().equals(idOrName)) {
                return t;
            }
            if (t.displayName().equals(idOrName)) {
                return t;
            }
        }
        return null;
    }

    /** 所有环境类型（不可变） */
    public Iterable<EnvironmentType> all() {
        return types.values();
    }
}
