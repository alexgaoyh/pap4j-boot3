package cn.net.pap.example.proguard.controller;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadFactory;

/**
 * 线程泄露演示与排查实战控制器 (Thread Leak Demo & Analyzer)
 * <p>
 * 本类的主要作用是：演示在日常业务开发中“错误创建局部线程池”是如何导致 JVM 线程资源泄露的，
 * 并提供一个基于 {@link ThreadMXBean} 的实用排查接口，用于在运行时动态抓取并精准定位泄露源头。
 * <p>
 * 核心功能与使用方法：
 * <ul>
 * <li>
 * <b>【对照实验接口】 /api/threads/process</b><br>
 * - 错误示范 (?useCorrectPool=false 默认)：模拟每次请求都在方法内部 new 一个 ExecutorService 且不关闭。
 * 由于工作线程是 GC Root 且一直挂起等待新任务，会导致严重的线程与内存泄露（演示中被标记为 bad-local-pool-N）。<br>
 * - 正确示范 (?useCorrectPool=true)：复用由 Spring 容器统一生命周期管理的全局线程池 (correctBizPool)，
 * 保证线程数量在可控范围内，且支持应用层面的优雅停机。
 * </li>
 * <li>
 * <b>【监控排查接口】 /api/threads/analyze</b><br>
 * - 利用 JMX 获取当前 JVM 所有存活线程。<br>
 * - 使用了经典的“正则抹平分组法”（将线程名中的数字全部替换为 N），实现按业务线程池类型进行归类统计并倒序排列。<br>
 * - 当线上发生线程飙高告警时，此接口能第一时间揪出数量异常的“罪魁祸首”。
 * </li>
 * </ul>
 * <p>
 * 最佳实践箴言：
 * 1. 凡开启资源，必考虑关闭；局部变量创建的线程池若不显式 shutdown，绝不会被垃圾回收。
 * 2. 永远要为自定义线程池显式命名（通过 ThreadFactory ），这是发生故障时能快速排查的唯一救命稻草。
 * 3. 生产环境强烈建议将线程池封装为 Spring Bean (如 ThreadPoolTaskExecutor)，由框架兜底管理生命周期。
 */
@RestController
@RequestMapping("/api/threads")
public class ThreadLeakDemoController {

    // ------------------------------------------------------------------------
    // 1. 内部静态配置类：统一管理线程池 Bean，完美解决优雅停机与单文件限制
    // ------------------------------------------------------------------------
    @Configuration
    public static class ThreadPoolConfig {

        @Bean("correctBizPool")
        public ThreadPoolTaskExecutor correctBizPool() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(5);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("correct-biz-pool-"); // 自动设置带编号的线程名前缀

            // 拒绝策略：由调用者所在的线程执行
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

            // 优雅停机核心配置
            executor.setWaitForTasksToCompleteOnShutdown(true); // 停机时等待任务完成
            executor.setAwaitTerminationSeconds(30);            // 最多等待 30 秒

            executor.initialize();
            return executor;
        }
    }

    // ------------------------------------------------------------------------
    // 2. Controller 核心逻辑
    // ------------------------------------------------------------------------

    private final ThreadPoolTaskExecutor correctBizPool;

    // 通过构造器注入 Spring 管理的全局线程池
    public ThreadLeakDemoController(ThreadPoolTaskExecutor correctBizPool) {
        this.correctBizPool = correctBizPool;
    }

    /**
     * 业务接口：模拟线程池的正确与错误使用
     *
     * @param useCorrectPool 开关：true-使用全局正确的线程池，false-使用局部错误的线程池（默认）
     */
    @GetMapping("/process")
    public String processTask(@RequestParam(defaultValue = "false") boolean useCorrectPool) {

        Runnable bizTask = () -> {
            try {
                // 模拟耗时 50ms 的业务逻辑
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        if (useCorrectPool) {
            // 【正确用法】：提交给 Spring 容器管理的全局线程池
            // 数量被严格限制，且应用关闭时会自动优雅回收
            correctBizPool.execute(bizTask);
            return "任务已提交至【全局】正确线程池，Spring 会守护它的生命周期！";
        } else {
            // 【错误用法/泄露现场】：每次请求局部 new 一个线程池，且不关闭
            // 这些线程会作为 GC Root 一直存活，死等任务，吃光内存
            // 给泄露的线程池起个显眼的名字，方便在监控中抓现行
            ExecutorService leakingLocalPool = Executors.newFixedThreadPool(2, new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);

                // 致命毒药：每次创建线程池，直接在堆内存划走 10MB 空间 因为这个匿名内部类会被 ThreadPoolExecutor 持有，所以它随线程池共存亡
                private final byte[] heavyPayload = new byte[10 * 1024 * 1024];

                @Override
                public Thread newThread(Runnable r) {
                    // 命名为 bad-local-pool-x
                    return new Thread(r, "bad-local-pool-" + counter.getAndIncrement());
                }
            });
            leakingLocalPool.submit(bizTask);
            return "任务已提交至【局部】错误线程池，你刚刚制造了 2 个永远无法回收的线程！";
        }
    }

    /**
     * 监控接口：使用 ThreadMXBean 排查并揪出泄露的线程
     */
    @GetMapping("/analyze")
    public Map<String, Integer> analyzeThreadLeak() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadBean.getAllThreadIds();

        // 仅获取线程基础信息，避免获取锁信息带来性能损耗
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);

        Map<String, Integer> prefixCountMap = new LinkedHashMap<>();

        for (ThreadInfo info : threadInfos) {
            if (info == null) continue;

            String originalName = info.getThreadName();

            // 核心排查技巧：将所有数字替换为 "N"，实现精准分组
            // "pool-1-thread-1" -> "pool-N-thread-N"
            // "correct-biz-pool-1" -> "correct-biz-pool-N"
            String groupedName = originalName.replaceAll("\\d+", "N");

            prefixCountMap.put(groupedName, prefixCountMap.getOrDefault(groupedName, 0) + 1);
        }

        // 按线程数量倒序排列，排在最前面的大概率就是泄露的“罪魁祸首”
        return prefixCountMap.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
}