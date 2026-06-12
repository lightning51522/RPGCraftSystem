package com.rpgcraft.combat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobRating;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 战斗模块命令
 * <p>
 * 提供战斗日志开关、随机刷新开关，以及 RPG 生物召唤命令。
 * 召唤命令（{@code /rpg spawn}）原本位于 core 的 {@code RPGCommands} 中，
 * 因强依赖战斗系统的怪物属性初始化和默认怪物属性词汇表，迁移到战斗模块，
 * 实现「命令与所操作数据/逻辑的归属一致」，同时让 core 不再硬编码属性字段名。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg combatlog          — 查看战斗日志开关状态
 * /rpg combatlog on/off   — 开关战斗日志
 * /rpg randspawn          — 查看随机刷新开关状态
 * /rpg randspawn on/off   — 开关随机刷新
 * /rpg spawn &lt;entity&gt; &lt;level&gt; [json_attributes] — 召唤指定等级的 RPG 生物
 * </pre>
 */
@EventBusSubscriber(modid = CombatMod.MODID)
public class CombatCommands {

    private CombatCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 战斗日志开关指令 ===
                .then(Commands.literal("combatlog")
                        .executes(context -> executeCombatLogStatus(context))
                        .then(Commands.literal("on")
                                .executes(context -> executeCombatLogToggle(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> executeCombatLogToggle(context, false)))
                )

                // === 随机刷新开关指令 ===
                .then(Commands.literal("randspawn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> executeRandSpawnStatus(context))
                        .then(Commands.literal("on")
                                .executes(context -> executeRandSpawnToggle(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> executeRandSpawnToggle(context, false)))
                )

                // === 召唤指令 ===
                .then(Commands.literal("spawn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("entity", IdentifierArgument.id())
                                .suggests((context, builder) -> {
                                    BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .executes(context -> executeSpawn(context,
                                                IdentifierArgument.getId(context, "entity"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level")))
                                        .then(Commands.argument("attributes", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(context -> executeSpawnCustom(context,
                                                        IdentifierArgument.getId(context, "entity"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(context, "attributes")))
                                        )
                                )
                        )
                )
        );
    }

    // === 战斗日志开关（每玩家，默认关闭，持久化保存到 PlayerPreferences 附件） ===

    /**
     * 查询指定玩家的战斗日志是否启用
     * <p>
     * 从 {@link com.rpgcraft.core.preference.PlayerPreferences} 附件读取，默认关闭。
     *
     * @param player 服务端玩家
     * @return true 表示启用战斗日志
     */
    public static boolean isCombatLogEnabled(net.minecraft.server.level.ServerPlayer player) {
        return player.getData(com.rpgcraft.core.attribute.AttributeManager.PLAYER_PREFERENCES).isCombatLogEnabled();
    }

    /**
     * 显示当前战斗日志开关状态
     */
    private static int executeCombatLogStatus(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = isCombatLogEnabled(player);
        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("战斗日志状态: " + status),
                false
        );
        return enabled ? 1 : 0;
    }

    /**
     * 切换战斗日志开关状态
     * <p>
     * 写入 {@link com.rpgcraft.core.preference.PlayerPreferences} 附件，持久化保存。
     */
    private static int executeCombatLogToggle(CommandContext<CommandSourceStack> context, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
        com.rpgcraft.core.preference.PlayerPreferences prefs =
                player.getData(com.rpgcraft.core.attribute.AttributeManager.PLAYER_PREFERENCES);
        prefs.setCombatLogEnabled(enabled);

        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("战斗日志已" + status),
                true
        );
        return enabled ? 1 : 0;
    }

    // === 随机刷新开关（全局，默认关闭） ===

    /** 全局随机刷新开关，默认关闭。开启后自然刷新的怪物会从权重表中随机选择等级和评级 */
    private static volatile boolean randomSpawnEnabled = false;

    /**
     * 查询随机刷新是否启用
     *
     * @return true = 自然刷新使用权重表随机等级/评级
     */
    public static boolean isRandomSpawnEnabled() {
        return randomSpawnEnabled;
    }

    /**
     * 显示当前随机刷新开关状态
     */
    private static int executeRandSpawnStatus(CommandContext<CommandSourceStack> context) {
        String status = randomSpawnEnabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("随机刷新状态: " + status + "（自然生成的怪物" +
                        (randomSpawnEnabled ? "会从权重表中随机选择等级和评级" : "使用配置静态等级") + "）"),
                false
        );
        return randomSpawnEnabled ? 1 : 0;
    }

    /**
     * 切换随机刷新开关
     */
    private static int executeRandSpawnToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        randomSpawnEnabled = enabled;

        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("随机刷新已" + status + "（" +
                        (enabled ? "自然生成的怪物将从权重表中随机选择等级和评级" : "自然生成的怪物使用配置静态等级") + "）"),
                true
        );
        return enabled ? 1 : 0;
    }

    // === 召唤指令 ===

    /** 合法的 JSON 属性字段名（不含 attack_type，它单独解析） */
    private static final Set<String> VALID_ATTRIBUTE_FIELDS = Set.of(
            "life", "strength", "defense", "resistance",
            "critical_rate", "critical_ratio", "base_exp"
    );

    private static final Gson GSON = new Gson();

    /**
     * 召唤指定等级的 RPG 生物
     * <p>
     * 生成流程：
     * <ol>
     *   <li>从注册表解析实体类型，失败则发送错误消息</li>
     *   <li>在命令源位置创建实体</li>
     *   <li>将实体加入世界（触发 CombatEventHandler.onEntityJoinLevel 初始化默认属性）</li>
     *   <li>覆盖为命令指定的等级：通过战斗系统重新初始化属性</li>
     * </ol>
     * 如果实体不是 LivingEntity 或不在 MobAttributeConfig 中，
     * 仍会正常召唤但不会应用 RPG 属性缩放。
     */
    private static int executeSpawn(CommandContext<CommandSourceStack> context, Identifier entityId, int level) {
        // 1. 从注册表解析实体类型
        var entityTypeOptional = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (entityTypeOptional.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知的实体类型: " + entityId));
            return 0;
        }

        // 2. 获取服务端世界和命令源位置
        ServerLevel serverLevel = context.getSource().getLevel();
        Vec3 pos = context.getSource().getPosition();
        Vec2 rotation = context.getSource().getRotation();

        // 3. 创建实体
        EntityType<?> entityType = entityTypeOptional.get();
        Entity entity = entityType.create(serverLevel, EntitySpawnReason.COMMAND);
        if (entity == null) {
            context.getSource().sendFailure(Component.literal("无法创建实体: " + entityId));
            return 0;
        }

        // 4. 设置位置和朝向
        entity.snapTo(pos.x, pos.y, pos.z, rotation.y, rotation.x);

        // 5. 加入世界（触发 EntityJoinLevelEvent -> CombatEventHandler.onEntityJoinLevel）
        if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
            context.getSource().sendFailure(Component.literal("实体生成失败（可能达到生成上限）"));
            return 0;
        }

        // 6. 如果是 LivingEntity，覆盖为命令指定的等级
        if (entity instanceof LivingEntity livingEntity) {
            var config = MobAttributeConfig.getConfig(entityId);
            if (config.isPresent()) {
                // 覆盖属性为命令指定的等级（initializeMobAttributes 会重写所有属性值）
                CombatEventHandler.initializeMobAttributes(livingEntity, level);

                String entityName = entityType.getDescription().getString();
                context.getSource().sendSuccess(
                        () -> Component.literal(String.format("已召唤 等级%d 的 %s", level, entityName)),
                        true
                );
            } else {
                // 实体没有 RPG 属性配置，仍然召唤但发出警告
                String entityName = entityType.getDescription().getString();
                context.getSource().sendSuccess(
                        () -> Component.literal(String.format("已召唤 %s（未配置 RPG 属性，等级 %d 不生效）",
                                entityName)),
                        true
                );
            }
        } else {
            // 非 LivingEntity（如矿车、船等），正常召唤
            String entityName = entityType.getDescription().getString();
            context.getSource().sendSuccess(
                    () -> Component.literal(String.format("已召唤 %s（非生物实体，不支持 RPG 等级）", entityName)),
                    true
            );
        }

        return 1;
    }

    /**
     * 召唤自定义属性的 RPG 生物
     * <p>
     * 解析 JSON 字符串中的属性覆盖，与 {@link #executeSpawn} 类似的生成流程，
     * 但通过战斗系统的 initializeMobAttributesCustom() 应用覆盖值。
     * <p>
     * JSON 中指定的属性值为最终值（不经过等级缩放），未指定的属性使用配置默认值 + 等级缩放。
     */
    private static int executeSpawnCustom(CommandContext<CommandSourceStack> context,
                                          Identifier entityId, int level, String jsonInput) {
        // 1. 解析 JSON
        JsonObject json;
        try {
            JsonElement element = GSON.fromJson(jsonInput, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                context.getSource().sendFailure(Component.literal("JSON 格式错误: 需要一个 JSON 对象 {...}"));
                return 0;
            }
            json = element.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            context.getSource().sendFailure(Component.literal("JSON 格式错误: " + e.getMessage()));
            return 0;
        }

        // 2. 验证字段名
        for (String key : json.keySet()) {
            if (!VALID_ATTRIBUTE_FIELDS.contains(key) && !"attack_type".equals(key) && !"rating".equals(key)) {
                context.getSource().sendFailure(Component.literal(
                        "未知属性: " + key + "。合法字段: attack_type, rating, base_exp, life, strength, defense, resistance, critical_rate, critical_ratio"));
                return 0;
            }
        }

        // 3. 提取属性覆盖
        Map<String, Integer> overrides = new LinkedHashMap<>();
        for (String field : VALID_ATTRIBUTE_FIELDS) {
            if (json.has(field)) {
                try {
                    overrides.put(field, json.getAsJsonPrimitive(field).getAsInt());
                } catch (Exception e) {
                    context.getSource().sendFailure(Component.literal("属性 " + field + " 的值必须是整数"));
                    return 0;
                }
            }
        }

        // 4. 提取攻击类型覆盖
        AttackType attackTypeOverride = null;
        if (json.has("attack_type")) {
            try {
                attackTypeOverride = AttackType.valueOf(
                        json.getAsJsonPrimitive("attack_type").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                context.getSource().sendFailure(Component.literal(
                        "未知攻击类型: " + json.getAsJsonPrimitive("attack_type").getAsString()
                                + "。合法值: PHYSICAL, MAGIC, PHYSICAL_WITH_MAGIC, MAGIC_WITH_PHYSICAL, MIX_TYPE"));
                return 0;
            }
        }

        // 5. 提取评级覆盖
        MobRating rating = MobRating.NORMAL;
        if (json.has("rating")) {
            String ratingStr = json.getAsJsonPrimitive("rating").getAsString();
            rating = MobRating.fromName(ratingStr);
            if (rating == null) {
                context.getSource().sendFailure(Component.literal(
                        "未知评级: " + ratingStr + "。合法值: NORMAL, STRONG, ELITE, NOTORIOUS_ELITE, BOSS, LORD"));
                return 0;
            }
        }

        // 6. 解析实体类型并生成（复用 executeSpawn 的生成逻辑）
        var entityTypeOptional = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (entityTypeOptional.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知的实体类型: " + entityId));
            return 0;
        }

        ServerLevel serverLevel = context.getSource().getLevel();
        Vec3 pos = context.getSource().getPosition();
        Vec2 rotation = context.getSource().getRotation();

        EntityType<?> entityType = entityTypeOptional.get();
        Entity entity = entityType.create(serverLevel, EntitySpawnReason.COMMAND);
        if (entity == null) {
            context.getSource().sendFailure(Component.literal("无法创建实体: " + entityId));
            return 0;
        }

        entity.snapTo(pos.x, pos.y, pos.z, rotation.y, rotation.x);

        if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
            context.getSource().sendFailure(Component.literal("实体生成失败（可能达到生成上限）"));
            return 0;
        }

        // 7. 应用自定义属性
        if (entity instanceof LivingEntity livingEntity) {
            var config = MobAttributeConfig.getConfig(entityId);
            if (config.isPresent()) {
                CombatEventHandler.initializeMobAttributesCustom(livingEntity, level, overrides, attackTypeOverride, rating);

                String entityName = entityType.getDescription().getString();
                String ratingStr = rating != MobRating.NORMAL
                        ? " [" + rating.getDisplayName() + "]"
                        : "";
                context.getSource().sendSuccess(
                        () -> Component.literal(String.format("已召唤 等级%d%s 的 %s（自定义属性）",
                                level, ratingStr, entityName)),
                        true
                );
            } else {
                String entityName = entityType.getDescription().getString();
                context.getSource().sendSuccess(
                        () -> Component.literal(String.format("已召唤 %s（未配置 RPG 属性，自定义属性不生效）",
                                entityName)),
                        true
                );
            }
        } else {
            String entityName = entityType.getDescription().getString();
            context.getSource().sendSuccess(
                    () -> Component.literal(String.format("已召唤 %s（非生物实体，不支持 RPG 等级）", entityName)),
                    true
            );
        }

        return 1;
    }
}
