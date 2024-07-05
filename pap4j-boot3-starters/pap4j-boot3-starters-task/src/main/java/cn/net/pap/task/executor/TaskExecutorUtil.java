package cn.net.pap.task.executor;

import cn.net.pap.task.callable.PapCallable;
import cn.net.pap.task.enums.TaskEnums;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 线程池任务执行
 */
public class TaskExecutorUtil {

    // setCorePoolSize 相较于 Runtime.getRuntime().availableProcessors(); 的倍数
    public static final Integer corePoolSizeTimes = 2;

    // setMaxPoolSize 相较于 Runtime.getRuntime().availableProcessors(); 的倍数
    public static final Integer maxPoolSizeTimes = 2 * 2;

    // setQueueCapacity 相较于 Runtime.getRuntime().availableProcessors() 的倍数
    public static final Integer queueCapacityTimes = 2 * 100;

    /**
     * 执行
     *
     * @param beanName
     * @param tasks
     */
    public static <V> TaskEnums executeTasks(String beanName, List<PapCallable<V>> tasks) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(availableProcessors * corePoolSizeTimes);
        executor.setMaxPoolSize(availableProcessors * maxPoolSizeTimes);
        executor.setQueueCapacity(availableProcessors * queueCapacityTimes);
        executor.setBeanName(beanName);
        executor.initialize();

        List<Future<V>> futures = new ArrayList<>();
        try {
            for (int idx = 0; idx < tasks.size(); idx++) {
                PapCallable<V> task = tasks.get(idx);
                Future<V> future = executor.submit(task);
                futures.add(future);
            }
        } catch (Exception e) {
            if (e instanceof TaskRejectedException) {
                return TaskEnums.reject(((TaskRejectedException) e).getMessage());
            } else {
                return TaskEnums.unknown(e.getMessage());
            }
        } finally {
            for (Future<V> future : futures) {
                try {
                    future.get(); // 等待任务完成
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Task interrupted: " + e.getMessage());
                } catch (ExecutionException e) {
                    System.err.println("Task execution failed: " + e.getCause());
                }
            }
            executor.shutdown();
            try {
                if (!executor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.getThreadPoolExecutor().shutdownNow();
                    if (!executor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                        System.err.println("Executor did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        return TaskEnums.SUCCESS;
    }

}
