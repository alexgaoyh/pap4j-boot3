package cn.net.pap.common.qlexpress.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>函数式提取结果传输对象。</p>
 *
 * @param extractedFields 提取结果字段集
 * @param rawPayload      原始 JSON 数据字符串
 * @param statuses        逐条规则执行状态（审计），与 {@code rules} 一一对应
 */
public record FunctionalExtractionResultDTO(Map<String, Object> extractedFields,
                                            String rawPayload,
                                            List<RuleExecStatus> statuses) implements Serializable {

    public FunctionalExtractionResultDTO(Map<String, Object> extractedFields, String rawPayload) {
        this(extractedFields, rawPayload, Collections.emptyList());
    }
}
