package cn.net.pap.common.jsonorm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonSchemaToEsMappingUtil} 单元测试套件。
 */
class JsonSchemaToEsMappingUtilTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ORDER_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "title": "订单数据模型",
              "required": ["order_id", "order_type", "total_price", "payment_details"],
              "additionalProperties": false,
              "properties": {
                "order_id": { "type": "string", "pattern": "^ORD-\\\\d{8}-\\\\d{6}$", "description": "全局唯一订单号" },
                "order_type": { "type": "string", "enum": ["PHYSICAL", "DIGITAL"] },
                "shipping_address": {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "recipient_name": { "type": "string" },
                    "phone": { "type": "string", "pattern": "^1\\\\d{10}$" },
                    "address_line": { "type": "string" }
                  }
                },
                "receiver_email": { "type": "string", "format": "email" },
                "total_price": { "type": "number", "minimum": 0.01 },
                "payment_details": {
                  "oneOf": [
                    {
                      "type": "object",
                      "properties": {
                        "wechat_unionid": { "type": "string" },
                        "payment_time": { "type": "string", "format": "date-time" }
                      }
                    },
                    {
                      "type": "object",
                      "properties": {
                        "card_number": { "type": "string", "pattern": "^\\\\d{16}$" },
                        "card_type": { "type": "string", "enum": ["VISA", "MASTERCARD", "AMEX"] }
                      }
                    }
                  ]
                }
              }
            }
            """;

    private static final String DEPT_TREE_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "title": "部门节点模型",
              "required": ["dept_id", "dept_name"],
              "additionalProperties": false,
              "properties": {
                "dept_id": { "type": "integer", "minimum": 1000 },
                "dept_name": { "type": "string" },
                "leader": { "type": "string" },
                "children": { "type": "array", "items": { "$ref": "#" } }
              }
            }
            """;

    @Test
    void generateOrderSchemaMapping() throws Exception {
        JsonNode mapping = generate(ORDER_SCHEMA, Set.of("total_price"), 10000);

        assertEquals("false", mapping.at("/mappings/dynamic").asText());
        assertEquals("text", mapping.at("/mappings/properties/order_id/type").asText());
        assertEquals("standard", mapping.at("/mappings/properties/order_id/analyzer").asText());
        assertEquals("keyword", mapping.at("/mappings/properties/order_id/fields/keyword/type").asText());
        assertEquals(1024, mapping.at("/mappings/properties/order_id/fields/keyword/ignore_above").asInt());

        assertEquals("keyword", mapping.at("/mappings/properties/order_type/type").asText());
        assertEquals("scaled_float", mapping.at("/mappings/properties/total_price/type").asText());
        assertEquals(10000, mapping.at("/mappings/properties/total_price/scaling_factor").asInt());

        // 未传金额参数时默认 double
        assertEquals("double", generate(ORDER_SCHEMA).at("/mappings/properties/total_price/type").asText());

        assertEquals("keyword", mapping.at("/mappings/properties/receiver_email/type").asText());
        assertEquals("object", mapping.at("/mappings/properties/shipping_address/type").asText());
        assertEquals("false", mapping.at("/mappings/properties/shipping_address/dynamic").asText());
        assertEquals("text", mapping.at("/mappings/properties/shipping_address/properties/phone/type").asText());

        assertEquals("object", mapping.at("/mappings/properties/payment_details/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/payment_details/properties/wechat_unionid/type").asText());
        assertEquals("date", mapping.at("/mappings/properties/payment_details/properties/payment_time/type").asText());
        assertEquals("strict_date_optional_time||epoch_millis", mapping.at("/mappings/properties/payment_details/properties/payment_time/format").asText());
        // fail loud: 不再输出 ignore_malformed，非法日期由 ES 拒收
        assertTrue(mapping.at("/mappings/properties/payment_details/properties/payment_time/ignore_malformed").isMissingNode());
        assertEquals("keyword", mapping.at("/mappings/properties/payment_details/properties/card_type/type").asText());
    }

    @Test
    void generateDeptTreeSchemaMapping() throws Exception {
        JsonNode mapping = generate(DEPT_TREE_SCHEMA);

        assertEquals("false", mapping.at("/mappings/dynamic").asText());
        assertEquals("long", mapping.at("/mappings/properties/dept_id/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/dept_name/type").asText());

        assertEquals("nested", mapping.at("/mappings/properties/children/type").asText());
        assertEquals("nested", mapping.at("/mappings/properties/children/properties/children/type").asText());
        assertEquals("nested", mapping.at("/mappings/properties/children/properties/children/properties/children/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/children/properties/dept_id/type").asText());
        assertEquals("false", mapping.at("/mappings/properties/children/dynamic").asText());
    }

    @Test
    void stripNullFromNullableType() throws Exception {
        String schema = """
                { "type": "object", "properties": { "note": { "type": ["string", "null"] }, "num": { "type": ["number", "null"] } } }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("text", mapping.at("/mappings/properties/note/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/num/type").asText());
    }

    @Test
    void resolveDefsReference() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": { "addr": { "$ref": "#/$defs/address" } },
                  "$defs": {
                    "address": {
                      "type": "object",
                      "properties": { "city": { "type": "string" }, "zip": { "type": "string", "enum": ["100000"] } }
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("object", mapping.at("/mappings/properties/addr/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/addr/properties/city/type").asText());
        assertEquals("keyword", mapping.at("/mappings/properties/addr/properties/zip/type").asText());
    }

    @Test
    void rejectInvalidFieldNames() {
        assertThrows(IllegalArgumentException.class, () -> generate("{ \"type\": \"object\", \"properties\": { \"a.b\": { \"type\": \"string\" } } }"));
        assertThrows(IllegalArgumentException.class, () -> generate("{ \"type\": \"object\", \"properties\": { \"_id\": { \"type\": \"string\" } } }"));
        assertThrows(IllegalArgumentException.class, () -> generate("{ \"type\": \"object\", \"properties\": { \"@type\": { \"type\": \"string\" } } }"));
    }

    @Test
    void deterministicOutput() {
        String json1 = JsonSchemaToEsMappingUtil.toJson(JsonSchemaToEsMappingUtil.generateIndexMapping(ORDER_SCHEMA));
        String json2 = JsonSchemaToEsMappingUtil.toJson(JsonSchemaToEsMappingUtil.generateIndexMapping(ORDER_SCHEMA));
        assertEquals(json1, json2);
    }

    @Test
    void testFormatDateIpAndTime() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "birth_date": { "type": "string", "format": "date" },
                    "client_ip": { "type": "string", "format": "ipv4" },
                    "ipv6_addr": { "type": "string", "format": "ipv6" },
                    "start_time": { "type": "string", "format": "time" }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("date", mapping.at("/mappings/properties/birth_date/type").asText());
        assertEquals("strict_date", mapping.at("/mappings/properties/birth_date/format").asText());
        assertEquals("ip", mapping.at("/mappings/properties/client_ip/type").asText());
        assertEquals("ip", mapping.at("/mappings/properties/ipv6_addr/type").asText());
        assertEquals("keyword", mapping.at("/mappings/properties/start_time/type").asText());
    }

    @Test
    void testBooleanAndScalarArray() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "active": { "type": "boolean" },
                    "tags": { "type": "array", "items": { "type": "string" } },
                    "scores": { "type": "array", "items": { "type": "number" } },
                    "counts": { "type": "array", "items": { "type": "integer" } }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("boolean", mapping.at("/mappings/properties/active/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/tags/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/scores/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/counts/type").asText());
    }

    @Test
    void testFlattenedScenarios() throws Exception {
        // 变长无 properties object
        assertEquals("flattened", generate("{ \"type\": \"object\", \"properties\": { \"meta\": { \"type\": \"object\" } } }")
                .at("/mappings/properties/meta/type").asText());

        // oneOf 含标量分支
        String scalarUnion = """
                {
                  "type": "object",
                  "properties": {
                    "contact": { "oneOf": [{ "type": "string" }, { "type": "object", "properties": { "phone": { "type": "string" } } }] }
                  }
                }
                """;
        assertEquals("flattened", generate(scalarUnion).at("/mappings/properties/contact/type").asText());

        // oneOf 字段类型冲突
        String conflictUnion = """
                {
                  "type": "object",
                  "properties": {
                    "content": { "oneOf": [{ "type": "object", "properties": { "val": { "type": "string" } } }, { "type": "object", "properties": { "val": { "type": "number" } } }] }
                  }
                }
                """;
        JsonNode conflict = generate(conflictUnion);
        assertEquals("object", conflict.at("/mappings/properties/content/type").asText());
        assertEquals("flattened", conflict.at("/mappings/properties/content/properties/val/type").asText());

        // $ref 自引用深度达 8 层截断
        String selfRef = """
                { "type": "object", "additionalProperties": false, "properties": { "parent": { "$ref": "#" } } }
                """;
        JsonNode res = generate(selfRef);
        assertEquals("object", res.at("/mappings/properties/parent/type").asText());
        assertEquals("flattened", res.at("/mappings/properties/parent/properties/parent/properties/parent/properties/parent/properties/parent/properties/parent/properties/parent/properties/parent/type").asText());
    }

    @Test
    void testIfThenElseMerge() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": { "type": { "type": "string" } },
                  "if": { "properties": { "type": { "const": "A" } } },
                  "then": { "properties": { "special_a": { "type": "string" } } },
                  "else": { "properties": { "special_b": { "type": "boolean" } } }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("text", mapping.at("/mappings/properties/type/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/special_a/type").asText());
        assertEquals("boolean", mapping.at("/mappings/properties/special_b/type").asText());
    }

    @Test
    void testEnumStrongTypeRestore() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "level": { "type": "integer", "enum": [1, 2, 3] },
                    "enabled": { "type": "boolean", "enum": [true, false] },
                    "mixed": { "enum": ["red", 1] }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("long", mapping.at("/mappings/properties/level/type").asText());
        assertEquals("boolean", mapping.at("/mappings/properties/enabled/type").asText());
        assertEquals("keyword", mapping.at("/mappings/properties/mixed/type").asText());
    }

    @Test
    void testConstMapping() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "fixed_code": { "const": "ABC" },
                    "fixed_num": { "const": 42 },
                    "fixed_price": { "const": 9.99 }
                  }
                }
                """;
        JsonNode mapping = generate(schema, Set.of("fixed_price"), 10000);
        assertEquals("keyword", mapping.at("/mappings/properties/fixed_code/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/fixed_num/type").asText());
        assertEquals("scaled_float", mapping.at("/mappings/properties/fixed_price/type").asText());
    }

    @Test
    void testPureNullTypeGraceful() throws Exception {
        JsonNode mapping = generate("{ \"type\": \"object\", \"properties\": { \"nothing\": { \"type\": \"null\" } } }");
        assertTrue(mapping.at("/mappings/properties/nothing").isMissingNode());
    }

    @Test
    void testPolymorphicRootMapping() throws Exception {
        String schema = """
                {
                  "oneOf": [
                    { "type": "object", "properties": { "f1": { "type": "string" } } },
                    { "type": "object", "properties": { "f2": { "type": "integer" } } }
                  ]
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("false", mapping.at("/mappings/dynamic").asText());
        assertEquals("text", mapping.at("/mappings/properties/f1/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/f2/type").asText());
    }

    @Test
    void testSnapshotFullStructure() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "count": { "type": "integer" },
                    "items": { "type": "array", "items": { "type": "object", "properties": { "label": { "type": "string" } } } }
                  }
                }
                """;
        String expectedJson = """
                {
                  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
                  "mappings": {
                    "properties": {
                      "name": { "type": "text", "analyzer": "standard", "fields": { "keyword": { "type": "keyword", "ignore_above": 1024 } } },
                      "count": { "type": "long" },
                      "items": {
                        "type": "nested",
                        "properties": {
                          "label": { "type": "text", "analyzer": "standard", "fields": { "keyword": { "type": "keyword", "ignore_above": 1024 } } }
                        }
                      }
                    }
                  }
                }
                """;
        assertEquals(MAPPER.readTree(expectedJson), generate(schema));
    }

    @Test
    void testCustomMoneyKeywordsAndScalingFactor() throws Exception {
        String schema = """
                { "type": "object", "properties": { "custom_val": { "type": "number" }, "normal_val": { "type": "number" } } }
                """;
        JsonNode mapping = generate(schema, Set.of("custom_val"), 100);
        assertEquals("scaled_float", mapping.at("/mappings/properties/custom_val/type").asText());
        assertEquals(100, mapping.at("/mappings/properties/custom_val/scaling_factor").asInt());
        assertEquals("double", mapping.at("/mappings/properties/normal_val/type").asText());
    }

    @Test
    void testRefChainCycleDetected() {
        String cycleSchema = """
                {
                  "type": "object",
                  "properties": { "a": { "$ref": "#/$defs/A" } },
                  "$defs": { "A": { "$ref": "#/$defs/B" }, "B": { "$ref": "#/$defs/A" } }
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> generate(cycleSchema));
    }

    @Test
    void testStructuralObjectFieldRecursionCapped() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": { "a": { "$ref": "#/$defs/nodeA" } },
                  "$defs": {
                    "nodeA": { "type": "object", "properties": { "b": { "$ref": "#/$defs/nodeB" } } },
                    "nodeB": { "type": "object", "properties": { "a": { "$ref": "#/$defs/nodeA" } } }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("object", mapping.at("/mappings/properties/a/type").asText());
        assertEquals("object", mapping.at("/mappings/properties/a/properties/b/type").asText());
        assertEquals("flattened", mapping.at("/mappings/properties/a/properties/b/properties/a/properties/b/properties/a/properties/b/properties/a/properties/b/type").asText());
    }

    private static JsonNode generate(String schemaJson) throws Exception {
        return generate(schemaJson, null, 10000);
    }

    private static JsonNode generate(String schemaJson, Set<String> moneyFields, int moneyScalingFactor) throws Exception {
        Map<String, Object> map = JsonSchemaToEsMappingUtil.generateIndexMapping(schemaJson, moneyFields, moneyScalingFactor);
        return MAPPER.readTree(JsonSchemaToEsMappingUtil.toJson(map));
    }
}
