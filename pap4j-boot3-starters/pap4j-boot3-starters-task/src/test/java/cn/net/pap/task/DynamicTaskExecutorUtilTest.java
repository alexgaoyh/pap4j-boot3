package cn.net.pap.task;

import cn.net.pap.task.util.DynamicTaskExecutorUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DynamicTaskExecutorUtilTest {

    private static final Logger log = LoggerFactory.getLogger(DynamicTaskExecutorUtilTest.class);

    // @Test
    public void taskTest1() {
        // 创建线程池（由调用方管理生命周期）
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                8,
                8,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100),
                r -> new Thread(r, "dynamic-task-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );

        try {
            // 准备任务
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                tasks.add(() -> {
                    Thread.sleep(1000); // 模拟任务执行
                    return "Task-" + taskId + " result";
                });
            }

            // 执行任务
            List<DynamicTaskExecutorUtil.TaskResult<String>> results = DynamicTaskExecutorUtil.executeCallableTasks(executor, tasks, 4, // 最大并发4
                    progress -> System.out.printf("进度: %.1f%%%n", progress.getProgressPercent()));

            // 处理结果
            results.forEach(result -> {
                if (result.isSuccess()) {
                    System.out.println("成功: " + result.getResult());
                } else {
                    System.out.println("失败: " + result.getException().getMessage());
                }
            });
        } finally {
            // 关闭线程池（由调用方负责）
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.warn("部分线程池任务未在 60 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
