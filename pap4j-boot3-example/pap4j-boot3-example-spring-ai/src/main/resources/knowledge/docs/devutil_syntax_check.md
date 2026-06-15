## 语法校验与自检（Syntax Check）

> **元数据**
> - **组件/引擎**：QLExpress
> - **分类/模块**：开发调试工具
> - **类型**：Java API / 开发工具
> - **关键字**：check, 校验, 报错定位



QLExpress 引擎提供了语法静态检查功能，通过调用 `runner.check()` 方法可以在不实际执行脚本的前提下，对脚本进行语法层面的静态分析和验证。这个功能非常适合在开发阶段或配置阶段对脚本进行预检，帮助开发者快速定位脚本的语法错误。

check 方法会分析脚本的语法结构，如果存在问题，它会返回包含行号和列号的错误信息，帮助开发者精确定位到出错的位置。

**适用场景**：
- 用户在界面上提交了一段新的规则脚本，在保存前可以先调用 check 进行语法预校验。
- 开发阶段编写了复杂的嵌套表达式，可以先 check 确认括号匹配是否正确。
- 怀疑脚本中有关键字拼写错误（如 `rreturn`、`returnn` 等），可以通过 check 的报错信息快速定位。

**调试技巧**：当 check 报告某行某列有错误时，重点检查该位置附近的括号是否匹配、是否有未闭合的字符串引号、关键字是否拼写正确。

**Java 侧静态校验示例**：
```java
import com.ql.util.express.ExpressRunner;
import java.util.ArrayList;
import java.util.List;

public class RuleValidator {
    public static void main(String[] args) {
        ExpressRunner runner = new ExpressRunner();
        String expression = "json.score > 60 return json.status"; // 缺失分号语句
        
        List<String> errorList = new ArrayList<>();
        // checkInstruction 方法在不实际执行的情况下，校验语法并收集错误信息
        boolean isOk = runner.checkInstruction(expression, errorList);
        if (!isOk) {
            System.err.println("语法错误: " + errorList);
        }
    }
}
```
