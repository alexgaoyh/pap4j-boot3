package cn.net.pap.common.jsqlparser;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonToSqlConverterTest {

    private static final Logger log = LoggerFactory.getLogger(JsonToSqlConverterTest.class);

    @Test
    public void testConvertJson2Sql1() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("json2sql1.json")) {
            assertNotNull(is, "json2sql1.json not found in resources");
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String sql = JsonToSqlConverter.convert(json);
            log.info("Converted SQL:\n{}", sql);

            assertTrue(sql.contains("SELECT classes.grade, COUNT(students.id) AS student_count"));
            assertTrue(sql.contains("FROM students"));
            assertTrue(sql.contains("LEFT JOIN classes ON students.class_id = classes.id"));
            assertTrue(sql.contains("WHERE (classes.grade = '三年级' AND (students.hometown = '北京' OR students.hometown = '上海'))"));
            assertTrue(sql.contains("GROUP BY classes.grade"));
            assertTrue(sql.contains("ORDER BY student_count DESC"));
            assertTrue(sql.contains("LIMIT 10 OFFSET 0"));
        }
    }

    @Test
    public void testVariousOperators() throws IOException {
        String json = """
                {
                  "main_table": "products",
                  "filters": [
                    {
                      "logic": "AND",
                      "conditions": [
                        {
                          "field": "name",
                          "operator": "LIKE",
                          "value": "%phone%"
                        },
                        {
                          "field": "status",
                          "operator": "IN",
                          "value": ["ACTIVE", "PENDING"]
                        },
                        {
                          "field": "deleted_at",
                          "operator": "IS_NULL"
                        },
                        {
                          "field": "price",
                          "operator": "GREATER_OR_EQUALS",
                          "value": 99.99
                        }
                      ]
                    }
                  ]
                }
                """;
        String sql = JsonToSqlConverter.convert(json);
        log.info("Generated product SQL: {}", sql);
        assertTrue(sql.contains("SELECT * FROM products"));
        assertTrue(sql.contains("name LIKE '%phone%'"));
        assertTrue(sql.contains("status IN ('ACTIVE', 'PENDING')"));
        assertTrue(sql.contains("deleted_at IS NULL"));
        assertTrue(sql.contains("price >= 99.99"));
    }

    @Test
    public void testValidationRequiredFields() {
        // missing main_table
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert("{\"limit\": 5}"));

        // missing join_table
        String missingJoinTable = """
                {
                  "main_table": "students",
                  "joins": [{"on_left": "a", "on_right": "b"}]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert(missingJoinTable));

        // missing agg function
        String missingAggFunc = """
                {
                  "main_table": "students",
                  "aggs": [{"field": "id"}]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert(missingAggFunc));

        // missing order field
        String missingOrderField = """
                {
                  "main_table": "students",
                  "orders": [{"direction": "DESC"}]
                }
                """;
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert(missingOrderField));
    }

    @Test
    public void testFallbackMechanisms() throws IOException {
        // Select specified columns when groups/aggs are absent
        String sqlWithCols = JsonToSqlConverter.convert("{\"main_table\": \"students\", \"columns\": [\"id\", \"name\"]}");
        assertTrue(sqlWithCols.startsWith("SELECT id, name FROM students"));

        // Default Join Type (LEFT) and default operator (EQUALS) and default order direction (ASC)
        String json = """
                {
                  "main_table": "students",
                  "joins": [{"join_table": "classes", "on_left": "students.class_id", "on_right": "classes.id"}],
                  "filters": [{"field": "classes.grade", "value": "三年级"}],
                  "orders": [{"field": "students.name"}]
                }
                """;
        String sql = JsonToSqlConverter.convert(json);
        log.info("Fallback SQL: {}", sql);
        assertTrue(sql.contains("LEFT JOIN classes"));
        assertTrue(sql.contains("classes.grade = '三年级'"));
        assertTrue(sql.contains("ORDER BY students.name ASC"));
    }

    @Test
    public void testNullSafety() {
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert(null));
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert("   "));
        assertThrows(IllegalArgumentException.class, () -> JsonToSqlConverter.convert("null"));
    }
}
