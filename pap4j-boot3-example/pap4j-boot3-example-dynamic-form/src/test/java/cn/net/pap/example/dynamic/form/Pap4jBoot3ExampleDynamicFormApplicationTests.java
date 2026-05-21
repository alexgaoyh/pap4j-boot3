package cn.net.pap.example.dynamic.form;

import cn.net.pap.example.dynamic.form.service.DynamicRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class Pap4jBoot3ExampleDynamicFormApplicationTests {

    @Autowired
    private DynamicRecordService recordService;

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
