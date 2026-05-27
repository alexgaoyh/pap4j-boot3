package cn.net.pap.common.qlexpress.dto;

import java.io.Serializable;

/**
 * <p>基于函数/表达式的 JSON 数据提取规则。</p>
 */
public record FunctionalExtractionRuleDTO(String targetField, String expression) implements Serializable {
}
