package cn.net.pap.quartz.service.impl;

import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.repository.TaskDataRepository;
import cn.net.pap.quartz.service.ITaskDataService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 动态槽位流式补齐并行任务处理服务实现类，直接实现 ITaskDataService 接口。
 *
 * <p>核心设计：</p>
 * <ol>
 *     <li>复用既有 BATCH_SIZE 作为系统最大并发槽位上限，不新增调度参数；</li>
 *     <li>彻底废除 CountDownLatch / future.get() 的批次同步屏障；</li>
 *     <li>processBatchSafely() 只做“算空闲槽位 -> 查待处理数据 -> 原子抢占并异步派发”；</li>
 *     <li>单任务结束立即归还槽位，下一次定时触发即可继续补位，长任务只占用自己的槽位。</li>
 * </ol>
 *
 * <p>与串行/旧并行实现的关系：</p>
 * <p>本类仍然通过注入的 {@link ITaskDataService} 复用原有业务处理、成功/失败标记、指数退避重试等逻辑，
 * 仅重写并发调度与槽位管理部分，对外接口完全兼容。</p>
 */
@Service("taskDataParallelStreamingServiceImpl")
public class TaskDataParallelStreamingServiceImpl implements ITaskDataService {

    private static final Logger log = LoggerFactory.getLogger(TaskDataParallelStreamingServiceImpl.class);

    private final ITaskDataService taskDataService;
    private final TaskDataRepository taskDataRepository;
    private final TransactionTemplate transactionTemplate;

    // 线程池完全私有化，不接受容器注入覆盖，防止排查困难与竞态冲突
    private ThreadPoolTaskExecutor taskDataExecutor;

    // 复用既有批处理大小，作为系统最大并发槽位上限
    private static final int BATCH_SIZE = 10;

    // 允许的最大重试尝试次数
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // 崩溃/僵死任务判定超时时间
    private static final Duration PROCESSING_TIMEOUT = Duration.ofHours(24);

    // 优雅关闭时等待运行中任务结束的最长时间
    private static final int SHUTDOWN_AWAIT_SECONDS = 30;

    // 单任务耗时超过该阈值时打印长耗时告警
    private static final long LONG_TASK_WARN_MILLIS = 60_000L;

