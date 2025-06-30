package cn.net.pap.task;

import cn.net.pap.task.util.DynamicTaskExecutorUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DynamicTaskExecutorUtilTest {

    // @Test
    public void taskTest1() {
        // 创建线程池（由调用方管理生命周期）
        ExecutorService executor = Executors.newFixedThreadPool(8);

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
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

}
