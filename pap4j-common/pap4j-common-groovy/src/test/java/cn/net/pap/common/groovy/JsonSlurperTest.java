package cn.net.pap.common.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * JsonSlurper 单元测试工具类
 * 演示 Groovy 在数据解析领域的便捷性
 */
public class JsonSlurperTest {

    private static final Logger log = LoggerFactory.getLogger(JsonSlurperTest.class);

    @Test
    public void testJsonParsing() {
        // --- 原始数据 (Raw Data) ---
        String rawJson = """
            {
                "user": {
                    "id": 1001,
                    "name": "Alex",
                    "roles": ["ADMIN", "DEVELOPER"],
                    "address": {
                        "city": "Shanghai",
                        "zip": "200000"
                    }
                },
                "status": "success",
                "timestamp": "2024-03-20T10:00:00Z"
            }
            """;

        // --- Groovy 解析脚本 (Groovy Script) ---
        // 相比 Java 需要定义大量的 POJO 或使用复杂的 Map 嵌套访问，
        // Groovy 的 GPath 语法可以通过点操作符轻松触达深层属性。
        String groovyCode = """
            import groovy.json.JsonSlurper
            
            def parse(jsonText) {
                def slurper = new JsonSlurper()
                def root = slurper.parseText(jsonText)
                
                // 利用 Groovy 动态特性进行深层导航
                return [
                    userName: root.user.name,
                    primaryRole: root.user.roles[0],
                    city: root.user.address.city,
                    isSuccess: root.status == 'success'
                ]
            }
            """;

        GroovyShell shell = new GroovyShell();
        Script script = shell.parse(groovyCode);
        
        // 调用 Groovy 函数并解析结果
        Object resultObj = script.invokeMethod("parse", new Object[]{rawJson});
        assertNotNull(resultObj);
        
        Map<String, Object> resultMap = (Map<String, Object>) resultObj;
        
        log.info("解析后的结果: {}", resultMap);

        // 验证解析正确性
        assertEquals("Alex", resultMap.get("userName"));
        assertEquals("ADMIN", resultMap.get("primaryRole"));
        assertEquals("Shanghai", resultMap.get("city"));
        assertEquals(true, resultMap.get("isSuccess"));
    }
}
