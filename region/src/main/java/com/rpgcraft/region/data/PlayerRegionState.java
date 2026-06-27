package com.rpgcraft.region.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家当前所处区域集合（运行态附件）
 * <p>
 * 记录玩家上次 tick 检查时所处的区域 ID 集合，用于计算「进入 / 离开」差集，
 * 避免对已在区域内的玩家反复 add/remove 修饰符。
 * <p>
 * <b>不需要持久化跨会话</b>：玩家上线时为空，首次 tick 检查会自动补齐修饰符。
 * 但因附件机制要求提供 Codec，这里仍定义一个最小 Codec（仅序列化 regionIds 字段）。
 */
public class PlayerRegionState {

    /**
     * 序列化 Codec（MapCodec）
     * <p>
     * 用 list 编解码（DFU 无原生 Set codec），构造时转 LinkedHashSet。
     * 用 optionalFieldOf 兼容旧存档。
     */
    public static final MapCodec<PlayerRegionState> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Identifier.CODEC.listOf()
                            .optionalFieldOf("regions", List.of())
                            .forGetter(state -> List.copyOf(state.regionIds))
            ).apply(instance, PlayerRegionState::new)
    );

    /** 当前所处区域 ID 集合（LinkedHashSet 保持稳定顺序） */
    private final Set<Identifier> regionIds;

    /** 默认构造（上线时使用，空集合） */
    public PlayerRegionState() {
        this.regionIds = new LinkedHashSet<>();
    }

    /** 反序列化构造 */
    public PlayerRegionState(List<Identifier> regionIds) {
        this.regionIds = new LinkedHashSet<>(regionIds);
    }

    /** 当前区域 ID 集合（只读视图） */
    public Set<Identifier> getRegionIds() {
        return java.util.Collections.unmodifiableSet(regionIds);
    }

    /**
     * 用新区域集合替换当前状态
     *
     * @param newRegions 新的区域 ID 集合
     */
    public void set(Set<Identifier> newRegions) {
        regionIds.clear();
        regionIds.addAll(newRegions);
    }
}
