package cn.net.pap.common.vips;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * libvips 图像处理器单元测试与压力测试
 */
public class VipsImageProcessorTest {
    private static final Logger log = LoggerFactory.getLogger(VipsImageProcessorTest.class);

    @TempDir
    static File tempDir;

    private static File inputFile;
    private static final int CONCURRENT_THREADS = 10;
    private static final int TOTAL_REQUESTS = 1000;

    @BeforeAll
    public static void setUpAll() throws IOException {
        // 1. 初始化 libvips
        log.info("在运行测试前初始化 libvips 以隔离动态库加载耗时...");
        VipsImageProcessor.ensureInitialized();
        log.info("libvips 预初始化完成");

        // 2. 创建一张 800x600 带细节线条的测试源图片并保存为 PNG（一次性创建，所有用例复用）
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 800, 600);
        g.setColor(Color.YELLOW);
        g.drawLine(0, 0, 800, 600);
        g.dispose();

        inputFile = new File(tempDir, "input_test.png");
        ImageIO.write(img, "png", inputFile);
        log.info("测试源图片创建成功: {}", inputFile.getAbsolutePath());
    }

    @AfterAll
    public static void tearDownAll() {
        // 所有测试都合并到了一个类中，在类执行结束时显式关闭 libvips 是安全且推荐的
        VipsImageProcessor.shutdown();
    }

    /**
     * 测试转码为 WebP 格式
     */
    @Test
    public void testConvertFormatToWebp() throws Exception {
        File outputFile = new File(tempDir, "output_test.webp");

        log.info("正在使用 JNA libvips 将测试 PNG 转换为 WebP...");
        VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());

        // 验证输出文件存在且不为空
        assertTrue(outputFile.exists(), "应当成功创建 WebP 输出文件");
        assertTrue(outputFile.length() > 0, "输出文件体积应当大于 0");

        // 通过读取文件开头的 RIFF/WEBP 头部字节来校验 WebP 格式有效性
        byte[] header = new byte[12];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
            int read = fis.read(header);
            assertEquals(12, read, "读取的头部字节长度应当为 12");
        }
        String riff = new String(header, 0, 4);
        String webp = new String(header, 8, 4);
        assertEquals("RIFF", riff, "WebP 文件必须以 RIFF 开头");
        assertEquals("WEBP", webp, "WebP 文件头必须包含 WEBP");
        log.info("成功转换 PNG 到 WebP 并校验 RIFF/WEBP 文件头通过");
    }

    /**
     * 测试转码为 JPEG 格式
     */
    @Test
    public void testConvertFormatToJpg() throws Exception {
        File outputFile = new File(tempDir, "output_test.jpg");

        log.info("正在使用 JNA libvips 将测试 PNG 转换为 JPEG...");
        VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());

        assertTrue(outputFile.exists(), "应当成功创建 JPEG 输出文件");
        assertTrue(outputFile.length() > 0, "输出文件体积应当大于 0");

        // 验证输出的图像可以正常被 ImageIO 读取，且分辨率保持 800x600
        BufferedImage outputImg = ImageIO.read(outputFile);
        assertEquals(800, outputImg.getWidth(), "输出图像宽度应仍为 800");
        assertEquals(600, outputImg.getHeight(), "输出图像高度应仍为 600");
        log.info("成功转换 PNG 到 JPEG，验证分辨率为: {}x{}", outputImg.getWidth(), outputImg.getHeight());
    }

    /**
     * 并发压力测试：验证吞吐量与内存稳定性
     */
    @Test
    public void runStressTest() throws Exception {
        log.info("=== 开始 libvips JNA 并发压力测试 ===");
        log.info("总请求数: {}, 并发线程数: {}", TOTAL_REQUESTS, CONCURRENT_THREADS);

        // 记录初始 JVM 堆内存状况
        System.gc();
        Thread.sleep(500);
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long initialUsedHeap = memoryMXBean.getHeapMemoryUsage().getUsed();
        log.info("初始 JVM 堆内存占用: {} MB", String.format("%.2f", initialUsedHeap / (1024.0 * 1024.0)));

        // 严格遵循项目线程池规范 (guard.md)，显式使用 ThreadPoolExecutor 声明有界队列与拒绝策略
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CONCURRENT_THREADS,
                CONCURRENT_THREADS,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(TOTAL_REQUESTS),
                r -> new Thread(r, "stress-test-worker"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        List<Future<Boolean>> futures = new ArrayList<>(TOTAL_REQUESTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                File outputFile = new File(tempDir, "stress_output_" + index + ".webp");
                try {
                    VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
                    return outputFile.exists() && outputFile.length() > 0;
                } catch (IOException e) {
                    log.error("在第 {} 次转码任务中发生失败", index, e);
                    return false;
                }
            }));
        }

        // 等待所有并发任务执行完毕
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(15, TimeUnit.SECONDS)) {
                successCount++;
            }
        }

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;
        double qps = (successCount / (double) totalDuration) * 1000;

        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // 触发垃圾回收，统计最终内存大小以验证是否存在内存泄漏
        System.gc();
        Thread.sleep(500);
        long finalUsedHeap = memoryMXBean.getHeapMemoryUsage().getUsed();

        log.info("=== 并发压力测试已完成 ===");
        log.info("成功转码次数: {} / {}", successCount, TOTAL_REQUESTS);
        log.info("总耗时: {} 毫秒", totalDuration);
        log.info("吞吐量 (QPS): {}", String.format("%.2f", qps));
        log.info("单张平均耗时: {} 毫秒", String.format("%.2f", (double) totalDuration / TOTAL_REQUESTS));
        log.info("最终 JVM 堆内存占用 (GC后): {} MB", String.format("%.2f", finalUsedHeap / (1024.0 * 1024.0)));
        log.info("堆内存差值 (Heap Delta): {} MB", String.format("%.2f", (finalUsedHeap - initialUsedHeap) / (1024.0 * 1024.0)));

        assertEquals(TOTAL_REQUESTS, successCount, "所有的转码任务必须全部成功，不应存在任何错误");

        // 验证堆内存使用非常平稳（堆内存净增应当小于 20MB），证明无堆外及堆内内存泄漏
        double heapDeltaMB = (finalUsedHeap - initialUsedHeap) / (1024.0 * 1024.0);
        assertTrue(heapDeltaMB < 20.0, "JVM 堆内存占用应当保持平稳 (Delta < 20MB)，证明无内存泄漏");
    }
}
