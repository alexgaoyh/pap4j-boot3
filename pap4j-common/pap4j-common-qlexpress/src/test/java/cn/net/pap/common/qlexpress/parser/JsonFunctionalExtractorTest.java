package cn.net.pap.common.qlexpress.parser;

import cn.net.pap.common.qlexpress.Express4RunnerUtil;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionRuleDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JsonFunctionalExtractorTest {

    @Test
    @Order(1)
    @DisplayName("场景0：json-path简单示例1")
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
    @Order(2)
    @DisplayName("场景0：json-path简单示例2")
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

    @Test
    @Order(3)
    @DisplayName("场景1：数据清洗与标准化（脱敏、格式化、默认值）")
    public void testDataCleaningAndStandardization() {
        String jsonData = """
                {
                  "user": {
                    "phone": "13812345678",
                    "idCard": "110101199001011234",
                    "nickname": "  King   ",
                    "email": "",
                    "score": null
                  },
                  "config": {
                    "is_active": "1"
                  }
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                // 1. 手机号脱敏 (使用 SUBSTRING 函数)
                new FunctionalExtractionRuleDTO("maskedPhone",
                        "SUBSTRING(json.user.phone, 0, 3) + '****' + SUBSTRING(json.user.phone, 7)"),
                // 2. 身份证处理
                new FunctionalExtractionRuleDTO("birthDate", "SUBSTRING(json.user.idCard, 6, 14)"),
                // 3. 字符串去空格与转大写 (清洗)
                new FunctionalExtractionRuleDTO("cleanNickname", "UPPER(TRIM(json.user.nickname))"),
                // 4. 空值处理与默认值 (ISBLANK 函数)
                new FunctionalExtractionRuleDTO("emailStatus", "ISBLANK(json.user.email) ? 'N/A' : json.user.email"),
                new FunctionalExtractionRuleDTO("finalScore", "json.user.score == null ? 0 : json.user.score"),
                // 5. 类型转换 (逻辑值)
                new FunctionalExtractionRuleDTO("activeFlag", "json.config.is_active == '1'")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("138****5678", fields.get("maskedPhone"));
        assertEquals("19900101", fields.get("birthDate"));
        assertEquals("KING", fields.get("cleanNickname"));
        assertEquals("N/A", fields.get("emailStatus"));
        assertEquals(0, fields.get("finalScore"));
        assertEquals(true, fields.get("activeFlag"));
    }

    @Test
    @Order(4)
    @DisplayName("场景2：多源指标计算（算术、权重、除法精度）")
    public void testIndicatorCalculation() {
        String jsonData = """
                {
                  "stats": {
                    "viewCount": 1000,
                    "clickCount": 45,
                    "orderCount": 5
                  },
                  "weights": {
                    "view": 0.2,
                    "click": 0.8
                  }
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                // 1. 点击率计算 (使用自定义 DIV2 保证精度)
                new FunctionalExtractionRuleDTO("clickRate", "DIV2(json.stats.clickCount, json.stats.viewCount)"),
                // 2. 转化率计算
                new FunctionalExtractionRuleDTO("orderRate", "DIV2(json.stats.orderCount, json.stats.clickCount)"),
                // 3. 综合加权得分
                new FunctionalExtractionRuleDTO("weightedScore", "json.stats.viewCount * json.weights.view + json.stats.clickCount * json.weights.click")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        // 45/1000 = 0.045 -> ROUND_HALF_UP(2) = 0.05
        assertEquals(new BigDecimal("0.05"), fields.get("clickRate"));
        // 5/45 = 0.111... -> 0.11
        assertEquals(new BigDecimal("0.11"), fields.get("orderRate"));
        // 1000 * 0.2 + 45 * 0.8 = 200 + 36 = 236.0
        assertEquals(236.0, ((Number) fields.get("weightedScore")).doubleValue());
    }

    @Test
    @Order(5)
    @DisplayName("场景3：复杂数组加工（多维提取、列表过滤、元素拼接）")
    public void testArrayProcessing() {
        String jsonData = """
                {
                  "logs": [
                    {"level": "INFO", "msg": "started", "code": 200},
                    {"level": "ERROR", "msg": "failed", "code": 500},
                    {"level": "WARN", "msg": "slow", "code": 400},
                    {"level": "ERROR", "msg": "timeout", "code": 504}
                  ],
                  "tags": ["prod", "web", "high-priority"]
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                // 1. 提取所有错误码 (JsonPath)
                new FunctionalExtractionRuleDTO("errorCodes", "$.logs[?(@.level == 'ERROR')].code"),
                // 2. 检查是否存在特定状态 (结合 JSON_PATH 函数在 QLExpress 中使用)
                new FunctionalExtractionRuleDTO("hasError", "LIST_SIZE(JSON_PATH(json, '$.logs[?(@.level == \\'ERROR\\')]')) > 0"),
                // 3. 标签云拼接
                new FunctionalExtractionRuleDTO("tagCloud", "LIST_JOIN(json.tags, '#')"),
                // 4. 统计日志总数
                new FunctionalExtractionRuleDTO("totalLogs", "LIST_SIZE(json.logs)")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        List<Integer> errorCodes = (List<Integer>) fields.get("errorCodes");
        assertEquals(2, errorCodes.size());
        assertTrue(errorCodes.contains(500));
        assertTrue(errorCodes.contains(504));

        assertEquals(true, fields.get("hasError"));
        assertEquals("prod#web#high-priority", fields.get("tagCloud"));
        assertEquals(4, fields.get("totalLogs"));
    }

    @Test
    @Order(6)
    @DisplayName("场景4：防御性提取（属性缺失、结构变化、防止抛错）")
    public void testDefensiveExtraction() {
        // 某些字段可能完全缺失
        String jsonData = """
                {
                  "payload": {
                    "body": "hello"
                  }
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                // 1. 尝试访问深层缺失属性 (QLExpress 默认处理 null)
                new FunctionalExtractionRuleDTO("missingHeader", "json.payload.header == null ? 'DEFAULT' : json.payload.header"),
                // 2. 针对数组字段缺失的防御
                new FunctionalExtractionRuleDTO("itemSize", "json.payload.items == null ? 0 : LIST_SIZE(json.payload.items)"),
                // 3. TERNARY 逻辑防御
                new FunctionalExtractionRuleDTO("typeLabel", "TERNARY(json.type, 'EXIST', 'ABSENT')")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("DEFAULT", fields.get("missingHeader"));
        assertEquals(0, fields.get("itemSize"));
        assertEquals("ABSENT", fields.get("typeLabel"));
    }

    @Test
    @Order(7)
    @DisplayName("场景5：无穷极树形结构处理（递归搜索、扁平化提取、深度过滤）")
    @SuppressWarnings("unchecked")
    public void testRecursiveTreeExtraction() {
        String jsonData = """
                {
                  "category": {
                    "id": "root",
                    "name": "电子产品",
                    "children": [
                      {
                        "id": "c1",
                        "name": "手机",
                        "children": [
                          { "id": "c1-1", "name": "智能手机" },
                          { "id": "c1-2", "name": "功能机" }
                        ]
                      },
                      {
                        "id": "c2",
                        "name": "电脑",
                        "children": [
                          {
                            "id": "c2-1",
                            "name": "笔记本",
                            "children": [
                              { "id": "c2-1-1", "name": "轻薄本" },
                              { "id": "c2-1-2", "name": "游戏本" }
                            ]
                          },
                          { "id": "c2-2", "name": "台式机" }
                        ]
                      }
                    ]
                  }
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                // 1. 扁平化提取：获取树中所有的 ID (使用 JsonPath 递归扫描 ..)
                new FunctionalExtractionRuleDTO("allCategoryIds", "$..id"),
                // 2. 保留层级的扁平化提取：获取带路径的名称列表，并用 | 分隔
                new FunctionalExtractionRuleDTO("categoryHierarchy", "LIST_JOIN(TREE_FLATTEN(json.category, 'children', 'name', ' > '), ' | ')"),
                // 2.1 仅保留叶子节点的层级提取
                new FunctionalExtractionRuleDTO("leafHierarchy", "LIST_JOIN(TREE_LEAF_FLATTEN(json.category, 'children', 'name', ' > '), ' | ')"),
                // 3. 深度搜索：找到名称为 '游戏本' 的节点的 ID
                new FunctionalExtractionRuleDTO("gamingLaptopId", "$..[?(@.name == '游戏本')].id"),
                // 4. 统计树中节点总数
                new FunctionalExtractionRuleDTO("totalCategories", "LIST_SIZE(JSON_PATH(json, '$..id'))"),
                // 5. 检查是否存在某个深层节点 (结合 JSON_PATH 函数在 QLExpress 中逻辑判定)
                new FunctionalExtractionRuleDTO("hasMobile", "LIST_SIZE(JSON_PATH(json, '$..[?(@.name == \\'手机\\')]')) > 0")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        // 验证扁平化 ID 列表
        List<String> ids = (List<String>) fields.get("allCategoryIds");
        assertTrue(ids.contains("root"));
        assertTrue(ids.contains("c2-1-1"));
        assertTrue(ids.contains("c2-1-2"));
        assertEquals(9, ids.size());

        // 验证层级结构保留
        String hierarchy = (String) fields.get("categoryHierarchy");
        assertTrue(hierarchy.contains("电子产品 > 电脑 > 笔记本 > 游戏本"));
        assertTrue(hierarchy.contains("电子产品 > 手机 > 智能手机"));
        assertTrue(hierarchy.contains(" | ")); // 验证多条数据分隔符

        // 验证仅保留叶子节点的提取
        String leafHierarchy = (String) fields.get("leafHierarchy");
        // 叶子节点路径应该包含，但中间节点路径（如 '电子产品 > 电脑'）不应作为独立项存在
        assertTrue(leafHierarchy.contains("电子产品 > 电脑 > 笔记本 > 游戏本"));
        assertTrue(leafHierarchy.contains("电子产品 > 手机 > 智能手机"));
        assertFalse(leafHierarchy.contains(" | 电子产品 > 电脑 | ")); // 中间节点不应独立出现

        // 验证深度搜索结果
        List<String> searchResult = (List<String>) fields.get("gamingLaptopId");
        assertEquals(1, searchResult.size());
        assertEquals("c2-1-2", searchResult.get(0));

        // 验证统计
        assertEquals(9, fields.get("totalCategories"));
        assertEquals(true, fields.get("hasMobile"));
    }

}
