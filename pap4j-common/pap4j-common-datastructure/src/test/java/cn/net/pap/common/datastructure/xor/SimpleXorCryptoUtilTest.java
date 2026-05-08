package cn.net.pap.common.datastructure.xor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * SimpleXorCryptoUtil 单元测试
 * 验证：正确性、对称性、原位处理效率
 */
public class SimpleXorCryptoUtilTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleXorCryptoUtilTest.class);

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

    @Test
    @DisplayName("java端加密图像，然后rust层生成wasm解密图像，将如下生成的这个数据放到rust_wasm.html中进行使用")
    public void testImageEncryptionForHtml() throws IOException {
        // 1. 创建 2x2 测试图像
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(0, 1, Color.GREEN.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        image.setRGB(1, 1, Color.WHITE.getRGB());

        // 2. 转为 BMP 字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", baos);
        byte[] originalBytes = baos.toByteArray();

        // 3. 调用工具类加密
        byte[] encryptedBytes = originalBytes.clone();
        SimpleXorCryptoUtil.processInPlace(encryptedBytes);

        // 4. 构建 JS 数组格式字符串
        StringBuilder jsArrayBuilder = new StringBuilder();
        jsArrayBuilder.append("const encryptedData = new Uint8Array([");

        for (int i = 0; i < encryptedBytes.length; i++) {
            // Java byte 有符号 (-128~127)，转为无符号整数 (0~255)
            jsArrayBuilder.append(Byte.toUnsignedInt(encryptedBytes[i]));
            if (i < encryptedBytes.length - 1) {
                jsArrayBuilder.append(",");
            }
        }
        jsArrayBuilder.append("]);");

        // 5. 使用 log.info 输出结果
        log.info("图像加密完成，原始大小: {} bytes", originalBytes.length);
        log.info("复制以下代码到 HTML 中使用:\n{}", jsArrayBuilder.toString());
    }

}
