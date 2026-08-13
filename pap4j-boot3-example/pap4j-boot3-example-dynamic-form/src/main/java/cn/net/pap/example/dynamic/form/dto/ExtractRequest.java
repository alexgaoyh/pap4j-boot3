package cn.net.pap.example.dynamic.form.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * <p>数据提取请求：对一批业务数据逐条执行提取规则。</p>
 *
 * @param records 业务数据列表
 * @param rules   提取规则列表
 */
public record ExtractRequest(List<Map<String, Object>> records, List<ExtractRuleDTO> rules) implements Serializable {
}
