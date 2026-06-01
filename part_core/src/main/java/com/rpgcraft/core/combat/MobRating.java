package com.rpgcraft.core.combat;

/**
 * 怪物评级枚举
 * <p>
 * 定义敌对生物的评级等级，每个评级对应一个全属性倍率。
 * 评级在等级缩放和 JSON 覆盖之后应用，作为最终乘数。
 * <p>
 * 通过 {@code /rpg spawn <entity> <level> {"rating":"ELITE"}} 指令指定评级。
 * 默认评级为 {@link #NORMAL}（1.0x，不影响属性）。
 */
public enum MobRating {

    /** 普通 — 1.0x */
    NORMAL("普通", 1.0),
    /** 强壮 — 1.25x */
    STRONG("强壮", 1.25),
    /** 精英 — 1.5x */
    ELITE("精英", 1.5),
    /** 恶名精英 — 2.0x */
    NOTORIOUS_ELITE("恶名精英", 2.0),
    /** 头目 — 3.0x */
    BOSS("头目", 3.0),
    /** 领主 — 5.0x */
    LORD("领主", 5.0);

    /** 中文显示名称 */
    private final String displayName;

    /** 全属性倍率 */
    private final double multiplier;

    MobRating(String displayName, double multiplier) {
        this.displayName = displayName;
        this.multiplier = multiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMultiplier() {
        return multiplier;
    }

    /**
     * 根据枚举名称或中文显示名查找评级
     *
     * @param name 枚举名称（如 "ELITE"）或中文显示名（如 "精英"），不区分大小写
     * @return 对应的评级，未找到时返回 null
     */
    public static MobRating fromName(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase();
        for (MobRating rating : values()) {
            if (rating.name().equals(upper) || rating.displayName.equals(name)) {
                return rating;
            }
        }
        return null;
    }
}
