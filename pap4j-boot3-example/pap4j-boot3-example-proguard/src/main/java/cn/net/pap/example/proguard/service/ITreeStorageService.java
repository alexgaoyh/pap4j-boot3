package cn.net.pap.example.proguard.service;

import java.util.List;
import java.util.Map;

/**
 * 树形存储服务接口
 */
public interface ITreeStorageService {

    /**
     * 批量保存具有父子关系的树形数据
     * @param inputData 包含 sequence, parentId (业务键) 和 attr1 的原始数据列表
     */
    void batchSaveHierarchicalData(List<Map<String, Object>> inputData);
 
    /**
     * 记录点击：每次点击进行数量累加，并同时判断数量与时间间隔，达到阈值时刷写数据库
     *
     * @param id 树形存储ID
     */
    void recordClick(Long id);
 
    /**
     * 获取内存中尚未写入数据库的缓冲计数值（主要用于测试与监控）
     *
     * @param id 树形存储ID
     * @return 内存中的剩余计数值
     */
    long getMemoryCount(Long id);
}
