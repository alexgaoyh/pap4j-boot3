package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.dto.ProguardDTO;
import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class ProguardTest {

    @Autowired
    ProguardRepository proguardRepository;

    @Autowired
    IProguardService proguardService;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void transTest() {
        Long proguardId = System.currentTimeMillis();

        PlatformTransactionManager transactionManager = SpringUtils.getBean(PlatformTransactionManager.class);

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
        if(proguardDB != null) {
            System.out.println(proguardDB.getProguardId() + " : " + proguardDB.getProguardName());
        } else {
            System.out.println("-------------rollback-----------------");
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
            System.out.println(optional.get().getProguardId() + " : " + optional.get().getProguardName());
        }

        List<Proguard> proguards = proguardService.searchAllByProguardNameRange(proguardId + "-" + (proguardId + 10l) + "," + proguardId);
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
        Page<Proguard> proguardsPageable = proguardService.searchAllByNaiveSQL("select * from proguard order by proguard_id desc", pageable);
        System.out.println(proguardsPageable);

        Pageable pageable2 = PageRequest.of(1, 3);
        Page<Proguard> proguardsPageable2 = proguardService.searchAllByNaiveSQL("select * from proguard order by proguard_id desc", pageable2);
        System.out.println(proguardsPageable2);

        Pageable pageable3 = PageRequest.of(1, 3);
        Page<Map> proguardsPageable3 = proguardService.searchAllByNaiveSQLMap("select proguard_id, proguard_name from proguard order by proguard_id desc", pageable3);
        System.out.println(proguardsPageable3);

        String updateSQL = "update proguard set proguard_name = ? where proguard_id = ?";
        List<Object> params1 = Arrays.asList("alexgaoyh", proguardId);
        // alexgaoyh2 -> null 的时候，验证事务
        List<Object> params2 = Arrays.asList("alexgaoyh2", proguardId);
        List<List<Object>> paramsList = new ArrayList<>();
        paramsList.add(params1);
        paramsList.add(params2);
        List<String> naiveSQLList = Arrays.asList(updateSQL, updateSQL);
        Boolean b = proguardService.executeNaiveSQLBatch(naiveSQLList, paramsList);
        System.out.println(b);
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

        SearchConditionDTO idEqual = new SearchConditionDTO("proguardId", SearchConditionDTO.Operator.EQUAL, 1l);
        SearchConditionDTO greaterEqual = new SearchConditionDTO("proguardId", SearchConditionDTO.Operator.GREATER_THAN, 1l);
        SearchConditionDTO nameLike = new SearchConditionDTO("proguardName", SearchConditionDTO.Operator.LIKE, "gao");

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(1l);
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
        System.out.println(proguards1);

        conditions.clear();
        conditions.add(nameLike);

        List<Proguard> proguards2 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards2);

        conditions.clear();
        conditions.add(idEqual);
        conditions.add(nameLike);

        List<Proguard> proguards3 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards3);

        conditions.clear();
        conditions.add(greaterEqual);

        List<Proguard> proguards4 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards4);
    }

    @Test
    public void crudTest2() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(1l);
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

        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1l);

        System.out.println(proguardByProguardId);

        proguardService.deleteAllById(1l);

        System.out.println("deleteAllById");
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
        proguard1.setProguardId(1l);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);
        proguard1.setAbstractList(arrayNode);

        ObjectNode objectNode = mapper.valueToTree(abstractMap);
        proguard1.setAbstractObj(objectNode);

        proguardService.saveAndFlush(proguard1);

        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1l);

        System.out.println(proguardByProguardId);

        proguardService.deleteAllById(1l);

        System.out.println("deleteAllById");

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
        proguard1.setProguardId(1l);
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

        Proguard proguardByProguardId = proguardService.getProguardByProguardId(1l);
        System.out.println(proguardByProguardId);

    }

    public Integer get_sync(String seqName, int length) {
        synchronized (seqName) {
            Proguard proguardByProguardId = proguardService.getProguardByProguardId(1l);
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
                proguard1.setProguardId(1l);
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
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        String seqName = "testSync";
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Future<Integer>> futures1 = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            int finalI = i;
            CountDownLatch finalLatch = latch;
            futures1.add(executorService.submit(() -> {
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
        executorService.shutdown();

        Proguard proguardByProguardId = proguardRepository.getProguardByProguardId(1l);
        assertEquals(proguardByProguardId.getProguardIdx(), numThreads);
    }

    // @Test
    public void testStringLockFailure() throws InterruptedException {
        int numThreads = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        String seqName = "testSync";
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            int finalI = i;
            executorService.submit(() -> {
                try {
                    Integer syncInt = get_sync(new String(seqName), finalI);
                    results.add(syncInt);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertNotEquals(numThreads, results.get(results.size() - 1));
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
                    System.out.printf("Processed %d records | Current Memory: %d MB | Max Memory Used: %d MB%n",
                            batchCount,
                            bytesToMB(currentMemory),
                            bytesToMB(maxMemoryUsed));
                }

                Proguard entity = (Proguard) scroll.get();
                // System.out.println(entity);

                batchCount++;
            }
        } finally {
            scroll.close();
            session.close();
        }

        // 最终内存报告
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("Final Memory Usage: %d MB | Peak Memory Usage: %d MB%n",
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

        System.out.println(middle - start + " : " + eachResultList.size());
        System.out.println(end - middle + " : " + batchResultList.size());
    }

}
