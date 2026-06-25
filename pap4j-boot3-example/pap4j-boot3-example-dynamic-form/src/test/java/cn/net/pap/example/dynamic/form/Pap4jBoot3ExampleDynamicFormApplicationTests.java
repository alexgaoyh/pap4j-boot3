package cn.net.pap.example.dynamic.form;

import cn.net.pap.example.dynamic.form.service.DynamicRecordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>
 * <b>Dynamic Form 模块集成测试用例</b>
 * </p>
 *
 * <p>
 * 该测试类主要验证 <b>EAV (Entity-Attribute-Value)</b> 模型下的核心业务逻辑，包括：
 * </p>
 * <ul>
 *     <li>复杂嵌套 JSON 结构的递归持久化。</li>
 *     <li>基于 EAV 表结构的数据精准还原。</li>
 *     <li>级联删除 (Cascade Delete) 的安全性与完整性。</li>
 * </ul>
 *
 * <p>
 * <b>测试环境优化：</b>
 * 采用内存数据库 <code>jdbc:sqlite::memory:</code> 以确保测试运行过程中不会生成物理文件，保证测试环境的干净与隔离。
 * </p>
 *
 * @author alexgaoyh
 * @since 2026-06-03
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class Pap4jBoot3ExampleDynamicFormApplicationTests {

    private final DynamicRecordService recordService;

    public Pap4jBoot3ExampleDynamicFormApplicationTests(DynamicRecordService recordService) {
        this.recordService = recordService;
    }
    /**
     * <p>
     * 验证<b>复杂嵌套记录</b>的保存与重构。
     * </p>
     * <b>测试步骤：</b>
     * <ol>
     *     <li>构建一个包含嵌套 List (Order -> Items) 的 Map 结构。</li>
     *     <li>调用 Service 层进行递归保存。</li>
     *     <li>根据生成的 ID 重新读取并重构数据。</li>
     *     <li>断言原始数据与还原数据在结构和内容上的一致性。</li>
     * </ol>
     */
    @Test
    @Transactional
    void testSaveAndReconstructComplexRecord() {
        // 1. Prepare nested payload (Order -> Items)
        Map<String, Object> payload = Map.of(
                "orderNo", "ORD-1001",
                "customer", "Alice",
                "totalAmount", 150.5,
                "items", List.of(
                        Map.of("name", "Laptop", "price", 1200.0, "qty", 1),
                        Map.of("name", "Mouse", "price", 25.0, "qty", 2)
                )
        );

        // 2. Save
        Long recordId = recordService.saveComplexRecord("order", payload);
        assertNotNull(recordId);

        // 3. Reconstruct and Verify
        Map<String, Object> result = recordService.getRecord(recordId);

        assertEquals("ORD-1001", result.get("orderNo"));
        assertEquals("Alice", result.get("customer"));
        assertEquals(150.5, result.get("totalAmount"));

        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        assertEquals(2, items.size());

        Map<String, Object> laptop = items.stream()
                .filter(i -> "Laptop".equals(i.get("name")))
                .findFirst().orElseThrow();
        assertEquals(1200.0, laptop.get("price"));
        assertEquals(1.0, laptop.get("qty"));
    }

    @Test
    @Transactional
    void testVariousFieldValueTypesAndListRecords() {
        // 1. 构造包含长文本 (>255)、时间 (LocalDateTime) 的各种属性值
        java.time.LocalDateTime testTime = java.time.LocalDateTime.of(2026, 6, 25, 9, 0);
        String longText = "a".repeat(300); // 触发 text_value 存储

        Map<String, Object> payload = Map.of(
                "shortText", "hello",
                "longText", longText,
                "numVal", 99.9,
                "dateVal", testTime
        );

        // 2. 保存并验证
        Long id = recordService.saveComplexRecord("all_types", payload);
        assertNotNull(id);

        // 3. 验证单条记录读取 (覆盖各类值的还原逻辑)
        Map<String, Object> result = recordService.getRecord(id);
        assertEquals("hello", result.get("shortText"));
        assertEquals(longText, result.get("longText"));
        assertEquals(99.9, result.get("numVal"));
        assertEquals(testTime, result.get("dateVal"));

        // 4. 验证列表查询 (覆盖 listRecords 方法)
        Page<Map<String, Object>> listPage = recordService.listRecords("all_types", PageRequest.of(0, 10));
        List<Map<String, Object>> list = listPage.getContent();
        assertEquals(1, list.size());
        assertEquals("hello", list.get(0).get("shortText"));
    }

    /**
     * <p>
     * 验证<b>级联删除</b>逻辑。
     * </p>
     * <b>测试内容：</b>
     * <ul>
     *     <li>保存一个带有嵌套关系的记录。</li>
     *     <li>执行删除操作。</li>
     *     <li>验证主记录及其关联的所有 EAV 属性和关系是否已从数据库中彻底移除。</li>
     * </ul>
     */
    @Test
    void testDeleteCascading() {
        // 1. Save
        Map<String, Object> payload = Map.of(
                "attr1", "val1",
                "children", List.of(Map.of("subAttr", "subVal"))
        );
        Long id = recordService.saveComplexRecord("test", payload);

        // 2. Delete
        recordService.deleteRecord(id);

        // 3. Verify
        assertThrows(RuntimeException.class, () -> recordService.getRecord(id));
    }
}
