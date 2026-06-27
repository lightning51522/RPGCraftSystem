package com.rpgcraft.profession;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeModifier;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 职业属性加成应用器（从 {@link ProfessionManager} 抽离）。
 * <p>
 * 集中职责：按等级线性计算并应用/移除职业的属性加成。修饰符通过 sourceId 区分
 * 主职业（{@link #MAIN_PREFIX}）与副职业（{@link #SECONDARY_PREFIX}），互不冲突。
 * <p>
 * <h3>应用语义</h3>
 * 「重新应用」= 先移除该职业在该前缀下的全部修饰符，再按指定等级添加。
 * 移除只看 sourceId（与等级无关），故 {@link #applyBonusAtLevel} 的 {@code add=false}
 * 分支用任意等级（这里固定 1）即可。
 *
 * @apiNote 包私有；对外由 {@link ProfessionManager} 委托调用，保持门面 API 稳定。
 */
final class ProfessionBonusApplier {

    /** 修饰符来源前缀：主职业 */
    static final String MAIN_PREFIX = "rpgcraftprofession";
    /** 修饰符来源前缀：副职业 */
    static final String SECONDARY_PREFIX = "rpgcraftprofession_sec";

    private ProfessionBonusApplier() {
    }

    /** 计算某属性加成对应的修饰符 sourceId（区分主/副职业前缀）。 */
    private static Identifier modifierSourceId(Identifier attrId, boolean secondary) {
        String prefix = secondary ? SECONDARY_PREFIX : MAIN_PREFIX;
        return Identifier.fromNamespaceAndPath(prefix,
                "bonus_" + attrId.getNamespace() + "_" + attrId.getPath());
    }

    /**
     * 按指定等级应用或移除某职业的全部属性加成。
     *
     * @param secondary true 时用副职业前缀的修饰符 sourceId，false 时用主职业前缀
     *                  （由调用方显式传入，多副职业共存后某职业是否"副职业"需由调用上下文决定）
     */
    static void applyBonusAtLevel(ServerPlayer player, IProfession prof, int level, boolean add,
                                  boolean secondary) {
        if (level < 1) level = 1;
        for (Map.Entry<Identifier, Integer> entry : prof.getBaseBonusMap().entrySet()) {
            Identifier attrId = entry.getKey();
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(attrId);
            if (attrEntry == null) continue;
            IAttribute attr = player.getData(attrEntry.getSupplier());
            Identifier sourceId = modifierSourceId(attrId, secondary);
            if (add) {
                attr.addModifier(AttributeModifier.of(sourceId, Operation.ADDITION,
                        prof.getBonusAtLevel(attrId, level)));
            } else {
                attr.removeModifier(sourceId);
            }
        }
    }

    /**
     * 重新应用单个职业的加成（先移除再按等级添加）。
     * <p>
     * 参数化合并了原本三处近乎重复的 {@code reapplyMain/Secondary/Bonus} 实现：调用方传入
     * 前缀标志（{@code secondary}），方法体内统一执行"移除(1)→添加(level)"。
     *
     * @param prof      职业
     * @param level     按此等级添加（移除时与等级无关）
     * @param secondary true=副职业前缀，false=主职业前缀
     */
    private static void reapply(ServerPlayer player, IProfession prof, int level, boolean secondary) {
        // 移除（用任意等级，remove 只看 sourceId）
        applyBonusAtLevel(player, prof, 1, false, secondary);
        applyBonusAtLevel(player, prof, level, true, secondary);
    }

    /** 重新应用当前主职业加成。用于等级变化、登录重建。 */
    static void reapplyMain(ServerPlayer player) {
        ProfessionData data = ProfessionManager.getData(player);
        IProfession prof = ProfessionManager.getRegistry().getProfession(data.getProfessionId());
        if (prof == null) return;
        reapply(player, prof, data.getProfessionLevel(prof.getId()), false);
    }

    /** 重新应用指定副职业的加成。用于该副职业等级变化。 */
    static void reapplySecondary(ServerPlayer player, Identifier secondaryId) {
        ProfessionData data = ProfessionManager.getData(player);
        if (!data.isSecondaryActive(secondaryId)) return;
        IProfession prof = ProfessionManager.getRegistry().getProfession(secondaryId);
        if (prof == null) return;
        reapply(player, prof, data.getProfessionLevel(secondaryId), true);
    }

    /** 重新应用所有已激活副职业的加成。用于登录/重生重建（所有修饰符都已重置）。 */
    static void reapplyAllSecondary(ServerPlayer player) {
        ProfessionData data = ProfessionManager.getData(player);
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession prof = ProfessionManager.getRegistry().getProfession(secId);
            if (prof == null) continue;
            reapply(player, prof, data.getProfessionLevel(secId), true);
        }
    }

    /** 重新应用所有生效加成（主 + 全部已激活副）。 */
    static void reapplyAll(ServerPlayer player) {
        reapplyMain(player);
        reapplyAllSecondary(player);
    }

    /** 移除当前主职业加成（兼容旧 API {@code removeProfessionBonuses}）。 */
    static void removeMain(ServerPlayer player) {
        ProfessionData data = ProfessionManager.getData(player);
        IProfession prof = ProfessionManager.getRegistry().getProfession(data.getProfessionId());
        if (prof != null) applyBonusAtLevel(player, prof, 1, false, false);
    }
}
