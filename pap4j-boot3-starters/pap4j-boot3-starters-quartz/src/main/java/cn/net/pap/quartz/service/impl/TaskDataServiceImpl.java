package cn.net.pap.quartz.service.impl;

import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.repository.TaskDataRepository;
import cn.net.pap.quartz.service.ITaskDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskDataServiceImpl implements ITaskDataService {

    private static final Logger log = LoggerFactory.getLogger(TaskDataServiceImpl.class);

    private final TaskDataRepository taskDataRepository;
    private final TransactionTemplate transactionTemplate;

    public TaskDataServiceImpl(TaskDataRepository taskDataRepository, PlatformTransactionManager transactionManager) {
        this.taskDataRepository = taskDataRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private static final int BATCH_SIZE = 10;

    private static final int MAX_RETRY_ATTEMPTS = 1;

    private static final Duration PROCESSING_TIMEOUT = Duration.ofHours(24);

    /**
     * 高并发安全的批量处理方法 - 核心入口
     */
    @Override
    public void processBatchSafely() {
        int processedCount;

        do {
            // 1. 查询待处理数据（不加锁）
            List<TaskData> pendingData = taskDataRepository.findPendingData(
                    PageRequest.of(0, BATCH_SIZE));

            if (pendingData.isEmpty()) {
                break;
            }

            // 2. 原子性地抢占并处理这批数据
            processedCount = acquireAndProcessBatch(pendingData);

        } while (processedCount == BATCH_SIZE);
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
            return 0; // 没有抢到任何数据
        }

        // 处理抢到的数据
        processAcquiredData(acquiredData, processToken);

        return acquiredData.size();
    }

    /**
     * 处理已抢占的数据
     */
    private void processAcquiredData(List<TaskData> acquiredData, String processToken) {
        acquiredData.stream().forEach(data -> {
            // “至少一次（At-Least-Once）”消费保证：因为外部 HTTP 接口通常是不可逆且无法回滚的（例如发了短信、扣了款）。
            // 如果 JVM 在处理到第 5 条数据时突然宕机，前 4 条由于已经单独提交了事务，状态是  SUCCESS ，不会被重复消费；只有剩下的会被恢复重新执行。
            // 所以目前的方式，执行完一条记录就刷新数据库，容错性较好。
            processSingleDataSafely(data, processToken);
        });
    }

    /**
     * 安全处理单条数据 (核心控制流：无大事务)
     */
    private void processSingleDataSafely(TaskData data, String processToken) {
        try {
            // 1. 执行外部业务逻辑 (无事务，阻断连接占用)
            processBusinessLogic(data);

            // 2. 标记处理成功 (独立小事务，立即提交)
            boolean success = markTaskSuccessInNewTransaction(data.getId(), processToken);
            if (!success) {
                log.warn("标记成功失败，数据可能已被其他进程处理: {}", data.getId());
            }

        } catch (Exception e) {
            // 3. 标记处理失败 (独立小事务，立即提交)
            markTaskFailureInNewTransaction(data, processToken, e);
        }
    }

    /**
     * 处理业务逻辑 - 根据实际业务需求实现
     */
    private void processBusinessLogic(TaskData data) {
        // 这里实现具体的业务处理逻辑
        // 例如：数据转换、调用外部接口、计算等

        // 模拟业务处理
        String content = data.getDataContent();
        if (content == null) {
            throw new RuntimeException("数据内容为空");
        }

        // 业务处理示例：将内容转换为大写
        String processedContent = content.toUpperCase();
        // log.info(processedContent);
        // 这里可以根据需要更新数据内容或其他字段

        // 模拟处理耗时
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 如果处理过程中发生可重试的异常，可以抛出特定异常
        if (content.contains("RETRY")) {
            throw new RuntimeException("模拟可重试异常");
        }

        // 如果发生不可重试的异常
        if (content.contains("FAIL")) {
            throw new RuntimeException("模拟不可重试异常");
        }
    }

    /**
     * 在独立的事务中标记成功
     */
    private boolean markTaskSuccessInNewTransaction(Long id, String processToken) {
        Boolean result = transactionTemplate.execute(status -> 
            taskDataRepository.markAsSuccess(id, processToken) > 0
        );
        return Boolean.TRUE.equals(result);
    }

    /**
     * 在独立的事务中标记失败
     */
    private void markTaskFailureInNewTransaction(TaskData data, String processToken, Exception e) {
        try {
            transactionTemplate.execute(status -> {
                if (data.getProcessAttempts() >= MAX_RETRY_ATTEMPTS ||
                        (e.getMessage() != null && e.getMessage().contains("不可重试"))) {
                    // 超过重试次数或不可重试异常，标记为最终失败
                    int updated = taskDataRepository.markAsFailed(data.getId(), processToken, e.getMessage());
                    if (updated == 0) {
                        log.warn("标记失败时发生冲突: {}", data.getId());
                    }
                } else {
                    // 标记为可重试失败
                    taskDataRepository.markAsRetryableFailed(data.getId(), processToken, e.getMessage());
                }
                return null;
            });
        } catch (Exception ex) {
            log.error("将数据标记为失败时发生数据库异常: {}", data.getId(), ex);
        }
        log.error("处理数据失败: {}", data.getId(), e);
    }

    /**
     * 恢复卡在 PROCESSING 状态的数据（应用重启等情况），可以考虑增加索引提升处理效率
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverStuckData() {
        LocalDateTime timeout = LocalDateTime.now().minus(PROCESSING_TIMEOUT);

        int recovered = taskDataRepository.resetStuckProcessingData(timeout, MAX_RETRY_ATTEMPTS);

        if (recovered > 0) {
            log.info("恢复了 {} 条卡住的数据", recovered);
        }
    }

    /**
     * 手动重试失败的数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryFailedData(List<Long> dataIds) {
        // 这里可以实现手动重试逻辑
        // 例如将 FAILED 状态的数据重置为 PENDING
        List<TaskData> failedData = taskDataRepository.findAllById(dataIds);

        for (TaskData data : failedData) {
            if ("FAILED".equals(data.getProcessStatus())) {
                data.setProcessStatus("PENDING");
                data.setProcessAttempts(0);
                data.setErrorMessage(null);
                taskDataRepository.save(data);
            }
        }
    }

    /**
     * 获取处理统计信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Long> getProcessingStats() {
        // 这里可以实现统计逻辑
        // 返回各状态的数据数量
        return Map.of(
                "total", taskDataRepository.count(),
                "pending", taskDataRepository.countByProcessStatus("PENDING"),
                "processing", taskDataRepository.countByProcessStatus("PROCESSING"),
                "success", taskDataRepository.countByProcessStatus("SUCCESS"),
                "failed", taskDataRepository.countByProcessStatus("FAILED")
        );
    }

    @Override
    public void saveAll(List<TaskData> taskDataList) {
        taskDataRepository.saveAll(taskDataList);
    }

    @Override
    public void deleteAll() {
        taskDataRepository.deleteAll();
    }

    @Override
    public void callNptException(Long inputLong) {
        TaskData taskData = new TaskData();
        taskData.getDataContent().length();
    }

}
