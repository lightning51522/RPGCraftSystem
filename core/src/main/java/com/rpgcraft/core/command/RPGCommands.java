package com.rpgcraft.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.combat.MobRating;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.*;

/**
 * RPG 核心属性指令系统
 * <p>
 * 通过游戏内聊天指令查看和修改玩家的 RPG 属性值。
 * 等级、职业、战斗日志、HUD 等命令已迁移到各自的插件模块中。
 * <p>
 * 核心命令列表：
 * <pre>
 * /rpg list [player]
 * /rpg get &lt;attribute&gt; [player]
 * /rpg set &lt;attribute&gt; &lt;value&gt; [player]
 * /rpg setmax &lt;attribute&gt; &lt;value&gt; [player]
 * /rpg reset [player]
 * /rpg deathmode &lt;mode&gt;
 * /rpg spawn &lt;entity&gt; &lt;level&gt; [json_attributes]
 * </pre>
 * <p>
 * 已迁移的命令：
 * <ul>
 *   <li>{@code /rpg level/setlevel/addexp} → leveling 模块 {@code LevelingCommands}</li>
 *   <li>{@code /rpg profession [list|set]} → profession 模块 {@code ProfessionCommands}</li>
 *   <li>{@code /rpg combatlog} → combat 模块 {@code CombatCommands}</li>
 *   <li>{@code /rpg randspawn} → combat 模块 {@code CombatCommands}</li>
 *   <li>{@code /rpg hud} → client 模块 {@code ClientCommands}</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class RPGCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                .then(Commands.literal("list")
                        .executes(context -> executeList(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> executeList(context, EntityArgument.getPlayer(context, "player")))
                        )
                )

                .then(Commands.literal("get")
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
                                        builder.suggest(entry.getId().getPath());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeGet(context,
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "attribute")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(context -> executeGet(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "attribute")))
                                )
                        )
                )

                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
                                        builder.suggest(entry.getId().getPath());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> executeSet(context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "attribute"),
                                                IntegerArgumentType.getInteger(context, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeSet(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "attribute"),
                                                        IntegerArgumentType.getInteger(context, "value")))
                                        )
                                )
                        )
                )

                .then(Commands.literal("setmax")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
                                        builder.suggest(entry.getId().getPath());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> executeSetMax(context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "attribute"),
                                                IntegerArgumentType.getInteger(context, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeSetMax(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "attribute"),
                                                        IntegerArgumentType.getInteger(context, "value")))
                                        )
                                )
                        )
                )

                .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> executeReset(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> executeReset(context,
                                        EntityArgument.getPlayer(context, "player")))
                        )
                )

                .then(Commands.literal("deathmode")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (DeathRestoreMode mode : DeathRestoreMode.values()) {
                                        builder.suggest(mode.getCommandKey());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeDeathMode(context,
                                        StringArgumentType.getString(context, "mode")))
                        )
                )

                // === 召唤指令 ===

                .then(Commands.literal("spawn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("entity", IdentifierArgument.id())
                                .suggests((context, builder) -> {
                                    BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(context -> executeSpawn(context,
                                                IdentifierArgument.getId(context, "entity"),
                                                IntegerArgumentType.getInteger(context, "level")))
                                        .then(Commands.argument("attributes", StringArgumentType.greedyString())
                                                .executes(context -> executeSpawnCustom(context,
                                                        IdentifierArgument.getId(context, "entity"),
                                                        IntegerArgumentType.getInteger(context, "level"),
                                                        StringArgumentType.getString(context, "attributes")))
                                        )
                                )
                        )
                )
        );
    }

    private static int executeList(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            IAttribute attr = target.getData(entry.getSupplier());
            String path = entry.getId().getPath();

            String text;
            if (attr.hasMaxValue()) {
                text = String.format("  %s: %d / %d", path, attr.getValue(), attr.getMaxValue());
            } else {
                text = String.format("  %s: %d", path, attr.getValue());
            }

            context.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        context.getSource().sendSuccess(
                () -> Component.literal("—— " + target.getName().getString() + " 的属性列表 ——"),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.literal("当前死亡恢复模式: " + DeathRestoreMode.getCurrentMode().getDisplayName()),
                false
        );
        return AttributeManager.getRegistry().getAllEntries().size();
    }

    private static int executeGet(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName) {
        Optional<IAttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知属性: " + attrName));
            return 0;
        }

        IAttributeEntry entry = resolved.get();
        IAttribute attr = target.getData(entry.getSupplier());

        String text;
        if (attr.hasMaxValue()) {
            text = String.format("%s 的 %s: %d / %d", target.getName().getString(), attrName, attr.getValue(), attr.getMaxValue());
        } else {
            text = String.format("%s 的 %s: %d", target.getName().getString(), attrName, attr.getValue());
        }

        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return attr.getValue();
    }

    private static int executeSet(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName, int value) {
        Optional<IAttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知属性: " + attrName));
            return 0;
        }

        IAttributeEntry entry = resolved.get();
        IAttribute attr = target.getData(entry.getSupplier());

        attr.setValue(value);

        if (entry.getId().equals(AttributeManager.LIFE_ID)) {
            AttributeManager.syncVanillaHealth(target);
        }

        RPGCraftCore.checkAndSnapshotIfDying(target);

        EntityAttribute entityAttr = (EntityAttribute) attr;
        SyncPlayerAttributePacket.sendToClient(target, entry.getId(), entityAttr);

        String text = String.format("已将 %s 的 %s 设置为 %d", target.getName().getString(), attrName, attr.getValue());
        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return attr.getValue();
    }

    private static int executeSetMax(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName, int value) {
        Optional<IAttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知属性: " + attrName));
            return 0;
        }

        IAttributeEntry entry = resolved.get();
        IAttribute attr = target.getData(entry.getSupplier());

        attr.setMaxValue(value);

        if (entry.getId().equals(AttributeManager.LIFE_ID)) {
            AttributeManager.syncVanillaHealth(target);
        }

        // setmax 可能导致 currentValue 被钳制到极低值，检测是否需要创建死亡快照
        RPGCraftCore.checkAndSnapshotIfDying(target);

        EntityAttribute entityAttr = (EntityAttribute) attr;
        SyncPlayerAttributePacket.sendToClient(target, entry.getId(), entityAttr);

        String text = String.format("已将 %s 的 %s 最大值设置为 %d", target.getName().getString(), attrName, value);
        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return value;
    }

    private static int executeDeathMode(CommandContext<CommandSourceStack> context, String modeKey) {
        DeathRestoreMode mode = DeathRestoreMode.fromCommandKey(modeKey);
        if (mode == null) {
            context.getSource().sendFailure(Component.literal("未知死亡恢复模式: " + modeKey + "（可选: snapshot, rescan）"));
            return 0;
        }
        DeathRestoreMode.setCurrentMode(mode);
        context.getSource().sendSuccess(
                () -> Component.literal("死亡属性恢复模式已设置为: " + mode.getDisplayName()),
                true
        );
        return 1;
    }

    /**
     * 召唤指定等级的 RPG 生物
     * <p>
     * 生成流程：
     * <ol>
     *   <li>从注册表解析实体类型，失败则发送错误消息</li>
     *   <li>在命令源位置创建实体</li>
     *   <li>将实体加入世界（触发 CombatEventHandler.onEntityJoinLevel 初始化默认属性）</li>
     *   <li>覆盖为命令指定的等级：通过 RPGSystems.getCombatSystem() 重新初始化属性</li>
     * </ol>
     * 如果实体不是 LivingEntity 或不在 MobAttributeConfig 中，
     * 仍会正常召唤但不会应用 RPG 属性缩放。
     *
     * @param context  命令上下文
     * @param entityId 实体类型标识符（如 minecraft:zombie）
     * @param level    目标等级（>= 1）
     * @return 1 表示成功，0 表示失败
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
        //    onEntityJoinLevel 会检查 MobLevelData（此时 level=0 即未设置），
        //    使用配置默认等级进行初始化。
        if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
            context.getSource().sendFailure(Component.literal("实体生成失败（可能达到生成上限）"));
            return 0;
        }

        // 6. 如果是 LivingEntity，覆盖为命令指定的等级
        if (entity instanceof LivingEntity livingEntity) {
            var config = MobAttributeConfig.getConfig(entityId);
            if (config.isPresent()) {
                // 覆盖属性为命令指定的等级（initializeMobAttributes 会重写所有属性值）
                RPGSystems.getCombatSystem().initializeMobAttributes(livingEntity, level);

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
                                entityName, level)),
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

    /** 合法的 JSON 属性字段名（不含 attack_type，它单独解析） */
    private static final Set<String> VALID_ATTRIBUTE_FIELDS = Set.of(
            "life", "strength", "defense", "resistance",
            "critical_rate", "critical_ratio", "base_exp"
    );

    private static final Gson GSON = new Gson();

    /**
     * 召唤自定义属性的 RPG 生物
     * <p>
     * 解析 JSON 字符串中的属性覆盖，与 {@link #executeSpawn} 类似的生成流程，
     * 但通过 RPGSystems.getCombatSystem().initializeMobAttributesCustom() 应用覆盖值。
     * <p>
     * JSON 中指定的属性值为最终值（不经过等级缩放），未指定的属性使用配置默认值 + 等级缩放。
     *
     * @param context   命令上下文
     * @param entityId  实体类型标识符
     * @param level     目标等级
     * @param jsonInput JSON 属性字符串
     * @return 1 表示成功，0 表示失败
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
                RPGSystems.getCombatSystem().initializeMobAttributesCustom(livingEntity, level, overrides, attackTypeOverride, rating);

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

    private static Optional<IAttributeEntry> resolveAttribute(String name) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            if (entry.getId().getPath().equals(name)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static int executeReset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        AttributeManager.getRegistry().resetToDefaults(target);

        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            EntityAttribute attr = (EntityAttribute) target.getData(entry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(target, entry.getId(), attr);
        }

        // 重置后同步原版生命值，确保原版血条与自定义 life 属性一致
        AttributeManager.syncVanillaHealth(target);

        context.getSource().sendSuccess(
                () -> Component.literal("已重置 " + target.getName().getString() + " 的所有属性"),
                true
        );
        return AttributeManager.getRegistry().getAllEntries().size();
    }
}
