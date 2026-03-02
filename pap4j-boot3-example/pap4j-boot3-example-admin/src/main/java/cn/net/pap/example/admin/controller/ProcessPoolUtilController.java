package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.dto.ProcessResult;
import cn.net.pap.example.admin.util.ProcessPoolUtil;
import cn.net.pap.example.admin.util.ProcessPoolUtilExample;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/processPoolUtil")
public class ProcessPoolUtilController {

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

        @Bean(name = "testExecutorService")
        public static ExecutorService testExecutorService() {
            return Executors.newCachedThreadPool();
        }

    }

    @Autowired
    @Qualifier("processExecutor")
    private ThreadPoolTaskExecutor executor;

    @Autowired
    private ExecutorService testExecutorService;

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
                    mainClass, null, 0, testExecutorService
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
