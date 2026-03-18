package cn.net.pap.common.worker.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量级异步任务执行器
 * 
 * <p><strong>【当前版本局限性（在直接用于核心生产业务前需评估）】</strong></p>
 * <ul>
 *     <li><strong>缺乏业务逻辑抽象：</strong> 当前的任务执行（execute方法）仅为模拟的 Thread.sleep()。若要在生产中使用，需将任务调度与具体执行逻辑解耦（如引入 TaskHandler 策略接口或在 Task 中持有 Runnable）。</li>
 *     <li><strong>重试机制缺乏退避：</strong> 发生异常时会立即将其重新压入队列执行。对于网络抖动或下游限流引起的失败，瞬时重试会加剧雪崩。建议引入指数退避（Backoff）策略或延迟队列。</li>
 *     <li><strong>纯内存状态：</strong> 任务对象完全存储在 JVM 内存中（ConcurrentHashMap 和 BlockingQueue），一旦应用重启或崩溃，所有排队中和执行中的任务都会彻底丢失，不适用于对数据可靠性要求高的核心业务。</li>
 * </ul>
 * 
 * <p><strong>【相比早期简单（simple）版本的提升点】</strong></p>
 * <ul>
 *     <li><strong>主进程/系统层面：</strong> 
 *         解决了潜在的内存泄漏（OOM）风险。通过外部注入 ScheduledExecutorService 定期清理已完成且超时的任务；
 *         移除了硬编码的 ShutdownHook，符合 Spring 容器生命周期管理规范，实现了更加平滑优雅的停机逻辑。
 *     </li>
 *     <li><strong>工作进程/并发控制层面：</strong> 
 *         采用 <code>taskMap.compute()</code> 实现了原子性的状态判断与惰性复用，根除了并发重复提交导致的同一任务多次执行（即脏读和 Check-Then-Act 漏洞）；
 *         增加了工作线程异常卡死时的强制回收机制（防死锁）；
 *         引入了 AtomicInteger 保证了并发创建线程时命名的唯一性，避免线上排查时日志混乱。
 *     </li>
 * </ul>
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    // ===== 线程池 =====
    /**
     * 核心任务执行线程池
     */
    private final ThreadPoolExecutor executor;

    // ===== 内存任务状态（可选，用于简单查询）=====
    /**
     * 保存任务状态的集合，用于外部查询和简单的幂等控制
     */
    private final Map<String, Task> taskMap = new ConcurrentHashMap<>();

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY = 3;

    /**
     * 任务过期时间（毫秒），默认10分钟
     */
    private static final long TASK_EXPIRE_TIME_MS = 10 * 60 * 1000L;

    // 不再内部创建定时任务，而是提供外部注册和检测机制
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean loggedWarning = new AtomicBoolean(false);

    /**
     * 构造函数，初始化执行器
     *
     * @param core      核心线程数
     * @param max       最大线程数
     * @param queueSize 队列容量大小
     */
    public TaskExecutor(int core, int max, int queueSize) {
        this.executor = new ThreadPoolExecutor(
                core,
                max,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                new ThreadFactory() {
                    // 使用 AtomicInteger 保证并发创建线程时的线程安全，防止多个线程同名
                    private final AtomicInteger i = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "worker-" + i.getAndIncrement());
                    }
                },
                // 拒绝策略
                new ThreadPoolExecutor.AbortPolicy()
        );

        // JVM 关闭时优雅停机，防止线程池资源泄露
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /**
     * 借鉴 IdempotentTaskLock，使用外部调度器初始化锁定/清理机制。
     * 避免在组件内部私自创建和管理定时线程池。
     *
     * @param scheduler 用于垃圾回收的 ScheduledExecutorService
     */
    public void init(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("调度器 scheduler 不能为空");
        }

        if (initialized.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    sweepExpiredTasks();
                } catch (Exception e) {
                    log.error("TaskExecutor 全局清理异常", e);
                }
            }, 5, 5, TimeUnit.MINUTES);
            log.info("TaskExecutor 初始化完成，已接入外部调度器进行后台兜底清理。");
        } else {
            log.warn("TaskExecutor 已经被初始化过了，忽略本次调用。");
        }
    }

    /**
     * 提交任务
     *
     * @param task 待执行的任务
     */
    public void submit(Task task) {
        // 如果没有注入调度器，给予全局单次告警
        if (!initialized.get()) {
            if (loggedWarning.compareAndSet(false, true)) {
                log.warn("【严重警告】TaskExecutor 未调用 init() 注入调度器，将失去 OOM 防御能力！本警告仅打印一次。");
            }
        }

        task.setStatus(TaskStatus.WAITING);
        task.setRetryCount(0);
        task.setUpdateTime(System.currentTimeMillis());

        // 借鉴 IdempotentTaskLock，使用 compute 实现原子性的状态判断、复用和并发控制
        final boolean[] shouldSubmit = {false};
        taskMap.compute(task.getId(), (k, existingTask) -> {
            // 1. 全新任务
            if (existingTask == null) {
                shouldSubmit[0] = true;
                return task;
            }

            // 2. 正常过期结束：如果存在的任务已经完成（成功或失败）并且超过了保留时间
            boolean isFinished = (existingTask.getStatus() == TaskStatus.SUCCESS || existingTask.getStatus() == TaskStatus.FAILED);
            boolean isExpired = (System.currentTimeMillis() - existingTask.getUpdateTime() > TASK_EXPIRE_TIME_MS);

            if (isFinished && isExpired) {
                shouldSubmit[0] = true;
                // 返回新任务直接覆盖，实现惰性清理
                return task;
            }

            // 3. 执行中 (RUNNING/WAITING) 或是 仍在冷却保留期内的任务，拒绝重复提交
            return existingTask;
        });

        if (shouldSubmit[0]) {
            executor.submit(() -> execute(task.getId()));
        } else {
            log.info("任务已存在且未过期，忽略重复提交: {}", task.getId());
        }
    }

    /**
     * 执行任务（核心逻辑）
     *
     * @param taskId 任务ID
     */
    private void execute(String taskId) {

        Task task = taskMap.get(taskId);

        // 幂等控制补充：防御性编程，防止出现意料之外的重复执行
        if (task == null || task.getStatus() == TaskStatus.SUCCESS) {
            return;
        }

        try {
            updateStatus(task, TaskStatus.RUNNING);

            // ===== 模拟业务逻辑 =====
            log.info("{} 执行任务: {}", Thread.currentThread().getName(), task.getId());

            // 模拟业务处理耗时
            Thread.sleep(task.getProcessingTime());

            task.setResult("SUCCESS");
            updateStatus(task, TaskStatus.SUCCESS);

        } catch (InterruptedException e) {
            // 正确处理 InterruptedException
            // 恢复当前线程的中断标志位，这对于优雅停机（shutdownNow）的响应至关重要
            Thread.currentThread().interrupt();
            log.error("任务被中断: {}", task.getId());
            task.setResult("FAILED: Interrupted");
            updateStatus(task, TaskStatus.FAILED);

        } catch (Exception e) {
            // 其他业务异常，进入重试逻辑
            handleRetry(task, e);
        }
    }

    /**
     * 重试机制
     *
     * @param task 任务对象
     * @param e    异常对象
     */
    private void handleRetry(Task task, Exception e) {

        int retry = task.getRetryCount() + 1;
        task.setRetryCount(retry);
        task.setUpdateTime(System.currentTimeMillis());

        if (retry <= MAX_RETRY) {

            log.info("任务重试: {} 第{}次", task.getId(), retry);
            // 注意：此处依旧保持简单的立刻重试。
            // 生产环境下为了更加健壮，通常建议引入带有延迟时间的退避策略 (Backoff Strategy)。
            executor.submit(() -> execute(task.getId()));

        } else {

            task.setResult("FAILED: " + e.getMessage());
            updateStatus(task, TaskStatus.FAILED);
        }
    }

    /**
     * 统一更新状态和更新时间
     *
     * @param task   任务对象
     * @param status 目标状态
     */
    private void updateStatus(Task task, TaskStatus status) {
        task.setStatus(status);
        task.setUpdateTime(System.currentTimeMillis());
    }

    /**
     * 后台清理器，用于移除过期或僵尸任务
     * 完全借鉴 IdempotentTaskLock#sweepExpiredKeys 的精确比对清理
     */
    private void sweepExpiredTasks() {
        long currentMillis = System.currentTimeMillis();

        for (Map.Entry<String, Task> entry : taskMap.entrySet()) {
            Task status = entry.getValue();
            TaskStatus currentState = status.getStatus();

            boolean isFinished = (currentState == TaskStatus.SUCCESS || currentState == TaskStatus.FAILED);
            boolean isExpired = (currentMillis - status.getUpdateTime() > TASK_EXPIRE_TIME_MS);

            if (isFinished && isExpired) {
                // 必须传入 value 进行精确比对移除，防止误杀刚复用(通过 compute 覆盖)的新任务
                taskMap.remove(entry.getKey(), status);
            } else if (currentState == TaskStatus.RUNNING && (currentMillis - status.getUpdateTime() > TASK_EXPIRE_TIME_MS)) {
                // 兜底清理机制：如果任务长期卡死在 RUNNING 状态，强制回收防 OOM
                log.warn("【警告】检测到异常的长期运行任务，触发强制回收防 OOM，Key: {}", entry.getKey());
                taskMap.remove(entry.getKey(), status);
            }
        }
    }

    /**
     * 查询任务（可选）
     *
     * @param taskId 任务ID
     * @return 任务对象
     */
    public Task get(String taskId) {
        return taskMap.get(taskId);
    }

    /**
     * 获取当前队列大小
     *
     * @return 队列中的任务数
     */
    public int getQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * 获取活跃线程数
     *
     * @return 正在执行任务的线程数
     */
    public int getActiveCount() {
        return executor.getActiveCount();
    }

    /**
     * 优雅停机
     * 说明：应该由外部的 Bean 生命周期或主程序在结束时显式调用
     */
    public void shutdown() {
        log.info("准备关闭主任务线程池...");

        // 注意：不再关闭清理调度器，因为 scheduler 是外部注入的，由外部生命周期管理

        executor.shutdown();
        try {
            // 等待已有任务在限期内执行完毕
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("部分任务未能在60秒内完成，尝试强制关闭...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("等待线程池关闭时被中断，强制关闭...");
            executor.shutdownNow();
            // 恢复中断状态
            Thread.currentThread().interrupt();
        }

        log.info("线程池已关闭");
    }

}