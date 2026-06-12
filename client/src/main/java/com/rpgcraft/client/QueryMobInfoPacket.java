package com.rpgcraft.client;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
// ClientCommands 引用（同模块，替代原来对 core RPGCommands 的引用）
import com.rpgcraft.core.registry.RPGSystems;
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
 * 使用 {@link com.rpgcraft.core.registry.IMobDataProvider} 查询数据，然后通过 {@link SyncMobInfoPacket} 回复。
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
            if (!ClientCommands.isHudEnabled(player)) return;

            // 通过实体 ID 查找实体
            Entity entity = player.level().getEntity(data.entityId());
            if (entity == null) return;

            // 仅处理 LivingEntity（非玩家）
            if (!(entity instanceof LivingEntity livingEntity)) return;
            if (entity instanceof Player) return;

            var mobDataProvider = RPGSystems.getMobDataProvider();

            // 获取怪物等级：MobLevelData > MobAttributeConfig 配置默认 > 1
            MobLevelData levelData = mobDataProvider.getMobLevelData(livingEntity);
            int mobLevel;
            if (levelData.isSet()) {
                mobLevel = levelData.getLevel();
            } else {
                Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntity.getType());
                mobLevel = mobDataProvider.getConfig(typeId)
                        .map(MobAttributeConfig.MobAttributes::level)
                        .orElse(1);
            }

            // 获取基础经验值：MobLevelData 覆盖 > 配置值
            Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntity.getType());
            int baseExp;
            if (levelData.hasBaseExpOverride()) {
                baseExp = levelData.getBaseExpOverride();
            } else {
                baseExp = mobDataProvider.getConfig(typeId)
                        .map(MobAttributeConfig.MobAttributes::baseExp)
                        .orElse(100);
            }

            // 计算实际可获得经验（委托给可替换的计算器 — 通过 RPGSystems 查询等级系统获取）
            // 注意：ILevelCalculator 目前不在 core 接口中，此处使用简单公式估算
            // 精确计算由 leveling 模块的 LevelEventHandler 处理
            int playerLevel = RPGSystems.getLevelSystem().getLevel(player);
            int expGain = (int) (Math.sqrt((double) mobLevel / Math.max(1, playerLevel)) * baseExp);

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
