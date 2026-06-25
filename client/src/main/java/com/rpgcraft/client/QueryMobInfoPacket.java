package com.rpgcraft.client;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
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

/**
 * 怪物信息查询包 —— 客户端到服务端
 * <p>
 * 客户端在准星指向敌对实体时发送此包，请求服务端返回该实体的等级、属性和击杀经验。
 * 服务端通过实体 ID 查找实体，获取 {@link MobLevelData} 等级，
 * 通过 {@link AttributeSnapshotManager} 读取属性快照，
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
     * 服务端处理：查询实体等级、属性和经验，回复给请求的玩家
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

            // 计算实际可获得经验：调用 core 共享的等级差曲线工具，与服务端 DefaultLevelCalculator 同源，
            // 从根上避免预览值与实际发放经验漂移（注：此处不计入 exp_bonus 属性加成，仅为基准预览）
            int playerLevel = RPGSystems.getLevelSystem().getLevel(player);
            int expGain = com.rpgcraft.core.level.ExperienceGainCurve.gain(playerLevel, mobLevel, baseExp);

            // 读取属性快照（包含所有已注册属性的计算值）
            AttributeSnapshot snapshot = AttributeSnapshotManager.getSnapshot(livingEntity);

            // 读取生命值（从 EntityAttribute 直接读取， LIFE 是 core 自有属性）
            EntityAttribute lifeAttr = livingEntity.getData(AttributeManager.LIFE);
            int currentHealth = lifeAttr.getValue();
            int maxHealth = lifeAttr.getMaxValue();

            // 从快照读取战斗属性（属性未注册时读取值为 0，自动降级）
            // 注：mana/defense 已废弃（v0.6.0-alpha 起），此处传 0 保持网络协议兼容
            int strength = getSnapshotValue(snapshot, "rpgcraftcore", "strength");
            int resistance = getSnapshotValue(snapshot, "rpgcraftcore", "resistance");

            // 回复给客户端（mana=0, defense=0，已废弃）
            String ratingName = levelData.getRating().name();
            player.connection.send(new SyncMobInfoPacket(data.entityId(), ratingName, mobLevel,
                    currentHealth, maxHealth, strength, 0, 0, resistance,
                    Math.max(0, expGain)));
        });
    }

    /**
     * 从属性快照中读取指定属性的当前值
     * <p>
     * 属性未注册时返回 0（优雅降级，不崩溃）。
     *
     * @param snapshot  属性快照
     * @param namespace 命名空间
     * @param path      路径
     * @return 属性当前值，未注册时返回 0
     */
    private static int getSnapshotValue(AttributeSnapshot snapshot, String namespace, String path) {
        AttributeSnapshot.AttributeData data = snapshot.get(
                Identifier.fromNamespaceAndPath(namespace, path));
        return data != null ? data.currentValue() : 0;
    }
}
