package cn.net.pap.example.admin.util;

import cn.net.pap.example.admin.dto.ProcessResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProcessPoolUtil 单元测试
 * 运行环境要求：JDK 11+ (使用了 String.repeat)
 */
public class ProcessPoolUtilTest {

    // ==========================================
    // 全局测试用线程池
    // ==========================================
    private static ExecutorService testExecutorService;

    @BeforeAll
    public static void setUp() {
        // 在所有测试用例执行前，初始化一个用于读取流的线程池
        // 这里使用 CachedThreadPool 足够满足测试中短生命周期异步任务的需求
        testExecutorService = Executors.newCachedThreadPool();
    }

    @AfterAll
    public static void tearDown() {
        // 在所有测试用例执行完毕后，关闭线程池，释放资源
        if (testExecutorService != null && !testExecutorService.isShutdown()) {
            testExecutorService.shutdown();
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
    public void testNormalExecution() {
        String mainClass = NormalTask.class.getName();

        // 传入 testExecutorService
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 5, testExecutorService);

        assertEquals(0, result.getExitCode(), "正常执行的退出码应为 0");
        assertTrue(result.getOutput().contains("Hello from subprocess!"), "应能读取到标准输出");
    }

    @Test
    public void testTimeoutAndKilled() {
        String mainClass = SleepTask.class.getName();
        long startTime = System.currentTimeMillis();

        // 传入 testExecutorService，设置 2 秒超时
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 2, testExecutorService);
        long duration = System.currentTimeMillis() - startTime;

        assertEquals(-1, result.getExitCode(), "超时被强杀的退出码约定为 -1");
        assertTrue(result.getOutput().contains("TIMEOUT_OR_KILLED"), "输出中应包含超时标记");
        // 验证确实在 2 秒左右返回，证明没有死等
        assertTrue(duration >= 2000 && duration < 3500, "应该在触发超时后迅速返回");
    }

    @Test
    public void testOomPreventionTruncation() {
        String mainClass = HugeOutputTask.class.getName();

        // 传入 testExecutorService
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 10, testExecutorService);

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

        // 传入 testExecutorService
        ProcessResult result = ProcessPoolUtil.runJavaClass(mainClass, null, 5, testExecutorService);

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
            // 传入 testExecutorService
            ProcessResult res = ProcessPoolUtil.runJavaClass(mainClass, null, 10, testExecutorService);
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