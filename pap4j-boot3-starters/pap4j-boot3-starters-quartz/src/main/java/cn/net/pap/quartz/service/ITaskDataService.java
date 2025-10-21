package cn.net.pap.quartz.service;

import cn.net.pap.quartz.entity.TaskData;

import java.util.List;
import java.util.Map;

public interface ITaskDataService {

    /**
     * 高并发安全的批量处理方法 - 核心入口
     */
    public void processBatchSafely();

    /**
     * 恢复卡在 PROCESSING 状态的数据（应用重启等情况）
     */
    public void recoverStuckData();

    /**
     * 手动重试失败的数据
     */
    public void retryFailedData(List<Long> dataIds);

    /**
     * 获取处理统计信息
     */
    public Map<String, Long> getProcessingStats();

    /**
     * 保存所有
     *
     * @param taskDataList
     */
    public void saveAll(List<TaskData> taskDataList);

    public void deleteAll();
}
