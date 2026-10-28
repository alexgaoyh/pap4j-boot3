package cn.net.pap.common.worker.core;

import cn.net.pap.common.core.util.NamedThreadFactory;
import cn.net.pap.common.core.util.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进程内异步工作车间核心调度工具类。
 * 遵循“店长不炒菜，厨师不管排队，订单板是唯一真相”的核心规范。
 * 全面支持任务泛型返回值与异步结果获取。
 */
public class WorkerUtil implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkerUtil.class);

    /**
     * 默认租约时长：60 秒。
     */
    private static final long LEASE_DURATION_MS = 60_000L;

    /**
     * 订单板（状态唯一真相）：通过 ConcurrentHashMap 与 TaskRecord 不可变快照做原子状态流转。
     */
    private final ConcurrentHashMap<String, TaskRecord> registry = new ConcurrentHashMap<>();

    /**
     * 结果通知句柄映射表。
     */
    private final ConcurrentHashMap<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * 主任务排队区：有界阻塞队列。
     */
    private final ArrayBlockingQueue<TaskSpec<?>> queue;

    /**
     * 闹钟架：存放处于 WAITING_RETRY 状态的延迟任务。
     */
    private final DelayQueue<DelayedTask> alarmRack = new DelayQueue<>();

    /**
     * 厨师工作线程池。
     */
    private final ThreadPoolExecutor workers;

    /**
     * 闹钟独立调度线程池（单线程运行闹钟循环）。
     */
    private final ThreadPoolExecutor alarmExecutor;

    /**
     * 打烊标志位。
     */
    private final AtomicBoolean closing = new AtomicBoolean(false);

    /**
     * 默认构造方法，按机器 CPU 核心数自适应装配。
     */
    public WorkerUtil() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors()),
                Math.max(10, Runtime.getRuntime().availableProcessors() * 100));
    }

    /**
     * 显式指定厨师数与排队区容量的构造方法。
     *
     * @param workerCount   常驻厨师线程数
     * @param queueCapacity 主排队区有界容量
     */
    public WorkerUtil(int workerCount, int queueCapacity) {
        if (workerCount <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("workerCount and queueCapacity must be greater than 0");
        }

        this.queue = new ArrayBlockingQueue<>(queueCapacity);

        // 初始化常驻厨师池
        this.workers = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                new NamedThreadFactory("worker-chef-", 1),
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 初始化独立闹钟调度单线程池（避免使用禁用的 Executors.newSingleThreadExecutor）
        this.alarmExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                new NamedThreadFactory("worker-alarm-", 1),
                new ThreadPoolExecutor.DiscardPolicy()
        );

        // 启动常驻厨师循环
        for (int i = 0; i < workerCount; i++) {
            this.workers.execute(this::workerLoop);
        }

        // 启动独立闹钟循环
        this.alarmExecutor.execute(this::alarmLoop);
    }

    /**
     * 提交泛型任务规格至车间执行（三态提交与原子占座）。
     *
     * @param <T>  任务返回值类型
     * @param spec 待执行的任务规格描述
     * @return 提交结果响应（ACCEPTED / DUPLICATE / REJECTED）
     */
    @SuppressWarnings("unchecked")
    public <T> TaskSubmission<T> submit(TaskSpec<T> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("TaskSpec must not be null");
        }
        if (closing.get()) {
            CompletableFuture<T> rejectedFuture = new CompletableFuture<>();
            rejectedFuture.completeExceptionally(new RejectedExecutionException("车间已打烊，拒绝接收新任务"));
            return TaskSubmission.rejected("车间已打烊", rejectedFuture);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        final boolean[] isNew = {false};

        // 订单板原子占座（RESERVED）
        registry.compute(spec.id(), (id, existing) -> {
            if (existing == null || existing.state() == TaskState.SUCCEEDED || existing.state() == TaskState.FAILED) {
                isNew[0] = true;
                return TaskRecord.reserved(id);
            }
            return existing; // 正在排队、执行中或重试等待中的任务，保持不变
        });

        if (!isNew[0]) {
            // 重复提交，幂等返回既有 handle
            CompletableFuture<T> existingFuture = (CompletableFuture<T>) futures.get(spec.id());
            return TaskSubmission.duplicate(existingFuture);
        }

        futures.put(spec.id(), future);

        // 尝试推入排队区
        if (queue.offer(spec)) {
            registry.computeIfPresent(spec.id(), (id, r) -> r.toQueued());
            return TaskSubmission.accepted(future);
        } else {
            // 排队区满：原子回滚状态为 FAILED，杜绝脏单死锁在 WAITING 状态
            RejectedExecutionException ex = new RejectedExecutionException("排队区已满");
            registry.computeIfPresent(spec.id(), (id, r) -> r.toFailed(ex));
            future.completeExceptionally(ex);
            return TaskSubmission.rejected("排队区已满", future);
        }
    }

    /**
     * 根据任务 ID 查询订单板事实记录。
     *
     * @param id 任务 ID
     * @return 订单板记录快照，若不存在则返回 null
     */
    public TaskRecord getRecord(String id) {
        return registry.get(id);
    }

    /**
     * 获取当前主排队区积压任务数。
     *
     * @return 队列中的任务数
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取当前闹钟架（等待重试）中的任务数。
     *
     * @return 闹钟架等待任务数
     */
    public int getAlarmRackSize() {
        return alarmRack.size();
    }

    /**
     * 优雅打烊：挂打烊牌拒新单，限时排空主队列与闹钟架，超时未处理则回滚失败，两阶段终止线程池。
     *
     * @param timeout 超时时长数值
     * @param unit    时间单位
     * @return 超时未被执行的任务规格列表
     */
    public List<TaskSpec<?>> drainAndShutdown(long timeout, TimeUnit unit) {
        closing.set(true);

        long deadline = System.nanoTime() + unit.toNanos(timeout);
        // 等待主队列和闹钟架排空
        while ((!queue.isEmpty() || !alarmRack.isEmpty()) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                log.error("打烊排空等待被中断", e);
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 收集主队列未处理单据
        List<TaskSpec<?>> unexecuted = new ArrayList<>();
        queue.drainTo(unexecuted);

        // 收集闹钟架未处理单据
        List<DelayedTask> delayedList = new ArrayList<>();
        alarmRack.drainTo(delayedList);
        for (DelayedTask dt : delayedList) {
            unexecuted.add(dt.spec());
        }

        // 未执行单据状态置为失败，避免状态挂起
        RejectedExecutionException abortEx = new RejectedExecutionException("打烊排空超时未执行");
        for (TaskSpec<?> spec : unexecuted) {
            registry.computeIfPresent(spec.id(), (id, r) -> r.toFailed(abortEx));
            CompletableFuture<?> f = futures.get(spec.id());
            if (f != null && !f.isDone()) {
                f.completeExceptionally(abortEx);
            }
        }

        // 两阶段优雅关闭闹钟线程池与工作线程池（复用 ThreadPoolUtil）
        ThreadPoolUtil.shutdownAndAwaitTermination(alarmExecutor, 2, TimeUnit.SECONDS);
        ThreadPoolUtil.shutdownAndAwaitTermination(workers, 3, TimeUnit.SECONDS);

        return unexecuted;
    }

    @Override
    public void close() {
        drainAndShutdown(5, TimeUnit.SECONDS);
    }

    /**
     * 厨师工作主循环：取单 -> 盖章(租约) -> 炒菜 -> 结单（记录返回值并通知 Future）。
     */
    @SuppressWarnings("unchecked")
    private void workerLoop() {
        String workerName = Thread.currentThread().getName();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TaskSpec<?> spec = queue.poll(200, TimeUnit.MILLISECONDS);
                if (spec == null) {
                    if (closing.get() && queue.isEmpty() && alarmRack.isEmpty()) {
                        break; // 打烊且车间全空，安全离场
                    }
                    continue;
                }

                // 1. 盖章认领租约（防双跑）
                long leaseUntil = System.currentTimeMillis() + LEASE_DURATION_MS;
                TaskRecord running = registry.computeIfPresent(spec.id(), (id, r) -> r.withLease(workerName, leaseUntil));
                if (running == null) {
                    continue; // 任务已被取消或清理
                }

                CompletableFuture<Object> future = (CompletableFuture<Object>) futures.get(spec.id());

                // 2. 真正执行业务逻辑（支持泛型返回值，捕获一切异常保护厨师线程）
                try {
                    Object result = spec.action().call();
                    registry.computeIfPresent(spec.id(), (id, r) -> r.toSucceeded(result));
                    if (future != null) {
                        future.complete(result);
                    }
                } catch (Throwable t) {
                    log.error("任务 [{}] 执行发生异常: {}", spec.id(), t.getMessage(), t);
                    handleFailure(spec, running, t);
                }

            } catch (InterruptedException e) {
                log.error("厨师工作线程被中断退出: {}", workerName, e);
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 处理任务失败重试与超限终态逻辑。
     *
     * @param spec    任务规格
     * @param running 运行前事实快照
     * @param t       失败异常
     */
    private void handleFailure(TaskSpec<?> spec, TaskRecord running, Throwable t) {
        if (running.attempt() < spec.maxRetries()) {
            // 退避延迟计算：initialDelay * 2^(attempt - 1)
            long delayMillis = (long) (spec.initialDelayMs() * Math.pow(2, running.attempt() - 1));
            long nextRetryAt = System.currentTimeMillis() + delayMillis;

            // 跃迁至 WAITING_RETRY 状态
            registry.computeIfPresent(spec.id(), (id, r) -> r.toWaitingRetry(running.attempt() + 1, nextRetryAt, t));

            // 推入闹钟架，厨师当场释放
            alarmRack.offer(new DelayedTask(spec, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis)));
            log.info("任务 [{}] 第 {} 次执行失败，已进入 WAITING_RETRY 状态，将在 {}ms 后重新排队",
                    spec.id(), running.attempt(), delayMillis);
        } else {
            // 重试耗尽，彻底终态 FAILED
            registry.computeIfPresent(spec.id(), (id, r) -> r.toFailed(t));
            CompletableFuture<?> future = futures.get(spec.id());
            if (future != null) {
                future.completeExceptionally(t);
            }
            log.error("任务 [{}] 已达最大重试上限 {} 次，标记为彻底失败", spec.id(), spec.maxRetries());
        }
    }

    /**
     * 独立闹钟循环：负责在延迟到期后将单子从闹钟架取出，重新送回主队列。
     */
    private void alarmLoop() {
        while (!closing.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 到期方可弹出，无 CPU 轮询损耗
                DelayedTask delayed = alarmRack.poll(200, TimeUnit.MILLISECONDS);
                if (delayed != null) {
                    // 状态重新切换回 QUEUED
                    registry.computeIfPresent(delayed.spec().id(), (id, r) -> r.toQueued());

                    // 重新推入主排队区
                    if (!queue.offer(delayed.spec())) {
                        RejectedExecutionException ex = new RejectedExecutionException("重试回投排队区已满");
                        registry.computeIfPresent(delayed.spec().id(), (id, r) -> r.toFailed(ex));
                        CompletableFuture<?> future = futures.get(delayed.spec().id());
                        if (future != null) {
                            future.completeExceptionally(ex);
                        }
                        log.error("任务 [{}] 重试回投主排队区失败（队列已满）", delayed.spec().id());
                    } else {
                        log.info("任务 [{}] 重试延迟已到，已重新进入排队区 QUEUED", delayed.spec().id());
                    }
                }
            } catch (InterruptedException e) {
                log.error("闹钟循环被中断退出", e);
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 延迟队列元素内部记录类。
     *
     * @param spec             任务规格描述
     * @param triggerTimeNanos 触发时刻（纳秒绝对时间）
     */
    private record DelayedTask(TaskSpec<?> spec, long triggerTimeNanos) implements Delayed {

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(triggerTimeNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this == o) {
                return 0;
            }
            if (o instanceof DelayedTask other) {
                return Long.compare(this.triggerTimeNanos, other.triggerTimeNanos);
            }
            long diff = this.getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
            return (diff == 0) ? 0 : ((diff < 0) ? -1 : 1);
        }
    }

}
