package cn.net.pap.quartz.repository;

import cn.net.pap.quartz.entity.TaskData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskDataRepository extends JpaRepository<TaskData, Long> {

    /**
     * 原子性地抢占一批数据的所有权
     * 【注意】增加过滤条件：(d.nextProcessTime IS NULL OR d.nextProcessTime <= CURRENT_TIMESTAMP)，
     * 确保只有到了重试允许时间的任务，或者尚未失败重试过的任务，才会被本次并发批处理捡起。
     */
    @Modifying
    @Query("UPDATE TaskData d SET d.processStatus = 'PROCESSING', d.processToken = :processToken, d.processAttempts = d.processAttempts + 1, d.lastProcessTime = CURRENT_TIMESTAMP WHERE d.id IN :ids AND d.processStatus IN ('PENDING', 'RETRYABLE_FAILED') AND (d.nextProcessTime IS NULL OR d.nextProcessTime <= CURRENT_TIMESTAMP) AND d.processAttempts < :maxAttempts")
    int acquireBatchForProcessing(@Param("ids") List<Long> ids, @Param("processToken") String processToken, @Param("maxAttempts") int maxAttempts);

    /**
     * 原子性地标记单条数据成功
     */
    @Modifying
    @Query("UPDATE TaskData d SET d.processStatus = 'SUCCESS', d.processToken = NULL, d.finishTime = CURRENT_TIMESTAMP WHERE d.id = :id AND d.processToken = :processToken")
    int markAsSuccess(@Param("id") Long id, @Param("processToken") String processToken);

    /**
     * 原子性地标记单条数据失败
     */
    @Modifying
    @Query("UPDATE TaskData d SET d.processStatus = 'FAILED', d.processToken = NULL, d.errorMessage = :errorMessage, d.finishTime = CURRENT_TIMESTAMP WHERE d.id = :id AND d.processToken = :processToken")
    int markAsFailed(@Param("id") Long id, @Param("processToken") String processToken, @Param("errorMessage") String errorMessage);

    /**
     * 原子性地标记单条数据为可重试失败
     * 【注意】在标记为 RETRYABLE_FAILED 的同时，需要更新 nextProcessTime 字段。
     * 这告诉数据库，该任务在 nextProcessTime 之前应当被锁定/过滤，以实现退避重试延迟。
     */
    @Modifying
    @Query("UPDATE TaskData d SET d.processStatus = 'RETRYABLE_FAILED', d.processToken = NULL, d.errorMessage = :errorMessage, d.nextProcessTime = :nextProcessTime WHERE d.id = :id AND d.processToken = :processToken")
    int markAsRetryableFailed(@Param("id") Long id, @Param("processToken") String processToken, @Param("errorMessage") String errorMessage, @Param("nextProcessTime") LocalDateTime nextProcessTime);

    /**
     * 查询待处理的数据（不锁定）
     * 【注意】增加过滤条件：(d.nextProcessTime IS NULL OR d.nextProcessTime <= CURRENT_TIMESTAMP)，
     * 用于排他性地仅捞取过了退避重试时间点的数据。
     */
    @Query("SELECT d FROM TaskData d WHERE d.processStatus IN ('PENDING', 'RETRYABLE_FAILED') AND (d.nextProcessTime IS NULL OR d.nextProcessTime <= CURRENT_TIMESTAMP) ORDER BY d.lastProcessTime NULLS FIRST, d.id")
    List<TaskData> findPendingData(Pageable pageable);

    /**
     * 重置卡在 PROCESSING 状态的数据
     */
    @Modifying
    @Query("UPDATE TaskData d SET d.processStatus = 'RETRYABLE_FAILED', d.processToken = NULL WHERE d.processStatus = 'PROCESSING' AND d.lastProcessTime < :timeout AND d.processAttempts < :maxAttempts")
    int resetStuckProcessingData(@Param("timeout") LocalDateTime timeout, @Param("maxAttempts") int maxAttempts);

    /**
     * 根据处理令牌查询数据
     */
    List<TaskData> findByProcessToken(String processToken);

    /**
     * 根据处理状态统计数据数量
     */
    long countByProcessStatus(String processStatus);

}