package cn.net.pap.common.datastructure.jumpGame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跳跃游戏
 */
public class JumpGameTest {

    @Test
    public void test() {
        Integer[] nums1 = new Integer[]{2, 3, 1, 1, 4};
        Integer[] nums2 = new Integer[]{3, 2, 1, 0, 4};
        boolean nums1Bool = canJump(nums1);
        boolean nums2Bool = canJump(nums2);
        assertTrue(nums1Bool == true);
        assertTrue(nums2Bool == false);
    }

    /**
     * 跳跃游戏 看看 cover 能不能覆盖掉所有的数组，能覆盖掉就说明可以。
     *
     * @param nums
     * @return
     */
    public static boolean canJump(Integer[] nums) {
        int location = 0;
        int cover = 0;
        if (nums.length <= 1) {
            return true;
        }
        for (; location < nums.length - 1; location++) {
            cover = Math.max(nums[location] + location, cover);
            if (location >= cover) {
                return false;
            }
            if (cover >= nums.length - 1) {
                return true;
            }
        }
        return false;
    }

}
