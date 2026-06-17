package com.rpgcraft.profession;

import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 职业登录处理
 * <p>
 * 两项职责：
 * <ol>
 *   <li><b>存档数据校验/修复</b>：旧存档迁移或 datapack 变更可能导致玩家的主/副职业引用
 *       失效或违反新的类型规则（例如旧版本下任意职业都能做副职业，新版本要求副职业必须
 *       是 {@link IProfession.ProfessionType#SECONDARY}）。登录时检测并修复：失效的引用
 *       置 null 并打 WARN，避免加成应用逻辑抛 NPE。</li>
 *   <li><b>加成重建</b>：职业加成修饰符不参与序列化（{@code EntityAttribute.CODEC} 只存计算后
 *       的值），重新登录后从当前主/副职业等级重新应用全部加成。</li>
 * </ol>
 * 与 {@code com.rpgcraft.attributepoints.AttributePointsLoginEventHandler} 对称。
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionLoginEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        validateAndRepairProfessionData(player);
        ProfessionManager.reapplyAllBonuses(player);
        ProfessionManager.syncToClient(player);
    }

    /**
     * 校验并修复玩家职业附件数据。
     * <p>
     * 修复规则：
     * <ul>
     *   <li>主职业若不存在或非 PRIMARY（极端情况）→ 回退为 commoner（兜底必然存在）</li>
     *   <li>副职业若不存在、或非 SECONDARY 类型、或等于主职业 → 置 null（旧版本下任意职业可做副职业，
     *       新版本严格要求 SECONDARY；以及 apprentice 占位被真实副职业替换后旧引用需清理）</li>
     * </ul>
     */
    private static void validateAndRepairProfessionData(ServerPlayer player) {
        ProfessionData data = ProfessionManager.getData(player);
        IProfessionRegistry registry = ProfessionManager.getRegistry();

        // 主职业校验
        Identifier mainId = data.getProfessionId();
        IProfession main = registry.getProfession(mainId);
        if (main == null || main.getType() != IProfession.ProfessionType.PRIMARY) {
            ProfessionMod.LOGGER.warn("玩家 {} 的主职业 {} 失效或类型异常，回退为 commoner",
                    player.getName().getString(), mainId);
            data.setProfessionId(ProfessionManager.COMMONER_ID);
            data.unlock(ProfessionManager.COMMONER_ID);
            data.setProfessionLevel(ProfessionManager.COMMONER_ID,
                    Math.max(1, data.getProfessionLevel(ProfessionManager.COMMONER_ID)));
        }

        // 副职业校验
        Identifier secId = data.getSecondaryProfessionId();
        if (secId != null) {
            IProfession sec = registry.getProfession(secId);
            if (sec == null
                    || sec.getType() != IProfession.ProfessionType.SECONDARY
                    || secId.equals(data.getProfessionId())) {
                ProfessionMod.LOGGER.warn("玩家 {} 的副职业 {} 失效、类型违规或与主职业相同，已清空",
                        player.getName().getString(), secId);
                data.setSecondaryProfessionId(null);
                data.setSecondaryActive(false);
            }
        }
    }
}
