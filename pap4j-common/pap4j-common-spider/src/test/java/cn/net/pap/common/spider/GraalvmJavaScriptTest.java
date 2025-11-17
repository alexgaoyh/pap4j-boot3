package cn.net.pap.common.spider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraalvmJavaScriptTest {
    
    @Test
    public void test1() {
        try (Context context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()) {
            // 只使用 JavaScript
            String jsCode = """
                function calculate(a, b) {
                    return a * b + 10;
                }
                calculate(5, 3);
                """;
            Value result = context.eval("js", jsCode);
            System.out.println("计算结果: " + result.asInt()); // 输出: 计算结果: 25
            // 调用 JavaScript 函数
            Value calculateFunc = context.eval("js", "calculate");
            Value result2 = calculateFunc.execute(7, 2);
            System.out.println("函数调用结果: " + result2.asInt()); // 输出: 函数调用结果: 24
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test2() {
        try (Context context = Context.create("js")) {

            List<Map<String, Object>> sourceData = new ArrayList<>();
            sourceData.add(Map.of("firstName", "alex", "lastName", "gaoyh"));
            sourceData.add(Map.of("firstName", "http", "lastName", "pap.net.cn"));

            // 转换为 JSON 字符串
            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writeValueAsString(sourceData);

            context.getBindings("js").putMember("sourceDataJson", jsonData);

            String script = """
                function transform() {
                    const sourceData = JSON.parse(sourceDataJson);
                    return sourceData.map(item => {
                        return {
                            firstName: item.firstName,
                            lastName: item.lastName,
                            fullName: item.firstName + ' ' + item.lastName
                        };
                    });
                }
                transform();
            """;
            Value result = context.eval("js", script);
            List resultList = result.as(List.class);
            System.out.println(resultList);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
