package com.rpgcraft.region.spatial;

import com.rpgcraft.region.data.Region;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域位置查询门面
 * <p>
 * 给定一个实体或坐标，返回它当前所处的所有区域。内部委托 {@link RegionIndex}
 * 做 chunk 索引粗筛，再对候选区域做多边形 inside 精判。
 * <p>
 * <h3>性能特征</h3>
 * <ul>
 *   <li>O(1) chunk 查表 + 对少量候选区域做射线法 inside（顶点数通常 &lt; 10）</li>
 *   <li>单个实体查询耗时可忽略，适合每 N tick 对所有在线玩家调用</li>
 * </ul>
 *
 * @see RegionIndex
 */
public final class RegionLocator {

    private RegionLocator() {}

    /**
     * 查询实体当前所在的所有区域
     *
     * @param entity 目标实体
     * @return 区域列表（新建列表，可能为空）
     */
    public static List<Region> regionsAt(Entity entity) {
        return regionsAt(entity.level().dimension(), entity.position());
    }

    /**
     * 查询指定维度下某坐标点所在的所有区域
     *
     * @param dimension 维度 key
     * @param pos       世界坐标
     * @return 区域列表（新建列表，可能为空）
     */
    public static List<Region> regionsAt(ResourceKey<Level> dimension, Vec3 pos) {
        return regionsAt(dimension, pos.x, pos.y, pos.z);
    }

    /**
     * 查询指定维度下某整数方块坐标所在的所有区域
     *
     * @param dimension 维度 key
     * @param x         世界 X（浮点，取整后判定）
     * @param y         世界 Y
     * @param z         世界 Z（浮点，取整后判定）
     * @return 区域列表（新建列表，可能为空）
     */
    public static List<Region> regionsAt(ResourceKey<Level> dimension, double x, double y, double z) {
        // 取整数方块坐标（floor，向负无穷取整）
        int bx = floor(x);
        int by = floor(y);
        int bz = floor(z);
        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;

        List<Region> candidates = RegionIndex.get().regionsAtChunk(dimension, chunkX, chunkZ);
        if (candidates.isEmpty()) return Collections.emptyList();

        List<Region> hits = null;
        for (Region r : candidates) {
            if (r.getDimension().equals(dimension) && r.getPolygon().contains(bx, by, bz)) {
                if (hits == null) hits = new ArrayList<>(2);
                hits.add(r);
            }
        }
        return hits != null ? hits : Collections.emptyList();
    }

    /** 向负无穷取整（Math.floor 的 int 版本） */
    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
