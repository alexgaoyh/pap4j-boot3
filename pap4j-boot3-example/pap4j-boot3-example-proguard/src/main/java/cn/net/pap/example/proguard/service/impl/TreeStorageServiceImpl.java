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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 树形存储服务实现类
 */
@Service
public class TreeStorageServiceImpl implements ITreeStorageService {

    private static final Logger log = LoggerFactory.getLogger(TreeStorageServiceImpl.class);

    private final TreeStorageRepository treeStorageRepository;

    // 内存计数缓冲区：Key 为 id，Value 包含计数与上次更新时间戳
    private final Map<Long, PvCounter> pvBuffer = new ConcurrentHashMap<>();

    // 双重阈值定义
    private static final long FLUSH_COUNT_THRESHOLD = 50;                   // 数量阈值：50 次
    private static final long FLUSH_TIME_THRESHOLD_MS = 10 * 1000;          // 时间阈值：10 秒

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

    @Override
    public void recordClick(Long id) {
        PvCounter counter = pvBuffer.computeIfAbsent(id, k -> new PvCounter());

        // 1. 原子自增计数
        long currentCount = counter.count.incrementAndGet();
        long now = System.currentTimeMillis();
        long elapsed = now - counter.lastFlushTime.get();

        // 2. 双重条件判定：数量满足阈值，或者时间间隔满足阈值
        if (currentCount >= FLUSH_COUNT_THRESHOLD || elapsed >= FLUSH_TIME_THRESHOLD_MS) {
            synchronized (counter) {
                long toFlush = counter.count.get();
                long currentElapsed = now - counter.lastFlushTime.get();

                // 双重校验锁
                if (toFlush >= FLUSH_COUNT_THRESHOLD || currentElapsed >= FLUSH_TIME_THRESHOLD_MS) {
                    if (toFlush > 0) {
                        counter.count.addAndGet(-toFlush);
                        counter.lastFlushTime.set(now);

                        // 3. 执行数据库自增更新
                        treeStorageRepository.incrementSequence(id, (int) toFlush);
                    }
 
                    // 4. 将计数已归零的 ID 从 Map 中移除，防止内存无限膨胀
                    if (counter.count.get() == 0) {
                        pvBuffer.computeIfPresent(id, (k, val) -> {
                            if (val.count.get() == 0) {
                                return null; // 返回 null 即可在 Map 中原子删除该 Key
                            }
                            return val;
                        });
                    }
                }
            }
        }
    }

    @Override
    public long getMemoryCount(Long id) {
        PvCounter counter = pvBuffer.get(id);
        return counter != null ? counter.count.get() : 0L;
    }

    /**
     * 内部计数包装类，封装计数器与上次刷写时间戳
     */
    private static class PvCounter {
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong lastFlushTime = new AtomicLong(System.currentTimeMillis());
    }

}
