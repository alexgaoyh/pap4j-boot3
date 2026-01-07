package cn.net.pap.common.worker.simple;

import cn.net.pap.common.worker.simple.dto.SimpleTaskDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimpleMasterTest {
    private SimpleMaster master;

    @BeforeEach
    void setUp() {
        // 创建有3个工作进程的主进程
        master = new SimpleMaster(3);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // 测试结束后停止主进程
        master.shutdown();
        Thread.sleep(100); // 给工作进程一点时间完成
    }

    @Test
    @DisplayName("测试提交单个任务")
    void testSubmitSingleTask() throws InterruptedException {
        // 准备
        SimpleTaskDTO task = new SimpleTaskDTO("T001", "简单计算", 50);

        // 执行
        master.submitTask(task);

        // 等待任务处理
        Thread.sleep(200);

        // 验证：手动查看控制台输出
        System.out.println("测试单个任务完成");
    }

    @Test
    @DisplayName("测试并发提交多个任务")
    void testConcurrentTasks() throws InterruptedException {
        // 创建10个任务，处理时间不同
        for (int i = 0; i < 10; i++) {
            SimpleTaskDTO task = new SimpleTaskDTO(
                    "T" + String.format("%03d", i),
                    "任务-" + i,
                    100 + i * 10  // 每个任务处理时间递增
            );
            master.submitTask(task);
        }

        // 查看状态
        master.showStatus();

        // 等待所有任务处理
        Thread.sleep(2000);

        // 验证：多个工作进程应该并行处理任务
        System.out.println("测试并发任务完成");
    }

    @Test
    @DisplayName("测试工作进程负载均衡")
    void testLoadBalancing() throws InterruptedException {
        // 提交快速任务，看哪个工作进程处理
        for (int i = 0; i < 6; i++) {
            SimpleTaskDTO task = new SimpleTaskDTO("FAST-" + i, "快速任务", 20);
            master.submitTask(task);
        }

        Thread.sleep(500);

        // 理论上3个工作进程应该都能处理到任务
        System.out.println("负载均衡测试完成");
    }

    @Test
    @DisplayName("测试任务队列积压")
    void testTaskBacklog() throws InterruptedException {
        // 提交大量快速任务
        for (int i = 0; i < 100; i++) {
            SimpleTaskDTO task = new SimpleTaskDTO("BULK-" + i, "批量任务", 5);
            master.submitTask(task);
        }

        // 立即查看状态（应该有很多任务在队列中）
        master.showStatus();

        // 等待一会儿再查看
        Thread.sleep(1000);
        master.showStatus();
    }

    @Test
    @DisplayName("测试不同类型任务")
    void testDifferentTaskTypes() {
        // 演示如何扩展任务类型
        SimpleTaskDTO shortTask = new SimpleTaskDTO("S001", "短任务", 50);
        SimpleTaskDTO longTask = new SimpleTaskDTO("L001", "长任务", 500);
        SimpleTaskDTO ioTask = new SimpleTaskDTO("I001", "IO密集型任务", 300);

        master.submitTask(shortTask);
        master.submitTask(longTask);
        master.submitTask(ioTask);

        // 关键：等待足够长时间让任务处理完成
        System.out.println("\n=== 等待任务处理 ===");

        try {
            Thread.sleep(600);
            System.out.println("600ms后...");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("混合任务测试完成");
        master.showStatus();
    }

}
