package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.TreeStorage;
import cn.net.pap.example.proguard.repository.TreeStorageRepository;
import cn.net.pap.example.proguard.service.ITreeStorageService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证树形结构数据在 Service 层的处理逻辑
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class TreeStorageTest {

    private static final Logger log = LoggerFactory.getLogger(TreeStorageTest.class);

    private final ITreeStorageService treeStorageService;
    private final TreeStorageRepository treeStorageRepository;

    public TreeStorageTest(ITreeStorageService treeStorageService, TreeStorageRepository treeStorageRepository) {
        this.treeStorageService = treeStorageService;
        this.treeStorageRepository = treeStorageRepository;
    }

    @Test
    public void hierarchicalServiceInsertionTest() {
        // 1. 构造 JSON 模拟数据 (故意乱序：子节点出现在父节点之前)
        List<Map<String, Object>> inputData = new ArrayList<>();
        inputData.add(createItem(55, 33, "grand-child-2")); // 子先
        inputData.add(createItem(11, null, "root"));      // 父后
        inputData.add(createItem(44, 22, "grand-child-1"));
        inputData.add(createItem(22, 11, "child-1"));
        inputData.add(createItem(33, 11, "child-2"));

        log.info("调用 Service 执行批量导入 (输入数据已故意打乱顺序)...");
        
        // 2. 调用 Service 方法（内部包含 @Transactional 和 映射逻辑）
        treeStorageService.batchSaveHierarchicalData(inputData);

        // 3. 验证数据库中的关联关系
        log.info("验证数据库关联...");
        
        List<TreeStorage> allNodes = treeStorageRepository.findAll();
        
        TreeStorage node5 = allNodes.stream().filter(n -> n.getSequence() == 55).findFirst().orElseThrow();
        TreeStorage node3 = allNodes.stream().filter(n -> n.getSequence() == 33).findFirst().orElseThrow();

        // 验证节点 5 的 ParentId 是否指向了节点 3 的数据库自增 ID
        assertNotNull(node5.getParentId(), "节点 55 应该有父节点");
        assertEquals(node3.getId(), node5.getParentId(), "节点 55 的 ParentId 必须匹配节点 33 的数据库 ID");
        
        log.info("Service 层逻辑验证成功：节点 55 ({}) -> 父节点 33 ({})", node5.getId(), node5.getParentId());
    }

    private Map<String, Object> createItem(Integer seq, Integer parent, String attr) {
        Map<String, Object> item = new HashMap<>();
        item.put("sequence", seq);
        item.put("parentId", parent);
        item.put("attr1", attr);
        return item;
    }
 
    @Test
    public void testPvStressConcurrency() throws InterruptedException {
        // 1. 初始化一条测试数据 (sequence 初始为 0)
        TreeStorage initial = new TreeStorage(0, null, "stress-test");
        TreeStorage saved = treeStorageRepository.saveAndFlush(initial);
        Long targetId = saved.getId();
 
        int numThreads = 10;
        int clicksPerThread = 100;
        int expectedTotalClicks = numThreads * clicksPerThread; // 共 1000 次点击
 
        // 2. 线程池配置并发压测
        java.util.concurrent.ThreadPoolExecutor executor = new java.util.concurrent.ThreadPoolExecutor(
                numThreads,
                numThreads,
                0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(1),
                r -> new Thread(r, "pv-stress-thread"),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
        );
 
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(numThreads);
 
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < clicksPerThread; j++) {
                        treeStorageService.recordClick(targetId);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
 
        latch.await();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
 
        // 3. 校验总量一致性
        long dbVal = treeStorageRepository.findById(targetId).orElseThrow().getSequence();
        long memVal = treeStorageService.getMemoryCount(targetId);
 
        // 无论如何，数据库里的计数加上当前内存滞留的计数，必须等于总期望点击数 1000
        assertEquals(expectedTotalClicks, dbVal + memVal, "数据库计数加上内存中尚未触发阈值的余留计数应当等于总点击数");
        log.info("高并发一致性校验成功！数据库PV数: {}, 内存滞留数: {}, 总合规数: {}", dbVal, memVal, dbVal + memVal);
    }

}
