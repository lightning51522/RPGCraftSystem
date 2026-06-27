package com.rpgcraft.region;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.region.data.EnvironmentType;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.data.RegionDraft;
import com.rpgcraft.region.data.RegionPolygon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * 区域模块命令
 * <p>
 * 命令列表：
 * <pre>
 * /rpg findregion [name] — 查找最近的区域，返回其中心地面坐标
 * </pre>
 * <p>
 * <h3>{@code findregion} 语义</h3>
 * <ul>
 *   <li><b>省略 name</b>：在命令源当前维度下的所有区域中，返回距离命令源（平面 XZ）最近的区域</li>
 *   <li><b>带 name</b>：按「显示名或区域 ID」匹配（任一命中），在匹配集合中返回最近的</li>
 * </ul>
 * 匹配规则：显示名（如「火山」）全等，<b>或</b> 区域 ID 完整形式（{@code rpgcraftcore:volcano}）
 * 或 path 部分（{@code volcano}）全等，任一命中即纳入候选。
 * <p>
 * <h3>中心地面坐标</h3>
 * 区域多边形 XZ 包围盒中心，Y 取该位置的地表高度（{@link Heightmap.Types#MOTION_BLOCKING}，
 * 即最高阻挡运动方块，含树叶/流体，最贴近「可站立的地面」）。
 *
 * @see RegionsRegistry#matchByName(String)
 */
@EventBusSubscriber(modid = RegionMod.MODID)
public class RegionCommands {

    private RegionCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 查找区域指令 ===
                .then(Commands.literal("findregion")
                        // 无权限要求：所有玩家可用（仅查询，不修改状态）
                        .executes(RegionCommands::executeFindNearest)
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(RegionCommands::executeFindByName))
                )

                // === 创建/定稿区域指令 ===
                // setregion <ID> <NAME> <SIZE> init  → 初始化草稿（玩家为中心的正方形）
                // setregion <ID> <NAME> done         → 定稿草稿为正式区域
                .then(Commands.literal("setregion")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    EnvironmentTypeRegistry.get().all().forEach(t ->
                                            builder.suggest(t.id().getPath()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("name", StringArgumentType.string())
                                        // init 分支：需要 SIZE 参数
                                        .then(Commands.argument("size", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .then(Commands.literal("init")
                                                        .executes(RegionCommands::executeSetRegionInit)))
                                        // done 分支：忽略 SIZE
                                        .then(Commands.literal("done")
                                                .executes(RegionCommands::executeSetRegionDone))
                                )
                        )
                )

                // === 添加点到草稿指令 ===
                // addregion <NAME> → 将玩家当前整数坐标加入 NAME 草稿
                .then(Commands.literal("addregion")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(RegionCommands::executeAddRegion))
                )

                // === 删除运行时区域指令 ===
                // delregion <NAME> → 删除 NAME 运行时区域
                .then(Commands.literal("delregion")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(RegionCommands::executeDelRegion))
                )

                // === 区域进出提示开关 ===
                // regionnotify          → 查看当前状态
                // regionnotify on|off   → 开关提示
                .then(Commands.literal("regionnotify")
                        // 无权限要求：所有玩家可控制自己的提示开关
                        .executes(RegionCommands::executeRegionNotifyStatus)
                        .then(Commands.literal("on")
                                .executes(RegionCommands::executeRegionNotifyOn))
                        .then(Commands.literal("off")
                                .executes(RegionCommands::executeRegionNotifyOff))
                )
        );
    }

    // === findregion（省略 name：当前维度下所有区域中最近） ===

    private static int executeFindNearest(CommandContext<CommandSourceStack> context) {
        return doFind(context, null);
    }

    // === findregion <name>（按显示名或 ID 匹配，取最近） ===

    private static int executeFindByName(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        return doFind(context, name);
    }

    /**
     * 查找并返回最近区域的中心地面坐标
     *
     * @param context 命令上下文
     * @param name    名称过滤（null 表示不过滤，在当前维度全部区域中找最近）
     * @return 1 成功，0 失败（无区域/无匹配）
     */
    private static int doFind(CommandContext<CommandSourceStack> context, String name) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ResourceKey<Level> dimension = level.dimension();
        Vec3 origin = source.getPosition();

        // 1. 确定候选区域集合
        List<Region> candidates;
        if (name == null) {
            // 省略 name：当前维度下所有区域（严格限制当前维度）
            candidates = RegionsRegistry.get().inDimension(dimension);
            if (candidates.isEmpty()) {
                source.sendFailure(Component.literal("当前维度下没有定义任何区域"));
                return 0;
            }
        } else {
            // 带 name：按显示名或 ID 匹配（所有维度）
            candidates = RegionsRegistry.get().matchByName(name);
            if (candidates.isEmpty()) {
                source.sendFailure(Component.literal("没有找到匹配的区域: " + name));
                return 0;
            }
        }

        // 2. 优先在当前维度内按平面距离找最近；当前维度无候选时回退到任意维度
        Region nearest = findNearestInDimension(candidates, dimension, origin);
        boolean crossDimension = false;
        if (nearest == null) {
            // 当前维度无匹配（仅带 name 的跨维度场景可能发生），取候选中第一个
            nearest = candidates.get(0);
            crossDimension = true;
        }

        // 3. 计算中心地面坐标
        RegionPolygon poly = nearest.getPolygon();
        int cx = poly.centerX();
        int cz = poly.centerZ();
        // 地面 Y：用区域所在维度的 ServerLevel 查询高度图
        // （跨维度场景下需取目标维度的 Level；同维度直接用命令源的 level）
        ServerLevel targetLevel = nearest.getDimension().equals(dimension)
                ? level : source.getServer().getLevel(nearest.getDimension());
        int groundY = poly.getMinY();
        if (targetLevel != null) {
            // 强制加载目标 chunk 到 FULL 状态：高度图只在已生成的 chunk 上有数据，
            // chunk 未生成时 getHeight 直接返回世界底部 minY（如 -64，落入虚空）。
            // 此处按 chunk 坐标请求 FULL chunk，触发地形生成后再查高度图。
            int chunkX = cx >> 4;
            int chunkZ = cz >> 4;
            targetLevel.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            groundY = targetLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
        }

        // 4. 计算距离（跨维度时距离无意义，不显示）
        String distLabel;
        if (crossDimension) {
            distLabel = "";
        } else {
            double dx = cx - origin.x;
            double dz = cz - origin.z;
            distLabel = String.format(" §7(距离约 %.1f 格)", Math.sqrt(dx * dx + dz * dz));
        }

        // 5. 返回结果
        String dimLabel = nearest.getDimension().identifier().toString();
        String nameLabel = nearest.getName().isEmpty() ? nearest.getId().toString() : nearest.getName();
        String finalDistLabel = distLabel;
        int finalGroundY = groundY;
        source.sendSuccess(() -> Component.literal(String.format(
                "§a[区域] §f%s §7(%s) §a中心地面坐标: §e%d, %d, %d%s",
                nameLabel, dimLabel, cx, finalGroundY, cz, finalDistLabel)), false);

        return 1;
    }

    /**
     * 在候选区域中，按当前维度优先 + 平面距离最近 找出最近区域
     *
     * @return 最近的区域；若候选中无当前维度区域则返回 null
     */
    private static Region findNearestInDimension(List<Region> candidates,
                                                  ResourceKey<Level> dimension, Vec3 origin) {
        Region nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (Region r : candidates) {
            if (!r.getDimension().equals(dimension)) continue; // 仅当前维度
            RegionPolygon poly = r.getPolygon();
            double dx = poly.centerX() - origin.x;
            double dz = poly.centerZ() - origin.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = r;
            }
        }
        return nearest;
    }

    // ==================================================================
    // setregion / addregion / delregion
    // ==================================================================

    // === setregion <ID> <NAME> <SIZE> init ===

    private static int executeSetRegionInit(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String envIdStr = StringArgumentType.getString(context, "id");
        String name = StringArgumentType.getString(context, "name");
        int size = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "size");

        // 1. 校验环境类型 ID 已注册
        EnvironmentType envType = EnvironmentTypeRegistry.get().match(envIdStr);
        if (envType == null) {
            source.sendFailure(Component.literal("未知的环境类型 ID: " + envIdStr
                    + "（可用：" + listEnvironmentIds() + "）"));
            return 0;
        }

        // 2. 校验 NAME 未被草稿或正式区域占用
        if (RegionDraftManager.exists(name)) {
            source.sendFailure(Component.literal("草稿 " + name + " 已存在，请先用 done 定稿或换个名称"));
            return 0;
        }
        if (RegionsRegistry.get().get(RegionDraftManager.runtimeRegionId(name)) != null) {
            source.sendFailure(Component.literal("区域 " + name + " 已存在（运行时）"));
            return 0;
        }

        // 3. 以玩家当前整数坐标为中心创建草稿（初始正方形）
        BlockPos center = player.blockPosition();
        RegionDraft draft = RegionDraftManager.initDraft(
                name, envType, player.level().dimension(), center.getX(), center.getZ(), size);

        source.sendSuccess(() -> Component.literal(String.format(
                "§a[区域] §f%s §7草稿已初始化（环境 %s，中心 %d, %d，边长 %d）：%d 个初始顶点。"
                        + "使用 §e/rpg addregion %s §7添加点",
                name, envType.displayName(), center.getX(), center.getZ(), size,
                draft.vertexCount(), name)), false);
        return 1;
    }

    // === setregion <ID> <NAME> done ===

    private static int executeSetRegionDone(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        source.getPlayerOrException(); // 确保有玩家（虽未用其位置）
        String envIdStr = StringArgumentType.getString(context, "id");
        String name = StringArgumentType.getString(context, "name");

        // 1. 校验环境类型 ID（必须与草稿 init 时一致）
        EnvironmentType envType = EnvironmentTypeRegistry.get().match(envIdStr);
        if (envType == null) {
            source.sendFailure(Component.literal("未知的环境类型 ID: " + envIdStr));
            return 0;
        }

        // 2. 定稿（校验草稿存在 + envType 一致 + 几何有效）
        Region region;
        try {
            region = RegionDraftManager.finalize(name, envType);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        // 3. 持久化 + 同步 registry + 重建索引
        RuntimeRegionSavedData.get(source.getServer()).addRegion(source.getServer(), region);

        source.sendSuccess(() -> Component.literal(String.format(
                "§a[区域] §f%s §7已建立（环境 %s，%d 个顶点，ID %s）",
                name, envType.displayName(), region.getPolygon().vertexCount(),
                region.getId())), true);
        return 1;
    }

    // === addregion <NAME> ===

    private static int executeAddRegion(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");

        // 1. 校验草稿存在
        RegionDraft draft = RegionDraftManager.get(name);
        if (draft == null) {
            source.sendFailure(Component.literal("草稿 " + name + " 不存在，请先用 setregion init 创建"));
            return 0;
        }

        // 2. 校验玩家维度 = 草稿维度
        if (!player.level().dimension().equals(draft.getDimension())) {
            source.sendFailure(Component.literal("维度不匹配：草稿在 "
                    + draft.getDimension().identifier() + "，你在 "
                    + player.level().dimension().identifier()));
            return 0;
        }

        // 3. 添加玩家当前整数坐标
        BlockPos pos = player.blockPosition();
        boolean added = draft.addPoint(new int[]{pos.getX(), pos.getZ()});

        if (added) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "§a[区域] §f%s §7已添加点 (%d, %d)，当前 %d 个顶点",
                    name, pos.getX(), pos.getZ(), draft.vertexCount())), false);
        } else {
            source.sendSuccess(() -> Component.literal(String.format(
                    "§e[区域] §f%s §7点 (%d, %d) 导致边界自相交，已抛弃",
                    name, pos.getX(), pos.getZ())), false);
        }
        return 1;
    }

    // === delregion <NAME> ===

    private static int executeDelRegion(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        net.minecraft.resources.Identifier regionId = RegionDraftManager.runtimeRegionId(name);

        // 1. 校验是运行时区域（非静态）
        if (!RegionsRegistry.get().isRuntime(regionId)) {
            source.sendFailure(Component.literal("区域 " + name + " 不是运行时区域"
                    + "（仅能删除 setregion 创建的区域）"));
            return 0;
        }

        // 2. 删除（持久化 + 同步 registry + 重建索引）
        boolean removed = RuntimeRegionSavedData.get(source.getServer())
                .removeRegion(source.getServer(), regionId);

        if (removed) {
            source.sendSuccess(() -> Component.literal(
                    "§a[区域] §f" + name + " §7已删除"), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("删除失败：区域 " + name + " 不存在"));
            return 0;
        }
    }

    /** 列出所有已注册环境类型 ID（path），用于错误提示 */
    private static String listEnvironmentIds() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (EnvironmentType t : EnvironmentTypeRegistry.get().all()) {
            if (!first) sb.append(", ");
            sb.append(t.id().getPath());
            first = false;
        }
        return sb.toString();
    }

    // ==================================================================
    // regionnotify（区域进出提示开关）
    // ==================================================================

    // === regionnotify（查看状态） ===

    private static int executeRegionNotifyStatus(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = player.getData(AttributeManager.PLAYER_PREFERENCES.get()).isRegionNotifyEnabled();
        context.getSource().sendSuccess(() -> Component.literal(
                "§7区域进出提示: " + (enabled ? "§a开启" : "§c关闭")
                        + " §7(/rpg regionnotify on|off)"), false);
        return 1;
    }

    // === regionnotify on ===

    private static int executeRegionNotifyOn(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return setRegionNotify(context, true);
    }

    // === regionnotify off ===

    private static int executeRegionNotifyOff(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return setRegionNotify(context, false);
    }

    /** 设置区域进出提示开关（写入 PlayerPreferences 附件，自动持久化） */
    private static int setRegionNotify(CommandContext<CommandSourceStack> context, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.getData(AttributeManager.PLAYER_PREFERENCES.get()).setRegionNotifyEnabled(enabled);
        context.getSource().sendSuccess(() -> Component.literal(
                "§7区域进出提示已" + (enabled ? "§a开启" : "§c关闭")), false);
        return 1;
    }
}
