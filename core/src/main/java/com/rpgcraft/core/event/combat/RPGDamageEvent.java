package com.rpgcraft.core.event.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.Element;
import com.rpgcraft.core.event.RPGEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * RPG 伤害事件
 * <p>
 * 在伤害流程中的关键节点发射，允许子模块拦截和修改伤害计算。
 * <p>
 * <h3>Pre 事件</h3>
 * 在伤害计算之前发射。子模块可以：
 * <ul>
 *   <li>取消伤害（闪避、无敌、免疫）— 调用 {@link #cancel()}</li>
 *   <li>修改伤害值 — 调用 {@link Pre#setDamage(int)}</li>
 *   <li>修改攻击类型 — 调用 {@link Pre#setAttackType(AttackType)}</li>
 * </ul>
 * <p>
 * <h3>Post 事件</h3>
 * 在伤害应用到 LIFE 属性之后发射。子模块可以：
 * <ul>
 *   <li>触发附加效果（吸血、反伤、连击触发）</li>
 *   <li>记录伤害统计</li>
 * </ul>
 * <p>
 * <h3>伤害流程</h3>
 * <pre>
 * CombatEventHandler.onLivingDamagePre():
 *   1. RPGDamageEvent.Pre  → 子模块可取消/修改
 *   2. IDamageCalculator   → 公式计算（策略模式）
 *   3. 应用伤害到 LIFE
 *   4. RPGDamageEvent.Post → 子模块追加效果
 * </pre>
 */
public class RPGDamageEvent {

    /**
     * 伤害前事件
     * <p>
     * 在伤害计算之前发射。可取消，可修改伤害值和攻击类型。
     * <p>
     * 使用场景：
     * <ul>
     *   <li>闪避 — 一定概率取消伤害</li>
     *   <li>无敌/免疫 — 完全取消伤害</li>
     *   <li>伤害修改 — 护盾吸收、元素克制加成</li>
     *   <li>攻击类型转换 — 将物理伤害转为魔法伤害</li>
     * </ul>
     */
    public static class Pre extends RPGEvent {

        /** 攻击者，null 表示环境伤害 */
        @Nullable
        private final LivingEntity attacker;

        /** 被攻击目标 */
        private final LivingEntity target;

        /** 伤害来源类型（可被修改） */
        private AttackType attackType;

        /** 攻击元素标签（可被修改，默认 NONE 不触发元素减伤层） */
        private Element element;

        /** 原始伤害值（计算前，可被修改） */
        private int damage;

        /**
         * @param attacker  攻击者（null = 环境伤害）
         * @param target    被攻击目标
         * @param attackType 攻击类型
         * @param element   攻击元素标签（{@link Element#NONE} 表示无元素）
         * @param damage    原始伤害值（对环境伤害为 vanilla 值，对战斗伤害为公式前的初始值）
         */
        public Pre(@Nullable LivingEntity attacker, LivingEntity target,
                   AttackType attackType, Element element, int damage) {
            this.attacker = attacker;
            this.target = target;
            this.attackType = attackType;
            this.element = element;
            this.damage = damage;
        }

        /** 获取攻击者（null = 环境伤害，如摔落、溺水） */
        @Nullable
        public LivingEntity getAttacker() {
            return attacker;
        }

        /** 获取被攻击目标 */
        public LivingEntity getTarget() {
            return target;
        }

        /** 获取攻击类型 */
        public AttackType getAttackType() {
            return attackType;
        }

        /** 修改攻击类型（如将物理伤害转为魔法伤害） */
        public void setAttackType(AttackType attackType) {
            this.attackType = attackType;
        }

        /** 获取攻击元素标签（NONE 表示无元素，不触发元素减伤层） */
        public Element getElement() {
            return element;
        }

        /** 修改攻击元素标签（如将无元素攻击转为火元素攻击） */
        public void setElement(Element element) {
            this.element = element != null ? element : Element.NONE;
        }

        /** 获取当前伤害值 */
        public int getDamage() {
            return damage;
        }

        /** 修改伤害值（如护盾吸收减少伤害） */
        public void setDamage(int damage) {
            this.damage = Math.max(0, damage);
        }

        /** 是否为环境伤害（无攻击者） */
        public boolean isEnvironmental() {
            return attacker == null;
        }
    }

    /**
     * 伤害后事件
     * <p>
     * 在伤害已应用到目标的 LIFE 属性之后发射。不可取消（伤害已生效）。
     * <p>
     * 使用场景：
     * <ul>
     *   <li>吸血 — 攻击者回复造成伤害的一定比例</li>
     *   <li>反伤 — 目标对攻击者造成反弹伤害</li>
     *   <li>连击触发 — 一定概率触发额外攻击</li>
     *   <li>伤害日志/统计</li>
     * </ul>
     */
    public static class Post extends RPGEvent {

        /** 攻击者，null 表示环境伤害 */
        @Nullable
        private final LivingEntity attacker;

        /** 被攻击目标 */
        private final LivingEntity target;

        /** 实际造成的伤害值（已应用减伤后的最终值） */
        private final int damageDealt;

        /** 攻击类型 */
        private final AttackType attackType;

        /** 攻击元素标签（供子模块判断元素附着/统计等效果） */
        private final Element element;

        /** 目标是否因此次伤害死亡（LIFE 降到 0） */
        private final boolean lethal;

        /**
         * @param attacker   攻击者（null = 环境伤害）
         * @param target     被攻击目标
         * @param damageDealt 实际造成的伤害值
         * @param attackType  攻击类型
         * @param element    攻击元素标签（{@link Element#NONE} 表示无元素）
         * @param lethal     是否为致命伤害
         */
        public Post(@Nullable LivingEntity attacker, LivingEntity target,
                    int damageDealt, AttackType attackType, Element element, boolean lethal) {
            this.attacker = attacker;
            this.target = target;
            this.damageDealt = damageDealt;
            this.attackType = attackType;
            this.element = element;
            this.lethal = lethal;
        }

        /** 获取攻击者（null = 环境伤害） */
        @Nullable
        public LivingEntity getAttacker() {
            return attacker;
        }

        /** 获取被攻击目标 */
        public LivingEntity getTarget() {
            return target;
        }

        /** 获取实际造成的伤害值 */
        public int getDamageDealt() {
            return damageDealt;
        }

        /** 获取攻击类型 */
        public AttackType getAttackType() {
            return attackType;
        }

        /** 获取攻击元素标签（NONE 表示无元素） */
        public Element getElement() {
            return element;
        }

        /** 是否为致命伤害（目标 LIFE 将降到 0） */
        public boolean isLethal() {
            return lethal;
        }

        /** 是否为环境伤害 */
        public boolean isEnvironmental() {
            return attacker == null;
        }
    }
}
