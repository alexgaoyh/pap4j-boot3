package cn.net.pap.common.jsonorm.parser;

import cn.net.pap.common.jsonorm.dto.ExtractionResultDTO;
import cn.net.pap.common.jsonorm.dto.FieldInfoDTO;
import cn.net.pap.common.jsonorm.dto.SchemaDiffResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>基于 JSON Schema 的结构化数据提取全量单元测试。</p>
 */
public class JsonSchemaExtractionTest {

    private final JsonSchemaExtractor extractor = new JsonSchemaExtractor();
    private final JsonSchemaDiffTool diffTool = new JsonSchemaDiffTool();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testEcommerceDataExtraction() throws Exception {
        String schemaV1 = """
                {
                  "type": "object",
                  "properties": {
                    "orderId": { "type": "string", "x-extract": true },
                    "totalAmount": { "type": "number", "x-extract": true },
                    "user": {
                      "type": "object",
                      "properties": {
                        "userId": { "type": "string", "x-extract": true }
                      }
                    },
                    "items": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "skuId": { "type": "string", "x-extract": true }
                        }
                      }
                    }
                  }
                }
                """;

        String orderData = """
                {
                  "orderId": "ORD-2023-001",
                  "totalAmount": 299.98,
                  "user": { "userId": "U12345", "username": "zhangsan" },
                  "items": [
                    { "skuId": "SKU-A", "name": "键盘" },
                    { "skuId": "SKU-B", "name": "鼠标" }
                  ]
                }
                """;

        ExtractionResultDTO result = extractor.extract(orderData, schemaV1);
        Map<String, Object> core = result.coreFields();

        assertEquals("ORD-2023-001", core.get("orderId"));
        assertEquals(new BigDecimal("299.98"), core.get("totalAmount"));

        Map<String, Object> user = (Map<String, Object>) core.get("user");
        assertEquals("U12345", user.get("userId"));

        List<Object> items = (List<Object>) core.get("items");
        Map<String, Object> item0 = (Map<String, Object>) items.get(0);
        assertEquals("SKU-A", item0.get("skuId"));

        // 1. 验证完全平铺存储转换 (1:1 拍扁, 1:N JSON串)
        Map<String, Object> flattened = extractor.toFlattenedStorageMap(core);
        assertEquals("ORD-2023-001", flattened.get("orderId"));
        assertEquals(new BigDecimal("299.98"), flattened.get("totalAmount"));
        assertEquals("U12345", flattened.get("user.userId"));
        assertTrue(flattened.get("items").toString().contains("SKU-A"));

        // 2. 验证差异分析工具对列的预测：items 是 1:N 关系，应作为单列
        SchemaDiffResultDTO diff = diffTool.diff("{}", schemaV1);
        assertTrue(diff.addedFields().containsKey("items"));
        assertFalse(diff.addedFields().containsKey("items.skuId"));
    }

    @Test
    public void testSchemaDiffing() throws Exception {
        String schemaV1 = """
                {
                  "type": "object",
                  "properties": {
                    "orderId": { "type": "string", "x-extract": true }
                  }
                }
                """;

        String schemaV2 = """
                {
                  "type": "object",
                  "x-extract": true,
                  "properties": {
                    "orderId": { "type": "string" },
                    "status": { "type": "integer", "x-extract": true },
                    "user": {
                      "type": "object",
                      "properties": {
                        "email": { "type": "string", "x-extract": true }
                      }
                    }
                  }
                }
                """;

        SchemaDiffResultDTO diff = diffTool.diff(schemaV1, schemaV2);
        assertTrue(diff.addedFields().containsKey("user.email"));
        assertTrue(diff.addedFields().containsKey("root"));
        assertEquals("Long", diff.addedFields().get("status").javaType());
    }

    @Test
    public void testSchemaDiffingWithRefs() throws Exception {
        String schemaV1 = """
                {
                  "$defs": {
                    "address": {
                      "type": "object",
                      "properties": { "city": { "type": "string", "x-extract": true } }
                    }
                  },
                  "type": "object",
                  "properties": { "home": { "$ref": "#/$defs/address" } }
                }
                """;

        String schemaV2 = """
                {
                  "$defs": {
                    "address": {
                      "type": "object",
                      "properties": {
                        "city": { "type": "string", "x-extract": true },
                        "street": { "type": "string", "x-extract": true }
                      }
                    }
                  },
                  "type": "object",
                  "properties": {
                    "home": { "$ref": "#/$defs/address" },
                    "office": { "$ref": "#/$defs/address" }
                  }
                }
                """;

        SchemaDiffResultDTO diff = diffTool.diff(schemaV1, schemaV2);
        assertTrue(diff.addedFields().containsKey("home.street"));
        assertTrue(diff.addedFields().containsKey("office.city"));
    }

