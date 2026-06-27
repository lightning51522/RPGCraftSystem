package com.rpgcraft.attributes.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.Element;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import org.jspecify.annotations.Nullable;

/**
 * 伤害分类器：从 {@link DamageSource} 解析出 RPG 攻击类型与元素标签。
 * <p>
 * 从 {@link CombatEventHandler#onLivingDamagePre} 抽离的分类逻辑，使该事件处理器收缩为
 * 「分类 → 计算 → 应用 → 同步 → 日志」的线性流程。分类规则独立、单一职责，便于阅读与扩展。
 *
 * <h3>分类顺序</h3>
 * <ol>
 *   <li>默认 {@link AttackType#PHYSICAL} / {@link Element#NONE}</li>
 *   <li>按直接实体类型判定：
 *     <ul>
 *       <li>{@link AbstractArrow}：药箭（颜色 ≠ -1）→ {@link AttackType#MIX_TYPE}，否则 PHYSICAL</li>
 *       <li>{@link Player}：经 {@code IAttackTypeResolver}/{@code IElementResolver} 按武器 ID 解析</li>
 *       <li>其它生物：优先用 mob 配置覆盖，否则按实体类型 ID 查 mob 配置，缺失回退 PHYSICAL</li>
 *     </ul>
 *   </li>
 *   <li>魔法/药水伤害类型强制覆盖为 {@link AttackType#MAGIC}（含药水、凋零、龙息、声波等）</li>
 * </ol>
 * 元素标签仅玩家武器路径通过 {@code IElementResolver} 解析，其它路径保持 NONE
 * （未来可从 mob 配置扩展）。
 *
 * @see CombatEventHandler#onLivingDamagePre
 */
public final class DamageClassifier {

    private DamageClassifier() {
    }

    /**
     * 对一次伤害进行分类。
     *
     * @param source         伤害来源
     * @param attackerEntity 攻击实体（{@code source.getEntity()}），可为 null（环境伤害）
     * @return 分类结果（攻击类型 + 元素标签）
     */
    public static DamageClassification classify(DamageSource source, @Nullable Entity attackerEntity) {
        AttackType attackType = AttackType.PHYSICAL;
        Element element = Element.NONE;

        if (attackerEntity instanceof LivingEntity attacker) {
            if (source.getDirectEntity() instanceof AbstractArrow) {
                // Arrow 为药箭/普通箭，通过颜色判断是否有药水效果（-1 = 无效果）
                if (source.getDirectEntity() instanceof Arrow arrow && arrow.getColor() != -1) {
                    attackType = AttackType.MIX_TYPE; // 有药水效果的箭 → 混合伤害
                } else {
                    attackType = AttackType.PHYSICAL; // 普通箭/光灵箭 → 物理伤害
                }
            } else if (attacker instanceof Player player) {
                Identifier weaponId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
                attackType = RPGSystems.getAttackTypeResolver().resolve(weaponId);
                element = RPGSystems.getElementResolver().resolve(weaponId);
            } else {
                MobLevelData attackerLevelData = RPGSystems.getMobDataProvider().getMobLevelData(attacker);
                if (attackerLevelData.hasAttackTypeOverride()) {
                    attackType = attackerLevelData.getAttackTypeOverride();
                } else {
                    Identifier attackerTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
                    attackType = RPGSystems.getMobDataProvider().getConfig(attackerTypeId)
                            .map(MobAttributeConfig.MobAttributes::attackType)
                            .orElse(AttackType.PHYSICAL);
                }
            }
        }

        // 药水/魔法伤害强制使用 MAGIC 攻击类型
        // 包括：瞬间伤害药水(INDIRECT_MAGIC)、中毒/唤魔者尖牙(MAGIC)、
        //       凋零(WITHER)、龙息(DRAGON_BREATH)、监守者声波(SONIC_BOOM)
        if (source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.WITHER)
                || source.is(DamageTypes.DRAGON_BREATH)
                || source.is(DamageTypes.SONIC_BOOM)) {
            attackType = AttackType.MAGIC;
        }

        return new DamageClassification(attackType, element);
    }

    /**
     * 伤害分类结果：攻击类型 + 元素标签。
     *
     * @param attackType 攻击类型
     * @param element    元素标签（与攻击类型正交）
     */
    public record DamageClassification(AttackType attackType, Element element) {
    }
}
