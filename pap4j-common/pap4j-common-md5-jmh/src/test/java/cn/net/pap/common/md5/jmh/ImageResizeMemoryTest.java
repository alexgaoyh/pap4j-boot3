package cn.net.pap.common.md5.jmh;

import net.coobird.thumbnailator.Thumbnails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Iterator;

/**
 * 图片缩放内存占用对比测试类
 * <p>
 * 本测试旨在验证并量化两种不同图片缩放策略在“内存分配”上的巨大差异：
 * <ol>
 * <li>
 * <b>传统工具类方式 (Thumbnailator)</b>：
 * 通常会将整张原图的像素数据完全加载到内存中（形成巨大的 BufferedImage），然后再进行缩放运算。
 * 对于大分辨率图片（如 4000x3000），极易造成内存飙升甚至 OOM。
 * </li>
 * <li>
 * <b>低内存流式降采样方式 (ImageReader + Subsampling)</b>：
 * 在图片解码读取阶段，利用 {@link ImageReadParam#setSourceSubsampling} 设定采样率。
 * 底层解析时会直接跳过不需要的像素，只把目标大小的像素加载进内存。既省 CPU 又极大节省了内存。
 * </li>
 * </ol>
 * <p>
 * <b>测试原理：</b>
 * 使用 {@link com.sun.management.ThreadMXBean} 获取当前线程自启动以来分配的对象内存总量。
 * 这种方法从 JVM 底层统计累加值，不会受到垃圾回收（GC）导致的对象释放干扰，比测算 Runtime.getRuntime().freeMemory() 准确得多。
 * 测试包含了“预热阶段”，以排除类加载和 JIT 编译产生的额外内存分配对测试结果的干扰。
 */
public class ImageResizeMemoryTest {

    private File file;

    @BeforeEach
    public void setUp() throws Exception {
        // 1. 生成一张较大的测试图片 (例如 4000x3000 的大图)
        // 这样可以明显放大两种方法的内存差异
        file = File.createTempFile("test_large_image", ".jpg");
        BufferedImage img = new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, 4000, 3000);
        g2d.dispose();
        ImageIO.write(img, "jpg", file);
        System.out.println("测试图片已生成，大小: " + (file.length() / 1024) + " KB");
    }

    @AfterEach
    public void tearDown() {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    @Test
    public void compareMemoryAllocation() throws Exception {
        // 2. 预热阶段 (Warm-up)
        // 目的: 强制触发类的加载、静态变量初始化以及 JIT 编译，防止这些额外开销算入后续的内存测量中
        System.out.println("--- 开始预热 ---");
        for (int i = 0; i < 3; i++) {
            thumb_thumbnailator();
            thumb_lowMemory();
        }
        System.out.println("--- 预热完成 ---\n");

        int testIterations = 10; // 测试循环次数，取平均值更准确

        // 3. 测试 Thumbnailator 内存分配
        long startMem1 = getThreadAllocatedBytes();
        for (int i = 0; i < testIterations; i++) {
            BufferedImage img1 = thumb_thumbnailator();
        }
        long endMem1 = getThreadAllocatedBytes();
        long thumbnailatorTotalMem = endMem1 - startMem1;
        double thumbnailatorAvgMb = (thumbnailatorTotalMem / (double) testIterations) / 1024.0 / 1024.0;

        // 4. 测试 LowMemory (Subsampling) 内存分配
        long startMem2 = getThreadAllocatedBytes();
        for (int i = 0; i < testIterations; i++) {
            BufferedImage img2 = thumb_lowMemory();
        }
        long endMem2 = getThreadAllocatedBytes();
        long lowMemoryTotalMem = endMem2 - startMem2;
        double lowMemoryAvgMb = (lowMemoryTotalMem / (double) testIterations) / 1024.0 / 1024.0;

        // 5. 打印对比结果
        System.out.println("========== 内存分配测试结果 ==========");
        System.out.printf("Thumbnailator 平均每次分配: %.2f MB\n", thumbnailatorAvgMb);
        System.out.printf("LowMemory 方法 平均每次分配: %.2f MB\n", lowMemoryAvgMb);
        System.out.printf("内存消耗比例: Thumbnailator 是 LowMemory 的 %.1f 倍\n", thumbnailatorAvgMb / lowMemoryAvgMb);
        System.out.println("======================================");
    }

    // --- 以下为你提供的方法 ---

    public BufferedImage thumb_thumbnailator() throws Exception {
        return Thumbnails.of(file).size(141, Integer.MAX_VALUE).keepAspectRatio(true).outputFormat("jpg").outputQuality(0.7).asBufferedImage();
    }

    public BufferedImage thumb_lowMemory() throws Exception {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int actualWidth = reader.getWidth(0);
                int sampling = Math.max(actualWidth / 141, 1);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sampling, sampling, 0, 0);

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private long getThreadAllocatedBytes() {
        try {
            com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            return threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (Exception e) {
            System.err.println("当前 JVM 不支持 ThreadMXBean 内存分配监控");
            return 0;
        }
    }
}