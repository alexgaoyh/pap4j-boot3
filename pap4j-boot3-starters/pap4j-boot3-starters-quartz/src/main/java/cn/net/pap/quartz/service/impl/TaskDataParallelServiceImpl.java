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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 增量式多线程并行任务与重试处理服务实现类，直接实现 ITaskDataService 接口
 */
@Service("taskDataParallelServiceImpl")
public class TaskDataParallelServiceImpl implements ITaskDataService {

    private static final Logger log = LoggerFactory.getLogger(TaskDataParallelServiceImpl.class);

    private final ITaskDataService taskDataService;
    private final TaskDataRepository taskDataRepository;
    private final TransactionTemplate transactionTemplate;
    
    // 线程池完全私有化，不接受容器注入覆盖，防止排查困难与竞态冲突
    private ThreadPoolTaskExecutor taskDataExecutor;

    private static final int BATCH_SIZE = 10;

    // 允许的最大重试尝试次数
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // 崩溃/僵死任务判定超时时间
    private static final Duration PROCESSING_TIMEOUT = Duration.ofHours(24);

    // 标记系统是否正在执行优雅关闭销毁
    private volatile boolean isShuttingDown = false;
    // =========================================================================

    @Autowired
    public TaskDataParallelServiceImpl(ITaskDataService taskDataService,
                                       TaskDataRepository taskDataRepository,
                                       PlatformTransactionManager transactionManager) {
        this.taskDataService = taskDataService;
        this.taskDataRepository = taskDataRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 线程池完全私有化自管理初始化。
     * 【默认线程池参数设计说明】：
     * 1. corePoolSize=10, maxPoolSize=20: 支持最大 20 并发处理长耗时的外部 I/O 阻塞任务。
     * 2. queueCapacity=50: 控制积压等待队列大小，防止大量耗时任务堆积占用过多内存。
     * 3. allowCoreThreadTimeOut=true, keepAliveSeconds=60: 允许核心和非核心空闲线程在 60 秒后自动销毁以节约系统资源。
     * 4. RejectedExecutionHandler: 采用 CallerRunsPolicy，饱和时通过调用者线程同步执行达到背压限流效果。
     */
    @PostConstruct
    public void init() {
        log.warn("[Task-Data-Parallel] 正在自动初始化私有并发线程池...");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-data-parallel-");
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        this.taskDataExecutor = executor;
        
        log.info("[Task-Data-Parallel] 并行任务处理服务初始化完成。批处理大小: {}，僵死超时: {} 分钟", BATCH_SIZE, PROCESSING_TIMEOUT.toMinutes());
    }

    /**
     * 高并发安全的并发批量处理方法 - 核心入口
     * 
     * ==========================================
     * 💡 【关于多租户/多用户环境下“公平分配调度”的设计指导说明】：
     * 1. 潜在风险：在当前的 FIFO 设计中，如果租户 A 导入海量数据，会饿死租户 B/C 的紧急任务。
     * 2. 改造方案：如果业务要求高优先级公平分配，可以通过以下方式进行二次定制：
     *    - 字段扩展：在 TaskData 实体类中扩展 creator 或 tenantId 字段。
     *    - 捞数 SQL 限制：修改 `findPendingData` 的 SQL 采用分区窗口函数（如 ROW_NUMBER() OVER(PARTITION BY tenant_id ORDER BY last_process_time) <= N）对单租户捞取上限进行限制。
     *    - 内存过滤：对捞取候选任务列表（如 limit = BATCH_SIZE * 3）后，通过内存分组并执行 Round-Robin（轮询）算法挑选出 BATCH_SIZE 条用于锁定和执行。
     * ==========================================
     */
    @Override
    public void processBatchSafely() {
        // 1. 主动过载检测：若系统销毁中，或任务执行线程池正处于满载排队积压状态，则直接跳过本轮捞数，防止连接池死锁和重复抢占
        if (isShuttingDown) {
            log.warn("[Task-Data-Parallel] 服务正在关闭，跳过本轮任务捞取");
            return;
        }
        
        if (isExecutorBusy()) {
            log.warn("[Task-Data-Parallel] 任务执行线程池繁忙，跳过本轮数据库查询，保护数据库连接。当前队列等待数: {}", getQueueSize());
            return;
        }

        int processedCount;
        do {
            // 2. 捞取待处理数据 (不加锁)
            List<TaskData> pendingData = taskDataRepository.findPendingData(
                    PageRequest.of(0, BATCH_SIZE));

            if (pendingData.isEmpty()) {
                break;
            }

            // 3. 原子性抢占锁并分发执行
            processedCount = acquireAndProcessBatch(pendingData);

            // 如果已经处于关闭状态，立即中止后续大批次的循环捞取
            if (isShuttingDown) {
                break;
            }

        } while (processedCount == BATCH_SIZE);
    }

    /**
     * 检测线程池是否满载/繁忙
     */
    private boolean isExecutorBusy() {
        return taskDataExecutor.getThreadPoolExecutor().getQueue().remainingCapacity() == 0;
    }

    /**
     * 获取队列积压的任务数
     */
    private int getQueueSize() {
        return taskDataExecutor.getThreadPoolExecutor().getQueue().size();
    }

    /**
     * 原子性地抢占并处理一批数据
     */
    private int acquireAndProcessBatch(List<TaskData> pendingData) {
        List<Long> pendingIds = pendingData.stream()
                .map(TaskData::getId)
                .collect(Collectors.toList());

        // 生成唯一的处理令牌
        String processToken = UUID.randomUUID().toString();

        // 核心：在独立事务中原子性地抢占数据所有权并查询
        List<TaskData> acquiredData = transactionTemplate.execute(status -> {
            int acquiredCount = taskDataRepository.acquireBatchForProcessing(
                    pendingIds, processToken, MAX_RETRY_ATTEMPTS);

            if (acquiredCount == 0) {
                return java.util.Collections.emptyList();
            }

            // 查询刚刚被抢占的数据
            return taskDataRepository.findByProcessToken(processToken);
        });

        if (acquiredData == null || acquiredData.isEmpty()) {
            return 0; 
        }

        // 处理抢到的数据
        processAcquiredData(acquiredData, processToken);

        return acquiredData.size();
    }

    /**
     * 并发处理已抢占的数据，配合 CountDownLatch 实现批次流速管理。
     */
    private void processAcquiredData(List<TaskData> acquiredData, String processToken) {
        CountDownLatch latch = new CountDownLatch(acquiredData.size());
        
        acquiredData.forEach(data -> {
            try {
                // 提交原始的 TaskDataRunner 任务至线程池执行。
                taskDataExecutor.execute(new TaskDataRunner(data, processToken, latch));
            } catch (Throwable e) {
                latch.countDown();
                log.error("[Task-Data-Parallel] 提交并发任务失败，id: {}", data.getId(), e);
                // 提交失败立刻在独立事务中重置状态，防止任务变成 PROCESSING 卡死
                resetInterruptedDataInNewTransaction(data.getId(), processToken);
            }
        });

        try {
            // 同步等待本批次所有任务在线程池中处理完毕（或触发 CallerRunsPolicy 同步运行完），实现流控闭环。
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[Task-Data-Parallel] 等待并发批次执行完成被中断", e);
        }
    }

    /**
     * 安全处理单条数据 (核心控制流：业务执行期无大事务，防连接池枯竭)
     * [ADJUSTED] 相比原 TaskDataServiceImpl，直接复用其暴露的 public 业务和状态更新方法
     */
    private void processSingleDataSafely(TaskData data, String processToken) {
        try {
            // 1. 复用 ITaskDataService 原有业务逻辑。由于未开启事务，在此处调用耗时的外部 RPC/HTTP 接口不会长期霸占数据库连接，避免连接池枯竭。
            taskDataService.processBusinessLogic(data);

            // 2. 复用 ITaskDataService 标记成功：独立小事务，执行即释放。
            boolean success = taskDataService.markTaskSuccessInNewTransaction(data.getId(), processToken);
            if (!success) {
                log.warn("[Task-Data-Parallel] 标记任务成功失败，数据可能已被其他线程并发处理，id: {}", data.getId());
            }

        } catch (Exception e) {
            // 3. 标记失败或执行销毁期快速重置
            // 如果线程已被中断，或是因为应用正在关闭导致处理失败，我们不应当计入“真正的错误尝试次数”，而是应该秒级重置
            if (isShuttingDown || Thread.currentThread().isInterrupted() || e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                log.error("[Task-Data-Parallel] 检测到任务在并发运行中由于应用关闭而被中断，正在重置状态，id: {}", data.getId(), e);
                resetInterruptedDataInNewTransaction(data.getId(), processToken);
            } else {
                taskDataService.markTaskFailureInNewTransaction(data, processToken, e);
            }
        }
    }

    /**
     * 独立短事务，在被中断时将数据重置回待处理，减少 attempts 重试次数
     * [ADJUSTED] 此方法是并发重构独有的状态回滚逻辑
     */
    private void resetInterruptedDataInNewTransaction(Long id, String processToken) {
        try {
            transactionTemplate.execute(status -> {
                taskDataRepository.resetInterruptedData(id, processToken);
                return null;
            });
        } catch (Exception e) {
            log.error("[Task-Data-Parallel] 重置中断任务状态失败，id: {}", id, e);
        }
    }

    /**
     * 恢复卡在 PROCESSING 状态的数据（如应用突然宕机）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverStuckData() {
        LocalDateTime timeout = LocalDateTime.now().minus(PROCESSING_TIMEOUT);
        int recovered = taskDataRepository.resetStuckProcessingData(timeout, MAX_RETRY_ATTEMPTS);
        if (recovered > 0) {
            log.info("[Task-Data-Parallel] 恢复了 {} 条超时卡住的任务", recovered);
        }
    }

    /**
     * 可控的优雅关闭监听销毁方法。
     * [ADJUSTED] 并发特有的排空队列与重置中断任务逻辑
     */
    @PreDestroy
    public void destroy() {
        log.info("[Task-Data-Parallel] TaskDataParallelServiceImpl 正在关闭，开始执行可控清理流程...");
        
        // 1. 设置销毁标志，立刻阻断新一轮的定时捞数
        this.isShuttingDown = true;

        // 2. 优雅地关闭线程池，允许正在执行的活跃线程在网络 I/O 中尽快结束 (最长等待 5 秒)
        if (taskDataExecutor != null) {
            taskDataExecutor.shutdown();
            try {
                if (!taskDataExecutor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("[Task-Data-Parallel] 部分运行中线程未在 5 秒内结束，正在强制中断...");
                    taskDataExecutor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                taskDataExecutor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
                log.error("[Task-Data-Parallel] 关闭并发线程池时被中断: ", e);
            }
        }

        // 3. 主动排空（Drain）积压在线程池队列中未开始执行的任务，提取其持有的 TaskData 信息并秒级回滚数据库状态
        if (taskDataExecutor != null) {
            List<Runnable> drainedTasks = new ArrayList<>();
            taskDataExecutor.getThreadPoolExecutor().getQueue().drainTo(drainedTasks);
            
            if (!drainedTasks.isEmpty()) {
                log.info("[Task-Data-Parallel] 成功从等待队列中排空了 {} 个待处理任务，开始批量回滚状态...", drainedTasks.size());
                for (Runnable task : drainedTasks) {
                    if (task instanceof TaskDataRunner) {
                        ((TaskDataRunner) task).resetTask();
                    }
                }
            }
        }
        log.info("[Task-Data-Parallel] TaskDataParallelServiceImpl 可控清理及优雅关闭完成");
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
        return taskDataService.markTaskSuccessInNewTransaction(id, processToken);
    }

    @Override
    public void markTaskFailureInNewTransaction(TaskData data, String processToken, Exception e) {
        taskDataService.markTaskFailureInNewTransaction(data, processToken, e);
    }

    @Override
    public void retryFailedData(List<Long> dataIds) {
        taskDataService.retryFailedData(dataIds);
    }

    @Override
    public Map<String, Long> getProcessingStats() {
        return taskDataService.getProcessingStats();
    }

    @Override
    public void saveAll(List<TaskData> taskDataList) {
        taskDataService.saveAll(taskDataList);
    }

    @Override
    public void deleteAll() {
        taskDataService.deleteAll();
    }

    /**
     * 包装了 TaskData 及 Token 属性 of Runnable 执行器，支持队列排空重置。
     */
    private class TaskDataRunner implements Runnable {
        private final TaskData taskData;
        private final String token;
        private final CountDownLatch latch;

        public TaskDataRunner(TaskData taskData, String token, CountDownLatch latch) {
            this.taskData = taskData;
            this.token = token;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                // 如果已经在销毁中，则直接重置状态，跳过具体的业务逻辑
                if (isShuttingDown || Thread.currentThread().isInterrupted()) {
                    resetTask();
                    return;
                }
                processSingleDataSafely(taskData, token);
            } finally {
                latch.countDown();
            }
        }

        public void resetTask() {
            try {
                transactionTemplate.execute(status -> {
                    taskDataRepository.resetInterruptedData(taskData.getId(), token);
                    return null;
                });
                log.info("[Task-Data-Parallel] 成功将未开始任务从 PROCESSING 恢复回待处理状态，id: {}", taskData.getId());
            } catch (Exception e) {
                log.error("[Task-Data-Parallel] 回滚中断任务失败，id: {}", taskData.getId(), e);
            }
        }
    }
}
