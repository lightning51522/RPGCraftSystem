package com.rpgcraft.core.level;

/**
 * 击杀经验倍率曲线 —— 根据玩家等级与怪物等级的差值计算经验倍率。
 * <p>
 * 纯静态工具类，无 Minecraft 依赖，服务端（{@code leveling} 模块的
 * {@code DefaultLevelCalculator}）与客户端（{@code client} 模块的怪物信息预览）
 * 共同调用，从根上消除两端的公式漂移（参照 {@link ExpFormula} 的集中化模式）。
 *
 * <h2>设计目标</h2>
 * <ul>
 *   <li>玩家等级与怪物等级接近（差值 ±{@link #PEAK_HALF_WIDTH} 以内）时倍率最高，
 *       鼓励挑战与自身等级相仿的怪物。</li>
 *   <li>二者差值越大倍率越低；且<b>不对称</b>：怪物低于玩家时倍率下降更快，
 *       怪物高于玩家时下降较缓（鼓励挑战高等级怪而非碾压低等级怪）。</li>
 * </ul>
 *
 * <h2>锚点（分段线性）</h2>
 * 设 {@code d = 玩家等级 − 怪物等级}：
 * <table border="1">
 *   <tr><th>差值 d</th><th>倍率 M(d)</th><th>含义</th></tr>
 *   <tr><td>{@code |d| ≤ 5}</td><td>5.0×</td><td>峰值甜区</td></tr>
 *   <tr><td>{@code d = +20}</td><td>→ 保底 1 点经验</td><td>玩家比怪高 20 级</td></tr>
 *   <tr><td>{@code d > +20}</td><td>仍为保底 1 点</td><td>低级怪保底</td></tr>
 *   <tr><td>{@code d = −50}</td><td>0.1×</td><td>玩家比怪低 50 级</td></tr>
 *   <tr><td>{@code d < −50}</td><td>仍为 0.1×</td><td>高级怪封顶保护</td></tr>
 * </table>
 * 低级怪端在 15 级跨度内从 5× 跌到 0（更陡），高级怪端在 45 级跨度内从 5× 跌到 0.1×（更缓）。
 *
 * <p>调用方一般直接使用 {@link #gain(int, int, int)} 获得最终经验值；
 * 如需替换整条曲线，请实现 {@code IExperienceCurve}（见 {@code core.level.api}）。
 *
 * @see ExpFormula
 */
public final class ExperienceGainCurve {

    private ExperienceGainCurve() {
    }

    /** 甜区峰值倍率（玩家与怪物等级接近时的最高加成）。 */
    public static final double PEAK_MULTIPLIER = 5.0;

    /** 甜区半宽：玩家与怪物等级差值绝对值 ≤ 此值时取峰值倍率。 */
    public static final int PEAK_HALF_WIDTH = 5;

    /** 低级怪保底阈值：玩家比怪高 ≥ 此级差时，只给保底经验。 */
    public static final int LOW_MOB_GAP = 20;

    /** 高级怪封顶倍率：玩家比怪低 ≥ {@link #HIGH_MOB_GAP} 时，倍率恒为此值。 */
    public static final double HIGH_MOB_FLOOR = 0.1;

    /** 高级怪封顶阈值：玩家比怪低 ≥ 此级差时，倍率恒为 {@link #HIGH_MOB_FLOOR}。 */
    public static final int HIGH_MOB_GAP = 50;

    /** 低级怪保底经验点数（玩家远高于怪时每次击杀至少获得这么多）。 */
    public static final double LOW_MOB_MIN_EXP = 1.0;

    /**
     * 保底标记。{@link #multiplier} 返回值小于此常量表示"走低级怪保底逻辑"
     *（约定负数返回值代表保底，便于调用方区分"倍率接近 0"与"应给保底 1 点"）。
     */
    public static final double FLOOR_FLAG = 0.0;

    /**
     * 计算等级差倍率 {@code M(playerLevel, mobLevel)}，分段线性、连续。
     * <p>
     * 返回值约定：
     * <ul>
     *   <li>正值：实际倍率，{@code 经验 = baseExp × 倍率}</li>
     *   <li>负值（{@code < FLOOR_FLAG}）：走低级怪保底逻辑（每次击杀至少 1 点经验）</li>
     * </ul>
     * 调用方通常应使用 {@link #gain(int, int, int)} 而非直接处理此约定。
     *
     * @param playerLevel 玩家等级（内部会 clamp 到 ≥ 1）
     * @param mobLevel    怪物等级（内部会 clamp 到 ≥ 1）
     * @return 倍率，或负值表示走保底逻辑
     */
    public static double multiplier(int playerLevel, int mobLevel) {
        int p = Math.max(1, playerLevel);
        int m = Math.max(1, mobLevel);
        int d = p - m; // 玩家 − 怪物

        // 1) 甜区：|d| ≤ 5 → 峰值倍率
        if (Math.abs(d) <= PEAK_HALF_WIDTH) {
            return PEAK_MULTIPLIER;
        }

        if (d > 0) {
            // 2) 怪比玩家低（d > 5）：5× → 在 d=LOW_MOB_GAP 处线性降到 0
            //    超过 LOW_MOB_GAP 返回负值（保底标记）
            if (d >= LOW_MOB_GAP) {
                return FLOOR_FLAG - 1.0;
            }
            double t = (d - PEAK_HALF_WIDTH) / (double) (LOW_MOB_GAP - PEAK_HALF_WIDTH); // 0..1
            return PEAK_MULTIPLIER * (1.0 - t);
        } else {
            // 3) 怪比玩家高（d < -5）：5× → 在 d=-HIGH_MOB_GAP 处线性降到 HIGH_MOB_FLOOR
            //    超过 HIGH_MOB_GAP 恒为 HIGH_MOB_FLOOR
            int ad = -d;
            if (ad >= HIGH_MOB_GAP) {
                return HIGH_MOB_FLOOR;
            }
            double t = (ad - PEAK_HALF_WIDTH) / (double) (HIGH_MOB_GAP - PEAK_HALF_WIDTH); // 0..1
            return PEAK_MULTIPLIER + (HIGH_MOB_FLOOR - PEAK_MULTIPLIER) * t;
        }
    }

    /**
     * 端到端计算击杀获得的经验值：{@code baseExp × 倍率}，含保底与封顶逻辑。
     * <p>
     * 此方法是服务端实际发放经验、客户端预览经验的<b>唯一</b>统一入口。
     *
     * @param playerLevel 玩家等级（内部 clamp 到 ≥ 1）
     * @param mobLevel    怪物等级（内部 clamp 到 ≥ 1）
     * @param baseExp     怪物基础经验（来自配置，≤ 0 表示该怪物不给经验）
     * @return 实际获得的经验值；{@code ≤ 0} 表示不获得经验
     */
    public static int gain(int playerLevel, int mobLevel, int baseExp) {
        if (baseExp <= 0) {
            return 0;
        }
        double m = multiplier(playerLevel, mobLevel);
        if (m < FLOOR_FLAG) {
            // 低级怪保底：每次击杀至少 1 点经验
            return (int) Math.round(LOW_MOB_MIN_EXP);
        }
        return Math.max(1, (int) Math.round(m * baseExp));
    }
}
