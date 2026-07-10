package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试类：验证 Spring @Transactional 在高并发下的连接池枯竭问题以及 JPA 只读事务的脏检查优化。
 * 所有测试逻辑控制在当前类中。
 */
@SpringBootTest(
    classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class},
    properties = {
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.connection-timeout=1000",
        "spring.datasource.url=jdbc:h2:mem:${random.uuid};DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
    }
)
@Import(TransactionBestPracticeTest.TestService.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class TransactionBestPracticeTest {

    private static final Logger log = LoggerFactory.getLogger(TransactionBestPracticeTest.class);

    private final ProguardRepository proguardRepository;
    private final TestService testService;

    public TransactionBestPracticeTest(ProguardRepository proguardRepository, TestService testService) {
        this.proguardRepository = proguardRepository;
        this.testService = testService;
    }

    private Proguard createProguard(Long id, String name) {
        Proguard p = new Proguard();
        p.setProguardId(id);
        p.setProguardName(name);

        ObjectMapper mapper = new ObjectMapper();
        p.setAbstractList(mapper.createArrayNode());
        p.setAbstractObj(mapper.createObjectNode());

        return p;
    }

    /**
     * 验证在高并发下，长事务方法导致 HikariCP 连接池枯竭。
     */
    @Test
    public void testConnectionPoolStarvation() throws Exception {
        // 预存一条测试数据
        Proguard proguard = createProguard(999L, "TestPool");
        proguardRepository.saveAndFlush(proguard);

        int threadCount = 3;
        // 显式创建 ThreadPoolExecutor，指定有界队列和线程工厂，符合项目并发规范
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(10),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "task-worker-" + count++);
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<CompletableFuture<Void>> futuresWithTx = new ArrayList<>(threadCount);
        List<Throwable> exceptionsWithTx = new CopyOnWriteArrayList<>();

        log.info("【测试开始】使用 @Transactional（预期触发连接池饥饿，第3个线程超时失败）...");
        for (int i = 0; i < threadCount; i++) {
            futuresWithTx.add(CompletableFuture.runAsync(() -> {
                try {
                    // 每个线程持有连接 1500ms，而连接池大小为 2，第 3 个线程在获取连接时会超时（超时设为 1000ms）
                    testService.executeWithTransaction(999L, 1500L);
                } catch (Throwable t) {
                    exceptionsWithTx.add(t);
                    log.error("期望内的线程失败: {}", t.getMessage());
                }
            }, executor));
        }

        CompletableFuture.allOf(futuresWithTx.toArray(new CompletableFuture[0])).join();

        log.info("使用 @Transactional 时的异常数: {}", exceptionsWithTx.size());
        assertFalse(exceptionsWithTx.isEmpty(), "预期在高并发事务下至少有一个线程因连接池饥饿失败");
        boolean hasConnectionTimeout = exceptionsWithTx.stream()
                .anyMatch(t -> t.toString().contains("Connection") || t.toString().contains("timeout") || t.toString().contains("CannotCreateTransactionException"));
        assertTrue(hasConnectionTimeout, "抛出的异常中应当包含数据库连接获取超时的信息");

        // 验证没有 @Transactional 包裹时的情况
        List<CompletableFuture<Void>> futuresWithoutTx = new ArrayList<>(threadCount);
        List<Throwable> exceptionsWithoutTx = new CopyOnWriteArrayList<>();

        log.info("【测试开始】无 @Transactional（预期全部执行成功，不占用多余的连接）...");
        for (int i = 0; i < threadCount; i++) {
            futuresWithoutTx.add(CompletableFuture.runAsync(() -> {
                try {
                    // 查询完数据库后连接立即释放，Thread.sleep 期间不占用连接，所有线程均能正常执行
                    testService.executeWithoutTransaction(999L, 1500L);
                } catch (Throwable t) {
                    exceptionsWithoutTx.add(t);
                    log.error("非预期的线程失败: {}", t.getMessage(), t);
                }
            }, executor));
        }

        CompletableFuture.allOf(futuresWithoutTx.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        log.info("无 @Transactional 时的异常数: {}", exceptionsWithoutTx.size());
        assertTrue(exceptionsWithoutTx.isEmpty(), "预期在无事务包裹下，所有线程正常执行成功，不发生连接超时");
    }

    /**
     * 验证 JPA 只读事务的脏检查优化行为。
     */
    @Test
    public void testJpaReadOnlyDirtyChecking() {
        Long testId = 123456L;
        Proguard p = createProguard(testId, "OriginalName");
        proguardRepository.saveAndFlush(p);

        // 1. 功能验证：在 readOnly = true 事务中修改属性，提交事务后不会同步到数据库（脏检查/自动 flush 被绕过）
        testService.modifyWithReadOnlyTrue(testId, "NewNameTrue");
        Proguard p1 = proguardRepository.findById(testId).orElse(null);
        assertNotNull(p1);
        assertEquals("OriginalName", p1.getProguardName(), "在 readOnly = true 下，实体属性修改不应被自动 Flush 写入数据库");

        // 2. 功能验证：在普通读写事务中修改属性，提交事务后会自动写入数据库（触发脏检查和自动 flush）
        testService.modifyWithReadOnlyFalse(testId, "NewNameFalse");
        Proguard p2 = proguardRepository.findById(testId).orElse(null);
        assertNotNull(p2);
        assertEquals("NewNameFalse", p2.getProguardName(), "在非只读事务下，实体属性修改必须能被自动 Flush 写入数据库");

        // 3. 性能验证：模拟大批量数据加载，对比只读事务与读写事务的执行耗时
        log.info("正在为性能验证写入 2000 条数据...");
        List<Proguard> batch = new ArrayList<>(2000);
        for (long i = 10000L; i < 12000L; i++) {
            batch.add(createProguard(i, "PerfTest_" + i));
        }
        proguardRepository.saveAllAndFlush(batch);

        // 预热 JVM
        testService.loadEntitiesWithReadOnlyTrue();
        testService.loadEntitiesWithReadOnlyFalse();

        // 测试 readOnly = true
        long startTrue = System.nanoTime();
        List<Proguard> listTrue = testService.loadEntitiesWithReadOnlyTrue();
        long durationTrue = System.nanoTime() - startTrue;

        // 测试 readOnly = false
        long startFalse = System.nanoTime();
        List<Proguard> listFalse = testService.loadEntitiesWithReadOnlyFalse();
        long durationFalse = System.nanoTime() - startFalse;

        log.info("【性能验证】readOnly = true 加载数据耗时: {} ms", durationTrue / 1_000_000.0);
        log.info("【性能验证】readOnly = false 加载数据耗时: {} ms", durationFalse / 1_000_000.0);

        assertTrue(listTrue.size() >= 2000);
    }

    /**
     * 辅助测试的局部 Service
     */
    @Service
    public static class TestService {

        private final ProguardRepository proguardRepository;

        public TestService(ProguardRepository proguardRepository) {
            this.proguardRepository = proguardRepository;
        }

        /**
         * 声明式事务方法，包含慢操作（模拟外部系统调用），事务期间独占数据库连接。
         */
        @Transactional
        public void executeWithTransaction(Long id, long sleepMs) {
            // 查询引发连接绑定
            proguardRepository.findById(id);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 无事务包裹的方法，查询完后连接即归还，慢操作期间不占用连接。
         */
        public void executeWithoutTransaction(Long id, long sleepMs) {
            proguardRepository.findById(id);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * 只读事务修改实体
         */
        @Transactional(readOnly = true)
        public Proguard modifyWithReadOnlyTrue(Long id, String newName) {
            Proguard p = proguardRepository.findById(id).orElse(null);
            if (p != null) {
                p.setProguardName(newName);
            }
            return p;
        }

        /**
         * 读写事务修改实体
         */
        @Transactional(readOnly = false)
        public Proguard modifyWithReadOnlyFalse(Long id, String newName) {
            Proguard p = proguardRepository.findById(id).orElse(null);
            if (p != null) {
                p.setProguardName(newName);
            }
            return p;
        }

        /**
         * 只读事务批量加载
         */
        @Transactional(readOnly = true)
        public List<Proguard> loadEntitiesWithReadOnlyTrue() {
            return proguardRepository.findAll();
        }

        /**
         * 读写事务批量加载
         */
        @Transactional(readOnly = false)
        public List<Proguard> loadEntitiesWithReadOnlyFalse() {
            return proguardRepository.findAll();
        }
    }
}
