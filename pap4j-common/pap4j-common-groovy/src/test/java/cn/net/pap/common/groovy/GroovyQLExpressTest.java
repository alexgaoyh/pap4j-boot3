package cn.net.pap.common.groovy;

import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 跨模块集成测试：Groovy + QLExpress + JsonPath
 * 演示 Groovy 如何编排 QLExpress 的能力进行复杂数据处理
 */
public class GroovyQLExpressTest {

    private static final Logger log = LoggerFactory.getLogger(GroovyQLExpressTest.class);

    @Test
    public void testGroovyCallingQLExpress() {
        // 1. 原始 JSON 数据
        String rawJson = """
            {
                "orderId": "ORD-20240320-001",
                "items": [
                    {"name": "Java Book", "price": 100, "category": "tech"},
                    {"name": "Groovy Guide", "price": 80, "category": "tech"},
                    {"name": "Coffee Cup", "price": 20, "category": "lifestyle"}
                ],
                "discountCode": " SPRING2024 "
            }
            """;

        // 2. Groovy 脚本内容
        // 脚本逻辑：
        // a. 使用 QLExpress 的 JSON_PATH 函数提取数据
        // b. 使用 QLExpress 的 TRIM 和 UPPER 函数处理字符串
        // c. 在 Groovy 中完成最终结果的组装
        String groovyScript = """
            // QLExpress 的功能被封装在 Express4RunnerUtil 中
            import cn.net.pap.common.qlexpress.Express4RunnerUtil
            import com.alibaba.qlexpress4.QLOptions
            
            def processOrder(jsonText) {
                // 将 JSON 文本转换为 QLExpress 需要的上下文对象
                def document = com.jayway.jsonpath.JsonPath.parse(jsonText).json()
                def context = [json: document, data: jsonText]
                
                // 定义 QLExpress 表达式
                // 结合了 JsonPath 提取 和 QLExpress 自定义函数 (UPPER, TRIM)
                // 注意：在 Groovy 的双引号/三引号字符串中，$ 符号需要转义，除非是用于变量插值
                String qlExpr = "UPPER(TRIM(JSON_PATH(json, '\\$.discountCode')))"
                
                // 调用 QLExpress 执行引擎 (跨模块调用)
                def processedCode = Express4RunnerUtil.runner.execute(qlExpr, context, QLOptions.DEFAULT_OPTIONS).getResult()
                
                // 同时也利用 Groovy 自己的便捷语法计算总价
                def totalPrice = document.items.sum { it.price }
                
                return [
                    finalDiscountCode: processedCode,
                    totalPrice: totalPrice,
                    itemCount: document.items.size()
                ]
            }
            """;

        // 3. 执行 Groovy 脚本
        GroovyShell shell = new GroovyShell();
        Script script = shell.parse(groovyScript);
        Object resultObj = script.invokeMethod("processOrder", new Object[]{rawJson});
        
        Map<String, Object> resultMap = (Map<String, Object>) resultObj;
        log.info("混合处理结果: {}", resultMap);

        // 4. 验证结果
        // 验证 QLExpress 的处理结果 (TRIM + UPPER)
        assertEquals("SPRING2024", resultMap.get("finalDiscountCode"));
        // 验证 Groovy 的求和结果
        assertEquals(200, resultMap.get("totalPrice"));
        assertEquals(3, resultMap.get("itemCount"));
    }
}
