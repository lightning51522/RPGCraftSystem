package com.rpgcraft.core.equipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EquipmentLevelStars#stars} 的纯逻辑单元测试。
 * <p>
 * 验证 0~6 级的星形后缀算法（前 3 级空心星、4~6 级依次变实心），及越界钳制。
 */
class EquipmentLevelStarsTest {

    @Test
    void level0_isEmpty() {
        assertEquals("", EquipmentLevelStars.stars(0));
    }

    @Test
    void negative_isEmpty() {
        assertEquals("", EquipmentLevelStars.stars(-1));
        assertEquals("", EquipmentLevelStars.stars(-100));
    }

    @Test
    void levels1to3_areHollowStars() {
        assertEquals("☆", EquipmentLevelStars.stars(1));
        assertEquals("☆☆", EquipmentLevelStars.stars(2));
        assertEquals("☆☆☆", EquipmentLevelStars.stars(3));
    }

    @Test
    void levels4to6_fillLeftToRight() {
        assertEquals("★☆☆", EquipmentLevelStars.stars(4));
        assertEquals("★★☆", EquipmentLevelStars.stars(5));
        assertEquals("★★★", EquipmentLevelStars.stars(6));
    }

    @Test
    void aboveMax_isClampedTo6() {
        assertEquals("★★★", EquipmentLevelStars.stars(7));
        assertEquals("★★★", EquipmentLevelStars.stars(100));
    }

    @Test
    void alwaysAtMostThreeStarPositions() {
        // 任何等级的星位数都不超过 3
        for (int level = 0; level <= 6; level++) {
            String s = EquipmentLevelStars.stars(level);
            // 去掉实心+空心字符后应无剩余
            int len = s.replace("★", "").replace("☆", "").length();
            assertEquals(0, len, "level " + level + " 含非法字符: " + s);
            assertTrue(s.length() <= 3, "level " + level + " 星位数超过 3: " + s);
        }
    }
}
