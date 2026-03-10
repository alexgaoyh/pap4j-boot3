package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.dto.ProcessResult;
import cn.net.pap.example.admin.util.ProcessPoolUtil;
import cn.net.pap.example.admin.util.ProcessPoolUtilExample;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/processPoolUtil")
public class ProcessPoolUtilController {

    private static final Logger log = LoggerFactory.getLogger(ProcessPoolUtilController.class);

    @Configuration
    public class ThreadPoolConfig {

        @Bean(name = "processExecutor")
        public static ThreadPoolTaskExecutor processExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(5);
            executor.setMaxPoolSize(5);
            executor.setQueueCapacity(100);
            executor.setThreadNamePrefix("process-pool-");
            // --- 关键配置 ---
            // 设为 true，容器关闭时会等待任务完成
            executor.setWaitForTasksToCompleteOnShutdown(true);
            // 设置等待的最长时间（如果任务太长，不能无限等下去）
            executor.setAwaitTerminationSeconds(6000);
            // 设置拒绝策略。当队列满了且池也满了，由调用者线程执行，防止丢任务
            executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
            executor.initialize();
            return executor;
        }

        @Bean(name = "testThreadPoolExecutor")
        public static ThreadPoolExecutor testThreadPoolExecutor() {
            return new ThreadPoolExecutor(
                    5,
                    20,
                    60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(200),
                    r -> {
                        Thread t = new Thread(r, "process-pool-thread");
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }

    }

    @Autowired
    @Qualifier("processExecutor")
    private ThreadPoolTaskExecutor executor;

    @Autowired
    private ThreadPoolExecutor testThreadPoolExecutor;

    /**
     * 容器关闭前执行的清理逻辑
     * 无需继承接口，Spring 会自动扫描并执行标注了 @PreDestroy 的方法
     */
    @PreDestroy
    public void shutdownPools() {
        log.info("检测到项目关闭，正在清理线程资源...");

        // 1. 处理自定义的 ThreadPoolExecutor (testThreadPoolExecutor)
        if (testThreadPoolExecutor != null && !testThreadPoolExecutor.isShutdown()) {
            testThreadPoolExecutor.shutdown(); // 停止接收新任务
            try {
                // 等待 30 秒，给正在运行的任务一点时间
                if (!testThreadPoolExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                    testThreadPoolExecutor.shutdownNow(); // 超时强制关闭
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                testThreadPoolExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 2. 对于 processExecutor (ThreadPoolTaskExecutor)
        // 虽然在 @Bean 配置了 waitForTasksToCompleteOnShutdown，
        // 但显式调用 destroy() 可以确保立即触发 Spring 的销毁逻辑
        if (executor != null) {
            executor.destroy();
        }

        // 3. 清理结果缓存，防止内存残留
        results.clear();

        log.info("所有线程池已安全退出。");
    }

    /**
     * 最简单的“任务表”， 后续可以改为本地缓存
     */
    private final ConcurrentHashMap<String, ProcessResult> results = new ConcurrentHashMap<>();

    /**
     * NOT SUPPORT IN FAT JAR
     * @return
     */
    @Operation(summary = "异步请求")
    @GetMapping("/java")
    public String runJavaFuture() {
        String mainClass = ProcessPoolUtilExample.class.getName();

        String taskId = UUID.randomUUID().toString();
        ProcessResult result = new ProcessResult();
        results.put(taskId, result);

        executor.execute(() -> {
            ProcessResult r = ProcessPoolUtil.runJavaClass(
                    mainClass, null, 0, testThreadPoolExecutor
            );
            result.exitCode = r.getExitCode();
            result.output = r.getOutput();
            result.finished = r.isFinished();
        });

        return taskId;
    }

    /**
     * NOT SUPPORT IN FAT JAR
     * @return
     */
    @Operation(summary = "异步请求")
    @GetMapping("/javaResults")
    public ConcurrentHashMap<String, ProcessResult> runJavaFutureResults() {
        return results;
    }

}
