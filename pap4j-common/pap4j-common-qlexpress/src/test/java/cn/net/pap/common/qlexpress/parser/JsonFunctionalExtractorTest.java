package cn.net.pap.common.qlexpress.parser;

import cn.net.pap.common.qlexpress.Express4RunnerUtil;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionRuleDTO;
import cn.net.pap.common.qlexpress.dto.RuleExecStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    @Order(8)
    @DisplayName("场景6：规则执行状态审计 - 全部成功")
    public void testExtractPopulatesStatusesOnSuccess() {
        String jsonData = """
                {
                  "user": {
                    "name": "zhangsan",
                    "level": "VIP"
                  },
                  "amount": 500
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("userName", "json.user.name"),
                new FunctionalExtractionRuleDTO("isHighAmount", "json.amount > 300 ? 'YES' : 'NO'")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);

        assertEquals(2, result.statuses().size());
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
        assertEquals("zhangsan", result.extractedFields().get("userName"));
        assertEquals("YES", result.extractedFields().get("isHighAmount"));
    }

    @Test
    @Order(9)
    @DisplayName("场景7：规则执行状态审计 - 单条失败不影响其余（错误隔离）")
    public void testExtractRecordsFailedRuleStatus() {
        String jsonData = """
                {
                  "user": {
                    "name": "zhangsan",
                    "phone": "13812345678"
                  }
                }
                """;

        // SUBSTRING 越界必失败，验证失败可溯源且不影响其他规则
        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("userName", "json.user.name"),
                new FunctionalExtractionRuleDTO("badSubstring", "SUBSTRING(json.user.phone, 0, 99)")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);

        assertEquals(2, result.statuses().size());
        RuleExecStatus ok = result.statuses().get(0);
        RuleExecStatus failed = result.statuses().get(1);

        assertTrue(ok.success());
        assertEquals("userName", ok.targetField());
        assertFalse(failed.success());
        assertEquals("badSubstring", failed.targetField());
        assertTrue(failed.errorMsg() != null && !failed.errorMsg().isBlank());

        // 失败规则不产出字段，成功规则照常产出
        assertFalse(result.extractedFields().containsKey("badSubstring"));
        assertEquals("zhangsan", result.extractedFields().get("userName"));
    }

    @Test
    @Order(10)
    @DisplayName("场景8：规则预校验 - 合法规则集通过")
    public void testCheckRulesAcceptsValidRules() {
        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("id", "$.orderId"),
                new FunctionalExtractionRuleDTO("userName", "json.user.name"),
                new FunctionalExtractionRuleDTO("itemCount", "LIST_SIZE(json.items)"),
                new FunctionalExtractionRuleDTO("userRoles", "LIST_JOIN(json.user.roles, '|')")
        );

        Express4RunnerUtil.checkRules(rules);
    }

    @Test
    @Order(11)
    @DisplayName("场景9：规则预校验 - QL 语法错误被拒绝")
    public void testCheckRulesRejectsInvalidQl() {
        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("bad", "json.user.name +")
        );

        assertThrows(IllegalArgumentException.class, () -> Express4RunnerUtil.checkRules(rules));
    }

    @Test
    @Order(12)
    @DisplayName("场景10：规则预校验 - JsonPath 语法错误被拒绝")
    public void testCheckRulesRejectsInvalidJsonPath() {
        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("bad", "$..[")
        );

        assertThrows(IllegalArgumentException.class, () -> Express4RunnerUtil.checkRules(rules));
    }

    @Test
    @Order(13)
    @DisplayName("场景11：结果 DTO 向后兼容 - 2 参构造器 statuses 为空")
    public void testResultDtoBackwardCompatConstructor() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("id", "ORD-1");

        FunctionalExtractionResultDTO dto = new FunctionalExtractionResultDTO(fields, "{\"id\":\"ORD-1\"}");

        assertTrue(dto.statuses().isEmpty());
        assertEquals("ORD-1", dto.extractedFields().get("id"));
        assertEquals("{\"id\":\"ORD-1\"}", dto.rawPayload());
    }

    @Test
    @Order(14)
    @DisplayName("场景12：集合聚合算子（求和/均值/极值，含数字字符串与 null 跳过）")
    public void testListAggregationOperators() {
        String jsonData = """
                {
                  "items": [
                    {"name": "A", "price": 100, "qty": 2},
                    {"name": "B", "price": 300, "qty": 1},
                    {"name": "C", "price": 50, "qty": 3}
                  ],
                  "mixedNums": [10, "20", 30.5, null],
                  "dates": ["2024-03-15", "2024-01-01", "2024-02-20"]
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("totalPrice", "LIST_SUM(JSON_PATH(json, '$.items[*].price'))"),
                new FunctionalExtractionRuleDTO("avgPrice", "LIST_AVG(JSON_PATH(json, '$.items[*].price'))"),
                new FunctionalExtractionRuleDTO("maxPrice", "LIST_MAX(JSON_PATH(json, '$.items[*].price'))"),
                new FunctionalExtractionRuleDTO("minPrice", "LIST_MIN(JSON_PATH(json, '$.items[*].price'))"),
                new FunctionalExtractionRuleDTO("mixedSum", "LIST_SUM(json.mixedNums)"),
                new FunctionalExtractionRuleDTO("latestDate", "LIST_MAX(json.dates)")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        // 100 + 300 + 50 = 450
        assertEquals(0, new BigDecimal("450").compareTo((BigDecimal) fields.get("totalPrice")));
        // (100 + 300 + 50) / 3 = 150.0000（scale=4, HALF_UP）
        assertEquals(0, new BigDecimal("150").compareTo((BigDecimal) fields.get("avgPrice")));
        // 极值保留原元素类型（JSON 数字 → Integer/Long）
        assertEquals(300, ((Number) fields.get("maxPrice")).intValue());
        assertEquals(50, ((Number) fields.get("minPrice")).intValue());
        // null 跳过 + 数字字符串强转：10 + 20 + 30.5 = 60.5
        assertEquals(0, new BigDecimal("60.5").compareTo((BigDecimal) fields.get("mixedSum")));
        // 通用 Comparable：ISO 日期字符串取最大
        assertEquals("2024-03-15", fields.get("latestDate"));

        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(15)
    @DisplayName("场景13：集合聚合 - 空集合 / null 语义")
    public void testListAggregationEmptyAndNull() {
        String jsonData = """
                {
                  "emptyArr": [],
                  "nullField": null,
                  "onlyNulls": [null, null]
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("emptySum", "LIST_SUM(json.emptyArr)"),
                new FunctionalExtractionRuleDTO("nullSum", "LIST_SUM(json.nullField)"),
                new FunctionalExtractionRuleDTO("emptyAvg", "LIST_AVG(json.emptyArr)"),
                new FunctionalExtractionRuleDTO("nullMax", "LIST_MAX(json.nullField)"),
                new FunctionalExtractionRuleDTO("onlyNullsMin", "LIST_MIN(json.onlyNulls)")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        // SUM 空集 → 0；AVG/MAX/MIN 空集 → null（字段产出为 null，规则执行成功）
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) fields.get("emptySum")));
        assertEquals(0, new BigDecimal("0").compareTo((BigDecimal) fields.get("nullSum")));
        assertNull(fields.get("emptyAvg"));
        assertNull(fields.get("nullMax"));
        assertNull(fields.get("onlyNullsMin"));

        // null 结果是合法结果而非失败——审计里应全部 success
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(16)
    @DisplayName("场景14：集合聚合 - 非法元素与混合类型经审计暴露（错误隔离）")
    public void testListAggregationRejectsGarbage() {
        String jsonData = """
                {
                  "nums": [10, "abc", 30],
                  "mixed": [10, "abc"],
                  "ok": [1, 2, 3]
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("badSum", "LIST_SUM(json.nums)"),
                new FunctionalExtractionRuleDTO("badMax", "LIST_MAX(json.mixed)"),
                new FunctionalExtractionRuleDTO("okSum", "LIST_SUM(json.ok)")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();
        List<RuleExecStatus> statuses = result.statuses();

        // 正常规则不受影响
        assertEquals(0, new BigDecimal("6").compareTo((BigDecimal) fields.get("okSum")));
        // 非法元素（'abc'）/ 混合类型（Integer vs String）→ 该规则失败、不产出字段
        assertFalse(fields.containsKey("badSum"));
        assertFalse(fields.containsKey("badMax"));

        RuleExecStatus badSum = statuses.stream()
                .filter(s -> "badSum".equals(s.targetField())).findFirst().orElseThrow();
        RuleExecStatus badMax = statuses.stream()
                .filter(s -> "badMax".equals(s.targetField())).findFirst().orElseThrow();
        RuleExecStatus okSum = statuses.stream()
                .filter(s -> "okSum".equals(s.targetField())).findFirst().orElseThrow();

        assertFalse(badSum.success());
        assertTrue(badSum.errorMsg() != null && !badSum.errorMsg().isBlank());
        assertFalse(badMax.success());
        assertTrue(okSum.success());
    }

    @Test
    @Order(17)
    @DisplayName("场景15：集合去重与包含")
    public void testListDistinctAndContains() {
        String jsonData = """
                {
                  "tags": ["prod", "web", "prod", "web", "high"],
                  "nullList": null
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("distinctTags", "LIST_DISTINCT(json.tags)"),
                new FunctionalExtractionRuleDTO("hasWeb", "LIST_CONTAINS(json.tags, 'web')"),
                new FunctionalExtractionRuleDTO("hasNone", "LIST_CONTAINS(json.tags, 'none')"),
                new FunctionalExtractionRuleDTO("nullContains", "LIST_CONTAINS(json.nullList, 'x')")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals(List.of("prod", "web", "high"), fields.get("distinctTags"));
        assertEquals(true, fields.get("hasWeb"));
        assertEquals(false, fields.get("hasNone"));
        assertEquals(false, fields.get("nullContains"));
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(18)
    @DisplayName("场景16：正则三件套（校验/提取/替换）")
    public void testRegexOperators() {
        String jsonData = """
                {
                  "phone": "13812345678",
                  "badPhone": "23812345678",
                  "email": "zhangsan@example.com",
                  "text": "a  b   c"
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("phoneValid", "REGEX_TEST(json.phone, '^1[3-9]\\\\d{9}$')"),
                new FunctionalExtractionRuleDTO("badPhoneValid", "REGEX_TEST(json.badPhone, '^1[3-9]\\\\d{9}$')"),
                new FunctionalExtractionRuleDTO("emailUser", "REGEX_EXTRACT(json.email, '(\\\\w+)@(\\\\w+\\\\.\\\\w+)', 1)"),
                new FunctionalExtractionRuleDTO("emailFull", "REGEX_EXTRACT(json.email, '(\\\\w+)@(\\\\w+\\\\.\\\\w+)')"),
                new FunctionalExtractionRuleDTO("cleanText", "REGEX_REPLACE(json.text, '\\\\s+', ' ')")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals(true, fields.get("phoneValid"));
        assertEquals(false, fields.get("badPhoneValid"));
        assertEquals("zhangsan", fields.get("emailUser"));
        assertEquals("zhangsan@example.com", fields.get("emailFull"));
        assertEquals("a b c", fields.get("cleanText"));
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(19)
    @DisplayName("场景17：声明式脱敏与哈希指纹")
    public void testMaskAndHashOperators() {
        String jsonData = """
                {
                  "phone": "13812345678",
                  "idCard": "110101199001011234",
                  "shortVal": "ab"
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("maskedPhone", "MASK(json.phone, 3, 4)"),
                new FunctionalExtractionRuleDTO("maskedId", "MASK(json.idCard, 6, 4)"),
                new FunctionalExtractionRuleDTO("maskedCustom", "MASK(json.phone, 3, 4, '#')"),
                new FunctionalExtractionRuleDTO("shortPassthrough", "MASK(json.shortVal, 1, 1)"),
                new FunctionalExtractionRuleDTO("md5", "HASH_MASK(json.phone, 'MD5')"),
                new FunctionalExtractionRuleDTO("md5Again", "HASH_MASK(json.phone, 'MD5')"),
                new FunctionalExtractionRuleDTO("sha256", "HASH_MASK(json.phone, 'SHA-256')"),
                new FunctionalExtractionRuleDTO("shaSalted", "HASH_MASK(json.phone, 'SHA-256', 'dgn')")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("138****5678", fields.get("maskedPhone"));
        assertEquals("110101********1234", fields.get("maskedId"));
        assertEquals("138####5678", fields.get("maskedCustom"));
        // 过短（1 + 1 >= 2）→ 返回原值
        assertEquals("ab", fields.get("shortPassthrough"));

        String md5 = (String) fields.get("md5");
        String sha256 = (String) fields.get("sha256");
        // 确定性：同值同哈希
        assertEquals(md5, fields.get("md5Again"));
        // 长度：MD5 32 位、SHA-256 64 位十六进制
        assertEquals(32, md5.length());
        assertEquals(64, sha256.length());
        // 不同算法 / 加盐 → 结果不同
        assertFalse(md5.equals(sha256));
        assertFalse(md5.equals(fields.get("shaSalted")));
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(20)
    @DisplayName("场景18：字典/码值翻译（含数字键回退）")
    public void testDictMapOperator() {
        String jsonData = """
                {
                  "status": 1,
                  "level": "VIP",
                  "unknown": 9
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("statusName", "DICT_MAP(json.status, {'1':'已下单','2':'已支付'})"),
                new FunctionalExtractionRuleDTO("levelName", "DICT_MAP(json.level, {'VIP':'金卡','NORMAL':'普通'})"),
                new FunctionalExtractionRuleDTO("unknownName", "DICT_MAP(json.unknown, {'1':'已下单','2':'已支付'})")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        // JSON 数字 1 → 回退字符串键 "1"
        assertEquals("已下单", fields.get("statusName"));
        assertEquals("金卡", fields.get("levelName"));
        // 未命中 → 字段产出为 null
        assertNull(fields.get("unknownName"));
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(21)
    @DisplayName("场景19：类型强转 TO_INT/TO_STRING/TO_DECIMAL/TO_BOOLEAN")
    public void testTypeCoercionOperators() {
        String jsonData = """
                {
                  "numStr": "42",
                  "price": "3.14",
                  "amount": 500,
                  "badInt": "abc",
                  "flag": "是",
                  "zero": 0,
                  "flagYes": "yes",
                  "missing": null
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("intFromStr", "TO_INT(json.numStr)"),
                new FunctionalExtractionRuleDTO("intWithDefault", "TO_INT(json.badInt, 0)"),
                new FunctionalExtractionRuleDTO("intFromMissingDefault", "TO_INT(json.missing, 9)"),
                new FunctionalExtractionRuleDTO("intFromMissing", "TO_INT(json.missing)"),
                new FunctionalExtractionRuleDTO("strFromNum", "TO_STRING(json.amount)"),
                new FunctionalExtractionRuleDTO("decimalFromStr", "TO_DECIMAL(json.price)"),
                new FunctionalExtractionRuleDTO("boolFromCn", "TO_BOOLEAN(json.flag)"),
                new FunctionalExtractionRuleDTO("boolFromZero", "TO_BOOLEAN(json.zero)"),
                new FunctionalExtractionRuleDTO("boolFromYes", "TO_BOOLEAN(json.flagYes)"),
                new FunctionalExtractionRuleDTO("badBoolDefault", "TO_BOOLEAN(json.badInt, false)")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals(42, fields.get("intFromStr"));
        assertEquals(0, fields.get("intWithDefault"));
        assertEquals(9, fields.get("intFromMissingDefault"));
        // null 无默认值 → 字段产出为 null
        assertNull(fields.get("intFromMissing"));
        assertEquals("500", fields.get("strFromNum"));
        assertEquals(0, new BigDecimal("3.14").compareTo((BigDecimal) fields.get("decimalFromStr")));
        assertEquals(true, fields.get("boolFromCn"));
        assertEquals(false, fields.get("boolFromZero"));
        assertEquals(true, fields.get("boolFromYes"));
        assertEquals(false, fields.get("badBoolDefault"));
        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

    @Test
    @Order(22)
    @DisplayName("场景20：日期算子（格式化/加减/差值，不引入 NOW）")
    public void testDateOperators() {
        String jsonData = """
                {
                  "date": "2024-03-15",
                  "dateTime": "2024-03-15 10:30:00",
                  "start": "2024-03-01",
                  "end": "2024-03-15",
                  "epochSeconds": 1710379800,
                  "epochMillis": 1710379800000
                }
                """;

        List<FunctionalExtractionRuleDTO> rules = List.of(
                new FunctionalExtractionRuleDTO("formatted", "FORMAT_DATE(json.date, 'yyyy/MM/dd')"),
                new FunctionalExtractionRuleDTO("dateFromDateTime", "FORMAT_DATE(json.dateTime, 'yyyy-MM-dd')"),
                new FunctionalExtractionRuleDTO("addedDays", "DATE_ADD(json.date, 30, 'days')"),
                new FunctionalExtractionRuleDTO("addedHours", "DATE_ADD(json.dateTime, 1, 'hours')"),
                new FunctionalExtractionRuleDTO("epochAddDay", "DATE_ADD(json.epochSeconds, 1, 'days')"),
                new FunctionalExtractionRuleDTO("dateDiffDays", "DATE_DIFF(json.end, json.start, 'days')"),
                new FunctionalExtractionRuleDTO("dateDiffMonths", "DATE_DIFF(json.end, json.start, 'months')")
        );

        FunctionalExtractionResultDTO result = Express4RunnerUtil.extract(jsonData, rules);
        Map<String, Object> fields = result.extractedFields();

        assertEquals("2024/03/15", fields.get("formatted"));
        assertEquals("2024-03-15", fields.get("dateFromDateTime"));
        // 日期字符串 + 30 天 → ISO 日期（粒度保持）
        assertEquals("2024-04-14", fields.get("addedDays"));
        // 日期时间字符串 + 1 小时 → ISO 日期时间（粒度保持）
        assertEquals("2024-03-15T11:30:00", fields.get("addedHours"));
        // epoch 秒 + 1 天 → 原单位数字（epoch 往返与时区无关）
        assertEquals(1710379800L + 86400L, ((Number) fields.get("epochAddDay")).longValue());
        // 结束 - 开始：2024-03-15 - 2024-03-01 = 14 天
        assertEquals(14L, ((Number) fields.get("dateDiffDays")).longValue());
        assertEquals(0L, ((Number) fields.get("dateDiffMonths")).longValue());

        assertTrue(result.statuses().stream().allMatch(RuleExecStatus::success));
    }

}
