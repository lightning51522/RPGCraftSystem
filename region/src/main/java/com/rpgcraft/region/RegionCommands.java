package com.rpgcraft.region;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.data.RegionPolygon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
}
