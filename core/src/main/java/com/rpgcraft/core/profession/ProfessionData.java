package com.rpgcraft.core.profession;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 玩家职业附件数据 —— 持久化玩家的职业选择与进度
 * <p>
 * 包含当前主职业、副职业、可分配职业经验池、每个已解锁职业的等级、已解锁职业集合、
 * 副职业加成开关。通过 {@code ProfessionManager.PLAYER_PROFESSION} 附件注册到玩家实体。
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

    /** 副职业标识符（可为 null 表示无副职业） */
    private Identifier secondaryProfessionId;

    /** 可分配职业经验池：玩家升级时累积，在职业面板投入某职业升级时消耗 */
    private int skillPointPool;

    /** 每个已解锁职业的当前等级（键为职业 ID，值为等级，1..maxLevel） */
    private Map<Identifier, Integer> professionLevels;

    /** 已解锁的职业集合（含起点平民与已进阶的职业） */
    private Set<Identifier> unlockedProfessions;

    /** 副职业加成开关：true 时副职业的属性加成生效 */
    private boolean secondaryActive;

    /**
     * 默认构造函数 —— 主职业为平民，无副职业，池为 0，平民等级 1 且已解锁
     */
    public ProfessionData() {
        this.professionId = DEFAULT_PROFESSION_ID;
        this.secondaryProfessionId = null;
        this.skillPointPool = 0;
        this.professionLevels = new LinkedHashMap<>();
        this.professionLevels.put(DEFAULT_PROFESSION_ID, 1);
        this.unlockedProfessions = new java.util.LinkedHashSet<>();
        this.unlockedProfessions.add(DEFAULT_PROFESSION_ID);
        this.secondaryActive = false;
    }

    /**
     * 序列化 Codec。除 profession 外全部 optional，兼容旧存档。
     * <p>
     * 注意 {@code secondary} 字段的处理：副职业可为空（业务层用 null 表示"无副职业"），
     * 但 DFU 的 Codec 链路对 null 零容忍——{@code optionalFieldOf("secondary", null)} 会让 null
     * 默认值进入 {@code DataResult.Success.result()} 的 {@code Optional.of(value)} 触发 NPE
     * （见堆栈 {@code RecordCodecBuilder$Instance.decode} → {@code DataResult.Success.result}），
     * 导致整个 {@code player_profession} 附件反序列化失败、数据回到默认值（表现为等级/经验不保存）。
     * <p>
     * 正确做法：可空字段在 Codec 链路<b>全程保持 {@code Optional}</b>，绝不出 null——
     * {@code optionalFieldOf(name)} 字段缺失时返回 {@code Optional.empty()}（安全），
     * {@code forGetter} 返回 {@code Optional<T>}，只在 {@code apply} 函数体内用
     * {@code .orElse(null)} 拆包赋给业务字段。这样 RecordCodecBuilder 组合各字段时
     * 每个 {@code DataResult} 都是非 null 的 {@code Optional}，不会触发 NPE。
     */
    public static final MapCodec<ProfessionData> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("profession").forGetter(ProfessionData::getProfessionId),
                    Identifier.CODEC.optionalFieldOf("secondary")
                            .forGetter(d -> Optional.ofNullable(d.getSecondaryProfessionId())),
                    Codec.INT.optionalFieldOf("skill_point_pool", 0).forGetter(ProfessionData::getSkillPointPool),
                    Identifier.CODEC.listOf().optionalFieldOf("unlocked", List.of())
                            .forGetter(d -> List.copyOf(d.unlockedProfessions)),
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                            .optionalFieldOf("levels", Map.of())
                            .forGetter(d -> new LinkedHashMap<>(d.professionLevels))
            ).apply(instance, (prof, secondary, pool, unlockedList, levelsMap) -> {
                ProfessionData d = new ProfessionData();
                d.professionId = prof;
                d.secondaryProfessionId = secondary.orElse(null);
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

    public Identifier getSecondaryProfessionId() {
        return secondaryProfessionId;
    }

    public void setSecondaryProfessionId(Identifier secondaryProfessionId) {
        this.secondaryProfessionId = secondaryProfessionId;
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

    public boolean isSecondaryActive() {
        return secondaryActive;
    }

    public void setSecondaryActive(boolean secondaryActive) {
        this.secondaryActive = secondaryActive;
    }
}
