package cn.net.pap.quartz.service.impl;

import cn.net.pap.quartz.entity.TaskData;
import cn.net.pap.quartz.repository.TaskDataRepository;
import cn.net.pap.quartz.service.ITaskDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskDataServiceImpl implements ITaskDataService {

    private static final Logger log = LoggerFactory.getLogger(TaskDataServiceImpl.class);

    @Autowired(required = false)
    private TaskDataRepository taskDataRepository;

    private static final int BATCH_SIZE = 10;

    private static final int MAX_RETRY_ATTEMPTS = 1;

    private static final Duration PROCESSING_TIMEOUT = Duration.ofHours(24);

    /**
     * 高并发安全的批量处理方法 - 核心入口
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 核心：原子性地抢占数据所有权
        int acquiredCount = taskDataRepository.acquireBatchForProcessing(
                pendingIds, processToken, MAX_RETRY_ATTEMPTS);

        if (acquiredCount == 0) {
            return 0; // 没有抢到任何数据
        }

        // 查询刚刚被抢占的数据
        List<TaskData> acquiredData = taskDataRepository.findByProcessToken(processToken);

        // 处理抢到的数据
        processAcquiredDataInParallel(acquiredData, processToken);

        return acquiredData.size();
    }

    /**
     * 处理已抢占的数据
     */
    private void processAcquiredDataInParallel(List<TaskData> acquiredData, String processToken) {
        acquiredData.stream().forEach(data -> {
            processSingleDataSafely(data, processToken);
        });
    }

    /**
     * 安全处理单条数据
     */
    private void processSingleDataSafely(TaskData data, String processToken) {
        try {
            // 执行业务逻辑
            processBusinessLogic(data);

            // 原子性地标记成功
            int updated = taskDataRepository.markAsSuccess(data.getId(), processToken);
            if (updated == 0) {
                log.warn("标记成功失败，数据可能已被其他进程处理: {}", data.getId());
            }

        } catch (Exception e) {
            handleProcessingFailure(data, processToken, e);
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
        // System.out.println(processedContent);
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
     * 处理执行失败的情况
     */
    private void handleProcessingFailure(TaskData data, String processToken, Exception e) {
        // 根据异常类型和重试次数决定是标记为最终失败还是可重试失败
        if (data.getProcessAttempts() >= MAX_RETRY_ATTEMPTS ||
                e.getMessage().contains("不可重试")) {
            // 超过重试次数或不可重试异常，标记为最终失败
            int updated = taskDataRepository.markAsFailed(data.getId(), processToken, e.getMessage());
            if (updated == 0) {
                log.warn("标记失败时发生冲突: {}", data.getId());
            }
        } else {
            // 标记为可重试失败
            taskDataRepository.markAsRetryableFailed(data.getId(), processToken, e.getMessage());
        }

        log.error("处理数据失败: {}", data.getId(), e);
    }

    /**
     * 恢复卡在 PROCESSING 状态的数据（应用重启等情况）
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

}
