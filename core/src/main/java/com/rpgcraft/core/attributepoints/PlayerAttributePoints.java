package com.rpgcraft.core.attributepoints;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家属性点数据（附件）
 * <p>
 * 持久化存储玩家的属性点状态：
 * <ul>
 *   <li>{@code availablePoints}：当前可分配的属性点数</li>
 *   <li>{@code allocations}：属性 ID → 已分配点数 的映射</li>
 * </ul>
 * <p>
 * 放置在 core 模块的原因：客户端 UI（角色界面的属性点面板、信息插件）需直接读取
 * 此附件进行渲染，与 {@code PlayerLevelData}、{@code ProfessionData} 的位置策略一致。
 * <p>
 * <b>序列化</b>：两个字段均使用 {@code optionalFieldOf}（默认值 0 / 空），保证旧存档
 * （无此附件）反序列化为默认状态，向后兼容。
 */
public class PlayerAttributePoints {

    /** 可分配点数 NBT 键 */
    private static final String KEY_AVAILABLE = "available";

    /** 分配映射 NBT 键 */
    private static final String KEY_ALLOCATIONS = "allocations";

    /** 可分配点数编解码器（MapCodec，用于附件序列化） */
    public static final com.mojang.serialization.MapCodec<PlayerAttributePoints> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf(KEY_AVAILABLE, 0)
                            .forGetter(PlayerAttributePoints::getAvailablePoints),
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                            .optionalFieldOf(KEY_ALLOCATIONS, Map.of())
                            .forGetter(PlayerAttributePoints::getAllocations)
            ).apply(instance, PlayerAttributePoints::new)
    );

    /** 可分配点数 */
    private int availablePoints;

    /** 属性 ID → 已分配点数（使用 LinkedHashMap 保持稳定顺序） */
    private final Map<Identifier, Integer> allocations;

    /**
     * 默认构造函数（新建玩家时使用，所有值为默认）
     */
    public PlayerAttributePoints() {
        this.availablePoints = 0;
        this.allocations = new LinkedHashMap<>();
    }

    /**
     * 反序列化 / 数据迁移构造函数
     *
     * @param availablePoints 可分配点数
     * @param allocations     分配映射（会被复制到内部 LinkedHashMap）
     */
    public PlayerAttributePoints(int availablePoints, Map<Identifier, Integer> allocations) {
        this.availablePoints = Math.max(0, availablePoints);
        this.allocations = new LinkedHashMap<>(allocations);
    }

    /** 获取可分配点数 */
    public int getAvailablePoints() {
        return availablePoints;
    }

    /** 设置可分配点数（服务端使用，非负） */
    public void setAvailablePoints(int points) {
        this.availablePoints = Math.max(0, points);
    }

    /** 增加可分配点数 */
    public void addAvailablePoints(int delta) {
        this.availablePoints = Math.max(0, this.availablePoints + delta);
    }

    /**
     * 获取全部分配情况（不可变视图）
     *
     * @return 属性 ID → 已分配点数
     */
    public Map<Identifier, Integer> getAllocations() {
        return Collections.unmodifiableMap(allocations);
    }

    /**
     * 获取某属性已分配的点数
     *
     * @param attrId 属性 ID
     * @return 已分配点数（未分配过返回 0）
     */
    public int getAllocated(Identifier attrId) {
        return allocations.getOrDefault(attrId, 0);
    }

    /**
     * 设置某属性的已分配点数
     *
     * @param attrId 属性 ID
     * @param points 点数（≥ 0；= 0 时移除该键）
     */
    public void setAllocation(Identifier attrId, int points) {
        if (points <= 0) {
            allocations.remove(attrId);
        } else {
            allocations.put(attrId, points);
        }
    }

    /**
     * 累加某属性的已分配点数
     *
     * @param attrId 属性 ID
     * @param delta  增量
     */
    public void addAllocation(Identifier attrId, int delta) {
        setAllocation(attrId, getAllocated(attrId) + delta);
    }

    /**
     * 清空全部分配，所有已分配点数退还可分配池
     */
    public void resetAll() {
        int totalAllocated = allocations.values().stream().mapToInt(Integer::intValue).sum();
        availablePoints += totalAllocated;
        allocations.clear();
    }

    /**
     * 清空全部分配映射（不退还可分配点数）—— 用于快照恢复时重建数据
     */
    public void clearAllocations() {
        allocations.clear();
    }
}
