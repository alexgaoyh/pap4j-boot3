package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.TreeStorage;
import cn.net.pap.example.proguard.repository.TreeStorageRepository;
import cn.net.pap.example.proguard.service.ITreeStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 树形存储服务实现类
 */
@Service
public class TreeStorageServiceImpl implements ITreeStorageService {

    private static final Logger log = LoggerFactory.getLogger(TreeStorageServiceImpl.class);

    private final TreeStorageRepository treeStorageRepository;

    public TreeStorageServiceImpl(TreeStorageRepository treeStorageRepository) {
        this.treeStorageRepository = treeStorageRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveHierarchicalData(List<Map<String, Object>> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return;
        }

        // 1. 数据预处理：根据 parentId (null优先) 和 sequence 排序
        // 这一步至关重要，确保在主键自增场景下，父节点一定先于子节点被插入并生成 ID
        inputData.sort(java.util.Comparator.comparing(
                (Map<String, Object> m) -> (Integer) m.get("parentId"),
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())
        ).thenComparing(m -> (Integer) m.get("sequence")));

        // 内存映射：业务 Sequence -> 数据库生成的 ID
        Map<Integer, Long> sequenceToDbIdMap = new HashMap<>();

        for (Map<String, Object> raw : inputData) {
            Integer sequence = (Integer) raw.get("sequence");
            Integer bizParentId = (Integer) raw.get("parentId");
            String attr1 = (String) raw.get("attr1");

            // 根据业务父 ID 查找其实际生成的数据库 ID
            Long dbParentId = null;
            if (bizParentId != null) {
                dbParentId = sequenceToDbIdMap.get(bizParentId);
                if (dbParentId == null) {
                    log.warn("无法找到业务父节点 {} 对应的数据库 ID，当前节点 {} 将作为根节点处理", bizParentId, sequence);
                }
            }

            // 构建并保存实体
            TreeStorage entity = new TreeStorage(sequence, dbParentId, attr1);
            // 使用 saveAndFlush 确保 ID 立即生成并同步到实体中，供后续节点引用
            TreeStorage saved = treeStorageRepository.saveAndFlush(entity);

            // 更新映射表
            sequenceToDbIdMap.put(sequence, saved.getId());
            log.debug("节点保存成功: Sequence={}, DB_ID={}, Parent_DB_ID={}", sequence, saved.getId(), dbParentId);
        }
        
        log.info("成功批量导入 {} 条树形数据", inputData.size());
    }
}