    // 当前运行中的任务数，即已占用槽位数
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);

    // 补位派发锁：保证“算空位 -> 查库 -> 抢占 -> 提交并计数”这一小段是原子的
    private final ReentrantLock dispatchLock = new ReentrantLock();

    /**
     * SQLite 单写互斥锁（公平锁）。
     * SQLite 属于文件级单写数据库，同一时刻仅允许单个写事务。
     * OCR 等重度计算完全并行，数据库写操作在 JVM 内存层排队（通常仅 1~3ms），
     * 从而避免多连接同时申请写锁触发 [SQLITE_BUSY] The database file is locked。
     */
    private final ReentrantLock dbWriteLock = new ReentrantLock(true);

    // 标记系统是否正在执行优雅关闭销毁
    private volatile boolean isShuttingDown = false;

    /**
     * 创建任务数据流式并行处理服务。
     *
     * @param taskDataService    任务数据服务
     * @param taskDataRepository 任务数据仓储
     * @param transactionManager 事务管理器
     */
    @Autowired
    public TaskDataParallelStreamingServiceImpl(ITaskDataService taskDataService,
                                                TaskDataRepository taskDataRepository,
                                                PlatformTransactionManager transactionManager) {
        this.taskDataService = taskDataService;
        this.taskDataRepository = taskDataRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 初始化全局常驻工作线程池。
     * 线程数严格等于最大并发槽位数，队列容量为 0（SynchronousQueue），
     * 从源头上保证不会出现“槽位已满但任务仍在队列中堆积”的情况。
     */
    @PostConstruct
    public void init() {
        int poolSize = BATCH_SIZE > 0 ? BATCH_SIZE : 10;

        log.warn("[Task-Data-Parallel-Streaming] 正在自动初始化私有并发线程池...");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-data-parallel-streaming-");
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(0);
        executor.setAllowCoreThreadTimeOut(false);
        executor.setKeepAliveSeconds(60);
        // 关键：ThreadPoolTaskExecutor 默认 waitForTasksToCompleteOnShutdown=false，
        // 直接调用 shutdown() 会退化为 shutdownNow()，立即中断运行中任务。
        // 这里显式开启优雅关闭，配合 destroy() 中的 awaitTermination 实现真正的等待。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 不使用 CallerRunsPolicy，避免线程池饱和时把任务回退到调度线程同步执行，破坏非阻塞补位。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        this.taskDataExecutor = executor;

        log.info("[Task-Data-Parallel-Streaming] 并行任务处理服务初始化完成。最大并发槽位: {}，僵死超时: {} 分钟",
                poolSize, PROCESSING_TIMEOUT.toMinutes());
    }

    /**
     * 高并发安全的并发批量处理方法 - 核心入口。
     *
     * <p>本方法不再阻塞等待整批任务结束，只负责把当前空闲槽位补满：</p>
     * <ol>
     *     <li>计算空闲槽位，槽位已满直接退出；</li>
     *     <li>按空闲槽位数从数据库捞取待处理数据；</li>
     *     <li>原子抢占后异步派发，毫秒级返回。</li>
     * </ol>
     *
     * <p>需要外部定时器反复触发本方法，才能持续消费数据库中的待处理任务。</p>
     */
    @Override
    public void processBatchSafely() {
        if (isShuttingDown) {
            log.debug("[Task-Data-Parallel-Streaming] 服务正在关闭，跳过本轮任务捞取");
            return;
        }

        // 同一实例同一时刻只允许一个补位线程，其他并发调用立即返回，避免重复捞取与槽位超卖。
        if (!dispatchLock.tryLock()) {
            return;
        }

        try {
            if (isShuttingDown) {
                return;
            }

            // 1. 算空闲槽位
            int freeSlots = BATCH_SIZE - runningTaskCount.get();
            if (freeSlots <= 0) {
                return;
            }

            // 2. 按需向数据库查询待处理数据，最多拉取当前空闲的 freeSlots 条
            List<TaskData> pendingData = taskDataRepository.findPendingData(
                    PageRequest.of(0, freeSlots));

            if (pendingData.isEmpty()) {
                return;
            }

            // 3. 原子抢占并异步派发，提交完成后立即返回
            acquireAndProcessBatch(pendingData);

        } catch (Exception e) {
            log.error("[Task-Data-Parallel-Streaming] 本轮任务补位失败", e);
        } finally {
            dispatchLock.unlock();
        }
    }

    /**
     * 原子性地抢占并处理一批数据。
     */
    private int acquireAndProcessBatch(List<TaskData> pendingData) {
        List<Long> pendingIds = pendingData.stream()
                .map(TaskData::getId)
                .collect(Collectors.toList());

        // 生成唯一的处理令牌
        String processToken = UUID.randomUUID().toString();

        // 核心：在独立事务中原子性地抢占数据所有权并查询
        List<TaskData> acquiredData;
        dbWriteLock.lock();
        try {
            acquiredData = transactionTemplate.execute(status -> {
                int acquiredCount = taskDataRepository.acquireBatchForProcessing(
                        pendingIds, processToken, MAX_RETRY_ATTEMPTS);

                if (acquiredCount == 0) {
                    return Collections.emptyList();
                }

                // 查询刚刚被抢占的数据
                return taskDataRepository.findByProcessToken(processToken);
            });
        } finally {
            dbWriteLock.unlock();
        }

        if (acquiredData == null || acquiredData.isEmpty()) {
            return 0;
        }

        // 非阻塞异步派发
        processAcquiredDataInParallel(acquiredData, processToken);

        return acquiredData.size();
    }

    /**
     * 非阻塞异步派发已抢占的数据。
     * 每提交一个任务前先占用一个槽位，任务结束（无论成功、失败还是异常）后立即归还。
     */
    private void processAcquiredDataInParallel(List<TaskData> acquiredData, String processToken) {
        if (acquiredData == null || acquiredData.isEmpty()) {
            return;
        }

        for (TaskData data : acquiredData) {
            if (isShuttingDown) {
                resetInterruptedDataInNewTransaction(data.getId(), processToken);
                continue;
            }

            // 1. 占用槽位 (+1)
            runningTaskCount.incrementAndGet();

            try {
                // 2. 提交到常驻线程池，立即返回
                taskDataExecutor.execute(new TaskDataRunner(data, processToken));
            } catch (Throwable e) {
                // 提交失败，立即归还槽位并回滚状态，防止任务卡在 PROCESSING
                runningTaskCount.decrementAndGet();
                log.error("[Task-Data-Parallel-Streaming] 提交并发任务失败，id: {}", data.getId(), e);
                resetInterruptedDataInNewTransaction(data.getId(), processToken);
            }
        }
    }

    /**
     * 安全处理单条数据。
     *
     * <p>业务执行期无大事务，仅在状态更新时使用独立短事务；
     * 所有数据库写操作均由 dbWriteLock 保护，避免 SQLite 多连接写锁竞争。</p>
     */
    private void processSingleDataSafely(TaskData data, String processToken) {
        try {
            // 1. 复用 ITaskDataService 原有业务逻辑，通过本类虚方法便于测试替换。
            processBusinessLogic(data);

            // 如果业务执行期间线程被中断，不要误标成功，直接走回滚。
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("任务执行线程被中断");
            }

            // 2. 复用 ITaskDataService 标记成功：独立小事务，执行即释放。
            boolean success = markTaskSuccessInNewTransaction(data.getId(), processToken);
            if (!success) {
                log.warn("[Task-Data-Parallel-Streaming] 标记任务成功失败，数据可能已被其他线程并发处理，id: {}",
                        data.getId());
            }

        } catch (Exception e) {
            // 3. 标记失败或执行销毁期快速重置
            // 如果线程已被中断，或是因为应用正在关闭导致处理失败，不应当计入“真正的错误尝试次数”，
            // 而是应该秒级重置回可重试状态。
            if (isShuttingDown
                || Thread.currentThread().isInterrupted()
                || e instanceof InterruptedException
                || e.getCause() instanceof InterruptedException) {

                log.error("[Task-Data-Parallel-Streaming] 检测到任务在并发运行中由于应用关闭而被中断，正在重置状态，id: {}",
                        data.getId(), e);
                resetInterruptedDataInNewTransaction(data.getId(), processToken);
                // 清理中断标记，避免线程池复用该线程时污染后续任务。
                Thread.interrupted();

            } else {
                markTaskFailureInNewTransaction(data, processToken, e);
            }
        }
    }

    /**
     * 独立短事务，在被中断时将数据重置回待处理，减少 attempts 重试次数。
     *
     * @return 是否真正重置了记录
     */
    private boolean resetInterruptedDataInNewTransaction(Long id, String processToken) {
        dbWriteLock.lock();
        try {
            Boolean result = transactionTemplate.execute(status ->
                    taskDataRepository.resetInterruptedData(id, processToken) > 0
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("[Task-Data-Parallel-Streaming] 重置中断任务状态失败，id: {}", id, e);
            return false;
        } finally {
            dbWriteLock.unlock();
        }
    }

    /**
     * 恢复卡在 PROCESSING 状态的数据（如应用突然宕机）。
     */
    @Override
    public void recoverStuckData() {
        dbWriteLock.lock();
        try {
            taskDataService.recoverStuckData();
        } finally {
            dbWriteLock.unlock();
        }
    }

    /**
     * 可控的优雅关闭监听销毁方法。
     * 关闭线程池、等待运行中任务结束，并将未开始任务原子回滚，避免系统重启后任务永久卡死。
     */
    @PreDestroy
    public void destroy() {
        if (isShuttingDown) {
            return;
        }
        this.isShuttingDown = true;

        if (taskDataExecutor == null) {
            return;
        }

        log.info("[Task-Data-Parallel-Streaming] TaskDataParallelStreamingServiceImpl 正在关闭，开始执行可控清理流程...");

        Set<Runnable> unstartedTasks = new LinkedHashSet<>();

        try {
            // 1. 优雅关闭线程池，允许正在执行的活跃线程尽快结束
            taskDataExecutor.shutdown();

            try {
                if (!taskDataExecutor.getThreadPoolExecutor()
                        .awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                    log.warn("[Task-Data-Parallel-Streaming] 部分运行中线程未在 {} 秒内结束，正在强制中断...",
                            SHUTDOWN_AWAIT_SECONDS);
                    unstartedTasks.addAll(taskDataExecutor.getThreadPoolExecutor().shutdownNow());
                }
            } catch (InterruptedException e) {
                unstartedTasks.addAll(taskDataExecutor.getThreadPoolExecutor().shutdownNow());
                Thread.currentThread().interrupt();
                log.error("[Task-Data-Parallel-Streaming] 关闭并发线程池时被中断: ", e);
            }

            // 2. 兜底排空队列中未开始执行的任务
            BlockingQueue<Runnable> queue = taskDataExecutor.getThreadPoolExecutor().getQueue();
            if (!queue.isEmpty()) {
                queue.drainTo(unstartedTasks);
            }

        } catch (Exception e) {
            log.error("[Task-Data-Parallel-Streaming] 关闭线程池时发生异常", e);
        }

        // 3. 将未开始任务回滚为可重试状态，并归还其占用的槽位
        int resetCount = 0;
        for (Runnable task : unstartedTasks) {
            if (task instanceof TaskDataRunner runner) {
                runner.resetTask();
                runner.releaseSlot();
                resetCount++;
            }
        }

        log.info("[Task-Data-Parallel-Streaming] 可控清理及优雅关闭完成，回滚未开始任务数: {}", resetCount);
    }

    @Override
    public void callNptException(Long inputLong) {
        taskDataService.callNptException(inputLong);
    }

    @Override
    public void processBusinessLogic(TaskData data) {
        taskDataService.processBusinessLogic(data);
    }

    @Override
    public boolean markTaskSuccessInNewTransaction(Long id, String processToken) {
        dbWriteLock.lock();
        try {
            return taskDataService.markTaskSuccessInNewTransaction(id, processToken);
        } finally {
            dbWriteLock.unlock();
        }
    }

    @Override
    public void markTaskFailureInNewTransaction(TaskData data, String processToken, Exception e) {
        dbWriteLock.lock();
        try {
            taskDataService.markTaskFailureInNewTransaction(data, processToken, e);
        } finally {
            dbWriteLock.unlock();
        }
    }

    @Override
    public void retryFailedData(List<Long> dataIds) {
        dbWriteLock.lock();
        try {
            taskDataService.retryFailedData(dataIds);
        } finally {
            dbWriteLock.unlock();
        }
    }

    @Override
    public Map<String, Long> getProcessingStats() {
        return taskDataService.getProcessingStats();
    }

    @Override
    public void saveAll(List<TaskData> taskDataList) {
        dbWriteLock.lock();
        try {
            taskDataService.saveAll(taskDataList);
        } finally {
            dbWriteLock.unlock();
        }
    }

    @Override
    public void deleteAll() {
        dbWriteLock.lock();
        try {
            taskDataService.deleteAll();
        } finally {
            dbWriteLock.unlock();
        }
    }

    /**
     * 包装了 TaskData 及 Token 属性 of Runnable 执行器，支持队列排空重置与槽位幂等释放。
     */
    private class TaskDataRunner implements Runnable {

        private final TaskData taskData;
        private final String token;
        private final AtomicBoolean slotReleased = new AtomicBoolean(false);

        public TaskDataRunner(TaskData taskData, String token) {
            this.taskData = taskData;
            this.token = token;
        }

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();

            try {
                // 如果已经在销毁中，则直接重置状态，跳过具体的业务逻辑
                if (isShuttingDown || Thread.currentThread().isInterrupted()) {
                    resetTask();
                    Thread.interrupted();
                    return;
                }

                processSingleDataSafely(taskData, token);

            } catch (Throwable t) {
                log.error("[Task-Data-Parallel-Streaming] 任务执行异常, id: {}", taskData.getId(), t);

                // 非 Exception 的 Error 也尽量回滚，避免任务永久停留在 PROCESSING
                if (!(t instanceof Exception)) {
                    resetInterruptedDataInNewTransaction(taskData.getId(), token);
                }
            } finally {
                releaseSlot();

                long cost = System.currentTimeMillis() - startTime;
                int running = runningTaskCount.get();

                if (cost > LONG_TASK_WARN_MILLIS) {
                    log.warn("[Task-Data-Parallel-Streaming] 长耗时任务告警: id={}, 耗时={}s, 当前运行中任务数={}",
                            taskData.getId(), cost / 1000, running);
                } else {
                    log.info("[Task-Data-Parallel-Streaming] 任务完成: id={}, 耗时={}ms, 当前运行中任务数={}",
                            taskData.getId(), cost, running);
                }
            }
        }

        /**
         * 幂等归还槽位，防止队列排空与任务执行同时发生时重复递减。
         */
        public void releaseSlot() {
            if (slotReleased.compareAndSet(false, true)) {
                runningTaskCount.decrementAndGet();
            }
        }

        /**
         * 将未开始/被中断的任务回滚为可重试状态。
         */
        public void resetTask() {
            boolean reset = resetInterruptedDataInNewTransaction(taskData.getId(), token);
            if (reset) {
                log.info("[Task-Data-Parallel-Streaming] 成功将未开始任务从 PROCESSING 恢复回待处理状态，id: {}",
                        taskData.getId());
            } else {
                log.warn("[Task-Data-Parallel-Streaming] 未回滚任务，任务可能已执行完成或已被处理，id: {}",
                        taskData.getId());
            }
        }
    }
}
