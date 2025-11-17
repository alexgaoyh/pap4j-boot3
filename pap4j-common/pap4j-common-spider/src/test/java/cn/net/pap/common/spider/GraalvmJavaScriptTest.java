package cn.net.pap.common.spider;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

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
    
}
