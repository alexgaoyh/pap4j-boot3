package cn.net.pap.quartz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_data")
public class TaskData {

    @Id
    private Long id;

    /**
     * 数据内容
     */
    @Column(name = "data_content", length = 500)
    private String dataContent;

    /**
     * 处理状态：PENDING, PROCESSING, SUCCESS, FAILED, RETRYABLE_FAILED
     */
    @Column(name = "process_status", nullable = false, length = 20)
    private String processStatus = "PENDING";

    /**
     * 处理尝试次数
     */
    @Column(name = "process_attempts")
    private Integer processAttempts = 0;

    /**
     * 最后处理时间
     */
    @Column(name = "last_process_time")
    private LocalDateTime lastProcessTime;

    /**
     * 处理令牌，确保唯一性
     */
    @Column(name = "process_token")
    private String processToken;

    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * 创建时间
     */
    @Column(name = "created_time")
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 完成时间
     */
    @Column(name = "finished_time")
    private LocalDateTime finishTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDataContent() {
        return dataContent;
    }

    public void setDataContent(String dataContent) {
        this.dataContent = dataContent;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }

    public Integer getProcessAttempts() {
        return processAttempts;
    }

    public void setProcessAttempts(Integer processAttempts) {
        this.processAttempts = processAttempts;
    }

    public LocalDateTime getLastProcessTime() {
        return lastProcessTime;
    }

    public void setLastProcessTime(LocalDateTime lastProcessTime) {
        this.lastProcessTime = lastProcessTime;
    }

    public String getProcessToken() {
        return processToken;
    }

    public void setProcessToken(String processToken) {
        this.processToken = processToken;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }
}
