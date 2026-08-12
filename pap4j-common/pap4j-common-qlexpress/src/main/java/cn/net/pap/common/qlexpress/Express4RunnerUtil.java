package cn.net.pap.common.qlexpress;

import cn.net.pap.common.qlexpress.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionRuleDTO;
import cn.net.pap.common.qlexpress.dto.RuleExecStatus;
import cn.net.pap.common.qlexpress.operator.DivideOperator;
import cn.net.pap.common.qlexpress.operator.IsBlankOperator;
import cn.net.pap.common.qlexpress.operator.JsonPathOperator;
import cn.net.pap.common.qlexpress.operator.ListJoinOperator;
import cn.net.pap.common.qlexpress.operator.ListSizeOperator;
import cn.net.pap.common.qlexpress.operator.SubstringOperator;
import cn.net.pap.common.qlexpress.operator.TernaryOperator;
import cn.net.pap.common.qlexpress.operator.TreeFlattenOperator;
import cn.net.pap.common.qlexpress.operator.TreeLeafFlattenOperator;
import cn.net.pap.common.qlexpress.operator.TrimOperator;
import cn.net.pap.common.qlexpress.operator.UpperOperator;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Express4RunnerUtil {

    private static final Logger log = LoggerFactory.getLogger(Express4RunnerUtil.class);

    /** JsonPath 表达式前缀，用于引擎路由判定。 */
    private static final String JSON_PATH_PREFIX = "$";

    public static final Express4Runner runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);

    static {
        // 除法保留两位小数 详见 com.alibaba.qlexpress4.runtime.operator.number.BigDecimalMath
        System.setProperty("qlexpress4.division.min.scale", "2");
        runner.addFunction("DIV2", new DivideOperator(2));
        runner.addFunction("ISBLANK", new IsBlankOperator());
        runner.addFunction("UPPER", new UpperOperator());
        runner.addFunction("SUBSTRING", new SubstringOperator());
        runner.addFunction("TRIM", new TrimOperator());
        runner.addFunction("TREE_FLATTEN", new TreeFlattenOperator());
        runner.addFunction("TREE_LEAF_FLATTEN", new TreeLeafFlattenOperator());
        runner.addFunction("TERNARY", new TernaryOperator());
        runner.addFunction("JSON_PATH", new JsonPathOperator());
        runner.addFunction("LIST_JOIN", new ListJoinOperator());
        runner.addFunction("LIST_SIZE", new ListSizeOperator());
    }


    /**
     * 根据提取规则从 JSON 数据中提取核心字段。
     *
     * @param jsonData 原始 JSON 数据字符串
     * @param rules    提取规则列表
     * @return 提取结果 DTO
     */
    public static FunctionalExtractionResultDTO extract(String jsonData, List<FunctionalExtractionRuleDTO> rules) {
        Map<String, Object> extractedFields = new LinkedHashMap<>();
        List<RuleExecStatus> statuses = new ArrayList<>(rules.size());
        Object document = JsonPath.parse(jsonData).json();

        for (FunctionalExtractionRuleDTO rule : rules) {
            try {
                Object value = executeRule(jsonData, document, rule);
                if (value != null) {
                    extractedFields.put(rule.targetField(), value);
                }
                statuses.add(new RuleExecStatus(rule.targetField(), true, null));
            } catch (Exception e) {
                log.error("Failed to execute extraction rule for field: {}", rule.targetField(), e);
                statuses.add(new RuleExecStatus(rule.targetField(), false, e.getMessage()));
            }
        }

        return new FunctionalExtractionResultDTO(extractedFields, jsonData, statuses);
    }

    private static Object executeRule(String jsonData, Object document, FunctionalExtractionRuleDTO rule) throws Exception {
        String expr = rule.expression();

        // 1. 如果以 $ 开头，优先使用 JsonPath
        if (expr.trim().startsWith("$")) {
            return JsonPath.read(document, expr);
        }

        // 2. 否则使用 QLExpress 处理
        Map<String, Object> context = new HashMap<>();
        context.put("data", jsonData);
        context.put("json", document);

        // QLExpress 4 执行
        return runner.execute(expr, context, QLOptions.DEFAULT_OPTIONS).getResult();
    }

    /**
     * <p>规则集发布前静态校验：QL 表达式走 {@code runner.check}，以 {@code $} 开头的 JsonPath 走 {@code JsonPath.compile}。</p>
     * <p>路由判定与 {@link #executeRule(String, Object, FunctionalExtractionRuleDTO)} 保持一致。</p>
     *
     * @param rules 待校验规则集
     * @throws IllegalArgumentException 存在语法非法的规则时抛出，携带目标字段与表达式
     */
    public static void checkRules(List<FunctionalExtractionRuleDTO> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (FunctionalExtractionRuleDTO rule : rules) {
            String expr = rule.expression() == null ? "" : rule.expression().trim();
            try {
                if (expr.startsWith(JSON_PATH_PREFIX)) {
                    JsonPath.compile(expr);
                } else {
                    runner.check(expr);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Invalid rule [targetField=" + rule.targetField() + "]: " + expr, e);
            }
        }
    }

}
