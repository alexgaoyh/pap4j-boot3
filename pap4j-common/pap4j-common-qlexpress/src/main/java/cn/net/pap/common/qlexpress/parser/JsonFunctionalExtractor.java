package cn.net.pap.common.qlexpress.parser;

import cn.net.pap.common.qlexpress.parser.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.parser.dto.FunctionalExtractionRuleDTO;
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

/**
 * <p>基于函数和表达式的结构化数据提取引擎。</p>
 * <p>该引擎允许通过显式的处理逻辑（如 JsonPath 或 QLExpress）来从原始 JSON 中提取关键业务数据。</p>
 */
public class JsonFunctionalExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonFunctionalExtractor.class);

    public static final Express4Runner runner = new Express4Runner(InitOptions.DEFAULT_OPTIONS);

    static {
        runner.addFunction("JSON_PATH", new JsonPathFunction());
    }

    /**
     * 根据提取规则从 JSON 数据中提取核心字段。
     *
     * @param jsonData 原始 JSON 数据字符串
     * @param rules    提取规则列表
     * @return 提取结果 DTO
     */
    public FunctionalExtractionResultDTO extract(String jsonData, List<FunctionalExtractionRuleDTO> rules) {
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

    private Object executeRule(String jsonData, Object document, FunctionalExtractionRuleDTO rule) throws Exception {
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
