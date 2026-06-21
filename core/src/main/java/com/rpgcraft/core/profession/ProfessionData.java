package com.rpgcraft.core.profession;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玩家职业附件数据 —— 持久化玩家的职业选择与进度
 * <p>
 * 包含当前主职业、<b>已激活副职业集合</b>、可分配职业经验池、每个已解锁职业的等级、
 * 已解锁职业集合。通过 {@code ProfessionManager.PLAYER_PROFESSION} 附件注册到玩家实体。
 * <p>
 * <b>多副职业独立激活模型</b>：每个已解锁的副职业可独立激活/取消激活，所有已激活副职业
 * 的属性加成<b>共存</b>。取代旧版本「单一当前副职业 + 全局开关」模型。
 * <p>
 * 序列化使用 {@link #CODEC}，除 {@code profession} 外的字段均为 optional（缺失时取默认值），
 * 以兼容旧版本存档（旧存档只有 profession 字段）。
 */
public class ProfessionData {

    /** 默认职业标识符（平民），内联常量以避免对 ProfessionManager 的编译期依赖 */
    private static final Identifier DEFAULT_PROFESSION_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "commoner");

    /** 主职业标识符，默认为平民 */
    private Identifier professionId;

    /** 已激活的副职业集合（每个独立激活，加成共存） */
    private Set<Identifier> activeSecondaryProfessions;

    /** 可分配职业经验池：玩家升级时累积，在职业面板投入某职业升级时消耗 */
    private int skillPointPool;

    /** 每个已解锁职业的当前等级（键为职业 ID，值为等级，1..maxLevel） */
    private Map<Identifier, Integer> professionLevels;

    /** 已解锁的职业集合（含起点平民与已进阶的职业） */
    private Set<Identifier> unlockedProfessions;

    /**
     * 默认构造函数 —— 主职业为平民，无激活副职业，池为 0，平民等级 1 且已解锁
     */
    public ProfessionData() {
        this.professionId = DEFAULT_PROFESSION_ID;
        this.activeSecondaryProfessions = new java.util.LinkedHashSet<>();
        this.skillPointPool = 0;
        this.professionLevels = new LinkedHashMap<>();
        this.professionLevels.put(DEFAULT_PROFESSION_ID, 1);
        this.unlockedProfessions = new java.util.LinkedHashSet<>();
        this.unlockedProfessions.add(DEFAULT_PROFESSION_ID);
    }

    /**
     * 序列化 Codec。除 profession 外全部 optional，兼容旧存档。
     * <p>
     * 注意可空集合字段的处理：旧版本 {@code secondary} 字段（单一副职业）和 {@code secondaryActive}
     * 字段（全局开关）已被移除，改用 {@code active_secondary} 列表存储已激活副职业集合。
     * 旧存档中的 {@code secondary} 字段会被忽略（数据丢失，玩家需重新激活副职业）——
     * 因模组仍是 0.5.1-alpha 阶段，这是可接受的迁移成本。
     * <p>
     * DFU 的 Codec 链路对 null 零容忍（见旧 {@code secondary} 字段的处理经验），
     * 集合字段用 {@code listOf} + {@code optionalFieldOf} 默认 {@code List.of()} 避免任何 null。
     */
    public static final MapCodec<ProfessionData> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("profession").forGetter(ProfessionData::getProfessionId),
                    Identifier.CODEC.listOf().optionalFieldOf("active_secondary", List.of())
                            .forGetter(d -> List.copyOf(d.activeSecondaryProfessions)),
                    Codec.INT.optionalFieldOf("skill_point_pool", 0).forGetter(ProfessionData::getSkillPointPool),
                    Identifier.CODEC.listOf().optionalFieldOf("unlocked", List.of())
                            .forGetter(d -> List.copyOf(d.unlockedProfessions)),
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                            .optionalFieldOf("levels", Map.of())
                            .forGetter(d -> new LinkedHashMap<>(d.professionLevels))
            ).apply(instance, (prof, activeSecondary, pool, unlockedList, levelsMap) -> {
                ProfessionData d = new ProfessionData();
                d.professionId = prof;
                d.activeSecondaryProfessions = new java.util.LinkedHashSet<>(activeSecondary);
                d.skillPointPool = pool;
                d.unlockedProfessions = new java.util.LinkedHashSet<>(unlockedList);
                d.professionLevels = new LinkedHashMap<>(levelsMap);
                d.unlockedProfessions.add(DEFAULT_PROFESSION_ID);
                d.professionLevels.putIfAbsent(DEFAULT_PROFESSION_ID, 1);
                return d;
            })
    );

    public Identifier getProfessionId() {
        return professionId;
    }

    public void setProfessionId(Identifier professionId) {
        this.professionId = professionId;
    }

    /**
     * 已激活副职业集合的不可变视图
     */
    public Set<Identifier> getActiveSecondaryProfessions() {
        return Collections.unmodifiableSet(activeSecondaryProfessions);
    }

    /** 指定副职业是否已激活 */
    public boolean isSecondaryActive(Identifier professionId) {
        return activeSecondaryProfessions.contains(professionId);
    }

    /**
     * 设置某副职业的激活状态。
     *
     * @param professionId 副职业 ID（调用方应保证合法，本方法不校验）
     * @param active       true 加入激活集；false 移除
     */
    public void setSecondaryActive(Identifier professionId, boolean active) {
        if (active) {
            activeSecondaryProfessions.add(professionId);
        } else {
            activeSecondaryProfessions.remove(professionId);
        }
    }

    public int getSkillPointPool() {
        return skillPointPool;
    }

    public void setSkillPointPool(int skillPointPool) {
        this.skillPointPool = Math.max(0, skillPointPool);
    }

    public void addSkillPoints(int delta) {
        this.skillPointPool = Math.max(0, this.skillPointPool + delta);
    }

    /**
     * 获取某职业的等级（未记录则返回 0，调用方应按需视为 1 或未解锁）
     */
    public int getProfessionLevel(Identifier professionId) {
        return professionLevels.getOrDefault(professionId, 0);
    }

    public void setProfessionLevel(Identifier professionId, int level) {
        professionLevels.put(professionId, level);
    }

    /** 职业等级的不可变视图 */
    public Map<Identifier, Integer> getProfessionLevels() {
        return Collections.unmodifiableMap(professionLevels);
    }

    public boolean isUnlocked(Identifier professionId) {
        return unlockedProfessions.contains(professionId);
    }

    public void unlock(Identifier professionId) {
        unlockedProfessions.add(professionId);
        professionLevels.putIfAbsent(professionId, 1);
    }

    /** 已解锁职业集合的不可变视图 */
    public Set<Identifier> getUnlockedProfessions() {
        return Collections.unmodifiableSet(unlockedProfessions);
    }
}
