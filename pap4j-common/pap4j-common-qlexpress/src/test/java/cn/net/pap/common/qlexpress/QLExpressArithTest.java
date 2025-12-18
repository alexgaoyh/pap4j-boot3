package cn.net.pap.common.qlexpress;

import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.alibaba.qlexpress4.QLResult;
import com.alibaba.qlexpress4.exception.QLSyntaxException;
import com.alibaba.qlexpress4.runtime.trace.ExpressionTrace;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QLExpressArithTest {

    @Test
    public void test1() {
        Map<String, Integer> context = new HashMap<>();
        context.put("a", 10);
        context.put("b", 5);
        context.put("c", 2);

        String[][] tests = new String[][]{
                // 基础运算
                {"a + b", "15"}, {"a - b", "5"}, {"a * b", "50"}, {"a / b", "2"},

                // 优先级测试
                {"a + b * c", "20"}, {"(a + b) * c", "30"}, {"a * b + c", "52"}, {"a * (b + c)", "70"},

                // 结合性测试（减法和除法左结合）
                {"a - b - c", "3"}, {"a - (b - c)", "7"}, {"a / b / c", "1"}, {"a / (b / c)", "4"},

                // 括号测试
                {"(a - b) * c", "10"}, {"(a + b) / c", "7"}, {"(a * b) / c", "25"}, {"(a - b) + (c * b)", "15"},  // 10-5 + 2*5 = 5 + 10 = 15

                // 多运算符组合
                {"a + b + c", "17"},           // 10+5+2
                {"a * b * c", "100"},          // 10*5*2
                {"a - b + c", "7"},            // 10-5+2
                {"a + b - c", "13"},           // 10+5-2

                // 同级运算符混合
                {"a * b / c", "25"},           // 10*5/2 = 50/2 = 25
                {"a / b * c", "4"},            // 10/5*2 = 2*2 = 4
                {"a + b - c + a", "23"},       // 10+5-2+10
                {"a * b / c * a", "250"},      // 10*5/2*10 = 50/2*10 = 25*10 = 250

                // 多层括号嵌套
                {"((a + b) * c) / b", "6"},    // (15*2)/5 = 30/5 = 6
                {"(a * (b + c)) / a", "7"},    // (10*7)/10 = 70/10 = 7
                {"a * (b + (c * a))", "250"},  // 10*(5+20) = 10*25 = 250
                {"((a - b) + (c * b)) * c", "30"}, // (5+10)*2 = 15*2 = 30

                // 复杂混合运算
                {"a + b * c + a", "30"},       // 10+10+10 = 30
                {"a * b + c * b", "60"},       // 50+10 = 60
                {"(a + b) * (c + b)", "105"},  // 15*7 = 105
                {"a * b - c * a", "30"},       // 50-20 = 30

                // 结合性边界测试
                {"a - b - c - a", "-7"},       // 10-5-2-10 = -7
                {"a / b / c / a", "0"},        // 10/5/2/10 = 2/2/10 = 1/10 = 0 (整数除法)
                {"a - b + c - a", "-3"},        // 10-5+2-10 = -3

                // 特殊值组合
                {"0 + a + b", "15"},           // 0+10+5
                {"1 * a * b", "50"},           // 1*10*5
                {"a * 0 + b", "5"},            // 0+5
                {"a + 0 * b", "10"},           // 10+0

                // 更多括号变体
                {"(a + b) * (c * a)", "300"},  // 15*20 = 300
                {"(a / b) * (b * c)", "20"},   // 2*10 = 20
                {"(a * c) + (b * c)", "30"},   // 20+10 = 30
                {"(a - c) * (b + c)", "56"},   // 8*7 = 56

                // 深度嵌套
                {"a * (b + (c * (a - b)))", "150"}, // 10*(5+2*5) = 10*(5+10) = 10*15 = 150
                {"(a + (b * (c + a))) / b", "14"},   // (10 + (5 * (2 + 10))) / 5 = (10 + (5 * 12)) / 5 = (10 + 60) / 5 = 70 / 5 = 14

                // 对称性测试
                {"b + a", "15"}, {"b - a", "-5"}, {"b * a", "50"}, {"b / a", "0"},                // 5/10 = 0 (整数除法)

                // 复杂优先级组合
                {"a + b * c - a / b", "18"},   // 重新计算：a + (b*c) - (a/b) = 10 + 10 - 2 = 18
                // 保持原样测试
                {"(a + b) * c - a / b", "28"}, // 15*2-2 = 30-2 = 28
                {"a * (b - c) + b / c", "32"}, // a*(b-c) = 10*3 = 30, b/c=2, 30+2=32

                // 边界条件：除数为1
                {"a / 1", "10"}, {"(a + b) / 1", "15"},

                // 边界条件：乘数为1
                {"a * 1", "10"}, {"1 * (a + b)", "15"},

                // 边界条件：加0
                {"a + 0", "10"}, {"0 + a + b + c", "17"},

                // 边界条件：减0
                {"a - 0", "10"}, {"(a + b) - 0", "15"},

                // 混合复杂表达式
                {"a * b - a / b + c", "50"},   // 50-2+2=50
                {"(a + b * c) / (a - b)", "4"}, // (10+10)/5 = 20/5 = 4
                {"a * (b + c) - b * c", "60"}, // 10*7 - 10 = 70-10 = 60
        };

        for (String[] test : tests) {
            String expr = test[0];
            int expected = Integer.parseInt(test[1]);
            Object result = Express4RunnerUtil.runner.execute(expr, context, QLOptions.DEFAULT_OPTIONS).getResult();
            assertEquals(expected, ((Number) result).intValue(), "表达式测试失败: " + expr);
        }
    }

    @Test
    public void test2() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 10);
        context.put("b", 3);

        Object result = Express4RunnerUtil.runner.execute("DIV2(a, b)", context, QLOptions.DEFAULT_OPTIONS).getResult();
        assertEquals(3.33, ((Number) result).doubleValue(), "表达式测试失败: " + "DIV2(a, b)");
    }

    @Test
    public void test3() {
        Map<String, Object> context = new HashMap<>();

        Object result1 = Express4RunnerUtil.runner.execute("TERNARY(ISBLANK('A1'),UPPER(''),UPPER('K'))", context, QLOptions.DEFAULT_OPTIONS).getResult();
        assertEquals("K", result1.toString(), "表达式测试失败");

        Object result2 = Express4RunnerUtil.runner.execute("TERNARY(ISBLANK(''),UPPER(''),UPPER('K'))", context, QLOptions.DEFAULT_OPTIONS).getResult();
        assertEquals("", result2.toString(), "表达式测试失败");
    }

    @Test
    public void test4() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 10);
        context.put("b", 5);
        context.put("c", 2);

        Express4Runner runner = new Express4Runner(InitOptions.builder().traceExpression(true).build());
        QLResult result = runner.execute("(a + b * c) / (a - b)", context, QLOptions.builder().traceExpression(true).build());
        List<ExpressionTrace> expressionTraces = result.getExpressionTraces();
        System.out.println(expressionTraces.get(0).toPrettyString(0));
    }

    @Test
    public void test5() {
        String express  = """
            mapRule = {
                "userCard": {
                    "userName": userName,
                    "userAge": userAge,
                    "isAdult": userAge >= 18,
                    "hobbies": hobbyList,
                    "address": {
                        "city": city,
                        "district": district
                    }
                },
                "summary": "用户[" + userName + "]的信息卡片"
            };
            // 返回映射结果
            return mapRule;
        """;

        Map<String, Object> context = new HashMap<>();
        context.put("userName", "李四");
        context.put("userAge", 20);
        String[] hobbyList = new String[]{"阅读", "游泳", "编程"};
        context.put("hobbyList", hobbyList);
        context.put("city", "上海");
        context.put("district", "浦东新区");

        Object result = Express4RunnerUtil.runner.execute(express, context, QLOptions.builder().traceExpression(true).build());
        System.out.println(((QLResult)result).getResult());
    }

    @Test
    public void test6() {
        Map<String, Object> context = new HashMap<>();
        context.put("a", 1.0);
        context.put("b", 0.3);
        assertTrue((Boolean)Express4RunnerUtil.runner.execute("1.3==a+b", context, QLOptions.builder().precise(true).build()).getResult());

        Object result = Express4RunnerUtil.runner.execute("a / b", context, QLOptions.builder().precise(true).build()).getResult();
        assertTrue(new BigDecimal("3.33").equals(result));

    }

    /**
     * 校验语法正确性
     */
    @Test
    public void test7() {
        Express4Runner express4Runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
        try {
            express4Runner.check("a+b;\n(a+b");
        }
        catch (QLSyntaxException e) {
            assertEquals(2, e.getLineNo());
            assertEquals(4, e.getColNo());
            assertEquals("SYNTAX_ERROR", e.getErrorCode());
            assertEquals(
                    "[Error SYNTAX_ERROR: mismatched input '<EOF>' expecting ')']\n" + "[Near: a+b; (a+b<EOF>]\n"
                            + "                ^^^^^\n" + "[Line: 2, Column: 4]",
                    e.getMessage());
        }
    }

    /**
     * 解析脚本所需外部变量
     */
    @Test
    public void test8() {
        Express4Runner express4Runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);
        Set<String> outVarNames = express4Runner.getOutVarNames("TERNARY(ISBLANK(${input}),UPPER(''),UPPER('K'))");
        System.out.println(outVarNames);

        Map<String, Object> context = new HashMap<>();
        context.put("input", "pap");
        Object result = Express4RunnerUtil.runner.execute("TERNARY(ISBLANK(${input}),UPPER(''),UPPER('K'))", context, QLOptions.builder().precise(true).build()).getResult();
        System.out.println(result);
    }

}
