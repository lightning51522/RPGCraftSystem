package com.rpgcraft.core.level;

import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.attribute.MobAttributeConfig;
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
 * <p>
 * 经验公式：{@code 实际经验 = sqrt(怪物等级 / 玩家等级) * 基础经验}
 * <ul>
 *   <li>怪物等级和基础经验来自 {@link MobAttributeConfig}（未配置时默认等级 1、基础经验 100）</li>
 *   <li>玩家等级来自 {@link PlayerLevelData} 附件（最低为 1）</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
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

        Optional<MobAttributeConfig.MobAttributes> config = MobAttributeConfig.getConfig(typeId);
        if (config.isPresent()) {
            mobLevel = config.get().level();
            baseExp = config.get().baseExp();
        }

        // 获取玩家等级
        PlayerLevelData levelData = player.getData(LevelManager.PLAYER_LEVEL);
        int playerLevel = Math.max(1, levelData.getLevel());

        // 计算实际经验：sqrt(怪物等级 / 玩家等级) * 基础经验
        int expGain = (int) (Math.sqrt((double) mobLevel / playerLevel) * baseExp);
        if (expGain <= 0) return;

        // 增加经验（内部处理升级）
        levelData.addExperience(expGain);

        // 同步到客户端
        LevelManager.syncToClient(player);
    }
}
