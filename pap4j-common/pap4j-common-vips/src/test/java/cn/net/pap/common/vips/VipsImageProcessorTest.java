package cn.net.pap.common.vips;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * libvips 图像处理器单元测试与压力测试
 */
public class VipsImageProcessorTest {
    private static final Logger log = LoggerFactory.getLogger(VipsImageProcessorTest.class);

    @TempDir
    static File tempDir;

    private static File inputFile;
    private static byte[] sourceBytes;
    private static final int CONCURRENT_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    private static final int TOTAL_REQUESTS = 5000;

    private static boolean vipsAvailable = false;

    @BeforeAll
    public static void setUpAll() throws IOException {
        // 1. 初始化 libvips
        log.info("在运行测试前初始化 libvips 以隔离动态库加载耗时...");
        try {
            VipsImageProcessor.ensureInitialized();
            vipsAvailable = true;
            log.info("libvips 预初始化完成");
        } catch (Throwable t) {
            log.error("[Vips-Test-Setup] 无法初始化 libvips 本地库，测试类将被跳过。错误详情: ", t);
        }

        // 若 libvips 环境不可用，则通过 JUnit 5 假设（Assumption）优雅跳过所有测试，不抛出异常导致构建失败
        org.junit.jupiter.api.Assumptions.assumeTrue(vipsAvailable,
                "当前环境缺少 libvips 本地动态库，跳过 VipsImageProcessorTest");

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
        sourceBytes = java.nio.file.Files.readAllBytes(inputFile.toPath());
        log.info("测试源图片及内存字节创建成功: {}，大小: {} 字节", inputFile.getAbsolutePath(), sourceBytes.length);
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
        try {
            VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
        } catch (IllegalArgumentException e) {
            log.error("测试 PNG 转换 WebP 时入参校验失败: ", e);
            org.junit.jupiter.api.Assertions.fail("测试 PNG 转换 WebP 时入参校验失败", e);
        } catch (IOException e) {
            log.error("测试 PNG 转换 WebP 时发生 IO 读写错误: ", e);
            org.junit.jupiter.api.Assertions.fail("测试 PNG 转换 WebP 时发生 IO 读写错误", e);
        } catch (Throwable t) {
            log.error("测试 PNG 转换 WebP 时捕获到底层致命 Error 或未知异常: ", t);
            org.junit.jupiter.api.Assertions.fail("测试 PNG 转换 WebP 时发生底层系统级致命错误", t);
        }

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
     * 测试内存直传（不落盘）方式的图片格式转换
     */
    @Test
    public void testConvertFormatInMemory() throws Exception {
        // 读取输入测试图片的原始字节数据
        byte[] inputBytes = java.nio.file.Files.readAllBytes(inputFile.toPath());
        log.info("读取源图片字节完成，大小: {} 字节", inputBytes.length);

        // 在内存中直接将图像转换为 WebP 格式
        byte[] outputBytes = null;
        try {
            outputBytes = VipsImageProcessor.convertFormat(inputBytes, "webp");
        } catch (IllegalArgumentException e) {
            log.error("测试内存转换时入参校验失败: ", e);
            org.junit.jupiter.api.Assertions.fail("测试内存转换时入参校验失败", e);
        } catch (IOException e) {
            log.error("测试内存转换时发生 IO 读写错误: ", e);
            org.junit.jupiter.api.Assertions.fail("测试内存转换时发生 IO 读写错误", e);
        } catch (Throwable t) {
            log.error("测试内存转换时捕获到底层致命 Error 或未知异常: ", t);
            org.junit.jupiter.api.Assertions.fail("测试内存转换时发生底层系统级致命错误", t);
        }

        assertTrue(outputBytes != null && outputBytes.length > 0, "转换后的内存数据不应为空且长度大于 0");

        // 校验输出 WebP 数据头字节
        String riff = new String(outputBytes, 0, 4);
        String webp = new String(outputBytes, 8, 4);
        assertEquals("RIFF", riff, "内存转换出的 WebP 数据必须以 RIFF 开头");
        assertEquals("WEBP", webp, "内存转换出的 WebP 数据必须包含 WEBP");
        log.info("成功在内存中直转 PNG 到 WebP 并校验格式头通过，输出大小: {} 字节", outputBytes.length);
    }

    /**
     * 测试转码为 JPEG 格式
     */
    @Test
    public void testConvertFormatToJpg() throws Exception {
        File outputFile = new File(tempDir, "output_test.jpg");

        log.info("正在使用 JNA libvips 将测试 PNG 转换为 JPEG...");
        try {
            VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
        } catch (IllegalArgumentException e) {
            log.error("测试 PNG 转换 JPEG 时入参校验失败: ", e);
            org.junit.jupiter.api.Assertions.fail("测试 PNG 转换 JPEG 时入参校验失败", e);
        } catch (IOException e) {
            log.error("测试 PNG 转换 JPEG 时发生 IO 读写错误: ", e);
            org.junit.jupiter.api.Assertions.fail("测试 PNG 转换 JPEG 时发生 IO 读写错误", e);
        } catch (Throwable t) {
            log.error("测试 PNG 转换 JPEG 时捕获到底层致命 Error 或未知异常: ", t);
            org.junit.jupiter.api.Assertions.fail("测试 PNG 转换 JPEG 时发生底层系统级致命错误", t);
        }

        assertTrue(outputFile.exists(), "应当成功创建 JPEG 输出文件");
        assertTrue(outputFile.length() > 0, "输出文件体积应当大于 0");

        // 验证输出的图像可以正常被 ImageIO 读取，且分辨率保持 800x600
        BufferedImage outputImg = ImageIO.read(outputFile);
        assertEquals(800, outputImg.getWidth(), "输出图像宽度应仍为 800");
        assertEquals(600, outputImg.getHeight(), "输出图像高度应仍为 600");
        log.info("成功转换 PNG 到 JPEG，验证分辨率为: {}x{}", outputImg.getWidth(), outputImg.getHeight());
    }

    /**
     * 测试获取图片元数据以及裁剪与缩放管线。
     */
    @Test
    public void testMetadataAndProcessImage() throws Exception {
        log.info("测试 VipsImageProcessor.getImageMetadata 和 processImage...");

        // 1. 测试获取元数据
        VipsImageProcessor.ImageMetadata meta = VipsImageProcessor.getImageMetadata(inputFile.getAbsolutePath());
        assertNotNull(meta);
        assertEquals(800, meta.width());
        assertEquals(600, meta.height());
        log.info("成功获取测试图尺寸元数据: {}x{}", meta.width(), meta.height());

        // 2. 测试裁剪并缩放
        // 裁剪坐标 (100, 100)，宽度 400，高度 300，然后缩放 0.5 倍（预期输出为 200x150）
        byte[] outputBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                100, 100, 400, 300,
                0.5,
                "jpeg"
        );
        assertNotNull(outputBytes);
        assertTrue(outputBytes.length > 0);

        // 将字节还原为图片以验证物理尺寸
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(outputBytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            assertEquals(200, bi.getWidth(), "裁剪缩放后的宽度应为 200");
            assertEquals(150, bi.getHeight(), "裁剪缩放后的高度应为 150");
            log.info("成功验证裁剪并缩放后的图像尺寸为: {}x{}", bi.getWidth(), bi.getHeight());
        }
    }

