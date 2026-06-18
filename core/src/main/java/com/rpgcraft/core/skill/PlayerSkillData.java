package com.rpgcraft.core.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 玩家技能数据（附件）
 * <p>
 * 持久化存储玩家的技能状态：
 * <ul>
 *   <li>{@code cooldowns}：技能 ID → 冷却到期 tick（{@code level.getGameTime() + duration}）</li>
 *   <li>{@code learned}：已学习技能 ID 集合（MVP 下系统启动时全部注入，供学习系统预留）</li>
 * </ul>
 * <p>
 * 放置在 core 模块的原因：客户端 UI（技能栏 HUD、技能面板）需直接读取此附件进行渲染，
 * 与 {@code PlayerLevelData}、{@code ProfessionData}、{@code PlayerAttributePoints} 的位置策略一致。
 * <p>
 * <b>序列化</b>：两个字段均使用 {@code optionalFieldOf}（默认空），保证旧存档（无此附件）
 * 反序列化为默认状态，向后兼容。
 */
public class PlayerSkillData {

    private static final String KEY_COOLDOWNS = "cooldowns";
    private static final String KEY_LEARNED = "learned";

    /**
     * 序列化编解码器（MapCodec，用于附件序列化）
     * <p>
     * 使用 {@code optionalFieldOf} 使新增字段时兼容旧存档（缺失字段使用默认值）。
     * <p>
     * learned 用 list 编解码（DFU 无原生 Set codec），构造时转 LinkedHashSet。
     */
    public static final MapCodec<PlayerSkillData> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
                            .optionalFieldOf(KEY_COOLDOWNS, Map.of())
                            .forGetter(PlayerSkillData::getCooldowns),
                    // learned 用 list 编解码，构造函数内转 LinkedHashSet
                    Identifier.CODEC.listOf()
                            .optionalFieldOf(KEY_LEARNED, java.util.List.of())
                            .forGetter(data -> java.util.List.copyOf(data.getLearned()))
            ).apply(instance, PlayerSkillData::new)
    );

    /** 技能 ID → 冷却到期 tick（使用 LinkedHashMap 保持稳定顺序） */
    private final Map<Identifier, Long> cooldowns;

    /** 已学习技能 ID 集合（使用 LinkedHashSet 保持稳定顺序） */
    private final Set<Identifier> learned;

    /**
     * 默认构造函数（新建玩家时使用，所有值为空）
     */
    public PlayerSkillData() {
        this.cooldowns = new LinkedHashMap<>();
        this.learned = new LinkedHashSet<>();
    }

    /**
     * 反序列化 / 数据迁移构造函数
     *
     * @param cooldowns 冷却映射（会被复制到内部 LinkedHashMap）
     * @param learned   已学列表（会被复制到内部 LinkedHashSet，保持顺序与去重）
     */
    public PlayerSkillData(Map<Identifier, Long> cooldowns, java.util.List<Identifier> learned) {
        this.cooldowns = new LinkedHashMap<>(cooldowns);
        this.learned = new LinkedHashSet<>(learned);
    }

    // ====================================================================
    // 冷却
    // ====================================================================

    /**
     * 判断技能是否处于冷却中
     *
     * @param skillId   技能 ID
     * @param gameTime  当前 {@code level.getGameTime()}
     * @return {@code true} 仍在冷却中
     */
    public boolean isOnCooldown(Identifier skillId, long gameTime) {
        Long expire = cooldowns.get(skillId);
        return expire != null && expire > gameTime;
    }

    /**
     * 启动技能冷却（覆盖已有冷却）
     *
     * @param skillId     技能 ID
     * @param expireTick  冷却到期 tick
     */
    public void startCooldown(Identifier skillId, long expireTick) {
        cooldowns.put(skillId, expireTick);
    }

    /**
     * 获取技能剩余冷却 tick
     *
     * @param skillId   技能 ID
     * @param gameTime  当前 tick
     * @return 剩余 tick（≤ 0 表示可用）
     */
    public long getRemaining(Identifier skillId, long gameTime) {
        Long expire = cooldowns.get(skillId);
        return expire == null ? 0 : Math.max(0, expire - gameTime);
    }

    /**
     * 重置全部冷却（GM 调试）
     */
    public void clearCooldowns() {
        cooldowns.clear();
    }

    /**
     * 清理已过期的冷却条目（供 tick 调度周期调用，避免 Map 无限增长）
     *
     * @param gameTime 当前 tick
     */
    public void pruneExpired(long gameTime) {
        cooldowns.entrySet().removeIf(e -> e.getValue() <= gameTime);
    }

    /**
     * 获取全部冷却映射（不可变视图）
     */
    public Map<Identifier, Long> getCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    // ====================================================================
    // 已学技能
    // ====================================================================

    /**
     * 是否已学习某技能
     */
    public boolean hasLearned(Identifier skillId) {
        return learned.contains(skillId);
    }

    /**
     * 标记学习某技能
     */
    public void learn(Identifier skillId) {
        learned.add(skillId);
    }

    /**
     * 取消学习某技能
     */
    public void forget(Identifier skillId) {
        learned.remove(skillId);
    }

    /**
     * 获取已学技能集合（不可变视图）
     */
    public Set<Identifier> getLearned() {
        return Collections.unmodifiableSet(learned);
    }
}
