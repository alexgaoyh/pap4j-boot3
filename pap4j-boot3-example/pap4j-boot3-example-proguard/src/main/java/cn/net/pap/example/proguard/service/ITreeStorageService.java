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
}
