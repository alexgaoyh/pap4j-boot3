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

    /**
     * 【非阻塞分布式退避重试核心字段】
     * 下一次允许被捞起执行的时间点。在发生可重试错误时，系统计算未来的退避时间并写入此列。
     * 
     * 引入该时间控制字段的核心架构意义：
     * 1. 【避免瞬间耗尽尝试次数】：若不设延迟进行即时重试，在下游服务短暂瘫痪时，多次重试会在 1 秒钟之内被瞬间榨干并报错最终失败，使重试机制失效；
     * 2. 【平滑重试风暴（防惊群效应）】：配合随机抖动（Jitter）将失败任务在时间轴上散开，避免高并发下大量失败任务同时在下一秒重试，形成二次重试风暴轰炸下游；
     * 3. 【防止队列空转与饥饿】：未到重试时间的任务会被查询过滤，腾出批处理窗口（BATCH_SIZE）优先处理新进来的正常任务，防止故障任务无限期霸占处理通道。
     * 
     * 此外，基于数据库字段时间的异步退避设计彻底消除了内存中同步线程挂起（Thread.sleep）所带来的头部阻塞与连接池枯竭隐患。
     */
    @Column(name = "next_process_time")
    private LocalDateTime nextProcessTime;

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

    public LocalDateTime getNextProcessTime() {
        return nextProcessTime;
    }

    public void setNextProcessTime(LocalDateTime nextProcessTime) {
        this.nextProcessTime = nextProcessTime;
    }
}
