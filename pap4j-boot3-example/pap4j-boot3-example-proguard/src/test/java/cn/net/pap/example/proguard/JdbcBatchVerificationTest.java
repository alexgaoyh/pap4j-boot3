package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单元测试类：验证 JDBC 批量提交 (Batch) 与普通循环单条提交 (Non-Batch) 的性能差异及正确性。
 * 对比了 ProguardServiceImpl 中的循环原生 SQL 提交方式 (executeNaiveSQLBatch) 与 Spring JdbcTemplate.batchUpdate 提交方式。
 * 同时也对比了不同的批量插入机制的耗时表现。
 * <p>
 * 大批量写操作首选 JDBC/JdbcTemplate：当面临数千甚至数万级别的数据导入、批量更新时，JPA/Hibernate 的性能瓶颈非常明显。此时应当切换到 Spring JdbcTemplate 或 原生 JDBC 开启 Batch 模式。
 */
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class JdbcBatchVerificationTest {

    private static final Logger log = LoggerFactory.getLogger(JdbcBatchVerificationTest.class);

    private final ProguardRepository proguardRepository;
    private final IProguardService proguardService;

    public JdbcBatchVerificationTest(ProguardRepository proguardRepository, IProguardService proguardService) {
        this.proguardRepository = proguardRepository;
        this.proguardService = proguardService;
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
     * Record count: 2000
     * Non-Batch (executeNaiveSQLBatch) Update time: 695.9862 ms
     * Batch (executeNaiveSQLUpdateBatchUsingJdbcTemplate) Update time: 60.4299 ms
     */
    @Test
    public void testBatchVsNonBatchUpdate() {
        int recordCount = 2000;
        List<Long> ids = new ArrayList<>(recordCount);
        List<Proguard> entities = new ArrayList<>(recordCount);
        long baseId = 200000L;

        for (int i = 0; i < recordCount; i++) {
            long id = baseId + i;
            ids.add(id);
            entities.add(createProguard(id, "init_" + i));
        }

        proguardRepository.saveAllAndFlush(entities);

        long durationNonBatch;
        long durationBatch;

        try {
            durationNonBatch = runLoopUpdate(ids);
            verifyUpdates(ids, "loop_update_");

            durationBatch = runJdbcTemplateBatchUpdate(ids);
            verifyUpdates(ids, "jdbc_template_batch_");
        } finally {
            proguardRepository.deleteAllById(ids);
        }

        log.info("=========================================");
        log.info("JDBC Batch Verification Performance Summary:");
        log.info("Record count: {}", recordCount);
        log.info("Non-Batch (executeNaiveSQLBatch) Update time: {} ms", durationNonBatch / 1_000_000.0);
        log.info("Batch (executeNaiveSQLUpdateBatchUsingJdbcTemplate) Update time: {} ms", durationBatch / 1_000_000.0);
        log.info("=========================================");

        assertTrue(durationBatch > 0);
        assertTrue(durationNonBatch > 0);
    }

    /**
     * Record count: 1000
     * Method 1: JPA Loop Insert (saveAndFlush2) time: 2286.5786 ms
     * Method 2: JPA saveAllAndFlush time: 833.8763 ms
     * Method 3: JDBC Batch PreparedStatement time: 17.7598 ms
     */
    @Test
    public void testInsertBatchPerformance() {
        int recordCount = 1000;

        // 1. JPA 循环单条保存
        List<Proguard> listLoop = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            listLoop.add(createProguard(300000L + i, "jpa_loop_" + i));
        }
        long startJpaLoop = System.nanoTime();
        proguardService.saveAndFlush2(listLoop.toArray(new Proguard[0]));
        long durationJpaLoop = System.nanoTime() - startJpaLoop;

        List<Long> idsLoop = listLoop.stream().map(Proguard::getProguardId).toList();
        proguardRepository.deleteAllById(idsLoop);

        // 2. JPA saveAllAndFlush
        List<Proguard> listBatch = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            listBatch.add(createProguard(400000L + i, "jpa_batch_" + i));
        }
        long startJpaBatch = System.nanoTime();
        proguardService.saveAllAndFlush(listBatch);
        long durationJpaBatch = System.nanoTime() - startJpaBatch;

        List<Long> idsBatch = listBatch.stream().map(Proguard::getProguardId).toList();
        proguardRepository.deleteAllById(idsBatch);

        // 3. JDBC PreparedStatement 批量插入
        List<String> idsJdbc = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            idsJdbc.add(String.valueOf(500000L + i));
        }
        long startJdbcBatch = System.nanoTime();
        proguardService.executeNaiveSQLInsertBatchUsingJDBC(idsJdbc);
        long durationJdbcBatch = System.nanoTime() - startJdbcBatch;

        List<Long> idsJdbcLong = idsJdbc.stream().map(Long::parseLong).toList();
        proguardRepository.deleteAllById(idsJdbcLong);

        log.info("=========================================");
        log.info("JDBC/JPA Insert Batch Performance Summary:");
        log.info("Record count: {}", recordCount);
        log.info("Method 1: JPA Loop Insert (saveAndFlush2) time: {} ms", durationJpaLoop / 1_000_000.0);
        log.info("Method 2: JPA saveAllAndFlush time: {} ms", durationJpaBatch / 1_000_000.0);
        log.info("Method 3: JDBC Batch PreparedStatement time: {} ms", durationJdbcBatch / 1_000_000.0);
        log.info("=========================================");

        assertTrue(durationJpaLoop > 0);
        assertTrue(durationJpaBatch > 0);
        assertTrue(durationJdbcBatch > 0);
    }

    private long runLoopUpdate(List<Long> ids) {
        int recordCount = ids.size();
        List<String> sqlList = new ArrayList<>(recordCount);
        List<List<Object>> paramsList = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            sqlList.add("UPDATE proguard SET proguard_name = ? WHERE proguard_id = ?");
            List<Object> params = new ArrayList<>();
            params.add("loop_update_" + i);
            params.add(ids.get(i));
            paramsList.add(params);
        }
        long start = System.nanoTime();
        proguardService.executeNaiveSQLBatch(sqlList, paramsList);
        return System.nanoTime() - start;
    }

    private long runJdbcTemplateBatchUpdate(List<Long> ids) {
        int recordCount = ids.size();
        List<List<Object>> paramsList = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            List<Object> params = new ArrayList<>();
            params.add("jdbc_template_batch_" + i);
            params.add(ids.get(i));
            paramsList.add(params);
        }
        long start = System.nanoTime();
        proguardService.executeNaiveSQLUpdateBatchUsingJdbcTemplate(
                "UPDATE proguard SET proguard_name = ? WHERE proguard_id = ?",
                paramsList
        );
        return System.nanoTime() - start;
    }

    private void verifyUpdates(List<Long> ids, String prefix) {
        Proguard first = proguardRepository.getProguardByProguardId(ids.get(0));
        assertNotNull(first);
        assertEquals(prefix + "0", first.getProguardName());

        Proguard last = proguardRepository.getProguardByProguardId(ids.get(ids.size() - 1));
        assertNotNull(last);
        assertEquals(prefix + (ids.size() - 1), last.getProguardName());
    }
}
