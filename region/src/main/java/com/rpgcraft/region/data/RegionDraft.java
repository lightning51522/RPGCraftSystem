package com.rpgcraft.region.data;

import com.rpgcraft.region.spatial.ConcaveHull;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域草稿（内存态，不持久化）
 * <p>
 * 记录玩家通过 {@code setregion init} + {@code addregion} 构建中的区域状态：
 * <ul>
 *   <li>{@link #envTypeId}：init 时指定的环境类型 ID，done 时校验一致并套用其效果</li>
 *   <li>{@link #dimension}：init 时玩家所在维度，addregion 跨维度校验</li>
 *   <li>{@link #points}：累积的整数坐标点（含 init 正方形 4 顶点 + addregion 点）</li>
 *   <li>{@link #currentPolygon}：当前凹包边界（每次 addregion 增量重算）</li>
 * </ul>
 * <p>
 * <h3>凹包增量更新</h3>
 * init 时建立初始正方形（4 顶点的合法简单多边形）作为 {@link #currentPolygon}。
 * 每次 addregion 调用 {@link ConcaveHull#addPoint} 增量插入新点：成功则顶点+1，
 * 失败（导致自相交）则该点被抛弃（不入 points，保持 currentPolygon 不变）。
 * <p>
 * <h3>生命周期</h3>
 * 草稿仅存在于内存（{@link com.rpgcraft.region.RegionDraftManager}），服务器重启丢失。
 * done 后转为持久化的 {@link Region}，草稿随之移除。
 *
 * @see ConcaveHull
 * @see com.rpgcraft.region.RegionDraftManager
 */
public class RegionDraft {

    /** 草稿名（= 定稿后的区域显示名，全局唯一） */
    private final String name;
    /** 环境类型 ID（init 时记下，done 时校验一致） */
    private final Identifier envTypeId;
    /** 绑定维度（init 时记下，addregion 跨维度报错） */
    private final ResourceKey<Level> dimension;
    /** 累积的坐标点（含 init 正方形 4 顶点 + 成功 addregion 的点） */
    private final List<int[]> points;
    /** 当前凹包边界顶点序列（合法简单多边形，每次 addregion 增量更新） */
    private RegionPolygon currentPolygon;

    /**
     * 初始化草稿：以 center 为中心、边长 size 的正方形作为初始范围
     *
     * @param name      草稿名
     * @param envTypeId 环境类型 ID
     * @param dimension 维度
     * @param centerX   中心 X（整数）
     * @param centerZ   中心 Z（整数）
     * @param size      正方形边长（≥1）
     */
    public RegionDraft(String name, Identifier envTypeId, ResourceKey<Level> dimension,
                       int centerX, int centerZ, int size) {
        this.name = name;
        this.envTypeId = envTypeId;
        this.dimension = dimension;
        this.points = new ArrayList<>();

        // 构建初始正方形（4 顶点，逆时针）
        int half = size / 2;
        // 注意：size 为偶数时 half*size 精确；为奇数时取整，正方形略偏，但仍是合法矩形
        int minX = centerX - half;
        int maxX = centerX + half;
        int minZ = centerZ - half;
        int maxZ = centerZ + half;
        // 逆时针：左下 → 右下 → 右上 → 左上
        this.points.add(new int[]{minX, minZ});
        this.points.add(new int[]{maxX, minZ});
        this.points.add(new int[]{maxX, maxZ});
        this.points.add(new int[]{minX, maxZ});

        // 初始凹包 = 正方形本身（合法简单多边形）
        this.currentPolygon = new RegionPolygon(
                new ArrayList<>(this.points), RegionPolygon.DEFAULT_MIN_Y, RegionPolygon.DEFAULT_MAX_Y);
    }

    /** 草稿名 */
    public String getName() { return name; }

    /** 环境类型 ID */
    public Identifier getEnvTypeId() { return envTypeId; }

    /** 绑定维度 */
    public ResourceKey<Level> getDimension() { return dimension; }

    /** 累积点数（含初始正方形） */
    public int pointCount() { return points.size(); }

    /** 当前凹包边界顶点数 */
    public int vertexCount() {
        return currentPolygon != null ? currentPolygon.vertexCount() : 0;
    }

    /** 当前凹包几何（可能为 null，若 init 后未成功构建） */
    public RegionPolygon getCurrentPolygon() { return currentPolygon; }

    /**
     * 添加一个点，增量更新凹包边界
     * <p>
     * 基于 {@link #currentPolygon}（当前边界顶点）调用 {@link ConcaveHull#addPoint} 在最近
     * 顶点处尝试插入。若导致自相交则抛弃该点（不入 points，currentPolygon 不变）。
     *
     * @param point 待添加点 {x, z}（整数坐标）
     * @return true 若成功加入；false 若该点导致自相交被抛弃
     */
    public boolean addPoint(int[] point) {
        List<int[]> currentVertices = currentPolygon.getVertices();
        List<int[]> newHull = ConcaveHull.addPoint(currentVertices, point);
        if (newHull == null) {
            return false; // 自相交，抛弃
        }
        // 成功：记录坐标点，更新凹包边界
        points.add(point);
        currentPolygon = new RegionPolygon(
                new ArrayList<>(newHull), RegionPolygon.DEFAULT_MIN_Y, RegionPolygon.DEFAULT_MAX_Y);
        return true;
    }
}
