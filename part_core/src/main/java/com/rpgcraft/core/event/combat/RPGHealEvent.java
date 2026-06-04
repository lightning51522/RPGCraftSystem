package com.rpgcraft.core.event.combat;

import com.rpgcraft.core.event.RPGEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * RPG 治疗事件
 * <p>
 * 在治疗流程中的关键节点发射，允许子模块拦截和修改治疗效果。
 * <p>
 * <h3>Pre 事件</h3>
 * 在治疗应用到 LIFE 属性之前发射。子模块可以：
 * <ul>
 *   <li>取消治疗（禁疗 debuff、反治疗区域）— 调用 {@link #cancel()}</li>
 *   <li>修改治疗量 — 调用 {@link Pre#setHealAmount(int)}</li>
 *   <li>修改治疗来源 — 调用 {@link Pre#setHealSource(HealSource)}</li>
 * </ul>
 * <p>
 * <h3>Post 事件</h3>
 * 在治疗应用到 LIFE 属性之后发射。子模块可以：
 * <ul>
 *   <li>触发附加效果（过量治疗转化护盾、治疗增益 buff）</li>
 *   <li>记录治疗统计</li>
 * </ul>
 * <p>
 * <h3>治疗流程</h3>
 * <pre>
 * CombatEventHandler.onLivingHeal() / healEntity():
 *   1. RPGHealEvent.Pre  → 子模块可取消/修改治疗量
 *   2. 应用治疗到 LIFE
 *   3. RPGHealEvent.Post → 子模块追加效果
 * </pre>
 */
public class RPGHealEvent {

    /**
     * 治疗前事件
     * <p>
     * 在治疗应用到 LIFE 属性之前发射。可取消，可修改治疗量和治疗来源。
     * <p>
     * 使用场景：
     * <ul>
     *   <li>禁疗 — debuff 完全取消治疗</li>
     *   <li>治疗减免 — 减少一定比例的治疗量</li>
     *   <li>治疗加成 — 增加治疗量</li>
     *   <li>治疗来源转换 — 将原版治疗标记为自定义类型以触发特殊逻辑</li>
     * </ul>
     */
    public static class Pre extends RPGEvent {

        /** 治疗者，null 表示无来源（如自然回复、原版药水） */
        @Nullable
        private final LivingEntity healer;

        /** 被治疗目标 */
        private final LivingEntity target;

        /** 治疗来源类型（可被修改） */
        private HealSource healSource;

        /** 治疗量（可被修改） */
        private int healAmount;

        /**
         * @param healer     治疗者（null = 无来源，如自然回复）
         * @param target     被治疗目标
         * @param healSource 治疗来源类型
         * @param healAmount 治疗量（自定义生命值的扁平值）
         */
        public Pre(@Nullable LivingEntity healer, LivingEntity target,
                   HealSource healSource, int healAmount) {
            this.healer = healer;
            this.target = target;
            this.healSource = healSource;
            this.healAmount = healAmount;
        }

        /** 获取治疗者（null = 无来源，如自然回复） */
        @Nullable
        public LivingEntity getHealer() {
            return healer;
        }

        /** 获取被治疗目标 */
        public LivingEntity getTarget() {
            return target;
        }

        /** 获取治疗来源类型 */
        public HealSource getHealSource() {
            return healSource;
        }

        /** 修改治疗来源类型 */
        public void setHealSource(HealSource healSource) {
            this.healSource = healSource;
        }

        /** 获取当前治疗量 */
        public int getHealAmount() {
            return healAmount;
        }

        /** 修改治疗量（如治疗加成/减免），自动钳制到 ≥ 0 */
        public void setHealAmount(int healAmount) {
            this.healAmount = Math.max(0, healAmount);
        }

        /** 是否为原版来源的治疗 */
        public boolean isVanilla() {
            return healSource == HealSource.VANILLA;
        }
    }

    /**
     * 治疗后事件
     * <p>
     * 在治疗已应用到目标的 LIFE 属性之后发射。不可取消（治疗已生效）。
     * <p>
     * 使用场景：
     * <ul>
     *   <li>过量治疗转化 — 将超出上限的治疗量转为护盾或其他资源</li>
     *   <li>治疗触发 buff — 治疗时为目标附加增益效果</li>
     *   <li>治疗统计 — 记录治疗量数据</li>
     * </ul>
     */
    public static class Post extends RPGEvent {

        /** 治疗者，null 表示无来源 */
        @Nullable
        private final LivingEntity healer;

        /** 被治疗目标 */
        private final LivingEntity target;

        /** 治疗来源类型 */
        private final HealSource healSource;

        /** 实际治疗量（经过上限钳制后的真实值） */
        private final int actualHealed;

        /**
         * @param healer       治疗者（null = 无来源）
         * @param target       被治疗目标
         * @param healSource   治疗来源类型
         * @param actualHealed 实际治疗量（经过 maxValue 钳制后的真实增量）
         */
        public Post(@Nullable LivingEntity healer, LivingEntity target,
                    HealSource healSource, int actualHealed) {
            this.healer = healer;
            this.target = target;
            this.healSource = healSource;
            this.actualHealed = actualHealed;
        }

        /** 获取治疗者（null = 无来源） */
        @Nullable
        public LivingEntity getHealer() {
            return healer;
        }

        /** 获取被治疗目标 */
        public LivingEntity getTarget() {
            return target;
        }

        /** 获取治疗来源类型 */
        public HealSource getHealSource() {
            return healSource;
        }

        /** 获取实际治疗量（经过上限钳制后的真实增量） */
        public int getActualHealed() {
            return actualHealed;
        }

        /** 是否为原版来源的治疗 */
        public boolean isVanilla() {
            return healSource == HealSource.VANILLA;
        }
    }
}
