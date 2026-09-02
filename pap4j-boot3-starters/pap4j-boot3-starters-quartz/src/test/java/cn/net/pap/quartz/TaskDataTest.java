package cn.net.pap.quartz;

import cn.net.pap.quartz.repository.TaskDataRepository;
import cn.net.pap.quartz.util.BeanMethodInvoker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.service.ITaskDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

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

@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TaskDataTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskDataTest.class);

    private final ITaskDataService taskDataService;
    private final ApplicationContext applicationContext;
    private final TaskDataRepository taskDataRepository;

    public TaskDataTest(ITaskDataService taskDataService, ApplicationContext applicationContext, TaskDataRepository taskDataRepository) {
        this.taskDataService = taskDataService;
        this.applicationContext = applicationContext;
        this.taskDataRepository = taskDataRepository;
    }
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
                    logger.error("等待执行信号被中断", e);
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

    /**
     * 测试非阻塞数据库驱动的退避重试机制
     * 验证在并发环境下，单条数据失败后不会阻塞同批次其他数据的正常处理，
     * 并且能按照计算得到的 nextProcessTime 进行精准的延迟捞取重试，直到达到最大重试上限变为最终失败。
     */
    @Test
    public void testNonBlockingDatabaseDrivenRetry() {
        taskDataService.deleteAll();

        // 1. 准备测试数据：1条正常数据，1条会触发可重试异常的数据（包含 "RETRY"）
        TaskData taskRetry = new TaskData();
        taskRetry.setId(1L);
        taskRetry.setDataContent("RETRY task");
        taskRetry.setProcessStatus("PENDING");

        TaskData taskNormal = new TaskData();
        taskNormal.setId(2L);
        taskNormal.setDataContent("Normal task");
        taskNormal.setProcessStatus("PENDING");

        taskDataService.saveAll(List.of(taskRetry, taskNormal));

        // 2. 第一次批处理捞起：两个任务被同时捞起。
        // Normal 应该直接 SUCCESS 提交；Retry 会执行失败并进行非阻塞退避。
        taskDataService.processBatchSafely();

        // 【断言】Normal 任务成功，说明没有被失败的 Retry 任务拖慢/阻塞
        TaskData dbNormal = taskDataRepository.findById(2L).orElseThrow();
        assertEquals("SUCCESS", dbNormal.getProcessStatus());

        // 【断言】Retry 任务状态变为 RETRYABLE_FAILED，尝试次数加 1，且写入了未来的下一次可执行时间（nextProcessTime）
        TaskData dbRetry = taskDataRepository.findById(1L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetry.getProcessStatus());
        assertEquals(1, dbRetry.getProcessAttempts());
        org.junit.jupiter.api.Assertions.assertNotNull(dbRetry.getNextProcessTime());
        assertTrue(dbRetry.getNextProcessTime().isAfter(java.time.LocalDateTime.now()));

        // 3. 第二次批处理捞起（立即执行）：
        // 由于 Retry 任务的下一次可执行时间是未来时间，数据库 SQL 应将其过滤掉。
        taskDataService.processBatchSafely();
        
        // 【断言】Retry 任务依然维持原样，尝试次数没有被增加，说明未被重复抢占
        TaskData dbRetryImmediate = taskDataRepository.findById(1L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetryImmediate.getProcessStatus());
        assertEquals(1, dbRetryImmediate.getProcessAttempts());

        // 4. 模拟时间流逝：手动将下一次可执行时间调整到过去的 10 秒前，使其满足抢占条件
        dbRetryImmediate.setNextProcessTime(java.time.LocalDateTime.now().minusSeconds(10));
        taskDataRepository.save(dbRetryImmediate);

        // 5. 第三次批处理捞起：Retry 任务此时因到达时间被成功重新捞起，但会再次处理失败（attempts = 2）
        taskDataService.processBatchSafely();
        TaskData dbRetrySecond = taskDataRepository.findById(1L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetrySecond.getProcessStatus());
        assertEquals(2, dbRetrySecond.getProcessAttempts());

        // 6. 再次模拟时间流逝：调整重试时间到过去
        dbRetrySecond.setNextProcessTime(java.time.LocalDateTime.now().minusSeconds(10));
        taskDataRepository.save(dbRetrySecond);

        // 7. 第四次批处理捞起：第 3 次重新执行并再次失败。
        // 由于尝试次数已达到 MAX_RETRY_ATTEMPTS(3)，状态被最终置为 FAILED。
        taskDataService.processBatchSafely();
        TaskData dbRetryFinal = taskDataRepository.findById(1L).orElseThrow();
        assertEquals("FAILED", dbRetryFinal.getProcessStatus());
        assertEquals(3, dbRetryFinal.getProcessAttempts());
    }

}
