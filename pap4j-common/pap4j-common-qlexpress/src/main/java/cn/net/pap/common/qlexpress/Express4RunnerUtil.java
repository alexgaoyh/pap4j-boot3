package cn.net.pap.common.qlexpress;

import cn.net.pap.common.qlexpress.operator.DivideOperator;
import cn.net.pap.common.qlexpress.operator.IsBlankOperator;
import cn.net.pap.common.qlexpress.operator.JsonPathOperator;
import cn.net.pap.common.qlexpress.operator.ListJoinOperator;
import cn.net.pap.common.qlexpress.operator.ListSizeOperator;
import cn.net.pap.common.qlexpress.operator.TernaryOperator;
import cn.net.pap.common.qlexpress.operator.UpperOperator;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionRuleDTO;
import com.alibaba.qlexpress4.Express4Runner;
import com.alibaba.qlexpress4.InitOptions;
import com.alibaba.qlexpress4.QLOptions;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Express4RunnerUtil {

    private static final Logger log = LoggerFactory.getLogger(Express4RunnerUtil.class);

    public static final Express4Runner runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);

    static {
        // 除法保留两位小数 详见 com.alibaba.qlexpress4.runtime.operator.number.BigDecimalMath
        System.setProperty("qlexpress4.division.min.scale", "2");
        runner.addFunction("DIV2", new DivideOperator(2));
        runner.addFunction("ISBLANK", new IsBlankOperator());
        runner.addFunction("UPPER", new UpperOperator());
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
        Object document = JsonPath.parse(jsonData).json();

        for (FunctionalExtractionRuleDTO rule : rules) {
            try {
                Object value = executeRule(jsonData, document, rule);
                if (value != null) {
                    extractedFields.put(rule.targetField(), value);
                }
            } catch (Exception e) {
                log.error("Failed to execute extraction rule for field: {}", rule.targetField(), e);
            }
        }

        return new FunctionalExtractionResultDTO(extractedFields, jsonData);
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

}
