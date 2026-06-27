package com.rpgcraft.region.spatial;

/**
 * 线段相交判定工具（2D，XZ 平面）
 * <p>
 * 基于向量叉积（CCW orientation）的标准算法，用于 {@link ConcaveHull} 检测候选边是否与
 * 已建边界边相交（避免多边形自相交）。
 * <p>
 * <h3>orientation 语义</h3>
 * 三点 O→A→B 的方向：
 * <ul>
 *   <li>&gt; 0：A→B 相对 O→A 左转（逆时针）</li>
 *   <li>&lt; 0：右转（顺时针）</li>
 *   <li>= 0：三点共线</li>
 * </ul>
 * 叉积用 {@code long} 运算避免整数坐标相乘溢出。
 *
 * @see ConcaveHull
 */
public final class SegmentIntersection {

    private SegmentIntersection() {}

    /**
     * 判断线段 (p1,p2) 与线段 (p3,p4) 是否规范相交（有真正交点，非端点相切）
     * <p>
     * 标准算法：两线段规范相交当且仅当每条线段的两端点分别位于另一条线段的两侧
     *（即 orientation 符号严格相反）。
     * <p>
     * <b>端点共享不算相交</b>：若一线段的端点恰好落在另一线段上或端点上，视为不相交。
     * 这正是凹包算法需要的语义——相邻边共享顶点不应被误判为自相交。
     *
     * @param p1 第一条线段起点 {x, z}
     * @param p2 第一条线段终点 {x, z}
     * @param p3 第二条线段起点 {x, z}
     * @param p4 第二条线段终点 {x, z}
     * @return true 若两线段规范相交（有内部交点）
     */
    public static boolean segmentsIntersect(int[] p1, int[] p2, int[] p3, int[] p4) {
        long o1 = orientation(p1, p2, p3);
        long o2 = orientation(p1, p2, p4);
        long o3 = orientation(p3, p4, p1);
        long o4 = orientation(p3, p4, p2);

        // 规范相交：两对 orientation 严格异号
        if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0))
                && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) {
            return true;
        }

        // 共线情况：此处不处理（端点落在另一线段上的退化情形）。
        // 凹包算法中点集已去重、且坐标为整数，共线退化罕见；若发生由调用方逻辑兜底。
        return false;
    }

    /**
     * 三点 O→A→B 的方向（叉积）
     * <p>
     * 叉积 = (A.x - O.x) * (B.z - O.z) - (A.z - O.z) * (B.x - O.x)
     *
     * @param o 原点 {x, z}
     * @param a 第一点 {x, z}
     * @param b 第二点 {x, z}
     * @return &gt;0 左转，&lt;0 右转，=0 共线
     */
    private static long orientation(int[] o, int[] a, int[] b) {
        return (long) (a[0] - o[0]) * (b[1] - o[1])
                - (long) (a[1] - o[1]) * (b[0] - o[0]);
    }
}
