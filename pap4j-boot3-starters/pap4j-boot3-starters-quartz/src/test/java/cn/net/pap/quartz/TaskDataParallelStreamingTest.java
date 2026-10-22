package cn.net.pap.quartz;

import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.repository.TaskDataRepository;
import cn.net.pap.quartz.service.ITaskDataService;
import cn.net.pap.quartz.service.impl.TaskDataParallelStreamingServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link TaskDataParallelStreamingServiceImpl} 的流式槽位行为测试。
 *
 * <p>重点验证：</p>
 * <ol>
 *     <li>processBatchSafely() 非阻塞，只补满当前空闲槽位；</li>
 *     <li>并发调用 processBatchSafely() 不会超卖槽位，也不会重复消费；</li>
 *     <li>异步化后原有的数据库驱动指数退避重试语义保持不变。</li>
 * </ol>
 */
@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource(
        locations = "classpath:application.properties",
        properties = "spring.datasource.url=jdbc:h2:mem:pap4j-boot3-starters-quartz-streaming")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TaskDataParallelStreamingTest {

    private static final Logger log = LoggerFactory.getLogger(TaskDataParallelStreamingTest.class);

    private static final int MAX_CONCURRENT_SLOTS = 10;

    private final TaskDataRepository taskDataRepository;

    private final ITaskDataService taskDataParallelStreamingService;

    public TaskDataParallelStreamingTest(
            @Qualifier("taskDataParallelStreamingServiceImpl") ITaskDataService taskDataParallelStreamingService,
            TaskDataRepository taskDataRepository) {
        this.taskDataParallelStreamingService = taskDataParallelStreamingService;
        this.taskDataRepository = taskDataRepository;
    }

    private static final ExecutorService TEST_CLIENT_EXECUTOR = new ThreadPoolExecutor(
            5,
            5,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            r -> new Thread(r, "streaming-test-client-thread"),
            new ThreadPoolExecutor.AbortPolicy()
    );

    @AfterAll
    public static void shutdown() {
        TEST_CLIENT_EXECUTOR.shutdown();
        try {
            if (!TEST_CLIENT_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                TEST_CLIENT_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("关闭测试线程池被中断", e);
            TEST_CLIENT_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    public void drainAndClean() throws InterruptedException {
        try {
            awaitNoProcessing(Duration.ofSeconds(15));
        } catch (AssertionError e) {
            log.warn("等待任务排空超时，准备清理测试数据", e);
        } finally {
            taskDataParallelStreamingService.deleteAll();
        }
    }


    /**
     * 验证单次补位只填满当前槽位，不会一次性把所有待处理数据都拉进线程池。
     * 这是 md 中“外部定时器反复触发补位”的设计前提。
     */
    @Test
    public void testSingleRefillOnlyFillsFreeSlots() throws Exception {
        taskDataParallelStreamingService.deleteAll();

        int dataCount = 30;
        taskDataParallelStreamingService.saveAll(createTestData(3000L, dataCount, "single call task "));

        taskDataParallelStreamingService.processBatchSafely();

        // 等待第一批（最多 10 条）执行完成；本方法不会自动补位，因此剩余数据仍是 PENDING。
        awaitNoProcessing(Duration.ofSeconds(5));

        assertEquals((long) MAX_CONCURRENT_SLOTS, taskDataRepository.countByProcessStatus("SUCCESS"));
        assertEquals((long) (dataCount - MAX_CONCURRENT_SLOTS), taskDataRepository.countByProcessStatus("PENDING"));
    }

    /**
     * 验证多个线程同时调用 processBatchSafely() 时，dispatchLock 能防止槽位超卖。
     */
    @Test
    public void testConcurrentRefillCallsRespectSlotLimit() throws Exception {
        taskDataParallelStreamingService.deleteAll();

        int dataCount = 50;
        taskDataParallelStreamingService.saveAll(createTestData(1000L, dataCount, "concurrent task "));

        int callerCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(callerCount);

        for (int i = 0; i < callerCount; i++) {
            TEST_CLIENT_EXECUTOR.submit(() -> {
                try {
                    startLatch.await();
                    taskDataParallelStreamingService.processBatchSafely();
                } catch (InterruptedException e) {
                    log.error("等待并发补位信号被中断", e);
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS), "并发补位调用应在 5 秒内返回");

        int maxProcessing = 0;
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            taskDataParallelStreamingService.processBatchSafely();

            long processing = taskDataRepository.countByProcessStatus("PROCESSING");
            maxProcessing = Math.max(maxProcessing, (int) processing);

            long success = taskDataRepository.countByProcessStatus("SUCCESS");
            if (success >= dataCount && processing == 0) {
                break;
            }
            Thread.sleep(20);
        }

        awaitNoProcessing(Duration.ofSeconds(5));

        assertEquals((long) dataCount, taskDataRepository.countByProcessStatus("SUCCESS"));
        assertEquals(0L, taskDataRepository.countByProcessStatus("PENDING"));
        assertTrue(maxProcessing <= MAX_CONCURRENT_SLOTS,
                "并发槽位不应超过 " + MAX_CONCURRENT_SLOTS + "，实际峰值: " + maxProcessing);

        List<TaskData> allData = taskDataRepository.findAll();
        assertTrue(allData.stream().allMatch(data -> data.getProcessAttempts() == 1),
                "每个任务应只被抢占一次，不应出现重复消费");
    }

    /**
     * 验证异步化后，数据库驱动的指数退避重试语义保持不变。
     */
    @Test
    public void testAsyncDatabaseDrivenRetry() throws Exception {
        taskDataParallelStreamingService.deleteAll();

        TaskData taskRetry = new TaskData();
        taskRetry.setId(2001L);
        taskRetry.setDataContent("RETRY task");
        taskRetry.setProcessStatus("PENDING");

        TaskData taskNormal = new TaskData();
        taskNormal.setId(2002L);
        taskNormal.setDataContent("Normal task");
        taskNormal.setProcessStatus("PENDING");

        taskDataParallelStreamingService.saveAll(List.of(taskRetry, taskNormal));

        // 1. 第一次补位：Normal 成功，Retry 失败并进入指数退避
        taskDataParallelStreamingService.processBatchSafely();
        awaitNoProcessing(Duration.ofSeconds(5));

        TaskData dbNormal = taskDataRepository.findById(2002L).orElseThrow();
        assertEquals("SUCCESS", dbNormal.getProcessStatus());

        TaskData dbRetry = taskDataRepository.findById(2001L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetry.getProcessStatus());
        assertEquals(1, dbRetry.getProcessAttempts());
        assertNotNull(dbRetry.getNextProcessTime());
        assertTrue(dbRetry.getNextProcessTime().isAfter(LocalDateTime.now()));

        // 2. 立即再次补位：Retry 未到下一次可执行时间，不应被重新捞起
        taskDataParallelStreamingService.processBatchSafely();
        Thread.sleep(200);
        awaitNoProcessing(Duration.ofSeconds(5));

        TaskData dbRetryImmediate = taskDataRepository.findById(2001L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetryImmediate.getProcessStatus());
        assertEquals(1, dbRetryImmediate.getProcessAttempts());

        // 3. 模拟时间流逝：手动将 nextProcessTime 移至过去
        dbRetryImmediate.setNextProcessTime(LocalDateTime.now().minusSeconds(10));
        taskDataRepository.save(dbRetryImmediate);

        taskDataParallelStreamingService.processBatchSafely();
        awaitNoProcessing(Duration.ofSeconds(5));

        TaskData dbRetrySecond = taskDataRepository.findById(2001L).orElseThrow();
        assertEquals("RETRYABLE_FAILED", dbRetrySecond.getProcessStatus());
        assertEquals(2, dbRetrySecond.getProcessAttempts());

        // 4. 再次模拟时间流逝：第三次失败后达到 MAX_RETRY_ATTEMPTS = 3，状态变为 FAILED
        dbRetrySecond.setNextProcessTime(LocalDateTime.now().minusSeconds(10));
        taskDataRepository.save(dbRetrySecond);

        taskDataParallelStreamingService.processBatchSafely();
        awaitNoProcessing(Duration.ofSeconds(5));

        TaskData dbRetryFinal = taskDataRepository.findById(2001L).orElseThrow();
        assertEquals("FAILED", dbRetryFinal.getProcessStatus());
        assertEquals(3, dbRetryFinal.getProcessAttempts());
    }

    private List<TaskData> createTestData(long startId, int count, String contentPrefix) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    TaskData data = new TaskData();
                    data.setId(startId + i);
                    data.setDataContent(contentPrefix + i);
                    data.setProcessStatus("PENDING");
                    return data;
                })
                .collect(Collectors.toList());
    }

    private void awaitNoProcessing(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (taskDataRepository.countByProcessStatus("PROCESSING") == 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("等待 PROCESSING 状态归零超时");
    }
}
