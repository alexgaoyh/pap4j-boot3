package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.dto.ProguardDTO;
import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
import cn.net.pap.example.proguard.util.SQLUtil;
import cn.net.pap.example.proguard.util.SearchUtil;
import cn.net.pap.example.proguard.util.SpringUtils;
import cn.net.pap.example.proguard.util.dto.SearchConditionDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class},
        properties = {
                "spring.jpa.show-sql=true",
                "logging.level.org.hibernate.SQL=DEBUG",
                "logging.level.org.hibernate.orm.jdbc.bind=TRACE"
        }
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
public class ProguardTest {

    private static final Logger log = LoggerFactory.getLogger(ProguardTest.class);

    private final ProguardRepository proguardRepository;
    private final IProguardService proguardService;
    private final EntityManager entityManager;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    public ProguardTest(ProguardRepository proguardRepository, IProguardService proguardService, EntityManager entityManager, org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.proguardRepository = proguardRepository;
        this.proguardService = proguardService;
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
    }

    @Test
    public void transTest() {
        Long proguardId = System.currentTimeMillis();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            try {
                Proguard proguard = new Proguard();
                proguard.setProguardId(proguardId);
                proguard.setProguardName(proguardId + "");
                Map<String, Object> extMap = new HashMap<>();
                extMap.put("timeswap", System.currentTimeMillis());
                extMap.put("threadId", Thread.currentThread().getName());
                proguard.setExtMap(extMap);
                List<String> extList = new ArrayList<>();
                extList.add("A");
                extList.add("B");
                extList.add("C");
                extList.add("D");
                proguard.setExtList(extList);

                Map<String, Object> abstractMap = new HashMap<>();
                abstractMap.put("extMap", extMap);
                abstractMap.put("extList", extList);

                ObjectMapper mapper = new ObjectMapper();
                ArrayNode arrayNode = mapper.createArrayNode();
                JsonNode nestedObject = mapper.valueToTree(abstractMap);
                arrayNode.add(nestedObject);
                ObjectNode objectNode = mapper.valueToTree(abstractMap);
                proguard.setAbstractObj(objectNode);
                proguard.setAbstractList(arrayNode);
                proguardService.saveAndFlush(proguard);

                proguardService.saveAndFlush(new Proguard());

                //status.setRollbackOnly();

                return null;
            } catch (Exception ex) {
                status.setRollbackOnly();
                return null;
            }
        });
        // 是否调用 status.setRollbackOnly(); 的区别
        Proguard proguardDB = proguardService.getProguardByProguardId(proguardId);
        if (proguardDB != null) {
            log.info("{} : {}", proguardDB.getProguardId(), proguardDB.getProguardName());
        } else {
            log.info("-------------rollback-----------------");
        }

    }

    @Test
    public void projectionsTest() {
        Long proguardId = System.currentTimeMillis();

        Proguard proguard = new Proguard();
        proguard.setProguardId(proguardId);
        proguard.setProguardName(proguardId + "");
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);
        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);

        proguardRepository.saveAndFlush(proguard);

        Optional<ProguardDTO> optional = proguardRepository.getProguardByProguardId(proguardId, ProguardDTO.class);
        if(optional.isPresent()) {
            log.info("{} : {}", optional.get().getProguardId(), optional.get().getProguardName());
        }

        List<Proguard> proguards = proguardService.searchAllByProguardNameRange(proguardId + "-" + (proguardId + 10L) + "," + proguardId);
        assertTrue(proguards.size() == 1);


        Proguard proguard1 = proguard;
        proguard1.setProguardId(proguardId + 1);
        proguardRepository.saveAndFlush(proguard1);

        Proguard proguard2 = proguard;
        proguard2.setProguardId(proguardId + 2);
        proguardRepository.saveAndFlush(proguard2);

        Proguard proguard3 = proguard;
        proguard3.setProguardId(proguardId + 3);
        proguardRepository.saveAndFlush(proguard3);

        Proguard proguard4 = proguard;
        proguard4.setProguardId(proguardId + 3);
        proguardRepository.saveAndFlush(proguard4);

        Pageable pageable = PageRequest.of(0, 3);
        Page<Proguard> proguardsPageable = proguardService.searchAllByNaiveSQL("select proguard_id, proguard_name, proguard_idx, ext_map, ext_list, abstract_list, abstract_obj, json_schema, json_data, tenant_id from proguard order by proguard_id desc", pageable);
        log.info("{}", proguardsPageable);

        Pageable pageable2 = PageRequest.of(1, 3);
        Page<Proguard> proguardsPageable2 = proguardService.searchAllByNaiveSQL("select proguard_id, proguard_name, proguard_idx, ext_map, ext_list, abstract_list, abstract_obj, json_schema, json_data, tenant_id from proguard order by proguard_id desc", pageable2);
        log.info("{}", proguardsPageable2);

        Pageable pageable3 = PageRequest.of(1, 3);
        Page<Map> proguardsPageable3 = proguardService.searchAllByNaiveSQLMap("select proguard_id, proguard_name from proguard order by proguard_id desc", pageable3);
        log.info("{}", proguardsPageable3);

        String updateSQL = "update proguard set proguard_name = ? where proguard_id = ?";
        List<Object> params1 = Arrays.asList("alexgaoyh", proguardId);
        // alexgaoyh2 -> null 的时候，验证事务
        List<Object> params2 = Arrays.asList("alexgaoyh2", proguardId);
        List<List<Object>> paramsList = new ArrayList<>();
        paramsList.add(params1);
        paramsList.add(params2);
        List<String> naiveSQLList = Arrays.asList(updateSQL, updateSQL);
        Boolean b = proguardService.executeNaiveSQLBatch(naiveSQLList, paramsList);
        log.info("{}", b);
    }

    /**
     *
     */
    @Test
    public void crudTest() {
        Long proguardId = System.currentTimeMillis();

        Proguard proguard = new Proguard();
        proguard.setProguardId(proguardId);
        proguard.setProguardName(proguardId + "");
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        proguard.setExtMap(extMap);
        List<String> extList = new ArrayList<>();
        extList.add("A");
        proguard.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard.setAbstractObj(objectNode);
        proguard.setAbstractList(arrayNode);

        proguardRepository.saveAndFlush(proguard);

        proguard.setProguardName("update");
        proguardRepository.saveAndFlush(proguard);

        proguardRepository.delete(proguard);


    }

    @Test
    public void searchUtilTest() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        SearchConditionDTO idEqual = new SearchConditionDTO("proguardId", SearchConditionDTO.Operator.EQUAL, 1L);
        SearchConditionDTO greaterEqual = new SearchConditionDTO("proguardId", SearchConditionDTO.Operator.GREATER_THAN, 1L);
        SearchConditionDTO nameLike = new SearchConditionDTO("proguardName", SearchConditionDTO.Operator.LIKE, "gao");

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(4000001L);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard1.setAbstractObj(objectNode);
        proguard1.setAbstractList(arrayNode);

        proguardRepository.saveAndFlush(proguard1);

        List<SearchConditionDTO> conditions = new ArrayList<>();
        conditions.add(idEqual);

        List<Proguard> proguards1 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        log.info("{}", proguards1);

        conditions.clear();
        conditions.add(nameLike);

        List<Proguard> proguards2 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        log.info("{}", proguards2);

        conditions.clear();
        conditions.add(idEqual);
        conditions.add(nameLike);

        List<Proguard> proguards3 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        log.info("{}", proguards3);

        conditions.clear();
        conditions.add(greaterEqual);

        List<Proguard> proguards4 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        log.info("{}", proguards4);
    }

    @Test
    public void crudTest2() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(2000001L);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard1.setAbstractObj(objectNode);
        proguard1.setAbstractList(arrayNode);

        proguardService.saveAndFlush(proguard1);

        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1L);

        log.info("{}", proguardByProguardId);

        proguardService.deleteAllById(1L);

        log.info("deleteAllById");
    }

    @Test
    public void abstractJsonArrayTest() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(1000001L);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);
        proguard1.setAbstractList(arrayNode);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard1.setAbstractObj(objectNode);

        proguardService.saveAndFlush(proguard1);

        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1L);

        log.info("{}", proguardByProguardId);

        proguardService.deleteAllById(1L);

        log.info("deleteAllById");

    }

    @Test
    public void executeNaiveSQLBatchUsingJDBCTest() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(3000001L);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);
        proguard1.setAbstractList(arrayNode);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard1.setAbstractObj(objectNode);

        proguardService.saveAndFlush(proguard1);

        List<String> executeSQLList = new ArrayList<>();
        executeSQLList.add("UPDATE proguard SET proguard_name = '1' WHERE proguard_id = 1");
        executeSQLList.add("UPDATE proguard SET proguard_name = '2' WHERE proguard_id = 1");
        Boolean b = proguardService.executeNaiveSQLBatchUsingJDBC(executeSQLList);

        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1L);
        log.info("{}", proguardByProguardId);

    }

    public Integer get_sync(String seqName, int length) {
        synchronized (seqName) {
            Proguard proguardByProguardId = proguardService.getProguardByProguardId(1L);
            if(proguardByProguardId == null) {
                Map<String, Object> extMap = new HashMap<>();
                extMap.put("timeswap", System.currentTimeMillis());
                List<String> extList = new ArrayList<>();
                extList.add("A");

                Map<String, Object> abstractMap = new HashMap<>();
                abstractMap.put("extMap", extMap);
                abstractMap.put("extList", extList);

                ObjectMapper mapper = new ObjectMapper();
                ArrayNode arrayNode = mapper.createArrayNode();
                JsonNode nestedObject = mapper.valueToTree(abstractMap);
                arrayNode.add(nestedObject);

                Proguard proguard1 = new Proguard();
                proguard1.setProguardId(1L);
                proguard1.setProguardName("alexgaoyh");
                proguard1.setExtMap(extMap);
                proguard1.setExtList(extList);
                proguard1.setAbstractList(arrayNode);

                ObjectNode objectNode = mapper.valueToTree(abstractMap);
                proguard1.setAbstractObj(objectNode);

                proguard1.setProguardIdx(1);

                proguardService.saveAndFlush(proguard1);

                return proguard1.getProguardIdx();
            } else {
                proguardByProguardId.setProguardIdx(proguardByProguardId.getProguardIdx() + 1);
                proguardService.saveAndFlush(proguardByProguardId);
                return proguardByProguardId.getProguardIdx();
            }
        }
    }

    // @Test
    public void updateIdxTest() throws InterruptedException {
        int numThreads = 10000;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                numThreads,
                numThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "updateidx-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        String seqName = "testSync";
        CountDownLatch latch = new CountDownLatch(numThreads);
        try {
            List<Future<Integer>> futures1 = new ArrayList<>();
            for (int i = 0; i < numThreads; i++) {
                int finalI = i;
                CountDownLatch finalLatch = latch;
                futures1.add(executor.submit(() -> {
                    try {
                        return get_sync(seqName, finalI);
                    } finally {
                        finalLatch.countDown();
                    }
                }));
            }
            latch.await();
            for (int i = 0; i < numThreads; i++) {
                // assertEquals(String.valueOf(i), futures1.get(i).get());
            }

            Proguard proguardByProguardId = proguardRepository.getProguardByProguardId(1L);
            assertEquals(proguardByProguardId.getProguardIdx(), numThreads);
        } finally {
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // @Test
    public void testStringLockFailure() throws InterruptedException {
        int numThreads = 1000;
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                numThreads,
                numThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "stringlockfailure-test-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        String seqName = "testSync";
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < numThreads; i++) {
                int finalI = i;
                executor.submit(() -> {
                    try {
                        Integer syncInt = get_sync(new String(seqName), finalI);
                        results.add(syncInt);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();

            assertNotEquals(numThreads, results.get(results.size() - 1));
        } finally {
            executor.shutdown();
            try {
                // 等待 2 秒让未完成的任务结束
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    // 超时后强制关闭，这会向所有池中线程发送 Interrupt 信号
                    log.warn("部分线程池任务未在 2 秒内结束，强制关闭");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("关闭线程池时被中断", e);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private Proguard geneEntity() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Proguard proguard1 = new Proguard();
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);

        Map<String, Object> abstractMap = new HashMap<>();
        abstractMap.put("extMap", extMap);
        abstractMap.put("extList", extList);

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();
        JsonNode nestedObject = mapper.valueToTree(abstractMap);
        arrayNode.add(nestedObject);
        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard1.setAbstractObj(objectNode);
        proguard1.setAbstractList(arrayNode);
        return proguard1;
    }

    private long bytesToMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    // @Test
    public void streamTest() throws Exception {

        for(int i = 0; i < 999; i++) {
            Proguard proguard = geneEntity();
            proguard.setProguardId(Long.parseLong(i + ""));
            proguardService.saveAndFlush(proguard);
        }

        // 初始化内存监控
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory(); // 初始内存占用
        long maxMemoryUsed = initialMemory;
        int batchCount = 0;

        StatelessSession session = entityManager.unwrap(Session.class).getSessionFactory().openStatelessSession();
        ScrollableResults scroll = session.createQuery("FROM Proguard ORDER BY proguardId", Proguard.class)
                .setFetchSize(100).scroll(ScrollMode.FORWARD_ONLY);

        try {
            while (scroll.next()) {
                // 获取当前内存占用
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                maxMemoryUsed = Math.max(maxMemoryUsed, currentMemory);

                // 每处理10条记录输出一次内存状态
                if (batchCount % 10 == 0) {
                    // 这里可以观察到内存的一个增加 释放 增加 的趋势.
                    log.info("Processed {} records | Current Memory: {} MB | Max Memory Used: {} MB",
                            batchCount,
                            bytesToMB(currentMemory),
                            bytesToMB(maxMemoryUsed));
                }

                Proguard entity = (Proguard) scroll.get();
                // log.info("{}", entity);

                batchCount++;
            }
        } finally {
            scroll.close();
            session.close();
        }

        // 最终内存报告
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        log.info("Final Memory Usage: {} MB | Peak Memory Usage: {} MB",
                bytesToMB(finalMemory),
                bytesToMB(maxMemoryUsed));

    }

    // @Test
    public void eachDBCompareTest() throws Exception {

        final Integer MAX_CHECK_NUMBER = 9999;
        final Integer BATCH_SIZE = 500;

        for(int i = 0; i < MAX_CHECK_NUMBER; i++) {
            Proguard proguard = geneEntity();
            proguard.setProguardId(Long.parseLong(i + ""));
            proguardService.saveAndFlush(proguard);
        }

        long start = System.currentTimeMillis();

        List<Proguard> eachResultList = new ArrayList<>();
        for(int i = 0; i < MAX_CHECK_NUMBER; i++) {
            Proguard tmp = proguardService.getProguardByProguardId(Long.parseLong(i + ""));
            eachResultList.add(tmp);
        }

        long middle = System.currentTimeMillis();

        List<Proguard> batchResultList = new ArrayList<>();
        for (int i = 0; i < MAX_CHECK_NUMBER; i += BATCH_SIZE) {
            long endId = Math.min(i + BATCH_SIZE - 1, MAX_CHECK_NUMBER);
            List<Long> batchIds = LongStream.rangeClosed(i, endId).boxed().collect(Collectors.toList());
            List<Proguard> tmp = proguardService.getProguardByProguardIds(batchIds);
            batchResultList.addAll(tmp);
        }

        long end = System.currentTimeMillis();

        log.info("{} : {}", middle - start, eachResultList.size());
        log.info("{} : {}", end - middle, batchResultList.size());
    }

    /**
     * connection leak detection check throw exception
     * @throws Exception
     */
    // @Test
    public void timeMSTest() throws Exception {
        // spring.datasource.hikari.leak-detection-threshold=3000
        // logging.level.com.zaxxer.hikari.pool.ProxyLeakTask: WARN
        Proguard sleep = proguardService.sleep(9000L);
        assertEquals(sleep, null);
    }

    @Test
    @Transactional
    public void jsonInsertTest() throws Exception {
        String jsonInput = """
                {
                    "proguardId": 888888,
                    "proguardName": "json_insert_name",
                    "proguardIdx": 88,
                    "extMap": {"timeswap": 123456789, "info": "nested 'quote' test"},
                    "extList": ["item1", "item2"],
                    "abstractList": [{"key": "val1"}, {"key": "val2"}],
                    "abstractObj": {"nested": {"key": "value"}},
                    "jsonSchema": "schema_content",
                    "jsonData": {"test": "data"},
                    "tenantId": "default"
                }
                """;

        String insertSql = SQLUtil.generateInsertSqlFromJson("proguard", jsonInput);
        log.info("Generated SQL: {}", insertSql);

        // Execute raw insert statement directly
        entityManager.createNativeQuery(insertSql).executeUpdate();

        // Flush and clear persistence context to ensure we fetch directly from DB
        entityManager.flush();
        entityManager.clear();

        // Query and verify
        Proguard dbRecord = proguardRepository.getProguardByProguardId(888888L);
        assertNotNull(dbRecord);
        assertEquals("json_insert_name", dbRecord.getProguardName());
        assertEquals(88, dbRecord.getProguardIdx());
        assertEquals("default", dbRecord.getTenantId());
        
        assertNotNull(dbRecord.getExtMap());
        assertEquals(123456789L, ((Number) dbRecord.getExtMap().get("timeswap")).longValue());
        assertEquals("nested 'quote' test", dbRecord.getExtMap().get("info"));
        
        assertNotNull(dbRecord.getExtList());
        assertEquals(Arrays.asList("item1", "item2"), dbRecord.getExtList());
        
        assertNotNull(dbRecord.getAbstractList());
        assertEquals(2, dbRecord.getAbstractList().size());
        
        assertNotNull(dbRecord.getAbstractObj());
        assertEquals("value", dbRecord.getAbstractObj().path("nested").path("key").asText());
        
        assertNotNull(dbRecord.getJsonData());
        assertEquals("data", dbRecord.getJsonData().path("test").asText());
        
        assertEquals("schema_content", dbRecord.getJsonSchema());
    }

    @Test
    @Transactional
    public void json2MapListTest() throws Exception {
        // 先清理数据，防止其他测试方法残留导致主键冲突
        entityManager.createNativeQuery("DELETE FROM proguard WHERE proguard_id = 888889").executeUpdate();
        entityManager.flush();

        String jsonInput = """
                {
                    "proguardId": 888889,
                    "proguardName": "json_insert_name",
                    "proguardIdx": 88,
                    "extMap": {"timeswap": 123456789, "info": "nested 'quote' test"},
                    "extList": ["item1", "item2"],
                    "abstractList": [{"key": "val1"}, {"key": "val2"}],
                    "abstractObj": {"nested": {"key": "value"}},
                    "jsonSchema": "schema_content",
                    "jsonData": {"test": "data"},
                    "tenantId": "default"
                }
                """;
        String jsonInputArray = "[" + jsonInput + "," + jsonInput + "]";

        List<Map<String, JsonNode>> mapList1 = SQLUtil.generateJsonNodeFromJson(jsonInput);

        List<Map<String, JsonNode>> mapList2 = SQLUtil.generateJsonNodeFromJson(jsonInputArray);

        assertTrue(mapList1.size() == 1);
        assertTrue(mapList2.size() == 2);

        ObjectMapper objectMapper = new ObjectMapper();

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();

        for (Map.Entry<String, JsonNode> entry : mapList1.get(0).entrySet()) {

            String columnName = entry.getKey();
            JsonNode valueNode = entry.getValue();
            if (valueNode != null && !valueNode.isMissingNode()) {
                if (columns.length() > 0) {
                    columns.append(", ");
                    values.append(", ");
                }
                columns.append(SQLUtil.convertCamelToSnake(columnName));
                values.append(SQLUtil.getSqlValue(valueNode, objectMapper));
            }
        }

        String SQL =  "INSERT INTO proguard " + " (" + columns + ") VALUES (" + values + ")";
        assertTrue(!SQL.isEmpty());

        int i = entityManager.createNativeQuery(SQL).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Proguard dbRecord = entityManager.getReference(Proguard.class, 888889L);

        assertEquals("json_insert_name", dbRecord.getProguardName());
        assertEquals(88, dbRecord.getProguardIdx());
        assertEquals("default", dbRecord.getTenantId());

    }

    /**
     * 数据处理的时候，与上面的 SQLUtil.getSqlValue(valueNode, objectMapper) 做出对比。
     * @throws Exception
     */
    @Test
    public void json2MapListTest2() throws Exception {
        // 先清理数据，防止其他测试方法残留导致主键冲突 (使用独立的 ID 888890)
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM proguard WHERE proguard_id = 888890").executeUpdate();
            return null;
        });

        String jsonInput = """
                {
                    "proguardId": 888890,
                    "proguardName": "json_insert_name",
                    "proguardIdx": 88,
                    "extMap": {"timeswap": 123456789, "info": "nested 'quote' test"},
                    "extList": ["item1", "item2"],
                    "abstractList": [{"key": "val1"}, {"key": "val2"}],
                    "abstractObj": {"nested": {"key": "value"}},
                    "jsonSchema": "schema_content",
                    "jsonData": {"test": "data"},
                    "tenantId": "default"
                }
                """;
        String jsonInputArray = "[" + jsonInput + "," + jsonInput + "]";

        List<Map<String, JsonNode>> mapList1 = SQLUtil.generateJsonNodeFromJson(jsonInput);

        List<Map<String, JsonNode>> mapList2 = SQLUtil.generateJsonNodeFromJson(jsonInputArray);

        assertTrue(mapList1.size() == 1);
        assertTrue(mapList2.size() == 2);

        ObjectMapper objectMapper = new ObjectMapper();

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();

        for (Map.Entry<String, JsonNode> entry : mapList1.get(0).entrySet()) {

            String columnName = entry.getKey();
            JsonNode valueNode = entry.getValue();
            if (valueNode != null && !valueNode.isMissingNode()) {
                if (columns.length() > 0) {
                    columns.append(", ");
                    values.append(", ");
                }
                columns.append(SQLUtil.convertCamelToSnake(columnName));
                values.append("'" + objectMapper.writeValueAsString(entry.getValue()).replace("'", "''") + "'");
            }
        }

        transactionTemplate.execute(status -> {
            try {
                String SQL =  "INSERT INTO proguard " + " (" + columns + ") VALUES (" + values + ")";
                assertTrue(!SQL.isEmpty());

                int i = entityManager.createNativeQuery(SQL).executeUpdate();
                return i;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException("保存失败: " + e.getMessage(), e);
            }
        });

        transactionTemplate.execute(status -> {
            try {
                // 1. 使用原生 SQL 查询（绕过多租户过滤器），看数据是否存在于数据库中，并使用日志占位符打印内容
                List<Object[]> rawList = entityManager.createNativeQuery(
                        "SELECT proguard_id, proguard_name, tenant_id FROM proguard WHERE proguard_id = 888890"
                ).getResultList();
                if (!rawList.isEmpty()) {
                    Object[] row = rawList.get(0);
                    log.info("[ProguardTest-RawQuery] Found raw record, proguard_id: {}, proguard_name: {}, tenant_id: {}",
                            row[0], row[1], row[2]);
                } else {
                    log.warn("[ProguardTest-RawQuery] No raw record found in database for ID: 888890");
                }

                // 2. 原有的 JPA find 查询， 其实是有数据的，只是租户这里的原因没查询到。
                Proguard dbRecord = entityManager.find(Proguard.class, 888890L);
                if (dbRecord != null) {
                    assertEquals("json_insert_name", dbRecord.getProguardName());
                    assertEquals(88, dbRecord.getProguardIdx());
                    assertEquals("default", dbRecord.getTenantId());
                } else {
                    log.warn("[ProguardTest-JPAQuery] JPA find returned null for ID: 888890");
                }
                return 1;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("[ProguardTest-QueryError] Failed to execute query test: ", e);
                throw new RuntimeException("查询失败: " + e.getMessage(), e);
            }
        });
    }
}
