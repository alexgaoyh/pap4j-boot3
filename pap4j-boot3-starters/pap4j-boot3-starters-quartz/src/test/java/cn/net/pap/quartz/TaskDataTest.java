package cn.net.pap.quartz;

import cn.net.pap.quartz.util.BeanMethodInvoker;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.runner.RunWith;
import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.service.ITaskDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
public class TaskDataTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskDataTest.class);

    @Autowired
    private ITaskDataService taskDataService;

    @Autowired
    private ApplicationContext applicationContext;

    public static final ExecutorService executor = new ThreadPoolExecutor(
            20,
            20,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            r -> new Thread(r, "countdownlatch-test-executor"),
            new ThreadPoolExecutor.AbortPolicy()
    );

    @AfterAll
    public static void shutdown() {
        executor.shutdown();
        try {
            // 等待 2 秒让未完成的任务结束
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                logger.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("关闭线程池时被中断", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }

    @Test
    public void testSingleThreadProcessing() {
        taskDataService.deleteAll();

        // 准备测试数据
        List<TaskData> testData = createTestData(10);
        taskDataService.saveAll(testData);

        // 执行处理
        taskDataService.processBatchSafely();

        // 验证结果
        Map<String, Long> stats = taskDataService.getProcessingStats();
        assertEquals(10L, stats.get("success"));
        assertEquals(0L, stats.get("pending"));
        assertEquals(0L, stats.get("processing"));
    }

    @Test
    public void testHighConcurrencyProcessing() throws InterruptedException {
        taskDataService.deleteAll();
        // 准备测试数据
        int dataCount = 100;
        List<TaskData> testData = createTestData(dataCount);
        taskDataService.saveAll(testData);

        // 并发执行处理任务
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程就绪
                    taskDataService.processBatchSafely();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 同时启动所有线程
        startLatch.countDown();

        // 等待所有线程完成
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "处理任务未在指定时间内完成");

        // 验证结果
        Map<String, Long> stats = taskDataService.getProcessingStats();

        // 所有数据都应该被处理
        assertEquals(dataCount, stats.get("success"));
        // 验证没有数据处于中间状态
        assertEquals(0L, stats.get("pending"));
        assertEquals(0L, stats.get("processing"));
    }

    private List<TaskData> createTestData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    TaskData data = new TaskData();
                    data.setId((long) i);
                    data.setDataContent("Test data " + i);
                    data.setProcessStatus("PENDING");
                    return data;
                })
                .collect(Collectors.toList());
    }

    @Test
    public void callNptExceptionTest() throws Exception {
        String inputStr = "taskDataServiceImpl.callNptException(123L)";
        Exception exception = assertThrows(java.lang.RuntimeException.class, () -> {
            BeanMethodInvoker.invokeMethodCall(applicationContext, inputStr);
        });
        // 还可以进一步验证异常信息
        assertEquals("Failed to invoke method call: taskDataServiceImpl.callNptException(123L)", exception.getMessage());
        logger.error("callNptExceptionTest", exception);
    }



}
