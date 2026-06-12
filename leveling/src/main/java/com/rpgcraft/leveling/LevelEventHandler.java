package com.rpgcraft.leveling;

import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.core.level.api.ILevelCalculator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

/**
 * 等级系统事件处理器
 * <p>
 * 处理玩家击杀怪物时的经验获取逻辑。
 * 经验计算委托给 {@link ILevelCalculator}，可通过子模组替换。
 */
@EventBusSubscriber(modid = LevelingMod.MODID)
public class LevelEventHandler {

    /**
     * 玩家击杀怪物时发放经验
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // 只处理服务端
        if (event.getEntity().level().isClientSide()) return;

        // 检查是否为玩家击杀
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        LivingEntity target = event.getEntity();

        // 不处理玩家被杀的情况（target 是玩家时跳过）
        if (target instanceof Player) return;

        // 查询怪物的等级和基础经验
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        int mobLevel = 1;
        int baseExp = 100;

        // 优先使用 MobLevelData 中的等级覆盖（指令召唤等场景）
        MobLevelData mobLevelData = target.getData(LevelManager.MOB_LEVEL);
        if (mobLevelData.isSet()) {
            mobLevel = mobLevelData.getLevel();
        }

        Optional<MobAttributeConfig.MobAttributes> config = MobAttributeConfig.getConfig(typeId);
        if (config.isPresent()) {
            // 等级：MobLevelData 优先（上面已处理），其次使用配置值
            if (!mobLevelData.isSet()) {
                mobLevel = config.get().level();
            }
            // 基础经验：MobLevelData 覆盖 > 配置值
            if (mobLevelData.hasBaseExpOverride()) {
                baseExp = mobLevelData.getBaseExpOverride();
            } else {
                baseExp = config.get().baseExp();
            }
        }

        // 委托给可替换的经验计算器
        ILevelCalculator calculator = LevelManager.getLevelCalculator();
        int expGain = calculator.calculateExperienceGain(player, target, mobLevel, baseExp);
        if (expGain <= 0) return;

        // 增加经验（内部处理升级）
        PlayerLevelData levelData = player.getData(LevelManager.PLAYER_LEVEL);
        boolean leveledUp = levelData.addExperience(expGain);

        // 升级时标记属性快照脏（为未来属性点/升级加成做准备）
        if (leveledUp) {
            AttributeSnapshotManager.markDirty(player);
        }

        // 同步到客户端
        LevelManager.syncToClient(player);
    }
}
