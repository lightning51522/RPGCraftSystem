package com.rpgcraft.skills.network;

import com.rpgcraft.core.skill.PlayerSkillData;
import com.rpgcraft.skills.SkillsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能数据同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 同步玩家的技能冷却与已学技能到客户端，供技能栏 HUD 显示冷却倒计时。
 *
 * @param cooldowns 技能 ID → 冷却到期 tick
 * @param learned   已学习技能 ID 列表
 */
public record SyncPlayerSkillsPacket(Map<Identifier, Long> cooldowns, List<Identifier> learned)
        implements CustomPacketPayload {

    public static final Type<SyncPlayerSkillsPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_player_skills")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerSkillsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_LONG),
                            SyncPlayerSkillsPacket::cooldowns,
                    Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()),
                            SyncPlayerSkillsPacket::learned,
                    SyncPlayerSkillsPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端调用：处理从服务端收到的技能同步数据
     */
    public static void handle(SyncPlayerSkillsPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                PlayerSkillData skillData = clientPlayer.getData(SkillsManager.PLAYER_SKILLS);
                // 重建冷却
                skillData.clearCooldowns();
                for (Map.Entry<Identifier, Long> entry : data.cooldowns().entrySet()) {
                    skillData.startCooldown(entry.getKey(), entry.getValue());
                }
                // 重建已学集合
                data.learned().forEach(id -> {
                    if (!skillData.hasLearned(id)) skillData.learn(id);
                });
            }
        });
    }
}
