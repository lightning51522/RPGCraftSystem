package com.rpgcraft.leveling;

import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.core.level.api.ExperienceCurveManager;
import com.rpgcraft.core.level.api.ILevelCalculator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@link ILevelCalculator} 的默认实现。
 * <p>
 * 击杀经验 = {@code baseExp × 等级差倍率 × (1 + 经验加成%)}，其中：
 * <ul>
 *   <li><b>等级差倍率</b>：由 {@link ExperienceCurveManager} 当前生效的曲线决定
 *       （默认 {@link com.rpgcraft.core.level.ExperienceGainCurve} 的分段线性公式，
 *       可通过 {@link ExperienceCurveManager#setCurve} 替换）。</li>
 *   <li><b>经验加成%</b>：读取玩家 {@code exp_bonus} 属性
 *       （由 {@code attributes} 模块注册，装备/职业可通过属性管道叠加），默认 0。
 *       属性未注册时优雅降级为 0。</li>
 * </ul>
 *
 * <h2>等级差倍率锚点（默认曲线）</h2>
 * 设 {@code d = 玩家等级 − 怪物等级}：
 * <table border="1">
 *   <tr><th>差值 d</th><th>倍率</th></tr>
 *   <tr><td>{@code |d| ≤ 5}</td><td>5.0×（峰值甜区）</td></tr>
 *   <tr><td>{@code d ≥ +20}</td><td>保底 1 点经验（低级怪）</td></tr>
 *   <tr><td>{@code d = −50}</td><td>0.1×</td></tr>
 *   <tr><td>{@code d ≤ −50}</td><td>恒 0.1×（高级怪封顶保护）</td></tr>
 * </table>
 * 低级怪端在 15 级跨度内从 5× 线性跌到 0（更陡），高级怪端在 45 级跨度内从 5× 线性跌到 0.1×（更缓）。
 *
 * @see com.rpgcraft.core.level.ExperienceGainCurve
 * @see ExperienceCurveManager
 */
public class DefaultLevelCalculator implements ILevelCalculator {

    /**
     * 经验加成属性 ID。
     * <p>
     * 遵循"插件互不依赖"铁律：{@code leveling} 不依赖 {@code attributes} 模块，
     * 此处声明与 {@code DefaultAttributes.EXP_BONUS_ID} 字面量一致的本地常量形成松耦合契约。
     * 属性未注册（{@code attributes} 模块未加载）时读取快照返回 null，加成自动降级为 0。
     */
    static final Identifier EXP_BONUS_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "exp_bonus");

    @Override
    public int calculateExperienceGain(ServerPlayer killer, LivingEntity victim,
                                       int mobLevel, int baseExp) {
        PlayerLevelData levelData = killer.getData(LevelManager.PLAYER_LEVEL);
        int playerLevel = Math.max(1, levelData.getLevel());

        // 1) 等级差倍率（曲线可整体替换）
        double multiplier = ExperienceCurveManager.getCurve().multiplier(playerLevel, mobLevel);

        // 2) exp_bonus 属性加成（百分比，默认 0）
        double expBonusPercent = readExpBonus(killer);

        if (multiplier < 0) {
            // 低级怪保底：每次击杀至少 1 点经验（exp_bonus 仍可放大，但不低于 1）
            int floored = (int) Math.round(1.0 * (1 + expBonusPercent));
            return Math.max(1, floored);
        }
        if (baseExp <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(baseExp * multiplier * (1 + expBonusPercent)));
    }

    /**
     * 读取玩家的经验加成属性（整数十分比），未注册时返回 0。
     */
    private static double readExpBonus(ServerPlayer player) {
        AttributeSnapshot snapshot = AttributeSnapshotManager.getSnapshot(player);
        AttributeSnapshot.AttributeData data = snapshot.get(EXP_BONUS_ID);
        int bonus = (data != null) ? data.currentValue() : 0;
        return bonus / 100.0;
    }
}
