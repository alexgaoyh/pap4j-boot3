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
 * XmlParser 单元测试工具类
 * 演示 Groovy GPath 在 XML 解析领域的极致体验
 */
public class GPathTest {

    private static final Logger log = LoggerFactory.getLogger(GPathTest.class);

    @Test
    public void testXmlParsing() {
        // --- 原始数据 (Raw Data) ---
        // 模拟一个带有属性和嵌套结构的 XML
        String rawXml = """
            <response status="success">
                <user id="1001">
                    <name>Alex</name>
                    <roles>
                        <role type="primary">ADMIN</role>
                        <role type="secondary">DEVELOPER</role>
                    </roles>
                    <address city="Shanghai">
                        <zip>200000</zip>
                    </address>
                </user>
                <timestamp>2024-03-20T10:00:00Z</timestamp>
            </response>
            """;

        // --- Groovy 解析脚本 (Groovy Script) ---
        // Groovy 的 XmlParser 将 XML 转换为 Node 树。
        // 注意：response.user 这种写法在 GPath 中返回的是一个 NodeList（包含所有匹配的子节点）。
        String groovyCode = """
            import groovy.xml.XmlParser
            
            def parse(xmlText) {
                def response = new XmlParser().parseText(xmlText)
                
                // GPath 语法：
                // XML 结构的“多重性”本质。在 XML 规范中，任何一个标签（Element）在同级下都可以出现 0 次、1 次或多次。
                // 在 XmlParser 的 GPath 中，.（点操作符）其实是“向下寻找所有匹配该名称的子元素”。既然是“寻找所有”，结果自然是一个集合。写 [0] 就是在告诉代码：“我知道这里肯定只有一个，或者我只想要第一个”。
                // 1. response.attribute('status') 直接访问根节点属性
                // 2. response.user[0].name.text() 访问子节点文本
                // 3. response.user.address[0].attribute('city') 访问嵌套节点的属性
                return [
                    status: response.attribute('status'),
                    userName: response.user[0].name.text(),
                    primaryRole: response.user[0].roles.role.find { it.attribute('type') == 'primary' }.text(),
                    city: response.user[0].address[0].attribute('city'),
                    zip: response.user[0].address[0].zip.text()
                ]
            }
            """;

        GroovyShell shell = new GroovyShell();
        Script script = shell.parse(groovyCode);
        
        // 调用 Groovy 函数
        Object resultObj = script.invokeMethod("parse", new Object[]{rawXml});
        assertNotNull(resultObj);
        
        Map<String, Object> resultMap = (Map<String, Object>) resultObj;
        
        log.info("XML 解析后的结果: {}", resultMap);

        // 验证解析正确性
        assertEquals("success", resultMap.get("status"));
        assertEquals("Alex", resultMap.get("userName"));
        assertEquals("ADMIN", resultMap.get("primaryRole"));
        assertEquals("Shanghai", resultMap.get("city"));
        assertEquals("200000", resultMap.get("zip"));
    }
}
