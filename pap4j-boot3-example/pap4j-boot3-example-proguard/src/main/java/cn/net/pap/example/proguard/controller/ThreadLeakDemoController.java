package cn.net.pap.example.proguard.controller;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
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

    /**
     * 【系统级内存监控接口】
     * * <p><strong>核心功能：</strong><br>
     * 获取操作系统维度的物理内存状态及当前 Java 进程的虚拟内存占用，
     * 弥补了标准 JVM 堆监控（Heap Metrics）无法观测到本地内存（Native Memory）的盲区。</p>
     *
     * <p><strong>可解决的具体问题：</strong>
     * <ul>
     * <li><strong>定位线程泄露：</strong> 线程栈（Thread Stack）是在堆外分配的。如果堆内存稳定，但“当前进程占用虚拟内存”持续线性上涨，说明存在线程泄露。</li>
     * <li><strong>排查堆外内存溢出：</strong> 监控 DirectBuffer、MappedByteBuffer 或本地库（如 OpenCV、libvips）导致的 Native Memory 占用异常。</li>
     * <li><strong>预防容器 OOM Killer：</strong> 在 Docker/K8s 环境下，当进程 RSS 接近容器 Limit 时，可提前感知并触发预警或降级，防止进程被系统强制杀掉。</li>
     * <li><strong>诊断系统级内存争抢：</strong> 即使 Java 进程正常，也能发现宿主机上其他进程是否占满了内存，导致 Java 进程由于 SWAP 交换而变慢。</li>
     * </ul>
     * </p>
     *
     * @return 包含系统总内存、剩余内存、利用率及当前进程虚拟内存占用的 Map
     */
    @GetMapping("/getMemorySystemInfo")
    public Map<String, Object> getMemorySystemInfo() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        Map<String, Object> metrics = new LinkedHashMap<>();

        // ==========================================
        // 1. JVM 堆内存指标 (Heap Memory) - 纯 Java 对象
        // ==========================================
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        long heapMax = heapUsage.getMax();
        long heapCommitted = heapUsage.getCommitted();
        long heapUsed = heapUsage.getUsed();

        // JVM 启动参数 -Xmx 设定的最大上限。如果对象实在放不下，会报 OOM: Java heap space。
        metrics.put("JVM最大堆内存(MB)", heapMax / 1024 / 1024.0);

        // JVM 当前已经向操作系统申请到的堆内存大小（动态变化，通常在 -Xms 和 -Xmx 之间）。
        metrics.put("JVM已分配堆内存(MB)", heapCommitted / 1024 / 1024.0);

        // 当前真正在使用的堆内存（实际存放 Java 对象的空间）。
        // 排查价值：每次 GC 后这个值会下降。如果它呈锯齿状平稳，说明 Java 对象正常回收。
        metrics.put("JVM已使用堆内存(MB)", heapUsed / 1024 / 1024.0);

        // 堆内存压力评估指标
        metrics.put("JVM堆内存使用率", String.format("%.2f%%", (double) heapUsed / heapMax * 100));


        // ==========================================
        // 2. 进程级与系统级物理/虚拟内存指标 (Native & OS)
        // ==========================================
        long processMemory = osBean.getCommittedVirtualMemorySize();
        long totalPhysicalMemory = osBean.getTotalPhysicalMemorySize();
        long freePhysicalMemory = osBean.getFreePhysicalMemorySize();

        // 这是操作系统为当前运行的 Java 进程**承诺分配（Committed）**的虚拟内存总量。
        // 它包含了什么： 它不仅包含我们在 JVM 启动参数中设置的堆内存（-Xms, -Xmx），还包含了所有的堆外内存（Native Memory）。
        // 具体包括： 每一个线程的线程栈（Thread Stack）。 NIO 使用的直接内存（Direct Buffer）。
        // 关键点： 在处理高保真扫描图像时，如果底层调用了 libvips、OpenCV 或 libjpeg-turbo 等底层 C/C++ 库，这些库通过 JNI 直接向操作系统申请的内存，全部都会统计在这个值里，
        // 而不会出现在 JVM 的堆内存监控中。
        // 排查价值： 【核心比对法】将此值减去上方的“JVM已使用堆内存”。
        // 如果 JVM 堆内存一直很平稳，但这个“虚拟内存”指标却像爬楼梯一样不断上涨，
        // 说明你的应用存在堆外内存泄露（例如：底层图像处理库的内存未释放，或者局部线程池未关闭导致线程持续堆积）。
        metrics.put("当前进程占用总虚拟内存(MB)", processMemory / 1024 / 1024.0);

        // 服务器主板上插着的物理内存条的总容量（即机器的真实物理上限，例如 16GB 或 32GB）。
        metrics.put("系统总物理内存(GB)", totalPhysicalMemory / 1024 / 1024 / 1024.0);

        // 操作系统当前完全空闲、未被任何人使用的物理内存大小。
        // 排查价值： 这是服务器的生命安全线。
        // 当这个值开始逼近 0 时，Linux 系统会为了自保开始使用 SWAP（将硬盘当内存用），导致系统性能断崖式下跌。
        // 如果彻底耗尽，操作系统会触发 OOM Killer，直接暴力杀死消耗内存最大的进程（通常就是你的 Java 进程），这会导致应用在没有任何异常日志的情况下突然崩溃。
        metrics.put("系统剩余物理内存(GB)", freePhysicalMemory / 1024 / 1024 / 1024.0);

        metrics.put("系统整体内存利用率", String.format("%.2f%%", (1 - (double) freePhysicalMemory / totalPhysicalMemory) * 100));

        return metrics;
    }

}