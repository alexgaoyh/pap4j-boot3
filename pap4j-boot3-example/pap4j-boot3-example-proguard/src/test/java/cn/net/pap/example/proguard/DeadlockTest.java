package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
class DeadlockTest {

    private static final Logger log = LoggerFactory.getLogger(DeadlockTest.class);

    @Autowired
    private IProguardService proguardService;

    // @Test
    void testDeadlock() throws InterruptedException {
        init(); // 初始化数据

        CountDownLatch latch = new CountDownLatch(2); // 用于确保两个事务同时启动
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                2,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "test-deadlock-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        boolean deadlockOccurred = false;
        int attempt = 0;
        final int MAX_ATTEMPTS = 99999; // 最大尝试次数

        try {
            while (!deadlockOccurred && attempt < MAX_ATTEMPTS) {
                attempt++;
                System.out.println("Attempt " + attempt + " to trigger deadlock...");

                CountDownLatch finalLatch = latch;
                Callable<Void> task1 = () -> {
                    finalLatch.countDown();
                    finalLatch.await(); // 等待所有线程就绪

                    proguardService.checkDeadLock(1l, 2l);

                    return null;
                };

                Callable<Void> task2 = () -> {
                    finalLatch.countDown();
                    finalLatch.await(); // 等待所有线程就绪

                    proguardService.checkDeadLock(2l, 1l);

                    return null;
                };

                Future<Void> future1 = executor.submit(task1);
                Future<Void> future2 = executor.submit(task2);

                try {
                    future1.get();
                    future2.get();
                    latch = new CountDownLatch(2); // 重置 CountDownLatch
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        System.err.println("Deadlock detected: " + e.getCause().getMessage());
                        deadlockOccurred = true; // 假设死锁发生
                    }
                }
            }

            assertTrue(deadlockOccurred, "Deadlock did not occur within " + MAX_ATTEMPTS + " attempts.");
        } finally {
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    void init() {
        // 初始化两条数据
        Proguard proguard1 = geneEntity();
        proguard1.setProguardId(1L);
        proguardService.saveAndFlush(proguard1);

        Proguard proguard2 = geneEntity();
        proguard2.setProguardId(2L);
        proguardService.saveAndFlush(proguard2);
    }

    private Proguard geneEntity() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Proguard proguard = new Proguard();
        proguard.setProguardName("alexgaoyh");
        proguard.setExtMap(extMap);
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);
        return proguard;
    }
}