    @Test
    public void testUltimateScenario() throws Exception {
        String ultimateSchema = """
                {
                  "$defs": {
                    "category": {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string", "x-extract": true },
                        "sub": { "type": "array", "items": { "$ref": "#/$defs/category" } }
                      }
                    }
                  },
                  "type": "object",
                  "properties": {
                    "orderId": { "type": "string", "x-extract": true },
                    "category": { "$ref": "#/$defs/category" },
                    "location": {
                      "type": "array",
                      "items": [ { "type": "number", "x-extract": true }, { "type": "number", "x-extract": true } ]
                    }
                  }
                }
                """;

        String ultimateData = """
                {
                  "orderId": "ULT-001",
                  "category": {
                    "name": "电子产品",
                    "sub": [ { "name": "手机", "sub": [] } ]
                  },
                  "location": [116.39, 39.9]
                }
                """;

        ExtractionResultDTO result = extractor.extract(ultimateData, ultimateSchema);
        Map<String, Object> core = result.coreFields();

        Map<String, Object> cat = (Map<String, Object>) core.get("category");
        assertEquals("电子产品", cat.get("name"));

        List<Object> loc = (List<Object>) core.get("location");
        assertEquals(new BigDecimal("116.39"), loc.get(0));

        // 验证差异分析对递归结构的剪枝
        SchemaDiffResultDTO diff = diffTool.diff("{}", ultimateSchema);
        Map<String, FieldInfoDTO> added = diff.addedFields();

        assertTrue(added.containsKey("category.name"));
        assertTrue(added.containsKey("category.sub"));
        assertFalse(added.containsKey("category.sub.name"));
        assertTrue(added.containsKey("location"));
    }

    @Test
    public void testFullBranchCoverage() throws Exception {
        String coverageSchema = """
                {
                  "type": "object",
                  "x-extract": true,
                  "properties": {
                    "isActive": { "type": "boolean", "x-extract": true },
                    "nullableField": { "type": "null", "x-extract": true },
                    "conditionalField": {
                      "if": { "properties": { "type": { "const": "A" } } },
                      "then": { "properties": { "valueA": { "type": "string", "x-extract": true } } },
                      "else": { "properties": { "valueB": { "type": "string", "x-extract": true } } }
                    }
                  },
                  "patternProperties": {
                    "^p_.*": { "type": "string", "x-extract": true }
                  },
                  "allOf": [
                    { "properties": { "extra": { "type": "number", "x-extract": true } } }
                  ]
                }
                """;

        String coverageData = """
                {
                  "isActive": true,
                  "nullableField": null,
                  "conditionalField": { "type": "A", "valueA": "HIT_THEN" },
                  "p_dynamic": "PATTERN_HIT",
                  "extra": 123.45
                }
                """;

        ExtractionResultDTO result = extractor.extract(coverageData, coverageSchema);
        Map<String, Object> core = result.coreFields();

        assertEquals(true, core.get("isActive"));
        assertNull(core.get("nullableField"));
        assertEquals(new BigDecimal("123.45"), core.get("extra"));

        SchemaDiffResultDTO diff = diffTool.diff("{}", coverageSchema);
        Map<String, FieldInfoDTO> added = diff.addedFields();

        assertEquals("BigDecimal", added.get("extra").javaType());
        assertEquals("Boolean", added.get("isActive").javaType());
        assertTrue(added.containsKey("<pattern:^p_.*>"));

        // 覆盖 root 基本类型
        String rootSchemaStr = """
                { "type": "boolean", "x-extract": true }
                """;
        ExtractionResultDTO rootRes = extractor.extract("true", rootSchemaStr);
        assertEquals(true, rootRes.coreFields().get("root"));

        String nullSchema = """
                { "type": "null", "x-extract": true }
                """;
        ExtractionResultDTO nullRes = extractor.extract("null", nullSchema);
        assertNull(nullRes.coreFields().get("root"));
    }
}
