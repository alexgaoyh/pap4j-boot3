package cn.net.pap.task.executor;

import cn.net.pap.task.callable.PapCallable;
import cn.net.pap.task.enums.TaskEnums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 线程池任务执行
 */
public class TaskExecutorUtil {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorUtil.class);

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
    public static <V> List<TaskEnums> executeTasks(String beanName, List<PapCallable<V>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(availableProcessors * corePoolSizeTimes);
        executor.setMaxPoolSize(availableProcessors * maxPoolSizeTimes);
        executor.setQueueCapacity(availableProcessors * queueCapacityTimes);
        executor.setBeanName(beanName);
        // 1、如果 核心线程数 == 最大线程数 == 队列容量 == 1。
        // 1.1、提交第1个任务之后创建核心线程并执行任务，提交第2个任务后核心线程忙并放入队列，提交第3个任务时队列满触发拒绝策略
        // 1.2、最大同时执行的任务数 = corePoolSize = 1，最大等待的任务数 = queueCapacity = 1，总容量 = 1 + 1 = 2个任务（1个执行中 + 1个排队）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        // 初始化一个与 tasks 等长的 List，默认填满 unknown，防止被异常中断掩盖真实状态
        List<TaskEnums> results = new ArrayList<>(Collections.nCopies(tasks.size(), TaskEnums.NOT_EXECUTED));

        List<Future<V>> futures = new ArrayList<>();
        try {
            for (int idx = 0; idx < tasks.size(); idx++) {
                PapCallable<V> task = tasks.get(idx);
                // 将 catch 移入循环内部。防止提交任务触发拒绝策略时，直接跳出循环导致后续任务全被抛弃
                try {
                    Future<V> future = executor.submit(task);
                    futures.add(future);
                } catch (Throwable e) {
                    // 包含了所有的 Exception 和所有的 Error
                    futures.add(null); // 用 null 占位，保证 futures 长度与 tasks 对齐
                    if (e instanceof TaskRejectedException) {
                        results.set(idx, TaskEnums.REJECT);
                    } else {
                        results.set(idx, TaskEnums.UNKNOWN);
                    }
                }
            }
        } finally {
            for (int idx = 0; idx < futures.size(); idx++) {
                Future<V> future = futures.get(idx);
                if (future == null) {
                    continue; // 说明在 submit 阶段就失败了，直接跳过
                }
                try {
                    future.get(10, TimeUnit.MINUTES); // 等待任务完成
                    results.set(idx, TaskEnums.SUCCESS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Task interrupted ", e);
                    results.set(idx, TaskEnums.INTERRUPTED); // 记录异常状态
                    // break 跳出循环。当前线程的中断标志已被置为 true，若继续循环，后续所有的 future.get() 都会不经等待瞬间抛出 InterruptedException，从而导致疯狂刷屏报错的“日志风暴”。跳出后，剩余任务会保持默认的 unknown 状态。
                    break;
                } catch (ExecutionException e) {
                    log.error("Task execution failed ", e);
                    Throwable cause = e.getCause();
                    String msg = (cause != null) ? cause.getMessage() : e.getMessage();
                    results.set(idx, TaskEnums.EXECUTION_FAILED); // 记录异常状态
                } catch (TimeoutException e) {
                    log.error("Task execution timeout at index {}", idx, e);
                    results.set(idx, TaskEnums.TIMEOUT);
                    future.cancel(true); // 尝试强杀还在跑的超时任务
                }
            }
            executor.shutdown();
            try {
                if (!executor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.getThreadPoolExecutor().shutdownNow();
                    if (!executor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                        log.error("Executor did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                executor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        return results;
    }

}
