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
                },
                "order_date": { "type": "string", "format": "date" },
                "is_paid": { "type": "boolean" },
                "discount": { "type": "number", "enum": [0.0, 0.05, 0.1] },
                "currency": { "const": "CNY" },
                "tags": { "type": "array", "items": { "type": "string" } },
                "coords": { "type": "array", "items": [ { "type": "number" }, { "type": "number" } ] },
                "custom_attributes": { "type": "array", "items": { "type": "object" } },
                "order_items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "additionalProperties": false,
                    "properties": {
                      "sku": { "type": "string" },
                      "qty": { "type": "integer" },
                      "unit_price": { "type": "number" }
                    }
                  }
                },
                "customer": { "$ref": "#/$defs/customer" }
              },
              "$defs": {
                "customer": {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "vip_level": { "type": "integer", "enum": [1, 2, 3] }
                  }
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
                "active": { "type": "boolean" },
                "level": { "type": "integer", "enum": [1, 2, 3, 4] },
                "tags": { "type": "array", "items": { "type": "string" } },
                "children": { "type": "array", "items": { "$ref": "#" } }
              }
            }
            """;

    private static final String PRODUCT_SCHEMA = """
            {
              "type": "object",
              "title": "商品模型",
              "additionalProperties": false,
              "properties": {
                "sku": { "type": "string" },
                "price": { "type": "number" },
                "available": { "type": "boolean" },
                "stock": { "type": "integer" },
                "phone_support": { "type": "string", "format": "phone" },
                "specs": { "properties": { "color": { "type": "string" }, "size": { "type": "string" } } },
                "photo_ids": { "items": { "type": "string" } },
                "freeform": {},
                "attributes": { "type": "array" },
                "promo": {
                  "type": "object",
                  "properties": { "active": { "type": "boolean" }, "price": { "type": "number" } },
                  "if": { "properties": { "active": { "const": true } } },
                  "then": { "properties": { "promo_price": { "type": "number" }, "price": { "type": "number" } } },
                  "else": { "properties": { "markdown": { "type": "boolean" } } }
                },
                "vendor": {
                  "oneOf": [
                    { "type": "object", "properties": { "name": { "type": "string" } } },
                    { "type": "null" }
                  ]
                }
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

        // 补充字段覆盖: 自然日期 / boolean / number-enum→double / const / 标量数组 / tuple 数组 / 无结构对象数组 / nested 对象数组 / $ref
        assertEquals("date", mapping.at("/mappings/properties/order_date/type").asText());
        assertEquals("strict_date", mapping.at("/mappings/properties/order_date/format").asText());
        assertEquals("boolean", mapping.at("/mappings/properties/is_paid/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/discount/type").asText());
        assertEquals("keyword", mapping.at("/mappings/properties/currency/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/tags/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/coords/type").asText());
        assertEquals("flattened", mapping.at("/mappings/properties/custom_attributes/type").asText());
        assertEquals("nested", mapping.at("/mappings/properties/order_items/type").asText());
        assertEquals("false", mapping.at("/mappings/properties/order_items/dynamic").asText());
        assertEquals("text", mapping.at("/mappings/properties/order_items/properties/sku/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/order_items/properties/qty/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/order_items/properties/unit_price/type").asText());
        assertEquals("object", mapping.at("/mappings/properties/customer/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/customer/properties/name/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/customer/properties/vip_level/type").asText());
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

        // 递归节点补充字段: boolean / integer-enum→long / 标量数组（根层与子层均出现）
        assertEquals("boolean", mapping.at("/mappings/properties/active/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/level/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/tags/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/children/properties/tags/type").asText());
    }

    @Test
    void testProductCatalogSchemaMapping() throws Exception {
        JsonNode mapping = generate(PRODUCT_SCHEMA);

        // 常规类型
        assertEquals("text", mapping.at("/mappings/properties/sku/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/price/type").asText());
        assertEquals("boolean", mapping.at("/mappings/properties/available/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/stock/type").asText());

        // 未知 format → keyword（mapString default 分支）
        assertEquals("keyword", mapping.at("/mappings/properties/phone_support/type").asText());

        // 无 type 推断: 有 properties → object；有 items → array；空 schema → text
        assertEquals("object", mapping.at("/mappings/properties/specs/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/specs/properties/color/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/photo_ids/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/freeform/type").asText());

        // 数组无 items → text
        assertEquals("text", mapping.at("/mappings/properties/attributes/type").asText());

        // if/then/else: then 重复 base 的 price → base 优先(跳过)，promo_price/markdown 正常合并
        assertEquals("object", mapping.at("/mappings/properties/promo/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/promo/properties/price/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/promo/properties/promo_price/type").asText());
        assertEquals("boolean", mapping.at("/mappings/properties/promo/properties/markdown/type").asText());

        // oneOf 含 null 分支 → isAllObjectBranches 跳过 null，仅合并 object 分支
        assertEquals("object", mapping.at("/mappings/properties/vendor/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/vendor/properties/name/type").asText());
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
    void testArrayItemsOneOfMapping() throws Exception {
        // 标量 oneOf 元素 → 数组降级 flattened（修复：不再产出裸 nested）
        String scalarOneOfArray = """
                {
                  "type": "object",
                  "properties": {
                    "values": { "type": "array", "items": { "oneOf": [ { "type": "string" }, { "type": "number" } ] } }
                  }
                }
                """;
        JsonNode scalar = generate(scalarOneOfArray);
        assertEquals("flattened", scalar.at("/mappings/properties/values/type").asText());

        // object oneOf 元素 → 仍为 nested，properties 为分支并集（回归保护）
        String objectOneOfArray = """
                {
                  "type": "object",
                  "properties": {
                    "contacts": { "type": "array", "items": { "oneOf": [
                      { "type": "object", "properties": { "phone": { "type": "string" } } },
                      { "type": "object", "properties": { "email": { "type": "string" } } }
                    ] } }
                  }
                }
                """;
        JsonNode obj = generate(objectOneOfArray);
        assertEquals("nested", obj.at("/mappings/properties/contacts/type").asText());
        assertEquals("text", obj.at("/mappings/properties/contacts/properties/phone/type").asText());
        assertEquals("text", obj.at("/mappings/properties/contacts/properties/email/type").asText());
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

    @Test
    void testAllOfSchemaCompositionAndInheritanceWithLocalOverride() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "user": {
                      "type": "object",
                      "properties": {
                        "vip_level": { "type": "integer" },
                        "created_at": { "type": "string", "format": "date" }
                      },
                      "allOf": [
                        { "$ref": "#/$defs/BaseEntity" },
                        { "$ref": "#/$defs/ContactInfo" }
                      ]
                    }
                  },
                  "$defs": {
                    "BaseEntity": {
                      "type": "object",
                      "properties": {
                        "id": { "type": "string" },
                        "created_at": { "type": "string", "format": "date-time" }
                      }
                    },
                    "ContactInfo": {
                      "type": "object",
                      "properties": {
                        "email": { "type": "string", "format": "email" },
                        "phone": { "type": "string" }
                      }
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        // user should be an object mapping
        assertEquals("object", mapping.at("/mappings/properties/user/type").asText());
        // inherited from BaseEntity
        assertEquals("text", mapping.at("/mappings/properties/user/properties/id/type").asText());
        // inherited from ContactInfo
        assertEquals("keyword", mapping.at("/mappings/properties/user/properties/email/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/user/properties/phone/type").asText());
        // local properties
        assertEquals("long", mapping.at("/mappings/properties/user/properties/vip_level/type").asText());
        // local created_at (format: date) overrides BaseEntity's created_at (format: date-time)
        assertEquals("date", mapping.at("/mappings/properties/user/properties/created_at/type").asText());
        assertEquals("strict_date", mapping.at("/mappings/properties/user/properties/created_at/format").asText());
    }

    @Test
    void testAllOfPeerBranchConflictDegradesToFlattened() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "allOf": [
                        { "properties": { "status": { "type": "integer" }, "name": { "type": "string" } } },
                        { "properties": { "status": { "type": "string" }, "age": { "type": "integer" } } }
                      ]
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("object", mapping.at("/mappings/properties/profile/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/profile/properties/name/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/profile/properties/age/type").asText());
        // conflicting status field (integer vs string) degrades to flattened (consistent with oneOf)
        assertEquals("flattened", mapping.at("/mappings/properties/profile/properties/status/type").asText());
    }

    @Test
    void testNestedAllOfDeepInheritance() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "audit_record": {
                      "allOf": [
                        { "$ref": "#/$defs/MidLevel" },
                        { "properties": { "action": { "type": "string" } } }
                      ]
                    }
                  },
                  "$defs": {
                    "GrandBase": {
                      "properties": { "trace_id": { "type": "string" } }
                    },
                    "MidLevel": {
                      "allOf": [
                        { "$ref": "#/$defs/GrandBase" },
                        { "properties": { "operator_id": { "type": "integer" } } }
                      ]
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("object", mapping.at("/mappings/properties/audit_record/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/audit_record/properties/trace_id/type").asText());
        assertEquals("long", mapping.at("/mappings/properties/audit_record/properties/operator_id/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/audit_record/properties/action/type").asText());
    }

    @Test
    void testAllOfWithOneOfMixed() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "payload": {
                      "allOf": [
                        { "properties": { "common_id": { "type": "string" } } },
                        {
                          "oneOf": [
                            { "properties": { "wx_openid": { "type": "string" } } },
                            { "properties": { "ali_uid": { "type": "string" } } }
                          ]
                        }
                      ]
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("object", mapping.at("/mappings/properties/payload/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/payload/properties/common_id/type").asText());
    }

    @Test
    void testScalarAllOfFallsBackToFlattened() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "unstructured": {
                      "allOf": [
                        { "type": "string" },
                        { "type": "integer" }
                      ]
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("flattened", mapping.at("/mappings/properties/unstructured/type").asText());
    }

    @Test
    void testRootLevelAllOf() throws Exception {
        String schema = """
                {
                  "allOf": [
                    {
                      "properties": {
                        "org_id": { "type": "integer" }
                      }
                    },
                    {
                      "properties": {
                        "org_name": { "type": "string" }
                      }
                    }
                  ]
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("long", mapping.at("/mappings/properties/org_id/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/org_name/type").asText());
    }

    @Test
    void testArrayItemsWithAllOf() throws Exception {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "items_list": {
                      "type": "array",
                      "items": {
                        "allOf": [
                          {
                            "properties": {
                              "sku_id": { "type": "string" }
                            }
                          },
                          {
                            "properties": {
                              "price": { "type": "number" }
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        JsonNode mapping = generate(schema);
        assertEquals("nested", mapping.at("/mappings/properties/items_list/type").asText());
        assertEquals("text", mapping.at("/mappings/properties/items_list/properties/sku_id/type").asText());
        assertEquals("double", mapping.at("/mappings/properties/items_list/properties/price/type").asText());
    }

    private static JsonNode generate(String schemaJson) throws Exception {
        return generate(schemaJson, null, 10000);
    }

    private static JsonNode generate(String schemaJson, Set<String> moneyFields, int moneyScalingFactor) throws Exception {
        Map<String, Object> map = JsonSchemaToEsMappingUtil.generateIndexMapping(schemaJson, moneyFields, moneyScalingFactor);
        return MAPPER.readTree(JsonSchemaToEsMappingUtil.toJson(map));
    }
}
