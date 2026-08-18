package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.dto.SplitRecordDTO;
import cn.net.pap.example.proguard.exception.SplitRecordException;
import cn.net.pap.example.proguard.service.ISplitRecordCrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分表记录读写集成测试（v1：建表 / 保存 / 单条查询）。
 *
 * <p>方法级 {@code @Transactional} 回滚隔离：H2 内存库 DDL 也随事务回滚，无孤儿表；
 * 普通 Session 同事务可读，{@code save → get} 往返成立。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:${random.uuid};DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Transactional
public class SplitRecordCrudTest {

    private final ISplitRecordCrudService splitRecordCrudService;
    private final ObjectMapper objectMapper;

    public SplitRecordCrudTest(ISplitRecordCrudService splitRecordCrudService, ObjectMapper objectMapper) {
        this.splitRecordCrudService = splitRecordCrudService;
        this.objectMapper = objectMapper;
    }

    /**
     * 用例 1：建表幂等 → save → get 往返一致，ext 列入库值正确（字符串/数值）。
     */
    @Test
    public void createTableIdempotent_thenSaveThenGet_roundTrip() throws Exception {
        String table = "pap_business_order";
        splitRecordCrudService.createTableIfNotExists(table);
        splitRecordCrudService.createTableIfNotExists(table); // 幂等

        SplitRecordDTO dto = new SplitRecordDTO();
        dto.setData(objectMapper.writeValueAsString(orderData()));
        dto.setExtStr1("PAID");
        dto.setExtStr2("alexgaoyh 客户");
        dto.setExtNum1(new BigDecimal("99.50"));

        Long id = splitRecordCrudService.save(table, dto);
        assertNotNull(id, "save 应返回自增 id");
        assertEquals(id, dto.getId(), "自增 id 应回填到 dto.id");

        SplitRecordDTO got = splitRecordCrudService.get(table, id);
        assertNotNull(got);
        assertEquals("PAID", got.getExtStr1());
        assertEquals("alexgaoyh 客户", got.getExtStr2());
        assertTrue(new BigDecimal("99.50").compareTo(got.getExtNum1()) == 0, "数值应正确往返");
        assertEquals(dto.getData(), got.getData(), "权威 data 应原样往返");
    }

    /**
     * 用例 2：自增 id 递增且不同。
     */
    @Test
    public void save_returnsIncrementalAutoIncrementId() {
        String table = "pap_business_student";
        splitRecordCrudService.createTableIfNotExists(table);

        Long id1 = splitRecordCrudService.save(table, plainDto("{}"));
        Long id2 = splitRecordCrudService.save(table, plainDto("{}"));

        assertNotEquals(id1, id2, "两次 save 的 id 必须不同");
        assertTrue(id2 > id1, "id 应递增");
    }

    /**
     * 用例 3：SQL 注入防护——非法表名被拒绝（抛业务异常，不进 SQL）。
     */
    @Test
    public void sqlInjectionRejected() {
        assertThrows(SplitRecordException.class,
                () -> splitRecordCrudService.createTableIfNotExists("foo; DROP TABLE proguard"),
                "带分隔符的表名必须被拒绝");

        assertThrows(SplitRecordException.class,
                () -> splitRecordCrudService.save("foo; DROP TABLE proguard", plainDto("{}")),
                "save 的非法表名必须被拒绝");

        assertThrows(SplitRecordException.class,
                () -> splitRecordCrudService.get("foo; DROP TABLE proguard", 1L),
                "get 的非法表名必须被拒绝");
    }

    /**
     * 用例 4：跨表独立性——两表同 id 各写不同数据，get 各取各表（无实体、无身份 map，天然隔离）。
     */
    @Test
    public void sameIdAcrossTablesIndependent() {
        String tableA = "pap_business_a";
        String tableB = "pap_business_b";
        splitRecordCrudService.createTableIfNotExists(tableA);
        splitRecordCrudService.createTableIfNotExists(tableB);

        SplitRecordDTO a = new SplitRecordDTO();
        a.setData("{\"src\":\"A\"}");
        a.setExtStr1("table-a-data");
        Long idA = splitRecordCrudService.save(tableA, a);

        SplitRecordDTO b = new SplitRecordDTO();
        b.setData("{\"src\":\"B\"}");
        b.setExtStr1("table-b-data");
        Long idB = splitRecordCrudService.save(tableB, b);

        assertEquals(idA, idB, "两张新表各自从 id=1 开始");

        SplitRecordDTO gotA = splitRecordCrudService.get(tableA, 1L);
        SplitRecordDTO gotB = splitRecordCrudService.get(tableB, 1L);
        assertEquals("table-a-data", gotA.getExtStr1());
        assertEquals("table-b-data", gotB.getExtStr1());
        assertNotEquals(gotA.getData(), gotB.getData(), "同 id 跨表不得串数据");
    }

    // ---------------------------------------------------------------- 测试辅助

    private Map<String, Object> orderData() {
        Map<String, Object> map = new HashMap<>();
        map.put("orderNo", "ORD-20260801-001");
        map.put("customer", "alexgaoyh 客户");
        map.put("status", "PAID");
        map.put("amount", 99.50);
        map.put("orderDate", "2026-08-01");
        return map;
    }

    private SplitRecordDTO plainDto(String data) {
        SplitRecordDTO dto = new SplitRecordDTO();
        dto.setData(data);
        return dto;
    }
}
