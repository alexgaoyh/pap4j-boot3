package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.entity.TempQuery;
import cn.net.pap.example.proguard.repository.AutoIncrePreKeyRepository;
import cn.net.pap.example.proguard.service.ITempQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class TempQueryTest {

    @Autowired
    private ITempQueryService tempQueryService;

    @Autowired
    private AutoIncrePreKeyRepository autoIncrePreKeyRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    public void testBatchInsertAndQuery() {
        String bizType = "ORDER";

        tempQueryService.batchInsert(bizType, Arrays.asList(1L, 2L, 3L, 4L));

        List<TempQuery> list = tempQueryService.listByBizType(bizType);

        assertThat(list).hasSize(4);
        assertThat(list).extracting(TempQuery::getId).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    public void testExistsAndCount() {
        String bizType = "USER";

        tempQueryService.batchInsert(bizType, Arrays.asList(10L, 20L));

        assertThat(tempQueryService.exists(bizType, 10L)).isTrue();
        assertThat(tempQueryService.exists(bizType, 30L)).isFalse();
        assertThat(tempQueryService.countByBizType(bizType)).isEqualTo(2);
    }

    @Test
    public void testDeleteByBizType() {
        String bizType = "PRODUCT";

        tempQueryService.batchInsert(bizType, Arrays.asList(100L, 200L));

        int deleted = tempQueryService.deleteByBizType(bizType);

        assertThat(deleted).isEqualTo(2);
        assertThat(tempQueryService.countByBizType(bizType)).isZero();
    }

    @Test
    public void testIdRangeQuery() {
        tempQueryService.batchInsert("A", Arrays.asList(1L, 2L, 3L));
        tempQueryService.batchInsert("B", Arrays.asList(10L, 20L));

        List<TempQuery> list = tempQueryService.listByIdRange(2L, 15L);

        assertThat(list).extracting(TempQuery::getId).containsExactlyInAnyOrder(2L, 3L, 10L);
    }

    /**
     *
     */
    @Test
    public void testInClause() {
        Integer totalNumber = 9999;
        Integer searchNumber = 90;

        // 插入主表大量数据
        List<AutoIncrePreKey> all = new ArrayList<>();
        for (int i = 1; i <= totalNumber; i++) {
            AutoIncrePreKey e = new AutoIncrePreKey();
            e.setName("name-" + i);
            all.add(e);
        }
        autoIncrePreKeyRepository.saveAll(all);

        // 构造“IN 条件”等价的数据  写入临时表
        String bizType = "TEST_BIZ";
        List<Long> targetIds = new ArrayList<>();
        for (int i = 1; i <= totalNumber; i++) {
            targetIds.add(all.get(i - 1).getId());
        }
        for (Long id : targetIds) {
            try {
                tempQueryService.save(new TempQuery(bizType, id));
            } catch (Exception ignored) {
            }
        }

        List<AutoIncrePreKey> result = autoIncrePreKeyRepository.findByTempQueryBizType(bizType);
        assertThat(result).hasSize(targetIds.size());
        List<Long> resultIds = result.stream().map(AutoIncrePreKey::getId).toList();
        assertThat(resultIds).containsExactlyInAnyOrderElementsOf(targetIds);

        // ====== 预热（非常重要）======
        autoIncrePreKeyRepository.findByTempQueryBizType(bizType);
        autoIncrePreKeyRepository.findByIdIn(targetIds);

        // ====== IN 查询 ======
        long inTotal = 0;
        for (int i = 0; i < searchNumber; i++) {
            em.clear();
            long start = System.nanoTime();
            List<AutoIncrePreKey> inResult =
                    autoIncrePreKeyRepository.findByIdIn(targetIds);
            long cost = System.nanoTime() - start;
            inTotal += cost;

            assertThat(inResult).hasSize(targetIds.size());
        }

        // ====== 临时表查询 ======
        long tempTotal = 0;
        for (int i = 0; i < searchNumber; i++) {
            em.clear();
            long start = System.nanoTime();
            List<AutoIncrePreKey> tempResult =
                    autoIncrePreKeyRepository.findByTempQueryBizType(bizType);
            long cost = System.nanoTime() - start;
            tempTotal += cost;

            assertThat(tempResult).hasSize(targetIds.size());
        }

        System.out.println("IN avg(ms): " + inTotal / 5 / 1_000_000);
        System.out.println("TEMP avg(ms): " + tempTotal / 5 / 1_000_000);

    }

    @Test
    public void testTempQueryBatch() {
        Integer totalNumber = 9999;

        String bizType = "testTempQueryBatch";
        List<Long> targetIds = new ArrayList<>();
        for (int i = 1; i <= totalNumber; i++) {
            targetIds.add(Long.parseLong(i + ""));
        }
        tempQueryService.batchInsert(bizType, targetIds);
        assertThat(tempQueryService.countByBizType(bizType)).isEqualTo(Long.parseLong(totalNumber + ""));
    }

    @Test
    public void testTempQueryStyle() {
        Integer totalNumber = 9999;

        // 插入主表大量数据
        List<AutoIncrePreKey> all = new ArrayList<>();
        for (int i = 1; i <= totalNumber; i++) {
            AutoIncrePreKey e = new AutoIncrePreKey();
            e.setName("name-" + i);
            all.add(e);
        }
        autoIncrePreKeyRepository.saveAll(all);

        // 构造“IN 条件”等价的数据，封装这个数据。
        String bizType = "testTempQueryStyle";
        List<Long> targetIds = new ArrayList<>();
        for (int i = 1; i <= totalNumber; i++) {
            targetIds.add(all.get(i - 1).getId());
        }

        List<AutoIncrePreKey> result =
                tempQueryService.executeWithTempQuery(
                        bizType,
                        targetIds,
                        () -> autoIncrePreKeyRepository.findByTempQueryBizType(bizType)
                );

        assertThat(result).hasSize(targetIds.size());
    }


}
