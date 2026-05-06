package cn.net.pap.common.datastructure.xor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

/**
 * SimpleXorCryptoUtil 单元测试
 * 验证：正确性、对称性、原位处理效率
 */
public class SimpleXorCryptoUtilTest {

    @Test
    @DisplayName("基础功能测试：验证加密后再解密是否能够 100% 还原")
    void testFullCycle() {
        // 1. 准备原始模拟图像数据
        String originalText = "Deep Archive: Tea Culture Document Digitization 2026";
        byte[] originalData = originalText.getBytes();
        byte[] dataCopy = originalData.clone();

        // 2. 第一次处理（加密）
        SimpleXorCryptoUtil.processInPlace(dataCopy);

        // 验证数据确实变了
        Assertions.assertFalse(Arrays.equals(originalData, dataCopy), "加密后的数据不应与原始数据相同");

        // 3. 第二次处理（解密）
        SimpleXorCryptoUtil.processInPlace(dataCopy);

        // 4. 断言还原
        Assertions.assertArrayEquals(originalData, dataCopy, "两次异或后数据必须完全还原");
    }

    @Test
    @DisplayName("大文件性能测试：模拟 10MB 图像处理效率")
    void testLargeDataPerformance() {
        // 模拟 10MB 的图像字节数组
        int size = 10 * 1024 * 1024;
        byte[] largeData = new byte[size];
        new Random().nextBytes(largeData);

        long startTime = System.currentTimeMillis();

        // 执行原位加密
        SimpleXorCryptoUtil.processInPlace(largeData);
        // 执行原位解密
        SimpleXorCryptoUtil.processInPlace(largeData);

        long endTime = System.currentTimeMillis();

        // 异或运算极快，通常 10MB 在现代 CPU 上应在 10ms 以内
        Assertions.assertTrue((endTime - startTime) < 1000, "性能异常：异或处理速度过慢");
    }

    @Test
    @DisplayName("边界值测试：空数组处理")
    void testEdgeCases() {
        byte[] emptyData = new byte[0];
        // 不应抛出异常
        Assertions.assertDoesNotThrow(() -> SimpleXorCryptoUtil.processInPlace(emptyData));

        byte[] nullData = null;
        Assertions.assertDoesNotThrow(() -> SimpleXorCryptoUtil.processInPlace(nullData));
    }

    @Test
    @DisplayName("非原位处理测试：验证 process 方法是否返回新对象")
    void testProcessMethod() {
        byte[] original = {0x01, 0x02, 0x03, 0x04};
        byte[] result = SimpleXorCryptoUtil.process(original);

        // 验证引用不同
        Assertions.assertNotSame(original, result, "process 方法应当返回一个新数组副本");
        // 验证结果还原
        byte[] restored = SimpleXorCryptoUtil.process(result);
        Assertions.assertArrayEquals(original, restored);
    }
}
