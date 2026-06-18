package com.rpgcraft.skills.client;

import com.rpgcraft.core.network.CastSkillPacket;
import com.rpgcraft.skills.SkillsMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * 技能释放快捷键（仅客户端）
 * <p>
 * MVP 阶段固定一个技能快捷键（默认数字键 {@code 1}），按下时发送
 * {@link CastSkillPacket} 释放"快捷栏第 1 个技能"。
 * <p>
 * 技能槽位映射策略（MVP 简化）：硬编码释放 ID 为
 * {@code rpgcraftcore:heavy_strike} 的示范技能。后续接入技能栏 UI 后改为读取玩家配置的技能槽。
 * <p>
 * 分类复用 client 模块注册的 {@code rpgcraft} 分类（通过相同 Identifier 引用，
 * 不引入对 client 模块的编译期依赖——{@link KeyMapping.Category} 是 record，
 * 按值相等，相同 Identifier 即同一分类）。
 * <p>
 * <h3>注册方式</h3>
 * 按键注册（{@link RegisterKeyMappingsEvent}，Mod 总线事件）需通过 {@link SkillsMod} 入口的
 * {@code modEventBus.addListener} 挂载；tick 检测（{@link ClientTickEvent.Post}，Game 总线事件）
 * 用 {@code @EventBusSubscriber} 自动挂载。
 */
public final class SkillKeyMappings {

    /** 复用 client 模块的 rpgcraft 分类（按 Identifier 值相等，无需引用 client 类） */
    static final KeyMapping.Category RPGCRAFT_CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "rpgcraft")
    );

    /** 技能 1 快捷键（默认数字键 1） */
    public static final KeyMapping SKILL_1_KEY = new KeyMapping(
            "key.rpgcraftskills.skill1",
            GLFW.GLFW_KEY_1,
            RPGCRAFT_CATEGORY
    );

    /**
     * MVP 固定释放的技能 ID（后续改为读取技能栏槽位配置）
     */
    private static final Identifier MVP_SKILL_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "heavy_strike");

    private SkillKeyMappings() {
    }

    /**
     * 快捷键注册回调（Mod 事件总线）
     * <p>
     * 注：不调用 {@code registerCategory}（分类已由 client 模块的 CharacterScreenOpener 注册），
     * 仅注册键本身。NeoForge 26.1 允许多个 mod 引用同一分类 Identifier。
     * <p>
     * 在 {@link SkillsMod} 构造函数中通过 {@code modEventBus.addListener(SkillKeyMappings::registerKeyMappings)} 挂载。
     */
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SKILL_1_KEY);
        SkillsMod.LOGGER.debug("注册技能快捷键：1（释放技能 heavy_strike）");
    }

    /**
     * 客户端 tick 回调（Game 事件总线）—— 检测按键并发送释放请求
     * <p>
     * 仅在玩家存在、无其他界面打开时响应。
     */
    @EventBusSubscriber(value = Dist.CLIENT, modid = SkillsMod.MODID)
    public static class GameBus {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            if (SKILL_1_KEY.consumeClick()) {
                // 发送释放请求到服务端，服务端权威校验后执行
                mc.getConnection().send(new CastSkillPacket(MVP_SKILL_ID));
            }
        }
    }
}
