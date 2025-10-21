package cn.net.pap.quartz;

import org.junit.Test;
import org.junit.runner.RunWith;
import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.service.ITaskDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {QuartzAutoConfiguration.class})
@TestPropertySource("classpath:application.properties")
public class TaskDataTest {

    @Autowired
    private ITaskDataService taskDataService;

    private ExecutorService executorService = Executors.newFixedThreadPool(20);

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
            executorService.submit(() -> {
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

}
