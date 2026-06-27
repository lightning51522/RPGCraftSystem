package com.rpgcraft.core.attribute.api;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.Element;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * 属性伤害计算接口
 * <p>
 * 定义基于 RPG 属性的伤害减免和输出计算公式。默认实现由 combat 模块提供
 *（{@code com.rpgcraft.combat.DefaultDamageCalculator}）。
 * <p>
 * 其他模组可以替换此实现来提供更复杂的伤害公式（元素克制、装备加成等），
 * 通过 {@link com.rpgcraft.core.attribute.AttributeManager#setDamageCalculator(IDamageCalculator)} 注入。
 * <p>
 * <h3>伤害减免层次（Incoming）</h3>
 * <ol>
 *   <li><b>基础减伤</b>：由 {@link AttackType} 决定（物理=防御力减法、魔法=法抗百分比、混合=两者）</li>
 *   <li><b>元素减伤</b>：在基础减伤<b>之后</b>，若攻击带元素标签（{@link Element#isNone()} 为 false），
 *       额外乘以 {@code (1 - 对应元素抗性/100)}</li>
 * </ol>
 * <h3>伤害输出层次（Outgoing）</h3>
 * <ol>
 *   <li><b>基础输出</b>：由 {@link AttackType} 决定（力量/智力派生 + 暴击 + 固定伤害）</li>
 *   <li><b>元素增伤</b>：在基础输出<b>之后</b>，若攻击带元素标签（{@link Element#isNone()} 为 false），
 *       额外乘以 {@code 对应元素伤害加成/1000}（千分制倍率，1000 = 基准不变）</li>
 * </ol>
 * 旧签名（无 element 参数）默认元素为 {@link Element#NONE}（不触发元素层），
 * 保留向后兼容。
 */
public interface IDamageCalculator {

    /**
     * 计算实体受到伤害后的最终数值（减免后）
     *
     * @param target         受击实体
     * @param originalDamage 原始伤害（减免前）
     * @param type           伤害类型
     * @return 减免后的最终伤害（不低于 0）
     */
    int calculateIncomingDamage(LivingEntity target, int originalDamage, AttackType type);

    /**
     * 计算承伤（含攻击方穿透属性）
     * <p>
     * 默认实现忽略攻击方，委托给 {@link #calculateIncomingDamage(LivingEntity, int, AttackType)}。
     * combat 模块的默认实现会读取攻击方的物理/法术穿透属性来降低目标的有效防御/法抗。
     *
     * @param target         受击实体
     * @param originalDamage 原始伤害（减免前）
     * @param type           伤害类型
     * @param attacker       攻击方实体（null 时无穿透效果）
     * @return 减免后的最终伤害（不低于 0）
     */
    default int calculateIncomingDamage(LivingEntity target, int originalDamage,
                                         AttackType type, @Nullable LivingEntity attacker) {
        return calculateIncomingDamage(target, originalDamage, type);
    }

    /**
     * 计算承伤（含攻击方穿透属性 + 攻击元素标签）
     * <p>
     * 默认实现忽略元素，委托给 {@link #calculateIncomingDamage(LivingEntity, int, AttackType, LivingEntity)}。
     * combat 模块的默认实现会在基础减伤之后额外应用元素减伤层
     *（{@code damage × (1 - 对应元素抗性/100)}，仅当 {@code element} 非 NONE 时）。
     * <p>
     * 第三方实现应覆写此方法以纳入元素减伤，或在旧实现下保持 NONE 元素（不触发）。
     *
     * @param target         受击实体
     * @param originalDamage 原始伤害（减免前）
     * @param type           伤害类型
     * @param element        攻击元素标签（{@link Element#NONE} 时跳过元素减伤层）
     * @param attacker       攻击方实体（null 时无穿透效果）
     * @return 减免后的最终伤害（不低于 0）
     */
    default int calculateIncomingDamage(LivingEntity target, int originalDamage,
                                         AttackType type, Element element,
                                         @Nullable LivingEntity attacker) {
        return calculateIncomingDamage(target, originalDamage, type, attacker);
    }

    /**
     * 计算实体造成的伤害数值（含暴击）
     *
     * @param attacker 攻击方实体
     * @param type     攻击类型
     * @return 计算后的伤害数值
     */
    int calculateOutgoingDamage(LivingEntity attacker, AttackType type);

    /**
     * 计算实体造成的伤害数值（含暴击 + 攻击元素标签增伤）
     * <p>
     * 默认实现忽略元素，委托给 {@link #calculateOutgoingDamage(LivingEntity, AttackType)}。
     * combat 模块的默认实现会在基础输出之后额外应用元素增伤层
     *（{@code damage × 对应元素伤害加成/1000}，仅当 {@code element} 非 NONE 时）。
     * <p>
     * 第三方实现应覆写此方法以纳入元素增伤，或在旧实现下保持 NONE 元素（不触发）。
     *
     * @param attacker 攻击方实体
     * @param type     攻击类型
     * @param element  攻击元素标签（{@link Element#NONE} 时跳过元素增伤层）
     * @return 计算后的伤害数值（不低于 0）
     */
    default int calculateOutgoingDamage(LivingEntity attacker, AttackType type, Element element) {
        return calculateOutgoingDamage(attacker, type);
    }
}
