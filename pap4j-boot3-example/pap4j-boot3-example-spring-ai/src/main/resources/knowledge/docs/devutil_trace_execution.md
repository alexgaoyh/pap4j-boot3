## 执行逻辑追踪（Trace Execution）

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：开发调试工具
> - **类型**：Java API / 开发工具
> - **关键字**：trace, 链路过程, 计算步骤



QLExpress 引擎提供了执行链路追踪功能，通过在执行时开启 `traceExpression` 选项，可以获取脚本每一步计算的详细过程记录。每个操作数在计算过程中的实际值都会被记录下来，非常适合排查计算结果异常或调试复杂表达式。

追踪结果可以通过 `toPrettyString()` 方法以格式化的字符串形式输出，让计算过程的每一步都清晰可见。

**适用场景**：
- 一个复杂的嵌套表达式执行后返回了意外的结果，想知道哪个中间计算步骤出了问题。
- 涉及多个 json 字段组合运算时，需要确认每个字段的实际取值是否符合预期。
- 调试 DIV2、SUBSTRING 等函数的参数传入是否正确。

**使用方式**：在创建 QLExpress Runner 时开启 traceExpression 参数，执行后通过 runner 获取 traceInfo 对象，再调用 toPrettyString() 输出完整的计算链路日志，逐步核对每个操作数的实际值与预期值是否一致。

**Java 侧执行追踪示例**：
```java
import com.ql.util.express.ExpressRunner;
import com.ql.util.express.DefaultContext;
import java.util.ArrayList;
import java.util.List;

public class RuleDebug {
    public static void main(String[] args) throws Exception {
        ExpressRunner runner = new ExpressRunner();
        DefaultContext<String, Object> context = new DefaultContext<>();
        context.put("a", 10);
        context.put("b", 20);
        
        String expression = "a * (b + 5)";
        List<String> errorList = new ArrayList<>();
        
        // 执行规则并开启 trace 追踪每一步操作数和操作符的值
        Object result = runner.execute(expression, context, errorList, true, true);
        System.out.println("执行结果: " + result);
    }
}
```