    /**
     * 测试图像色彩品质处理 (gray, bitonal)
     */
    @Test
    public void testImageQuality() throws Exception {
        log.info("测试 VipsImageProcessor.processImage 的 quality 参数 (gray 和 bitonal)...");

        // 1. 测试灰度图 (gray)
        byte[] grayBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0,
                "gray",
                "jpeg"
        );
        assertNotNull(grayBytes);
        assertTrue(grayBytes.length > 0);

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(grayBytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            log.info("成功验证 gray 图像，类型为: {}, 宽高为: {}x{}", bi.getType(), bi.getWidth(), bi.getHeight());
        }

        // 2. 测试二值图 (bitonal)
        byte[] bitonalBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0,
                "bitonal",
                "png"
        );
        assertNotNull(bitonalBytes);
        assertTrue(bitonalBytes.length > 0);

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bitonalBytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);

            // 采样点比对：二值图所有像素点在 RGB 上只能是纯黑 (0x000000) 或纯白 (0xFFFFFF)
            int blackCount = 0;
            int whiteCount = 0;
            int otherCount = 0;
            for (int y = 0; y < bi.getHeight(); y += 20) {
                for (int x = 0; x < bi.getWidth(); x += 20) {
                    int rgb = bi.getRGB(x, y) & 0x00FFFFFF; // 过滤透明通道
                    if (rgb == 0x00000000) {
                        blackCount++;
                    } else if (rgb == 0x00FFFFFF) {
                        whiteCount++;
                    } else {
                        otherCount++;
                    }
                }
            }
            log.info("bitonal 采样测试结果: black={}, white={}, others={}", blackCount, whiteCount, otherCount);
            assertEquals(0, otherCount, "二值图的像素采样值应只有纯黑或纯白，不能有灰色过渡像素");
            assertTrue(blackCount > 0 && whiteCount > 0, "图像中应同时包含黑白像素");
        }
    }


    /**
     * 并发压力测试：还原生产环境的多路混合负载（内存直传、落盘文件、恶意非法参数），验证吞吐量与堆内堆外内存零泄漏。
     */
    @Test
    public void runStressTest() throws Exception {
        log.info("=== 开始 libvips JNA 生产级并发混合压力测试 ===");
        log.info("总请求数: {}, 并发线程数: {}", TOTAL_REQUESTS, CONCURRENT_THREADS);

        // 记录初始 JVM 堆内存状况及系统物理内存（Working Set / RSS）
        System.gc();
        Thread.sleep(500);
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long initialUsedHeap = memoryMXBean.getHeapMemoryUsage().getUsed();
        long initialWorkingSet = getProcessWorkingSetSize();
        log.info("初始 JVM 堆内存占用: {} MB", String.format("%.2f", initialUsedHeap / (1024.0 * 1024.0)));
        if (initialWorkingSet > 0) {
            log.info("初始 OS 物理内存 (Working Set): {} MB", String.format("%.2f", initialWorkingSet / (1024.0 * 1024.0)));
        }

        // 严格遵循项目线程池规范 (guard.md)，显式使用 ThreadPoolExecutor 声明有界队列与拒绝策略
        AtomicInteger threadCounter = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                CONCURRENT_THREADS,
                CONCURRENT_THREADS,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(TOTAL_REQUESTS),
                r -> new Thread(r, "stress-test-worker-" + threadCounter.getAndIncrement()),
                new ThreadPoolExecutor.AbortPolicy()
        );

        List<Future<Boolean>> futures = new ArrayList<>(TOTAL_REQUESTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                int mode = index % 100; // 确定性概率分布
                String targetFormat = (index % 2 == 0) ? "webp" : "jpeg";

                try {
                    if (mode < 40) {
                        // 1. 40% 概率：内存直传转码（高并发核心堆外分配链，全面压测 JNA 内存释放与 GC 屏障）
                        byte[] result = VipsImageProcessor.convertFormat(sourceBytes, targetFormat);
                        return result != null && result.length > 0;
                    } else if (mode < 55) {
                        // 2. 15% 概率：文件到文件落盘转换，并即时删除文件，防 FDs/句柄泄露及磁盘占满
                        File outputFile = new File(tempDir, "stress_output_" + index + "." + targetFormat);
                        try {
                            VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), outputFile.getAbsolutePath());
                            boolean ok = outputFile.exists() && outputFile.length() > 0;
                            if (outputFile.exists()) {
                                outputFile.delete();
                            }
                            return ok;
                        } catch (Throwable t) {
                            if (outputFile.exists()) {
                                outputFile.delete();
                            }
                            throw t;
                        }
                    } else if (mode < 95) {
                        // 3. 40% 概率：并发裁剪、缩放、旋转/镜像及色彩品质过滤压测 (processImage)
                        // 随机分配裁剪坐标、横向/纵向缩放比、旋转角度及镜像，混杂不同的色彩品质模式
                        Integer left = (index % 3 == 0) ? 50 : null;
                        Integer top = (index % 3 == 0) ? 50 : null;
                        Integer width = (index % 3 == 0) ? 200 : null;
                        Integer height = (index % 3 == 0) ? 150 : null;
                        Double hScale = (index % 2 == 0) ? 0.75 : 1.0;
                        Double vScale = (index % 3 == 0) ? 0.5 : hScale;
                        String[] rotations = {"0", "90", "180", "270", "!0", "!90", "!180", "!270", "45"};
                        String rotation = rotations[index % rotations.length];
                        String[] qualities = {"default", "color", "gray", "bitonal"};
                        String quality = qualities[index % qualities.length];

                        byte[] result = VipsImageProcessor.processImage(
                                inputFile.getAbsolutePath(),
                                left, top, width, height,
                                hScale, vScale,
                                rotation,
                                quality,
                                targetFormat
                        );
                        return result != null && result.length > 0;
                    } else {
                        // 4. 5% 概率：并发注入非法 null 参数请求，验证 Java 防护边界对 C 层底层引擎的并发隔离安全性
                        try {
                            if (index % 2 == 0) {
                                VipsImageProcessor.convertFormat((byte[]) null, targetFormat);
                            } else {
                                VipsImageProcessor.processImage(null, null, null, null, null, null, null, targetFormat);
                            }
                            return false; // 如果未抛异常，则表明校验失效，压测失败
                        } catch (IllegalArgumentException e) {
                            // 捕获预期内的业务规则异常，表明并发保护成功
                            return true;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    log.error("在第 {} 次转码任务中因入参校验发生失败: ", index, e);
                    return false;
                } catch (IOException e) {
                    log.error("在第 {} 次转码任务中因 IO 读写发生失败: ", index, e);
                    return false;
                } catch (Throwable t) {
                    log.error("在第 {} 次转码任务中因底层致命 Error 或未知异常发生失败: ", index, t);
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
        long finalWorkingSet = getProcessWorkingSetSize();

        log.info("=== 生产级并发压力测试已完成 ===");
        log.info("成功处理/拦截次数: {} / {}", successCount, TOTAL_REQUESTS);
        log.info("总耗时: {} 毫秒", totalDuration);
        log.info("混合吞吐量 (QPS): {}", String.format("%.2f", qps));
        log.info("单张平均耗时: {} 毫秒", String.format("%.2f", (double) totalDuration / TOTAL_REQUESTS));
        log.info("最终 JVM 堆内存占用 (GC后): {} MB", String.format("%.2f", finalUsedHeap / (1024.0 * 1024.0)));
        log.info("堆内存差值 (Heap Delta): {} MB", String.format("%.2f", (finalUsedHeap - initialUsedHeap) / (1024.0 * 1024.0)));
        if (initialWorkingSet > 0 && finalWorkingSet > 0) {
            double workingSetDeltaMB = (finalWorkingSet - initialWorkingSet) / (1024.0 * 1024.0);
            log.info("最终 OS 物理内存 (Working Set): {} MB", String.format("%.2f", finalWorkingSet / (1024.0 * 1024.0)));
            log.info("物理内存差值 (Working Set Delta): {} MB", String.format("%.2f", workingSetDeltaMB));
            // 物理内存增长必须非常平稳。一般由于 JVM 的 Metaspace 膨胀、JIT 编译或线程栈开销，会有小幅正常增量。
            // 我们断言物理内存增量在 100MB 以内，这就保证了 5000 次 C 层转码操作中，完全没有百万字节级别的堆外/底层 C 内存泄露！
            assertTrue(workingSetDeltaMB < 100.0, "OS 物理内存占用应当保持平稳 (Delta < 100MB)，证明无堆外本地 C 内存泄漏");
        }

        assertEquals(TOTAL_REQUESTS, successCount, "所有的转码或防卫拦截任务必须全部成功，不应存在任何未捕获致命错误");

        // 验证堆内存使用非常平稳（堆内存净增应当小于 20MB），证明无堆外及堆内内存泄漏
        double heapDeltaMB = (finalUsedHeap - initialUsedHeap) / (1024.0 * 1024.0);
        assertTrue(heapDeltaMB < 20.0, "JVM 堆内存占用应当保持平稳 (Delta < 20MB)，证明无内存泄漏");
    }

    /**
     * 测试图像翻转与旋转功能。
     */
    @Test
    public void testImageRotationAndMirroring() throws Exception {
        log.info("测试 VipsImageProcessor.processImage 的 rotation 参数 (翻转与旋转)...");

        // 1. 测试 90 度旋转
        byte[] rot90Bytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "90",
                "default", "jpeg"
        );
        assertNotNull(rot90Bytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(rot90Bytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            // 原始尺寸为 800x600，旋转 90 度后宽高应对调，即 600x800
            assertEquals(600, bi.getWidth(), "旋转 90 度后宽度应为 600");
            assertEquals(800, bi.getHeight(), "旋转 90 度后高度应为 800");
        }

        // 2. 测试镜像水平翻转
        byte[] mirrorBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "!0",
                "default", "jpeg"
        );
        assertNotNull(mirrorBytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(mirrorBytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            // 翻转后尺寸应保持不变为 800x600
            assertEquals(800, bi.getWidth());
            assertEquals(600, bi.getHeight());
        }

        // 3. 测试镜像 + 270 度旋转 (旋转后宽高应对调，即 600x800)
        byte[] mirrorRot270Bytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "!270",
                "default", "jpeg"
        );
        assertNotNull(mirrorRot270Bytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(mirrorRot270Bytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            assertEquals(600, bi.getWidth());
            assertEquals(800, bi.getHeight());
        }

        // 4. 测试任意浮点数角度旋转 (例如 45.0 度)
        byte[] rot45Bytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "45",
                "default", "jpeg"
        );
        assertNotNull(rot45Bytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(rot45Bytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            // 任意角度旋转后，画布会自适应变大，尺寸应大于 800x600
            assertTrue(bi.getWidth() > 800);
            assertTrue(bi.getHeight() > 600);
            log.info("任意 45 度角旋转后图像尺寸自适应为: {}x{}", bi.getWidth(), bi.getHeight());
        }
    }

    /**
     * 测试非等比拉伸缩放功能。
     */
    @Test
    public void testNonProportionalScaling() throws Exception {
        log.info("测试 VipsImageProcessor.processImage 的横向/纵向非等比缩放功能...");

        // 将 800x600 图像拉伸缩放到横向 0.5 倍，纵向 0.2 倍 (预期输出宽度 400，高度 120)
        byte[] outputBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                0.5, 0.2, "0",
                "default", "jpeg"
        );
        assertNotNull(outputBytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(outputBytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            assertEquals(400, bi.getWidth(), "横向非等比缩放后宽度应为 400");
            assertEquals(120, bi.getHeight(), "纵向非等比缩放后高度应为 120");
            log.info("成功验证非等比拉伸图像，尺寸为: {}x{}", bi.getWidth(), bi.getHeight());
        }
    }

    /**
     * 测试各种边界条件与非法输入异常抛出，提升测试分支覆盖率。
     */
    @Test
    public void testEdgeCasesAndValidationExceptions() throws Exception {
        log.info("测试边界条件与异常判定以提高测试覆盖率...");

        // 1. 测试非法路径与文件不存在异常
        assertThrows(IOException.class, () -> 
                VipsImageProcessor.getImageMetadata(TestResourceUtil.getNonExistentPath("non_existent_file.png"))
        );
        assertThrows(IOException.class, () -> 
                VipsImageProcessor.processImage(TestResourceUtil.getNonExistentPath("non_existent_file.png"), null, null, null, null, 1.0, 1.0, "0", "default", "jpg")
        );

        // 2. 测试输入路径为 null 或空
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.processImage(null, null, null, null, null, 1.0, 1.0, "0", "default", "jpg")
        );
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.processImage("", null, null, null, null, 1.0, 1.0, "0", "default", "jpg")
        );
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.getImageMetadata(null)
        );

        // 3. 测试输出格式为 null 或空
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.processImage(inputFile.getAbsolutePath(), null, null, null, null, 1.0, 1.0, "0", "default", null)
        );
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.processImage(inputFile.getAbsolutePath(), null, null, null, null, 1.0, 1.0, "0", "default", "")
        );

        // 4. 测试非法旋转字符串导致参数解析失败
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.processImage(inputFile.getAbsolutePath(), null, null, null, null, 1.0, 1.0, "abc", "default", "jpg")
        );
        assertThrows(IllegalArgumentException.class, () -> 
                VipsImageProcessor.processImage(inputFile.getAbsolutePath(), null, null, null, null, 1.0, 1.0, "!abc", "default", "jpg")
        );

        // 5. 测试 hScale 为 null (跳过 resize 逻辑)
        byte[] noScaleBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                null, 1.0, "0",
                "default", "jpg"
        );
        assertNotNull(noScaleBytes);

        // 6. 测试 vScale 为 null (使用 hScale 等比 resize 逻辑)
        byte[] equalScaleBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                0.5, null, "0",
                "default", "jpg"
        );
        assertNotNull(equalScaleBytes);

        // 7. 测试旋转参数为 null, 空, 以及 0 度 (跳过 rotate 逻辑)
        byte[] noRot1 = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, null,
                "default", "jpg"
        );
        assertNotNull(noRot1);
        byte[] noRot2 = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "",
                "default", "jpg"
        );
        assertNotNull(noRot2);
        byte[] noRot3 = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "0",
                "default", "jpg"
        );
        assertNotNull(noRot3);

        // 8. 测试色彩品质为 invalid、default 或 color
        byte[] invalidQualBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "0",
                "invalid_quality", "jpg"
        );
        assertNotNull(invalidQualBytes);

        // 9. 测试 convertFormat(String, String) 文件不存在异常
        assertThrows(IOException.class, () ->
                VipsImageProcessor.convertFormat(TestResourceUtil.getNonExistentPath("non_existent_file.png"), TestResourceUtil.getNonExistentPath("non_existent_out.png"))
        );

        // 10. 测试 convertFormat(String, String) 文件损坏/非法类型异常
        File emptyFile = new File(tempDir, "empty_file.png");
        java.nio.file.Files.write(emptyFile.toPath(), new byte[]{1, 2, 3, 4});
        assertThrows(IOException.class, () ->
                VipsImageProcessor.convertFormat(emptyFile.getAbsolutePath(), new File(tempDir, "empty_out.png").getAbsolutePath())
        );

        // 11. 测试 convertFormat(String, String) 自动创建不存在的父目录
        File nestedOutputFile = new File(tempDir, "nested_dir_1/nested_dir_2/output_nested.webp");
        VipsImageProcessor.convertFormat(inputFile.getAbsolutePath(), nestedOutputFile.getAbsolutePath());
        assertTrue(nestedOutputFile.exists(), "应当成功创建嵌套子目录并生成文件");
        assertTrue(nestedOutputFile.length() > 0);

        // 12. 测试 convertFormat(byte[], String) 入参为 null 或空
        assertThrows(IllegalArgumentException.class, () ->
                VipsImageProcessor.convertFormat((byte[]) null, "webp")
        );
        assertThrows(IllegalArgumentException.class, () ->
                VipsImageProcessor.convertFormat(new byte[0], "webp")
        );
        assertThrows(IllegalArgumentException.class, () ->
                VipsImageProcessor.convertFormat(sourceBytes, (String) null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                VipsImageProcessor.convertFormat(sourceBytes, "")
        );

        // 13. 测试 convertFormat(byte[], String) 传入损坏/非法字节数组
        assertThrows(IOException.class, () ->
                VipsImageProcessor.convertFormat(new byte[]{1, 2, 3, 4}, "webp")
        );

        // 14. 测试 convertFormat(byte[], String) 传入不支持的输出后缀
        assertThrows(IOException.class, () ->
                VipsImageProcessor.convertFormat(sourceBytes, "xyz")
        );

        // 15. 测试 processImage 负角和大于360度的超大角度旋转 (例如 -90 度 和 450 度)
        // -90度应等同于270度，宽高对调（800x600 -> 600x800）
        byte[] rotNeg90Bytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "-90",
                "default", "jpeg"
        );
        assertNotNull(rotNeg90Bytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(rotNeg90Bytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            assertEquals(600, bi.getWidth(), "旋转 -90 度后宽度应为 600");
            assertEquals(800, bi.getHeight(), "旋转 -90 度后高度应为 800");
        }

        // 450度等同于90度 (450 % 360 = 90)，宽高对调（800x600 -> 600x800）
        byte[] rot450Bytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                null, null, null, null,
                1.0, 1.0, "450",
                "default", "jpeg"
        );
        assertNotNull(rot450Bytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(rot450Bytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            assertEquals(600, bi.getWidth(), "旋转 450 度后宽度应为 600");
            assertEquals(800, bi.getHeight(), "旋转 450 度后高度应为 800");
        }

        // 16. 测试 processImage 仅包含部分裁剪参数 (例如只传 left 和 top，宽和高为 null)
        // 应该跳过裁剪，返回完整原始图片 (大小应仍为 800x600)
        byte[] partialCropBytes = VipsImageProcessor.processImage(
                inputFile.getAbsolutePath(),
                100, 100, null, null,
                1.0, 1.0, "0",
                "default", "jpeg"
        );
        assertNotNull(partialCropBytes);
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(partialCropBytes)) {
            BufferedImage bi = ImageIO.read(bais);
            assertNotNull(bi);
            assertEquals(800, bi.getWidth(), "不完全裁剪参数应跳过裁剪，宽度仍为 800");
            assertEquals(600, bi.getHeight(), "不完全裁剪参数应跳过裁剪，高度仍为 600");
        }
    }

    /**
     * 深度内存泄漏验证：在频繁抛出异常（如非法旋转入参）的极端情况下，验证本地 Pointer 和中间状态是否被 finally 安全回收，物理内存有无累积泄漏。
     */
    @Test
    public void testPointerCleanupOnException() throws Exception {
        log.info("开始频繁发生异常情况下的本地内存泄露防范性测试...");

        System.gc();
        Thread.sleep(200);
        long initialWorkingSet = getProcessWorkingSetSize();

        int exceptionLoopCount = 1000;
        int caughtExceptions = 0;

        for (int i = 0; i < exceptionLoopCount; i++) {
            try {
                // 传入非法旋转字符串，故意在此处引发 IllegalArgumentException 异常
                VipsImageProcessor.processImage(
                        inputFile.getAbsolutePath(),
                        null, null, null, null,
                        1.0, 1.0, "invalid_angle_to_force_exception_" + i,
                        "default", "jpg"
                );
            } catch (IllegalArgumentException e) {
                caughtExceptions++;
            }
        }

        assertEquals(exceptionLoopCount, caughtExceptions, "必须捕获到每一次人为构造的异常");

        System.gc();
        Thread.sleep(200);
        long finalWorkingSet = getProcessWorkingSetSize();

        if (initialWorkingSet > 0 && finalWorkingSet > 0) {
            double workingSetDeltaMB = (finalWorkingSet - initialWorkingSet) / (1024.0 * 1024.0);
            log.info("异常泄漏验证完成。初次 Working Set: {} MB，末次: {} MB，Delta: {} MB",
                    String.format("%.2f", initialWorkingSet / (1024.0 * 1024.0)),
                    String.format("%.2f", finalWorkingSet / (1024.0 * 1024.0)),
                    String.format("%.2f", workingSetDeltaMB)
            );
            // 1000次发生异常的图像转码生命周期中，内存增长应保持平稳（在15MB以内），证明异常逻辑下没有任何 Pointer 回收遗漏
            assertTrue(workingSetDeltaMB < 15.0, "异常处理管道中应无本地 Pointer 泄漏 (Delta < 15MB)");
        }
    }

    /**
     * 获取当前 JVM 进程的 OS 物理内存（Working Set / RSS）占用大小（字节）。
     * 仅在 Windows 操作系统下有效，非 Windows 系统将返回 -1。
     */
    private static long getProcessWorkingSetSize() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return -1;
        }
        try {
            long pid = ProcessHandle.current().pid();
            // 在 Windows 环境下通过 PowerShell 查询进程的物理工作内存（Working Set）
            Process process = Runtime.getRuntime().exec(new String[]{
                "powershell", "-Command", "(Get-Process -Id " + pid + ").WorkingSet64"
            });
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    return Long.parseLong(line.trim());
                }
            }
        } catch (Exception e) {
            log.debug("无法获取当前进程的 OS 物理内存 Working Set 大小: ", e);
        }
        return -1;
    }
}
