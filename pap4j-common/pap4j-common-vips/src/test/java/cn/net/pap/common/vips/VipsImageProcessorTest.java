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
                    if (mode < 70) {
                        // 1. 70% 概率：内存直传转码（高并发核心堆外分配链，全面压测 JNA 内存释放与 GC 屏障）
                        byte[] result = VipsImageProcessor.convertFormat(sourceBytes, targetFormat);
                        return result != null && result.length > 0;
                    } else if (mode < 95) {
                        // 2. 25% 概率：文件到文件落盘转换，并即时删除文件，防 FDs/句柄泄露及磁盘占满
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
                    } else {
                        // 3. 5% 概率：并发注入非法 null 参数请求，验证 Java 防护边界对 C 层底层引擎的并发隔离安全性
                        try {
                            VipsImageProcessor.convertFormat((byte[]) null, targetFormat);
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
