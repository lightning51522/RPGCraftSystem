package com.rpgcraft.core.network;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.command.RPGCommands;
import com.rpgcraft.core.level.LevelManager;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.core.level.api.ILevelCalculator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * 怪物信息查询包 —— 客户端到服务端
 * <p>
 * 客户端在准星指向敌对实体时发送此包，请求服务端返回该实体的等级和击杀经验。
 * 服务端通过实体 ID 查找实体，获取 {@link MobLevelData} 等级，
 * 使用 {@link ILevelCalculator} 计算经验，然后通过 {@link SyncMobInfoPacket} 回复。
 *
 * @param entityId 客户端准星指向的实体 ID
 */
public record QueryMobInfoPacket(int entityId) implements CustomPacketPayload {

    public static final Type<QueryMobInfoPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "query_mob_info")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, QueryMobInfoPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT, QueryMobInfoPacket::entityId,
            QueryMobInfoPacket::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：查询实体等级和经验，回复给请求的玩家
     */
    public static void handle(QueryMobInfoPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // 检查玩家 HUD 是否启用，禁用时不回复
            if (!RPGCommands.isHudEnabled(player.getUUID())) return;

            // 通过实体 ID 查找实体
            Entity entity = player.level().getEntity(data.entityId());
            if (entity == null) return;

            // 仅处理 LivingEntity（非玩家）
            if (!(entity instanceof LivingEntity livingEntity)) return;
            if (entity instanceof Player) return;

            // 获取怪物等级：MobLevelData > MobAttributeConfig 配置默认 > 1
            MobLevelData levelData = livingEntity.getData(LevelManager.MOB_LEVEL);
            int mobLevel;
            if (levelData.isSet()) {
                mobLevel = levelData.getLevel();
            } else {
                Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntity.getType());
                mobLevel = MobAttributeConfig.getConfig(typeId)
                        .map(MobAttributeConfig.MobAttributes::level)
                        .orElse(1);
            }

            // 获取基础经验值：MobLevelData 覆盖 > 配置值
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntity.getType());
            int baseExp;
            if (levelData.hasBaseExpOverride()) {
                baseExp = levelData.getBaseExpOverride();
            } else {
                baseExp = MobAttributeConfig.getConfig(typeId)
                        .map(MobAttributeConfig.MobAttributes::baseExp)
                        .orElse(100);
            }

            // 计算实际可获得经验（委托给可替换的计算器）
            ILevelCalculator calculator = LevelManager.getLevelCalculator();
            int expGain = calculator.calculateExperienceGain(player, livingEntity, mobLevel, baseExp);

            // 回复给客户端（包含评级名称和生命值）
            String ratingName = levelData.getRating().name();
            EntityAttribute lifeAttr = livingEntity.getData(AttributeManager.LIFE);
            int currentHealth = lifeAttr.getValue();
            int maxHealth = lifeAttr.getMaxValue();
            player.connection.send(new SyncMobInfoPacket(data.entityId(), ratingName, mobLevel,
                    currentHealth, maxHealth, Math.max(0, expGain)));
        });
    }
}
