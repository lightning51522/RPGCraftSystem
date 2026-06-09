package com.rpgcraft.core.attribute.api;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;

/**
 * 属性快照 —— 一次性捕获实体上所有已注册属性的当前值和最大值
 * <p>
 * 通过 {@link IAttributeRegistry#createSnapshot} 创建，
 * 通过 {@link IAttributeRegistry#applySnapshot} 恢复。
 * <p>
 * 快照是不可变的，可安全跨事件缓存（如死亡→重生）。
 */
public final class AttributeSnapshot {

    /**
     * 单个属性的快照数据
     *
     * @param currentValue 属性当前值
     * @param maxValue     属性最大值
     * @param displayName  属性显示名称
     */
    public record AttributeData(int currentValue, int maxValue, String displayName) {}

    private final Map<Identifier, AttributeData> data;

    public AttributeSnapshot(Map<Identifier, AttributeData> data) {
        this.data = Collections.unmodifiableMap(data);
    }

    /**
     * 获取指定属性的快照数据
     *
     * @return 属性数据，未找到返回 null
     */
    public AttributeData get(Identifier id) {
        return data.get(id);
    }

    /**
     * 获取快照中所有属性条目（只读）
     */
    public Map<Identifier, AttributeData> getAll() {
        return data;
    }
}
