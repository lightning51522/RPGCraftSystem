package com.rpgcraft.region.spatial;

import java.util.ArrayList;
import java.util.List;

/**
 * 凹包（concave hull）增量构建算法：边界替换法
 * <p>
 * 在一个<b>合法的初始简单多边形</b>（由 {@code setregion init} 提供的正方形）基础上，
 * 逐个插入新点（由 {@code addregion} 添加），每次插入保持多边形为简单多边形（无自相交）。
 * <p>
 * <h3>算法：最近顶点处插入 + 自相交校验</h3>
 * 每次新增点 P：
 * <ol>
 *   <li>在当前边界顶点中找到离 P <b>欧氏距离最近</b>的顶点 V（距离平方比较，避免 sqrt）</li>
 *   <li>V 承接两条边：前驱边 {@code A→V} 和后继边 {@code V→B}</li>
 *   <li>尝试将 P 插入 V 旁，使 {@code A→V→B} 变为 {@code A→V→P→B}（P 在 V 后）</li>
 *   <li>若导致自相交，再尝试 {@code A→P→V→B}（P 在 V 前）</li>
 *   <li>两种插入都自相交 → <b>抛弃 P</b>（保持原边界不变）</li>
 * </ol>
 * <p>
 * 自相交校验：插入产生两条新边（如 {@code V→P} 和 {@code P→B}），检查它们是否与边界
 * 其他边相交（用 {@link SegmentIntersection}）。共享端点的相邻边不算相交（规范相交语义）。
 * <p>
 * <h3>正确性基础</h3>
 * 算法成立的前提是<b>初始多边形合法</b>（简单多边形）。{@code setregion init} 提供的正方形
 * 必然合法。每次插入都严格校验自相交，故结果始终是简单多边形。
 * <p>
 * <h3>"抛弃点"语义</h3>
 * 若 P 只能通过自相交的边插入（两种位置都冲突），则 P 不被加入边界。
 * 这满足需求"导致边界交叉则抛弃该点"。
 * <p>
 * <h3>复杂度</h3>
 * 单次插入 O(n)（n = 当前顶点数，扫描找最近 + 相交校验）。游戏内区域顶点数通常 &lt; 100，毫秒级。
 *
 * @see SegmentIntersection
 */
public final class ConcaveHull {

    private ConcaveHull() {}

    /**
     * 向合法多边形插入一个新点
     * <p>
     * 在离 P 最近的边界顶点 V 处尝试插入，保持简单多边形性质。
     *
     * @param hull 当前边界顶点序列（逆时针，未闭合：hull[0..n-1]，隐含 hull[n-1]→hull[0] 闭合）
     * @param p    待插入点 {x, z}（整数）
     * @return 插入后的新顶点序列（顶点数 +1）；若 P 导致自相交或已在边界上则返回 null（应抛弃）
     * @throws IllegalArgumentException hull 顶点数 &lt; 3
     */
    public static List<int[]> addPoint(List<int[]> hull, int[] p) {
        int n = hull.size();
        if (n < 3) {
            throw new IllegalArgumentException("初始多边形至少需要 3 个顶点，实际 " + n);
        }

        // 1. P 已在边界上 → 无需插入
        for (int[] v : hull) {
            if (v[0] == p[0] && v[1] == p[1]) {
                return null;
            }
        }

        // 2. 找离 P 最近的边界顶点 V（距离平方，避免 sqrt）
        int vIdx = 0;
        long bestDistSq = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            long dx = hull.get(i)[0] - p[0];
            long dz = hull.get(i)[1] - p[1];
            long distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                vIdx = i;
            }
        }

        int aIdx = (vIdx - 1 + n) % n;  // V 的前驱 A
        int bIdx = (vIdx + 1) % n;       // V 的后继 B
        int[] V = hull.get(vIdx);
        int[] A = hull.get(aIdx);
        int[] B = hull.get(bIdx);

        // 3. 尝试位置1：V→P→B（在 vIdx 之后插入 P，原边 V→B 被替换为 V→P + P→B）
        if (canInsert(hull, vIdx, bIdx, V, p, B)) {
            List<int[]> result = new ArrayList<>(hull);
            result.add(vIdx + 1, p);
            return result;
        }

        // 4. 尝试位置2：A→P→V（在 vIdx 之前插入 P，原边 A→V 被替换为 A→P + P→V）
        if (canInsert(hull, aIdx, vIdx, A, p, V)) {
            List<int[]> result = new ArrayList<>(hull);
            result.add(vIdx, p);
            return result;
        }

        // 5. 两种位置都导致自相交 → 抛弃 P
        return null;
    }

    /**
     * 检查在某条边处插入 P 是否合法（新边不与现有边界边相交）
     * <p>
     * 插入语义：原边 {@code edgeStart→edgeEnd}（hull 中索引 edgeStartIdx→edgeEndIdx）
     * 被替换为 {@code edgeStart→P} + {@code P→edgeEnd}。
     *
     * @param hull         当前边界
     * @param edgeStartIdx 被替换边的起点索引
     * @param edgeEndIdx   被替换边的终点索引（= (edgeStartIdx+1)%n）
     * @param edgeStart    被替换边起点坐标
     * @param p            插入点
     * @param edgeEnd      被替换边终点坐标
     * @return true 若插入合法（新边不与现有边相交）
     */
    private static boolean canInsert(List<int[]> hull, int edgeStartIdx, int edgeEndIdx,
                                      int[] edgeStart, int[] p, int[] edgeEnd) {
        int n = hull.size();
        // 两条新边：edgeStart→p, p→edgeEnd
        for (int i = 0; i < n; i++) {
            // 跳过被替换的原边（它会被新边取代，不参与相交判定）
            if (i == edgeStartIdx) continue;
            int[] e1 = hull.get(i);
            int[] e2 = hull.get((i + 1) % n);
            // 新边1 (edgeStart→p) 与 hull 边 (e1→e2)
            if (SegmentIntersection.segmentsIntersect(edgeStart, p, e1, e2)) return false;
            // 新边2 (p→edgeEnd) 与 hull 边 (e1→e2)
            if (SegmentIntersection.segmentsIntersect(p, edgeEnd, e1, e2)) return false;
        }
        return true;
    }
}
