package com.rpgcraft.region.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * XZ 平面多边形 + Y 范围构成的柱体区域几何
 * <p>
 * 水平面 (X, Z) 是一个封闭多边形，顶点坐标均为整数；纵向由 [{@link #minY}, {@link #maxY}]
 * 限定柱体高度。区域在三维世界中的形状是「从 minY 到 maxY 的多边形拉伸柱体」。
 * <p>
 * <h3>判定顺序</h3>
 * <ol>
 *   <li><b>AABB 包围盒粗筛</b>：{@link #aabbContains(int, int, int)} 快速排除明显在外的点</li>
 *   <li><b>Y 范围判定</b>：{@code minY <= y <= maxY}</li>
 *   <li><b>XZ 多边形 inside</b>：{@link #containsXZ(int, int)} 射线法精确判定</li>
 * </ol>
 * <p>
 * <h3>边界处理</h3>
 * 射线法对落在多边形边上的点结果不稳定（取决于交点奇偶）。本实现约定：
 * <b>落在边上视为在区域内</b>（偏向「包含」）。整数坐标下此约定实现简单且确定。
 * <p>
 * <h3>不变量</h3>
 * 顶点数 ≥ 3（构造/加载时校验，违反则抛异常或跳过）。多边形不要求凸，
 * 但自相交多边形行为未定义（射线法对自相交边结果不确定）。
 *
 * @see Region
 */
public final class RegionPolygon {

    /** Y 范围缺省下界（覆盖整个世界高度） */
    public static final int DEFAULT_MIN_Y = -64;
    /** Y 范围缺省上界（覆盖整个世界高度） */
    public static final int DEFAULT_MAX_Y = 320;

    /**
     * DFU Codec：解析 {@code [[x,z], ...]} 顶点数组 + 可选 {@code y_range: [min,max]}
     * <p>
     * JSON 形如：
     * <pre>{@code
     * {
     *   "polygon": [[100,100],[300,100],[300,300],[100,300]],
     *   "y_range": [60, 120]   // 可选，缺省全高
     * }
     * }</pre>
     */
    public static final Codec<RegionPolygon> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(Codec.list(Codec.INT, 2, 2))
                            .fieldOf("polygon")
                            .forGetter(p -> packPoints(p.points)),
                    Codec.list(Codec.INT, 2, 2)
                            .optionalFieldOf("y_range", List.of(DEFAULT_MIN_Y, DEFAULT_MAX_Y))
                            .forGetter(p -> List.of(p.minY, p.maxY))
            ).apply(instance, RegionPolygon::fromCodec)
    );

    /** 多边形顶点（XZ 整数对），按顺序连接，首尾自动闭合 */
    private final List<int[]> points;
    /** 纵向下界（含） */
    private final int minY;
    /** 纵向上界（含） */
    private final int maxY;
    /** XZ 包围盒：minX */
    private final int minBoxX;
    /** XZ 包围盒：maxX */
    private final int maxBoxX;
    /** XZ 包围盒：minZ */
    private final int minBoxZ;
    /** XZ 包围盒：maxZ */
    private final int maxBoxZ;

    /**
     * 从顶点列表构造
     *
     * @param points XZ 顶点列表（每个元素为长度 2 的 {@code [x, z]}）
     * @param minY   Y 下界（含）
     * @param maxY   Y 上界（含）
     * @throws IllegalArgumentException 顶点数 &lt; 3 或 minY &gt; maxY
     */
    public RegionPolygon(List<int[]> points, int minY, int maxY) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("多边形至少需要 3 个顶点，实际 " + points.size());
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY (" + minY + ") 不能大于 maxY (" + maxY + ")");
        }
        this.points = new ArrayList<>(points);
        this.minY = minY;
        this.maxY = maxY;

        // 预算 XZ 包围盒，供 AABB 粗筛
        int bxMin = Integer.MAX_VALUE, bxMax = Integer.MIN_VALUE;
        int bzMin = Integer.MAX_VALUE, bzMax = Integer.MIN_VALUE;
        for (int[] p : points) {
            if (p[0] < bxMin) bxMin = p[0];
            if (p[0] > bxMax) bxMax = p[0];
            if (p[1] < bzMin) bzMin = p[1];
            if (p[1] > bzMax) bzMax = p[1];
        }
        this.minBoxX = bxMin;
        this.maxBoxX = bxMax;
        this.minBoxZ = bzMin;
        this.maxBoxZ = bzMax;
    }

    /** 顶点数量 */
    public int vertexCount() {
        return points.size();
    }

    /** XZ 包围盒 minX（供 chunk 索引预算） */
    public int getMinBoxX() { return minBoxX; }

    /** XZ 包围盒 maxX */
    public int getMaxBoxX() { return maxBoxX; }

    /** XZ 包围盒 minZ */
    public int getMinBoxZ() { return minBoxZ; }

    /** XZ 包围盒 maxZ */
    public int getMaxBoxZ() { return maxBoxZ; }

    /** Y 下界（含） */
    public int getMinY() { return minY; }

    /** Y 上界（含） */
    public int getMaxY() { return maxY; }

    /**
     * 完整判定：点 (x, y, z) 是否在柱体内（AABB + Y + XZ 多边形）
     *
     * @param x 世界 X 坐标
     * @param y 世界 Y 坐标
     * @param z 世界 Z 坐标
     * @return true 若在柱体内
     */
    public boolean contains(int x, int y, int z) {
        // 1. AABB 粗筛（含 Y 范围）
        if (!aabbContains(x, y, z)) return false;
        // 2. XZ 多边形精确判定
        return containsXZ(x, z);
    }

    /**
     * AABB 包围盒判定（含 Y 范围）
     * <p>
     * 比 {@link #contains} 快，用于 chunk 索引命中后的二次粗筛。
     */
    public boolean aabbContains(int x, int y, int z) {
        return y >= minY && y <= maxY
                && x >= minBoxX && x <= maxBoxX
                && z >= minBoxZ && z <= maxBoxZ;
    }

    /**
     * XZ 多边形 inside 判定（射线法）
     * <p>
     * 从点向 +X 方向发射水平射线，统计与多边形边的交点数：
     * 奇数次 = 内部，偶数次 = 外部。落在边上的点视为内部。
     *
     * @param x 世界 X 坐标
     * @param z 世界 Z 坐标
     * @return true 若 (x,z) 在多边形内或边上
     */
    public boolean containsXZ(int x, int z) {
        int n = points.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int[] pi = points.get(i);
            int[] pj = points.get(j);
            int xi = pi[0], zi = pi[1];
            int xj = pj[0], zj = pj[1];

            // 检查边 (pj→pi) 是否穿过点所在水平线 z
            // 条件：边的两端 z 分居 z 两侧（一个 <= z，另一个 > z）
            boolean zSpans = (zi > z) != (zj > z);
            if (!zSpans) continue;

            // 计算边在 z 水平线上的交点 x 坐标
            // 线性插值：x = xj + (z - zj) * (xi - xj) / (zi - zj)
            // 用 long 避免整数溢出（坐标可能很大）
            long dx = (long) xi - xj;
            long dz = (long) zi - zj;
            long crossX = xj + (long) (z - zj) * dx / dz;

            // 交点在点的右侧或正好在点上 → 翻转 inside（射线向 +X）
            // 用 <= 使落在边上的点视为内部
            if (x <= crossX) {
                inside = !inside;
            }
        }
        return inside;
    }

    // ---- Codec 辅助：List<int[]> ↔ List<List<Integer>> ----

    private static List<List<Integer>> packPoints(List<int[]> pts) {
        List<List<Integer>> out = new ArrayList<>(pts.size());
        for (int[] p : pts) {
            out.add(List.of(p[0], p[1]));
        }
        return out;
    }

    private static RegionPolygon fromCodec(List<List<Integer>> polygon, List<Integer> yRange) {
        List<int[]> pts = new ArrayList<>(polygon.size());
        for (List<Integer> pair : polygon) {
            pts.add(new int[]{pair.get(0), pair.get(1)});
        }
        int min = yRange.size() >= 1 ? yRange.get(0) : DEFAULT_MIN_Y;
        int max = yRange.size() >= 2 ? yRange.get(1) : DEFAULT_MAX_Y;
        return new RegionPolygon(pts, min, max);
    }
}
