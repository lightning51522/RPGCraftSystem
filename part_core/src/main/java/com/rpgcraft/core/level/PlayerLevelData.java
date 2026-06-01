package com.rpgcraft.core.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家等级数据附件
 * <p>
 * 独立于原版经验/等级系统的自定义等级和经验数据。
 * 通过 NeoForge AttachmentType 持久化到玩家实体上。
 * <p>
 * 等级最低为 1 级，经验为当前累计的增量经验（达到升级阈值时自动扣除并升级）。
 * 升级逻辑由 {@link #addExperience(int)} 内部处理，支持连续升级。
 */
public class PlayerLevelData {

    /** 玩家等级，最低为 1 */
    private int level;

    /** 当前累计经验（增量，达到升级阈值时扣除） */
    private int experience;

    /**
     * 默认构造（等级 1，经验 0）
     */
    public PlayerLevelData() {
        this.level = 1;
        this.experience = 0;
    }

    /**
     * 反序列化构造
     */
    public PlayerLevelData(int level, int experience) {
        this.level = Math.max(1, level);
        this.experience = Math.max(0, experience);
    }

    /**
     * 存档序列化 Codec
     * <p>
     * 字段映射：{@code "level"} → 等级，{@code "experience"} → 当前经验
     */
    public static final MapCodec<PlayerLevelData> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("level").forGetter(PlayerLevelData::getLevel),
                    Codec.INT.fieldOf("experience").forGetter(PlayerLevelData::getExperience)
            ).apply(instance, PlayerLevelData::new)
    );

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = Math.max(0, experience);
    }

    /**
     * 查询升到下一级所需经验
     *
     * @return 升级所需经验，达到最大等级时返回 -1
     */
    public int getExpForNextLevel() {
        return LevelConfig.getExpForLevel(level);
    }

    /**
     * 增加经验并自动处理升级
     * <p>
     * 循环判断：当前经验 ≥ 升级所需经验时，扣除升级经验并提升等级，
     * 直到不满足升级条件或达到最大等级。
     *
     * @param amount 增加的经验量（必须 ≥ 0）
     * @return 是否发生了升级
     */
    public boolean addExperience(int amount) {
        if (amount <= 0) return false;

        int maxLevel = LevelConfig.getMaxLevel();
        int oldLevel = level;
        experience += amount;

        // 连续升级循环
        while (level < maxLevel) {
            int required = LevelConfig.getExpForLevel(level);
            if (required <= 0 || experience < required) break;
            experience -= required;
            level++;
        }

        return level > oldLevel;
    }
}
