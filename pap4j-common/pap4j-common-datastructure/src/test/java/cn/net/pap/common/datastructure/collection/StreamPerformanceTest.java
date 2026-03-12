package cn.net.pap.common.datastructure.collection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Stream 与 ParallelStream 性能差异的单元测试
 * 基于 JDK 17
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StreamPerformanceTest {

    // 使用 JDK 14+ 的 record 定义轻量级数据载体
    record Order(int id, String category) {
    }

    // 预定义一些数据常量
    private static final int SMALL_DATA_SIZE = 2_000;       // 模拟从DB查出的小数据量（2000条）
    private static final int LARGE_DATA_SIZE = 5_000_000;   // 模拟大数据量（500万条）

    private static List<Order> smallData;
    private static List<Order> largeData;

    @BeforeAll
    static void setUp() {
        System.out.println("正在生成测试数据...");
        smallData = generateData(SMALL_DATA_SIZE);
        largeData = generateData(LARGE_DATA_SIZE);
        System.out.println("数据生成完毕！\n");

        // JVM 预热，防止 JIT 编译影响首次测试结果
        warmUp();
    }

    private static List<Order> generateData(int size) {
        return IntStream.range(0, size).mapToObj(i -> new Order(i, "Category_" + (i % 10))) // 分成10个类别
                .toList(); // JDK 16+ 的 toList()
    }

    private static void warmUp() {
        for (int i = 0; i < 100; i++) {
            smallData.stream().collect(Collectors.groupingBy(Order::category));
            smallData.parallelStream().collect(Collectors.groupingBy(Order::category));
        }
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("1. 小数据量对比 (几千条): stream() 通常比 parallelStream() 更快")
    void testSmallDataPerformance() {
        System.out.println("=== 测试一：小数据量 (" + SMALL_DATA_SIZE + " 条) ===");

        long start1 = System.nanoTime();
        Map<String, List<Order>> map1 = smallData.stream().collect(Collectors.groupingBy(Order::category));
        long time1 = System.nanoTime() - start1;

        long start2 = System.nanoTime();
        Map<String, List<Order>> map2 = smallData.parallelStream().collect(Collectors.groupingBy(Order::category));
        long time2 = System.nanoTime() - start2;

        System.out.printf("普通 Stream 耗时:   %,d 纳秒 (%.3f 毫秒)%n", time1, time1 / 1_000_000.0);
        System.out.printf("并行 Parallel 耗时: %,d 纳秒 (%.3f 毫秒)%n", time2, time2 / 1_000_000.0);
        if (time1 < time2) {
            System.out.println("实际结论：在当前数据量下，普通 Stream 更快。并行流的线程调度开销超过了计算收益。\n");
        } else {
            System.out.println("实际结论：在当前数据量和机器环境下，并行 Parallel 已经展现出速度优势。\n");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("2. 大数据量对比 (数百万条): parallelStream() 开始展现出优势")
    void testLargeDataPerformance() {
        System.out.println("=== 测试二：大数据量 (" + LARGE_DATA_SIZE + " 条) ===");

        long start1 = System.currentTimeMillis();
        Map<String, List<Order>> map1 = largeData.stream().collect(Collectors.groupingBy(Order::category));
        long time1 = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        Map<String, List<Order>> map2 = largeData.parallelStream().collect(Collectors.groupingBy(Order::category));
        long time2 = System.currentTimeMillis() - start2;

        System.out.printf("普通 Stream 耗时:   %d 毫秒%n", time1);
        System.out.printf("并行 Parallel 耗时: %d 毫秒%n", time2);
        if (time1 > time2) {
            System.out.println("实际结论：在大数据量下，并行 Parallel 展现出了碾压性的优势。由于数据处理量足够大，多核并行加速的收益远远盖过了底层的线程调度开销。\n");
        } else {
            System.out.println("实际结论：在当前机器环境下，即使是大数据量，普通 Stream 依然更快。这通常是因为测试机的 CPU 核心数较少，或者系统当前负载极高，导致无法发挥并行优势。\n");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("3. 高并发场景争抢点测试: Web请求并发下的 ParallelStream 性能衰减")
    void testHighConcurrencyContention() throws InterruptedException {
        System.out.println("=== 测试三：高并发场景下处理小数据量 ===");

        int concurrentRequests = 200; // 模拟 200 个并发 HTTP 请求
        ExecutorService tomcatThreadPool = Executors.newFixedThreadPool(50); // 模拟 Tomcat 线程池（50个工作线程）

        // 1. 测试全部使用普通 stream()
        CountDownLatch latch1 = new CountDownLatch(concurrentRequests);
        long start1 = System.currentTimeMillis();
        for (int i = 0; i < concurrentRequests; i++) {
            tomcatThreadPool.submit(() -> {
                smallData.stream().collect(Collectors.groupingBy(Order::category));
                latch1.countDown();
            });
        }
        latch1.await();
        long time1 = System.currentTimeMillis() - start1;

        // 2. 测试全部使用 parallelStream()
        CountDownLatch latch2 = new CountDownLatch(concurrentRequests);
        long start2 = System.currentTimeMillis();
        for (int i = 0; i < concurrentRequests; i++) {
            tomcatThreadPool.submit(() -> {
                // parallelStream 底层共享 ForkJoinPool.commonPool()
                smallData.parallelStream().collect(Collectors.groupingBy(Order::category));
                latch2.countDown();
            });
        }
        latch2.await();
        long time2 = System.currentTimeMillis() - start2;

        tomcatThreadPool.shutdown();

        System.out.printf("200个并发请求 全部用 Stream 处理总耗时:   %d 毫秒%n", time1);
        System.out.printf("200个并发请求 全部用 Parallel 处理总耗时: %d 毫秒%n", time2);
        if (time1 < time2) {
            System.out.println("实际结论：高并发下普通 Stream 完胜！这证明了：在外部已经有线程池（如 Tomcat）并发调度的场景下，内部再滥用 parallelStream 会导致全局 ForkJoinPool.commonPool() 发生严重的锁竞争和上下文切换，反而大幅拉低吞吐量。\n");
        } else {
            System.out.println("实际结论：在当前高并发测试中，Parallel 依然没有落后。这极其罕见，通常是因为你的机器 CPU 极其强悍。但在真实的 Web 高并发生产环境中，依然强烈建议避免这种嵌套并发造成的资源争抢风险。\n");
        }
    }

    @Test
    @DisplayName("测试：将 parallelStream 丢入自定义 ForkJoinPool 中执行，实现资源隔离")
    void testParallelStreamInCustomPool() {
        // 1. 准备测试数据 (模拟 100 个需要重度计算的任务)
        List<Integer> largeList = IntStream.range(0, 100).boxed().toList();

        // 2. 用于收集实际执行任务的线程名称，验证是否被隔离
        Set<String> threadNames = ConcurrentHashMap.newKeySet();

        // 3. 创建一个自定义的 ForkJoinPool，分配 4 个核心
        // 为了演示效果，我们传入一个自定义的 ThreadFactory，给线程打上专属标签
        ForkJoinPool.ForkJoinWorkerThreadFactory customFactory = pool -> {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("CustomBizPool-Worker-" + worker.getPoolIndex());
            return worker;
        };
        ForkJoinPool customThreadPool = new ForkJoinPool(4, customFactory, null, false);

        System.out.println("开始执行被隔离的并行任务...");
        long startTime = System.currentTimeMillis();

        try {
            // 4. 【核心黑魔法】将 parallelStream 提交到自定义线程池
            List<String> result = customThreadPool.submit(() -> largeList.parallelStream().map(item -> heavyCpuCalculation(item, threadNames)) // 模拟耗时计算并记录线程名
                    .toList()).get(); // 阻塞等待执行结果

            long costTime = System.currentTimeMillis() - startTime;
            System.out.printf("任务执行完毕，总耗时: %d 毫秒%n", costTime);
            System.out.printf("共处理了 %d 条数据%n", result.size());

            // 5. 打印并验证参与计算的线程
            System.out.println("\n=== 实际参与计算的线程列表 ===");
            threadNames.forEach(System.out::println);

            // 6. 断言 (Assertions) 验证隔离是否成功
            // 验证1：必须有我们自定义前缀的线程参与了工作
            boolean usedCustomPool = threadNames.stream().anyMatch(name -> name.startsWith("CustomBizPool-Worker-"));
            assertTrue(usedCustomPool, "任务没有使用自定义线程池执行！");

            // 验证2：绝对不能有公共池 (commonPool) 的线程参与工作
            boolean usedCommonPool = threadNames.stream().anyMatch(name -> name.contains("commonPool"));
            assertFalse(usedCommonPool, "发生了资源泄漏，公共 ForkJoinPool 被占用了！");

            // 验证3：主线程 (main) 也不应该参与计算（因为我们通过 submit 交出去了）
            assertFalse(threadNames.contains("main"), "主线程不应参与具体计算！");

            System.out.println("\n测试通过：成功实现了 ParallelStream 的线程池物理隔离！");

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            // 7. 优雅关闭线程池
            customThreadPool.shutdown();
        }
    }

    /**
     * 模拟重度 CPU 计算的方法
     */
    private String heavyCpuCalculation(Integer item, Set<String> threadNames) {
        // 记录当前执行此任务的线程名称
        String currentThreadName = Thread.currentThread().getName();
        threadNames.add(currentThreadName);

        try {
            // 模拟单个任务极其耗时 (比如复杂的加密算法，耗时 10 毫秒)
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "Result_" + item;
    }

}
