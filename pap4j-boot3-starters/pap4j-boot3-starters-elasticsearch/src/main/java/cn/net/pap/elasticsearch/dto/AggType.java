package cn.net.pap.elasticsearch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * 聚合类型枚举
 */
public enum AggType {

    /**
     * 分桶聚合 (terms)
     */
    TERMS,

    /**
     * 基数去重计数 (cardinality)
     */
    CARDINALITY,

    /**
     * 值计数 (value_count)
     */
    VALUE_COUNT,

    /**
     * 取 Top N 文档 (top_hits)
     */
    TOP_HITS,

    /**
     * 嵌套聚合包装器 (nested)
     */
    NESTED;

    /**
     * 根据字符串解析聚合类型
     *
     * @param value 聚合类型字符串
     * @return 匹配的 AggType，默认返回 TERMS
     */
    @JsonCreator
    public static AggType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TERMS;
        }
        return AggType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
