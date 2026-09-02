package cn.net.pap.quartz;

import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.repository.TaskDataRepository;
import cn.net.pap.quartz.service.ITaskDataService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TaskDataParallelTest {

    private static final Logger log = LoggerFactory.getLogger(TaskDataParallelTest.class);

    private final ITaskDataService taskDataParallelService;
    private final ApplicationContext applicationContext;
    private final TaskDataRepository taskDataRepository;

    public TaskDataParallelTest(
            @Qualifier("taskDataParallelServiceImpl") ITaskDataService taskDataParallelService,
            ApplicationContext applicationContext,
            TaskDataRepository taskDataRepository) {
        this.taskDataParallelService = taskDataParallelService;
        this.applicationContext = applicationContext;
        this.taskDataRepository = taskDataRepository;
    }

    public static final ExecutorService testClientExecutor = new ThreadPoolExecutor(
            10,
            10,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            r -> new Thread(r, "parallel-test-client-thread"),
            new ThreadPoolExecutor.AbortPolicy()
    );

    @AfterAll
    public static void shutdown() {
        testClientExecutor.shutdown();
        try {
            if (!testClientExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                testClientExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("关闭线程池被中断", e);
            testClientExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void testSingleThreadProcessing() {
        taskDataParallelService.deleteAll();

        // 准备 10 条测试数据
        List<TaskData> testData = createTestData(10);
        taskDataParallelService.saveAll(testData);

        // 执行并行处理
        taskDataParallelService.processBatchSafely();

        // 验证结果
        Map<String, Long> stats = taskDataParallelService.getProcessingStats();
        assertEquals(10L, stats.get("success"));
        assertEquals(0L, stats.get("pending"));
        assertEquals(0L, stats.get("processing"));
    }

    @Test
    public void testHighConcurrencyProcessing() throws InterruptedException {
        taskDataParallelService.deleteAll();

        int dataCount = 50;
        List<TaskData> testData = createTestData(dataCount);
        taskDataParallelService.saveAll(testData);

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            testClientExecutor.submit(() -> {
                try {
                    startLatch.await(); // 等待对齐信号
                    taskDataParallelService.processBatchSafely();
                } catch (InterruptedException e) {
                    log.error("等待执行信号被中断", e);
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 同时点火启动所有调度并发
        startLatch.countDown();

        boolean completed = endLatch.await(15, TimeUnit.SECONDS);
        assertTrue(completed, "多线程并发处理在 15 秒内未完成");

        Map<String, Long> stats = taskDataParallelService.getProcessingStats();
        assertEquals(dataCount, stats.get("success"));
        assertEquals(0L, stats.get("pending"));
        assertEquals(0L, stats.get("processing"));
    }

    @Test
    public void testNonBlockingDatabaseDrivenRetry() {
        taskDataParallelService.deleteAll();

        // 1. 准备测试数据：1 条正常，1 条包含 "RETRY"（会重试并退避）
        TaskData taskRetry = new TaskData();
        taskRetry.setId(101L);
        taskRetry.setDataContent("RETRY task");
        taskRetry.setProcessStatus("PENDING");

        TaskData taskNormal = new TaskData();
        taskNormal.setId(102L);
        taskNormal.setDataContent("Normal task");
        taskNormal.setProcessStatus("PENDING");

        taskDataParallelService.saveAll(List.of(taskRetry, taskNormal));

        // 2. 第一次批处理：Normal 成功，Retry 失败并进入指数退避
        taskDataParallelService.processBatchSafely();

        TaskData dbNormal = taskDataRepository.findById(102L).orElseThrow();
        assertEquals("SUCCESS", dbNormal.getProcessStatus());

        TaskData dbRetry = taskDataRepository.findById(101L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetry.getProcessStatus());
        assertEquals(1, dbRetry.getProcessAttempts());
        assertNotNull(dbRetry.getNextProcessTime());
        assertTrue(dbRetry.getNextProcessTime().isAfter(LocalDateTime.now()));

        // 3. 立即进行第二次处理：由于 Retry 没到下一次运行时间，它不会被重新捞起抢占
        taskDataParallelService.processBatchSafely();

        TaskData dbRetryImmediate = taskDataRepository.findById(101L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetryImmediate.getProcessStatus());
        assertEquals(1, dbRetryImmediate.getProcessAttempts());

        // 4. 模拟时间流逝：手动将 nextProcessTime 移至过去
        dbRetryImmediate.setNextProcessTime(LocalDateTime.now().minusSeconds(10));
        taskDataRepository.save(dbRetryImmediate);

        // 5. 第三次处理：重试任务到达时间，被再次捞起但又失败，attempts = 2
        taskDataParallelService.processBatchSafely();

        TaskData dbRetrySecond = taskDataRepository.findById(101L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetrySecond.getProcessStatus());
        assertEquals(2, dbRetrySecond.getProcessAttempts());

        // 6. 再次将 nextProcessTime 移至过去
        dbRetrySecond.setNextProcessTime(LocalDateTime.now().minusSeconds(10));
        taskDataRepository.save(dbRetrySecond);

        // 7. 第四次处理：第三次执行失败，累计达到 MAX_RETRY_ATTEMPTS = 3，状态变为 FAILED
        taskDataParallelService.processBatchSafely();

        TaskData dbRetryFinal = taskDataRepository.findById(101L).orElseThrow();
        assertEquals("FAILED", dbRetryFinal.getProcessStatus());
        assertEquals(3, dbRetryFinal.getProcessAttempts());
    }

    private List<TaskData> createTestData(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    TaskData data = new TaskData();
                    data.setId((long) i);
                    data.setDataContent("Parallel test data " + i);
                    data.setProcessStatus("PENDING");
                    return data;
                })
                .collect(Collectors.toList());
    }
}
