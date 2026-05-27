package cn.net.pap.common.qlexpress.parser;

import cn.net.pap.common.qlexpress.Express4RunnerUtil;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionRuleDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonFunctionalExtractorTest {

    @Test
    public void testFunctionalExtraction() {
        String jsonData = """
                {
                  "orderId": "ORD-123",
                  "amount": 500,
                  "user": {
                    "name": "zhangsan",
                    "level": "VIP"
                  },
                  "items": [
                    {"sku": "SKU-A", "price": 200},
                    {"sku": "SKU-B", "price": 300}
                  ]
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("id", "$.orderId"),
                new FunctionalExtractionRuleDTO("userName", "json.user.name"),
                new FunctionalExtractionRuleDTO("level", "json.user.level"),
                new FunctionalExtractionRuleDTO("isHighAmount", "json.amount > 300 ? 'YES' : 'NO'"),
                new FunctionalExtractionRuleDTO("firstItemSku", "$.items[0].sku"),
                new FunctionalExtractionRuleDTO("firstItemSkuViaFunc", "JSON_PATH(json, '$.items[0].sku')")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("ORD-123", fields.get("id"));
        assertEquals("zhangsan", fields.get("userName"));
        assertEquals("VIP", fields.get("level"));
        assertEquals("YES", fields.get("isHighAmount"));
        assertEquals("SKU-A", fields.get("firstItemSku"));
        assertEquals("SKU-A", fields.get("firstItemSkuViaFunc"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testComplexFunctionalExtraction() {
        String jsonData = """
                {
                  "traceId": "T-999",
                  "timestamp": 1622000000000,
                  "amount": 550.5,
                  "isPaid": true,
                  "status": "DELIVERED",
                  "user": {
                    "id": 1001,
                    "firstName": "Zhang",
                    "lastName": "San",
                    "roles": ["ADMIN", "EDITOR"],
                    "contact": {
                      "email": "zhangsan@example.com",
                      "phone": "13800138000"
                    },
                    "tags": ["VIP", "LOYAL"],
                    "address": null
                  },
                  "items": [
                    {"sku": "SKU-001", "name": "Item A", "price": 100, "qty": 2, "category": "ELECTRONICS"},
                    {"sku": "SKU-002", "name": "Item B", "price": 300, "qty": 1, "category": "BOOKS"},
                    {"sku": "SKU-003", "name": "Item C", "price": 50.5, "qty": 3, "category": "ELECTRONICS"}
                  ],
                  "metadata": {
                    "source": "MOBILE",
                    "flags": [1, 0, 1]
                  },
                  "emptyList": [],
                  "emptyObject": {}
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                // 1. Basic Path (JsonPath)
                new FunctionalExtractionRuleDTO("id", "$.traceId"),
                // 2. Nested Field (QLExpress)
                new FunctionalExtractionRuleDTO("userName", "json.user.firstName"),
                // 3. String Splicing (QLExpress)
                new FunctionalExtractionRuleDTO("fullName", "json.user.firstName + ' ' + json.user.lastName"),
                new FunctionalExtractionRuleDTO("userContactInfo", "json.user.contact.email + '(' + json.user.contact.phone + ')'"),
                // 4. Array Processing - Joining (Custom Function)
                new FunctionalExtractionRuleDTO("userRoles", "LIST_JOIN(json.user.roles, '|')"),
                new FunctionalExtractionRuleDTO("userTags", "LIST_JOIN(json.user.tags)"), // Default delimiter
                // 5. Array Processing - Size (QLExpress)
                new FunctionalExtractionRuleDTO("itemCount", "LIST_SIZE(json.items)"),
                // 6. Conditional Logic (QLExpress Ternary)
                new FunctionalExtractionRuleDTO("paymentStatus", "json.isPaid ? 'PAID' : 'UNPAID'"),
                // 7. Arithmetic (QLExpress)
                new FunctionalExtractionRuleDTO("item0Total", "json.items[0].price * json.items[0].qty"),
                // 8. JsonPath Filtering (JsonPath)
                new FunctionalExtractionRuleDTO("electronicsSkus", "$.items[?(@.category == 'ELECTRONICS')].sku"),
                // 9. Handling Null with Elvis/Conditional (QLExpress)
                new FunctionalExtractionRuleDTO("userAddress", "json.user.address == null ? 'Unknown' : json.user.address"),
                // 10. Deep Path via Custom Function
                new FunctionalExtractionRuleDTO("firstRole", "JSON_PATH(json, '$.user.roles[0]')"),
                // 11. Empty Structures
                new FunctionalExtractionRuleDTO("hasEmptyList", "LIST_SIZE(json.emptyList) == 0"),
                // 12. Mixed types in concatenation
                new FunctionalExtractionRuleDTO("orderSummary", "'Trace ' + json.traceId + ' amount: ' + json.amount")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("T-999", fields.get("id"));
        assertEquals("Zhang", fields.get("userName"));
        assertEquals("Zhang San", fields.get("fullName"));
        assertEquals("zhangsan@example.com(13800138000)", fields.get("userContactInfo"));
        assertEquals("ADMIN|EDITOR", fields.get("userRoles"));
        assertEquals("VIP,LOYAL", fields.get("userTags"));
        assertEquals(3, fields.get("itemCount"));
        assertEquals("PAID", fields.get("paymentStatus"));
        assertEquals(200, ((Number) fields.get("item0Total")).intValue());
        
        // JsonPath filter returns a list
        List<String> electronics = (List<String>) fields.get("electronicsSkus");
        assertEquals(2, electronics.size());
        assertTrue(electronics.contains("SKU-001"));
        assertTrue(electronics.contains("SKU-003"));

        assertEquals("Unknown", fields.get("userAddress"));
        assertEquals("ADMIN", fields.get("firstRole"));
        assertEquals(true, fields.get("hasEmptyList"));
        assertEquals("Trace T-999 amount: 550.5", fields.get("orderSummary"));
    }
}
