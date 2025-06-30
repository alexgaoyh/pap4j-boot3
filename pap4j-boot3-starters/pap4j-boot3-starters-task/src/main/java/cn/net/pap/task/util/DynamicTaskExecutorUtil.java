package cn.net.pap.task.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 动态任务执行工具类（线程池由外部管理）
 */
public class DynamicTaskExecutorUtil {

    /**
     * 执行可调用任务集合
     *
     * @param executor         线程池（必须由调用方初始化和销毁）
     * @param tasks            任务集合
     * @param maxConcurrent    最大并发数（null表示不限制）
     * @param progressListener 进度监听器（可选）
     * @param <T>              返回类型
     * @return 任务结果列表（按提交顺序）
     */
    public static <T> List<TaskResult<T>> executeCallableTasks(ExecutorService executor, Collection<? extends Callable<T>> tasks, Integer maxConcurrent, Consumer<TaskProgress> progressListener) {

        // 参数校验
        Objects.requireNonNull(executor, "ExecutorService must not be null");
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 初始化状态跟踪
        final int totalTasks = tasks.size();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        AtomicLong taskIdGenerator = new AtomicLong(0);
        List<TaskResult<T>> results = Collections.synchronizedList(new ArrayList<>(totalTasks));

        // 创建信号量控制并发
        Semaphore semaphore = maxConcurrent != null ? new Semaphore(maxConcurrent) : null;

        // 提交所有任务
        List<CompletableFuture<Void>> futures = new ArrayList<>(totalTasks);
        for (Callable<T> task : tasks) {
            final long taskId = taskIdGenerator.incrementAndGet();
            final long submitTime = System.currentTimeMillis();

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 获取许可（如果设置了并发限制）
                    if (semaphore != null) {
                        semaphore.acquire();
                    }

                    // 执行任务
                    long startTime = System.currentTimeMillis();
                    T result = null;
                    Throwable exception = null;
                    try {
                        result = task.call();
                    } catch (Throwable e) {
                        exception = e;
                        failedCount.incrementAndGet();
                    } finally {
                        completedCount.incrementAndGet();
                    }
                    long endTime = System.currentTimeMillis();

                    // 保存结果
                    results.add(new TaskResult<>(taskId, result, exception, submitTime, startTime, endTime));

                    // 通知进度更新
                    if (progressListener != null) {
                        progressListener.accept(new TaskProgress(totalTasks, completedCount.get(), failedCount.get()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    if (semaphore != null) {
                        semaphore.release();
                    }
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 按任务ID排序返回原始顺序的结果
        return results.stream().sorted(Comparator.comparingLong(TaskResult::getTaskId)).collect(Collectors.toList());
    }

    /**
     * 执行无返回值任务
     */
    public static void executeTasks(ExecutorService executor, Collection<Runnable> tasks, Integer maxConcurrent, Consumer<TaskProgress> progressListener) {
        executeCallableTasks(executor, wrapRunnables(tasks), maxConcurrent, progressListener);
    }

    // 包装Runnable为Callable
    private static Collection<Callable<Void>> wrapRunnables(Collection<Runnable> tasks) {
        return tasks.stream().map(task -> (Callable<Void>) () -> {
            task.run();
            return null;
        }).collect(Collectors.toList());
    }

    // 进度信息类
    public static final class TaskProgress {
        private final int total;
        private final int completed;
        private final int failed;

        public TaskProgress(int total, int completed, int failed) {
            this.total = total;
            this.completed = completed;
            this.failed = failed;
        }

        public int getTotal() {
            return total;
        }

        public int getCompleted() {
            return completed;
        }

        public int getFailed() {
            return failed;
        }

        public double getProgressPercent() {
            return total > 0 ? (completed * 100.0 / total) : 0;
        }
    }

    // 任务结果类
    public static final class TaskResult<T> {
        private final long taskId;
        private final T result;
        private final Throwable exception;
        private final long submitTime;
        private final long startTime;
        private final long endTime;

        public TaskResult(long taskId, T result, Throwable exception, long submitTime, long startTime, long endTime) {
            this.taskId = taskId;
            this.result = result;
            this.exception = exception;
            this.submitTime = submitTime;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public long getTaskId() {
            return taskId;
        }

        public T getResult() {
            return result;
        }

        public Throwable getException() {
            return exception;
        }

        public long getSubmitTime() {
            return submitTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public boolean isSuccess() {
            return exception == null;
        }

        public long getElapsedMillis() {
            return endTime - startTime;
        }
    }
}
