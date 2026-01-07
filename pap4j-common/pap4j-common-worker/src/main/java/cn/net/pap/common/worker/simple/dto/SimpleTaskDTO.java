package cn.net.pap.common.worker.simple.dto;

/**
 * 任务对象：简单的POJO
 */
public class SimpleTaskDTO {

    private final String id;

    private final String name;

    private final int processingTime; // 模拟处理时间（毫秒）

    public SimpleTaskDTO(String id, String name, int processingTime) {
        this.id = id;
        this.name = name;
        this.processingTime = processingTime;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getProcessingTime() {
        return processingTime;
    }

    @Override
    public String toString() {
        return String.format("Task[id=%s, name=%s, time=%dms]",
                id, name, processingTime);
    }

}
