package com.rpgcraft.core.registry;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.combat.MobRating;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 战斗系统接口
 * <p>
 * 由战斗模块注册实现，提供治疗、怪物初始化等公共 API。
 * 供其他模块（如药水效果、技能系统）调用战斗相关功能。
 *
 * @see RPGSystems#registerCombatSystem(ICombatSystem)
 * @see RPGSystems#getCombatSystem()
 */
public interface ICombatSystem {

    /**
     * 自定义治疗公共 API
     * <p>
     * 发射 {@link com.rpgcraft.core.event.combat.RPGHealEvent.Pre} 和
     * {@link com.rpgcraft.core.event.combat.RPGHealEvent.Post}，
     * 子模块可拦截、修改或追加治疗效果。
     *
     * @param target     接受治疗的实体
     * @param healAmount 治疗量（自定义生命值，扁平值）
     * @param healer     治疗者（null 表示无来源，如技能自身回复）
     * @return 实际治疗量（经过事件修改和上限钳制后的值）
     */
    int healEntity(LivingEntity target, int healAmount, @Nullable LivingEntity healer);

    /**
     * 初始化怪物自定义属性
     * <p>
     * 查询 {@link com.rpgcraft.core.attribute.MobAttributeConfig} 获取基础属性值，
     * 通过 {@link com.rpgcraft.core.level.api.IMobAttributeScaler} 按等级缩放，
     * 设置 vanilla MAX_HEALTH 和自定义属性附件。
     *
     * @param entity      目标生物实体
     * @param targetLevel 目标等级（≥ 1）
     */
    void initializeMobAttributes(LivingEntity entity, int targetLevel);

    /**
     * 初始化怪物自定义属性，支持 JSON 属性覆盖和评级倍率
     * <p>
     * 供 {@code /rpg spawn <entity> <level> {json}} 指令调用。
     * <ul>
     *   <li>{@code overrides} 中的属性值跳过等级缩放直接使用</li>
     *   <li>不在 overrides 中的属性使用配置默认值 + 等级缩放</li>
     *   <li>{@code attackTypeOverride} 非空时覆盖配置的攻击类型</li>
     *   <li>所有属性值最后乘以 {@code rating.getMultiplier()}</li>
     * </ul>
     *
     * @param entity              目标生物实体
     * @param targetLevel         目标等级（≥ 1）
     * @param overrides           属性覆盖映射（key 为属性名如 "life"，value 为覆盖值）
     * @param attackTypeOverride  攻击类型覆盖，null 表示使用配置值
     * @param rating              怪物评级，决定最终属性倍率
     */
    void initializeMobAttributesCustom(LivingEntity entity, int targetLevel,
                                       Map<String, Integer> overrides,
                                       @Nullable AttackType attackTypeOverride,
                                       MobRating rating);
}
