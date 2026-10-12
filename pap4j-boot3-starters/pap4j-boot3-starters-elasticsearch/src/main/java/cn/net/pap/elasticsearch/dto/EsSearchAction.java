package cn.net.pap.elasticsearch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * ES 检索操作类型枚举
 */
public enum EsSearchAction {

    /**
     * 分页多条件检索
     */
    SEARCH,

    /**
     * 聚合统计
     */
    AGGREGATE,

    /**
     * 单条记录明细查询
     */
    RECORD;

    /**
     * 根据字符串解析操作类型
     *
     * @param value 操作类型字符串
     * @return 匹配的 EsSearchAction，默认返回 SEARCH
     */
    @JsonCreator
    public static EsSearchAction fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return SEARCH;
        }
        return EsSearchAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
