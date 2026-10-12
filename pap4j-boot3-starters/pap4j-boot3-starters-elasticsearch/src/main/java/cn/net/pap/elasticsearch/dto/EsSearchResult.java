package cn.net.pap.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ES 统一检索结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsSearchResult {

    /**
     * 总命中数（SEARCH 用）
     */
    private Long total;

    /**
     * 当前页码（SEARCH 用）
     */
    private Integer pageNum;

    /**
     * 每页条数（SEARCH 用）
     */
    private Integer pageSize;

    /**
     * 检索命中文档列表（SEARCH 用）
     */
    @Builder.Default
    private List<HitRow> rows = Collections.emptyList();

    /**
     * 聚合计算结果（AGGREGATE 用）
     */
    private Map<String, Object> aggregations;

    /**
     * 单条记录明细（RECORD 用）
     */
    private Map<String, Object> record;

    /**
     * ES 原始响应数据（内部调试使用，JSON 序列化忽略防泄露）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Map<String, Object> rawResponse;

    /**
     * 命中文档行明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HitRow {

        /**
         * 文档 ID
         */
        private String id;

        /**
         * 评分
         */
        private Double score;

        /**
         * 原始文档源数据
         */
        private Map<String, Object> source;

        /**
         * 高亮字段映射: 字段名 -> 高亮片段列表
         */
        private Map<String, List<String>> highlight;

        /**
         * nested inner_hits 命中数据
         */
        private Map<String, Object> innerHits;
    }
}
