package cn.net.pap.common.md5.jmh;

import net.coobird.thumbnailator.Thumbnails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thumbnailator 内存影响测试
 */
public class ThumbnailatorConserveMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailatorConserveMemoryTest.class);
    // todo important
    private static final String KEY = "thumbnailator.conserveMemoryWorkaround";

    private byte[] imageBytes; // 模拟服务器上的图片二进制数据
    private int imageWidth;
    private int imageHeight;
    private static final int ITERATIONS = 10;
    private static final int WARM_UP = 3;

    @BeforeEach
    public void setUp() throws Exception {
        // 在内存中生成一张 4000x3000 的大图
        this.imageWidth = 4000;
        this.imageHeight = 3000;
        BufferedImage img = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "jpg", baos);
            this.imageBytes = baos.toByteArray();
        }
        log.info("测试图片已生成，分辨率: {}x{}，大小: {} KB", imageWidth, imageHeight, (this.imageBytes.length / 1024));
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(KEY);
        imageBytes = null;
    }

    @Test
    public void testConserveMemoryImpact() throws Exception {
        // 1. 测试默认模式 (false)
        System.clearProperty(KEY);
        double[] defaultAvg = runTest("默认模式(false)");

        System.gc();
        Thread.sleep(500);

        // 2. 测试优化模式 (true)
        System.setProperty(KEY, "true");
        double[] optimizedAvg = runTest("优化模式(true)");

        // 3. 结果输出与业务断言
        log.info("【测试图像信息】分辨率: {}x{} | 大小: {} KB", imageWidth, imageHeight, (this.imageBytes.length / 1024));
        log.info("【对比结果】默认平均: {} MB | 优化平均: {} MB", String.format("%.2f", defaultAvg[0]), String.format("%.2f", optimizedAvg[0]));
        log.info("【对比结果】默认平均: {} MS | 优化平均: {} MS", String.format("%.2f", defaultAvg[1]), String.format("%.2f", optimizedAvg[1]));

        if (optimizedAvg[0] < defaultAvg[0]) {
            double savedPercent = (1 - optimizedAvg[0] / defaultAvg[0]) * 100;
            log.info("优化成功，InputStream 解码内存分配减少了: {}%", String.format("%.2f", savedPercent));
        }

        assertTrue(optimizedAvg[0] < defaultAvg[0], "优化模式下的 InputStream 内存分配应当低于默认模式");
    }

    private double[] runTest(String modeName) throws Exception {
        // 预热
        for (int i = 0; i < WARM_UP; i++) {
            generateThumbnail();
        }

        // 正式计数
        long startBytes = getThreadAllocatedBytes();
        long startTime = System.nanoTime(); // 纳秒级精确耗时

        for (int i = 0; i < ITERATIONS; i++) {
            generateThumbnail();
        }

        long endTime = System.nanoTime();
        long totalAllocated = getThreadAllocatedBytes() - startBytes;

        // 计算平均值
        double avgMB = (totalAllocated / (1024.0 * 1024.0)) / ITERATIONS;
        double avgTimeMs = ((endTime - startTime) / 1_000_000.0) / ITERATIONS; // 毫秒

        log.info("{} 完成 -> 平均每次分配内存: {} MB | 平均每次耗时: {} ms", modeName, String.format("%.2f", avgMB), String.format("%.2f", avgTimeMs));

        return new double[]{avgMB, avgTimeMs};
    }

    /**
     * 每次处理都通过 getFtpStream() 获取一个全新的输入流
     */
    private void generateThumbnail() throws Exception {
        try (InputStream ftpStream = getFtpStream()) {
            Thumbnails.of(ftpStream).size(141, Integer.MAX_VALUE).outputFormat("jpg").asBufferedImage();
        }
    }

    private InputStream getFtpStream() {
        return new ByteArrayInputStream(this.imageBytes);
    }

    private long getThreadAllocatedBytes() {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        return bean.isThreadAllocatedMemorySupported() ? bean.getThreadAllocatedBytes(Thread.currentThread().getId()) : 0;
    }

}