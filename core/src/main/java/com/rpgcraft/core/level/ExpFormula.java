package com.rpgcraft.core.level;

/**
 * 经验公式工具类 —— 统一全工程的升级经验计算，消除散落多处的 {@code 50 × L^1.5} 拷贝。
 * <p>
 * 玩家等级系统（{@code leveling} 模块）和职业等级系统（{@code profession} 模块）使用同一默认公式：
 * 从 {@code level} 升到 {@code level+1} 所需经验 = {@code round(50 × level^1.5)}。
 * <p>
 * 集中此处后，调整公式系数只需改一处，避免客户端 UI 镜像与服务端校验漂移。
 *
 * @see com.rpgcraft.leveling.LevelConfig
 * @see com.rpgcraft.profession.ProfessionManager
 */
public final class ExpFormula {

    /** 基础系数 */
    public static final double BASE_COEFFICIENT = 50.0;
    /** 指数 */
    public static final double EXPONENT = 1.5;

    private ExpFormula() {
    }

    /**
     * 计算从 {@code level} 升到 {@code level+1} 所需经验：{@code round(50 × level^1.5)}。
     *
     * @param level 当前等级（≥ 1）
     * @return 升下一级所需经验；{@code level < 1} 返回 {@code -1}（视为不可升级/已满级，
     *         与 {@code ILevelRegistry.getExpForLevel} 及 {@code PlayerLevelData.addExperience} 的约定一致）
     */
    public static int expForNextLevel(int level) {
        if (level < 1) return -1;
        return (int) Math.round(BASE_COEFFICIENT * Math.pow(level, EXPONENT));
    }

    /**
     * 生成经验表：{@code table[i]} = 从 {@code i+1} 级升到 {@code i+2} 级所需经验，长度为 {@code maxLevel - 1}。
     *
     * @param maxLevel 等级上限（≥ 2 才有非空表）
     * @return 经验表数组；{@code maxLevel < 2} 时返回空数组
     */
    public static int[] generateExpTable(int maxLevel) {
        int length = Math.max(0, maxLevel - 1);
        int[] table = new int[length];
        for (int i = 0; i < length; i++) {
            // 数组索引 i 对应 level = i+1（升到 i+2 级所需）
            table[i] = expForNextLevel(i + 1);
        }
        return table;
    }
}
