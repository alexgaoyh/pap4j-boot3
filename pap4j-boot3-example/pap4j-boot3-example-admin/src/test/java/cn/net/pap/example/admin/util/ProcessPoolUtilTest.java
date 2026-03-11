package cn.net.pap.example.admin.util;

import cn.net.pap.example.admin.dto.ProcessResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProcessPoolUtil 单元测试
 * 运行环境要求：JDK 11+ (使用了 String.repeat)
 */
public class ProcessPoolUtilTest {

    private static final Logger log = LoggerFactory.getLogger(ProcessPoolUtilTest.class);

    // ==========================================
    // 全局测试用线程池
    // ==========================================
    private static ThreadPoolExecutor testThreadPoolExecutor;

    @BeforeAll
    public static void setUp() {
        // 在所有测试用例执行前，初始化一个用于读取流的线程池
        testThreadPoolExecutor = new ThreadPoolExecutor(5, 20, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "process-pool-thread-" + threadNumber.getAndIncrement());
                t.setUncaughtExceptionHandler((thread, e) -> {
                    log.error("线程池 {} 发生未捕获异常: ", thread.getName(), e);
                });
                return t;
            }
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    @AfterAll
    public static void tearDown() {
        // 在所有测试用例执行完毕后，关闭线程池，释放资源
        if (testThreadPoolExecutor != null && !testThreadPoolExecutor.isShutdown()) {
            testThreadPoolExecutor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!testThreadPoolExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    testThreadPoolExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                testThreadPoolExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==========================================
    // 模拟的外部 Java 进程目标类 (Dummy Tasks)
    // ==========================================

    public static class NormalTask {
        public static void main(String[] args) {
            System.out.println("Hello from subprocess!");
        }
    }

    public static class SleepTask {
        public static void main(String[] args) throws InterruptedException {
            // 睡眠 10 秒，用于测试超时和强杀
            Thread.sleep(10000);
        }
    }

    public static class HugeOutputTask {
        public static void main(String[] args) {
            // 疯狂输出数据，尝试撑爆内存 (输出约 6MB 数据)
            String chunk = "A".repeat(1024); // 1KB
            for (int i = 0; i < 6000; i++) {
                System.out.println(chunk);
            }
        }
    }

    public static class ErrorTask {
        public static void main(String[] args) {
            System.err.println("This is an error stream message.");
            System.exit(1);
        }
    }

    // ==========================================
    // 测试用例
    // ==========================================

    @Test
    public void testChinese() {
        List<String> commandList;
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        if (isWindows) {
            commandList = Arrays.asList("cmd.exe", "/c", "echo 中文!");
        } else {
            commandList = Arrays.asList("/bin/sh", "-c", "echo 中文!");
        }
        ProcessResult result = ProcessPoolUtil.runCommand(commandList, 5, testThreadPoolExecutor);
        assertEquals(0, result.getExitCode(), "正常执行的退出码应为 0");
        assertTrue(result.getOutput().contains("中文!"), "应能读取到标准输出");
    }

    @Test
    public void testNormalExecution() {
        String mainClass = NormalTask.class.getName();

        // 传入 testThreadPoolExecutor
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 5, testThreadPoolExecutor);

        assertEquals(0, result.getExitCode(), "正常执行的退出码应为 0");
        assertTrue(result.getOutput().contains("Hello from subprocess!"), "应能读取到标准输出");
    }

    @Test
    public void testTimeoutAndKilled() {
        String mainClass = SleepTask.class.getName();
        long startTime = System.currentTimeMillis();

        // 传入 testThreadPoolExecutor，设置 2 秒超时
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 2, testThreadPoolExecutor);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(-1, result.getExitCode(), "超时被强杀的退出码约定为 -1");
        assertTrue(result.getOutput().contains("TIMEOUT_OR_KILLED"), "输出中应包含超时标记");
        // 验证确实在 2 秒左右返回，证明没有死等
        assertTrue(duration >= 2000 && duration < 3500, "应该在触发超时后迅速返回");
    }

    @Test
    public void testOomPreventionTruncation() {
        String mainClass = HugeOutputTask.class.getName();

        // 传入 testThreadPoolExecutor
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 10, testThreadPoolExecutor);

        assertEquals(0, result.getExitCode(), "进程应正常结束退出码为 0");

        String output = result.getOutput();
        // 容错空间 = 最后一行可能的最大长度 (约 1024) + 警告信息的长度
        int tolerance = 1024 + "\n[WARNING] Output truncated due to exceeding max length.\n".length();
        assertTrue(output.length() <= ProcessPoolUtil.MAX_OUTPUT_LENGTH + tolerance, "输出总长度不应明显超过 5MB");
        assertTrue(output.contains("[WARNING] Output truncated"), "输出末尾应包含截断警告");
    }

    @Test
    public void testErrorStreamMerged() {
        String mainClass = ErrorTask.class.getName();

        // 传入 testThreadPoolExecutor
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 5, testThreadPoolExecutor);

        assertEquals(1, result.getExitCode(), "异常退出的进程码应为 1");
        assertTrue(result.getOutput().contains("This is an error stream message."), "错误流应被合并并读取到");
    }

    @Test
    public void testThreadInterruption() throws InterruptedException {
        String mainClass = SleepTask.class.getName();
        AtomicReference<ProcessResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 在新线程中执行
        Thread executorThread = new Thread(() -> {
            // 传入 testThreadPoolExecutor
            ProcessResult res = ProcessPoolUtil.runJavaClass(mainClass, null, 10, testThreadPoolExecutor);
            resultRef.set(res);
            latch.countDown();
        });

        executorThread.start();

        // 确保子进程已经启动并处于 wait 状态
        Thread.sleep(1000);

        // 模拟上层业务系统（如 Tomcat/Spring Boot）发出的线程中断信号
        executorThread.interrupt();

        // 等待线程执行结束
        latch.await();

        ProcessResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(-1, result.getExitCode(), "被中断的进程码约定为 -1");
        assertTrue(result.getOutput().contains("EXECUTION_INTERRUPTED"), "输出中应包含中断标记");
    }
}