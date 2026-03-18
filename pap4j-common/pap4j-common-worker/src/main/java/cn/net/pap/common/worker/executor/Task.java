package cn.net.pap.common.worker.executor;

/**
 * 任务实体类
 */
public class Task {

    /**
     * 任务ID，需保证唯一性
     */
    private String id;

    /**
     * 模拟任务处理时间（毫秒）
     */
    private long processingTime;

    /**
     * 任务状态
     * 增加 volatile 关键字，保证多线程环境下状态变更的可见性
     */
    private volatile TaskStatus status;

    /**
     * 重试次数
     * 增加 volatile 关键字，保证多线程环境下重试次数的可见性
     */
    private volatile int retryCount;

    /**
     * 任务执行结果
     * 增加 volatile 关键字，保证多线程环境下结果变更的可见性
     */
    private volatile String result;

    /**
     * 任务最后更新时间
     * 引入时间戳字段，用于配合定时任务清理内存中过期的任务，防止 OOM
     */
    private volatile long updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", processingTime=" + processingTime +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", result='" + result + '\'' +
                ", updateTime=" + updateTime +
                '}';
    }
}
