package com.rpgcraft.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.client.network.OpenCharacterScreenPacket;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.network.SyncAttributeSnapshotPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 角色界面命令
 * <p>
 * 提供 {@code /rpg character} 命令，玩家执行后服务端创建属性快照并发送到客户端，
 * 然后通知客户端打开角色界面。
 * <p>
 * 命令流程：
 * <pre>
 * /rpg character
 *   → 服务端创建全量属性快照
 *   → 发送 SyncAttributeSnapshotPacket（快照数据）
 *   → 发送 OpenCharacterScreenPacket（打开界面信号）
 *   → 客户端按序处理：缓存快照 → 打开 RPGCharacterScreen
 * </pre>
 * <p>
 * 使用 {@code @EventBusSubscriber(modid = ClientMod.MODID)} 注册，
 * 遵循与 {@link ClientCommands} 相同的模式。
 *
 * @see com.rpgcraft.client.ui.RPGCharacterScreen
 * @see OpenCharacterScreenPacket
 */
@EventBusSubscriber(modid = ClientMod.MODID)
public class CharacterCommands {

    private CharacterCommands() {
        // 禁止实例化
    }

    /**
     * 命令注册回调
     *
     * @param event NeoForge 命令注册事件
     */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 角色界面命令 ===
                .then(Commands.literal("character")
                        .executes(CharacterCommands::executeCharacter)
                )
        );
    }

    /**
     * 执行 /rpg character 命令
     * <p>
     * 服务端创建全量属性快照并通过两个网络包发送到客户端：
     * <ol>
     *   <li>{@link SyncAttributeSnapshotPacket} —— 属性快照数据（先发送）</li>
     *   <li>{@link OpenCharacterScreenPacket} —— 打开界面信号（后发送）</li>
     * </ol>
     * TCP 保证包的到达顺序，客户端 {@code enqueueWork} 保证处理顺序，
     * 因此快照一定在界面打开前就已缓存完毕。
     *
     * @param context 命令上下文
     * @return 1 表示成功
     */
    private static int executeCharacter(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();

        // 1. 创建全量属性快照
        AttributeSnapshot snapshot = AttributeManager.getRegistry().createSnapshot(player);

        // 2. 发送快照到客户端（先于界面信号）
        SyncAttributeSnapshotPacket.sendToClient(player, snapshot);

        // 3. 发送打开界面信号到客户端
        player.connection.send(new OpenCharacterScreenPacket());

        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.client.character.opened"),
                false
        );
        return 1;
    }
}
