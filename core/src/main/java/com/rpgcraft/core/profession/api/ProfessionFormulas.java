package com.rpgcraft.core.profession.api;

import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 综合属性派生公式解析工具（核心逻辑，跨模块复用）。
 * <p>
 * 封装「解出实体的活跃主职业 → 调用职业公式 → 回退默认公式」的解析链。
 * 战斗计算器（attributes 模块）和客户端 UI（client 模块）通过本工具
 * 统一访问综合属性（物理攻击/魔法攻击/物理防御），避免散落
 * {@code hasProfessionSystem / getProfession / instanceof ServerPlayer}
 * 样板代码。
 * <p>
 * <b>解析规则：</b>
 * <ul>
 *   <li>实体为 {@link ServerPlayer} 且职业模块已注册 → 调用当前主职业的
 *       {@link IProfession#computePhysicalAttack compute} 系列方法</li>
 *   <li>实体非玩家 或 职业模块未加载 → 回退到 {@link IProfession} 接口默认公式
 *       （力量×2+智力 等）</li>
 * </ul>
 * 主职业由 {@link RPGSystems#getProfessionSystem()}.{@code getProfession(player)} 获取，
 * 其类型满足 {@link IProfession.ProfessionType#isMainLike()} —— 副职业不参与综合属性派生。
 *
 * @see IProfession#computePhysicalAttack
 * @see IProfession#computeMagicalAttack
 * @see IProfession#computePhysicalDefense
 */
public final class ProfessionFormulas {

    private ProfessionFormulas() {
    }

    /**
     * 解出实体的活跃主职业。
     *
     * @param entity 攻击/防御实体
     * @return 当前主职业实例，若实体非玩家或职业系统未注册则返回 null
     */
    private static IProfession resolveMainProfession(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return null;
        if (!RPGSystems.hasProfessionSystem()) return null;
        return RPGSystems.getProfessionSystem().getProfession(player);
    }

    // ==================================================================
    // 综合属性查询（对外 API）
    // ==================================================================

    /**
     * 计算实体的物理攻击力。
     * <p>
     * 玩家：由当前主职业的 {@link IProfession#computePhysicalAttack} 派生；
     * 非玩家 或 无职业模块：回退默认公式 {@code 力量×2 + 智力}。
     *
     * @param entity       攻击实体
     * @param strength     力量属性当前值（管线最终值）
     * @param intelligence 智力属性当前值
     * @return 物理攻击力
     */
    public static int physicalAttack(LivingEntity entity, int strength, int intelligence) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) {
            return prof.computePhysicalAttack(strength, intelligence);
        }
        // fallback：IProfession 接口的默认公式
        return strength * 2 + intelligence;
    }

    /**
     * 计算实体的魔法攻击力。
     * <p>
     * 玩家：由当前主职业的 {@link IProfession#computeMagicalAttack} 派生；
     * 非玩家 或 无职业模块：回退默认公式 {@code 智力×2 + 力量}。
     *
     * @param entity       攻击实体
     * @param strength     力量属性当前值
     * @param intelligence 智力属性当前值
     * @return 魔法攻击力
     */
    public static int magicalAttack(LivingEntity entity, int strength, int intelligence) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) {
            return prof.computeMagicalAttack(strength, intelligence);
        }
        return intelligence * 2 + strength;
    }

    /**
     * 计算实体的物理防御力。
     * <p>
     * 玩家：由当前主职业的 {@link IProfession#computePhysicalDefense} 派生；
     * 非玩家 或 无职业模块：回退默认公式 {@code 力量×2}。
     * <p>
     * 魔法防御力不从此方法获得（魔法防御仅来自装备，无属性派生）。
     *
     * @param entity       防御实体
     * @param strength     力量属性当前值
     * @param intelligence 智力属性当前值
     * @return 物理防御力
     */
    public static int physicalDefense(LivingEntity entity, int strength, int intelligence) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) {
            return prof.computePhysicalDefense(strength, intelligence);
        }
        return strength * 2;
    }
}
