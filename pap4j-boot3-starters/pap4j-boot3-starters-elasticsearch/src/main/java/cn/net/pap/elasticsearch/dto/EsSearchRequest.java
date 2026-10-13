package cn.net.pap.elasticsearch.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ES 通用检索入参 DTO
 * <p>
 * 单一 DTO 统一覆盖 SEARCH (检索)、AGGREGATE (聚合)、RECORD (单条详情) 三种操作。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsSearchRequest {

    // ==================== 基础参数 ====================

    /**
     * 操作类型：SEARCH / AGGREGATE / RECORD
     */
    private EsSearchAction action = EsSearchAction.SEARCH;

    /**
     * 索引名称，为空时使用默认索引
     */
    private String indexName;

    /**
     * 文档 ID（RECORD 专用）
     */
    private String rid;

    // ==================== 检索参数（SEARCH / AGGREGATE 共用） ====================

    /**
     * 关键词（SEARCH 用）
     */
    private String keyword;

    /**
     * 关键词检索字段列表（SEARCH 用）
     */
    private List<String> keywordFields;

    /**
     * 高级检索条件列表（按传入顺序左结合组合）
     */
    private List<Condition> conditions;

    /**
     * 排序配置列表
     */
    private List<Sort> sorts;

    /**
     * 分页 — 页码（SEARCH 用，默认 1）
     */
    private Integer pageNum = 1;

    /**
     * 分页 — 每页条数（SEARCH 用，默认 10）
     */
    private Integer pageSize = 10;

    /**
     * 返回字段白名单
     */
    private List<String> sourceIncludes;

    /**
     * 返回字段黑名单
     */
    private List<String> sourceExcludes;

    /**
     * 高亮配置
     */
    private Highlight highlight;

    /**
     * 是否根据 keywordFields / conditions 自动推导高亮字段（默认 true）。
     * <p>
     * SEARCH 动作与参照实现一致会自动推导；NEIGHBORS 等场景可关闭以避免污染返回字段。
     * </p>
     */
    private Boolean autoHighlight = Boolean.TRUE;

    /**
     * nested inner_hits 高亮与子文档返回配置列表
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<InnerHits> innerHits;

    /**
     * 脚本排序配置（SEARCH 用）
     */
    private ScriptSort scriptSort;

    /**
     * 聚合后过滤（AGGREGATE 用，不影响聚合结果）
     */
    private List<Condition> postFilters;

    /**
     * 相邻定位匹配条件（NEIGHBORS 用，匹配当前记录在结果集中的位置）
     */
    private java.util.Map<String, Object> locate;

    // ==================== 聚合参数（AGGREGATE 专用） ====================

    /**
     * 聚合配置树
     */
    private List<AggNode> aggregations;

    /**
     * 是否只返回 buckets（默认 true）
     */
    private Boolean returnBucketsOnly = Boolean.TRUE;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建 SEARCH 检索请求
     *
     * @return EsSearchRequest 检索请求实例
     */
    public static EsSearchRequest search() {
        EsSearchRequest req = new EsSearchRequest();
        req.setAction(EsSearchAction.SEARCH);
        return req;
    }

    /**
     * 创建 AGGREGATE 聚合请求
     *
     * @return EsSearchRequest 聚合请求实例
     */
    public static EsSearchRequest aggregate() {
        EsSearchRequest req = new EsSearchRequest();
        req.setAction(EsSearchAction.AGGREGATE);
        req.setPageSize(0);
        return req;
    }

    /**
     * 创建 RECORD 单条文档获取请求
     *
     * @param rid 文档 ID
     * @return EsSearchRequest 单条记录请求实例
     */
    public static EsSearchRequest record(String rid) {
        EsSearchRequest req = new EsSearchRequest();
        req.setAction(EsSearchAction.RECORD);
        req.setRid(rid);
        return req;
    }

    // ==================== 链式调用辅助方法 ====================

    /**
     * 设置索引名
     *
     * @param indexName 索引名
     * @return 当前请求对象
     */
    public EsSearchRequest indexName(String indexName) {
        this.indexName = indexName;
        return this;
    }

    /**
     * 设置文档 ID
     *
     * @param rid 文档 ID
     * @return 当前请求对象
     */
    public EsSearchRequest rid(String rid) {
        this.rid = rid;
        return this;
    }

    /**
     * 设置检索关键词
     *
     * @param keyword 关键词
     * @return 当前请求对象
     */
    public EsSearchRequest keyword(String keyword) {
        this.keyword = keyword;
        return this;
    }

    /**
     * 设置关键词检索字段列表
     *
     * @param keywordFields 检索字段列表
     * @return 当前请求对象
     */
    public EsSearchRequest keywordFields(List<String> keywordFields) {
        this.keywordFields = keywordFields;
        return this;
    }

    /**
     * 设置高级检索条件列表
     *
     * @param conditions 条件列表
     * @return 当前请求对象
     */
    public EsSearchRequest conditions(List<Condition> conditions) {
        this.conditions = conditions;
        return this;
    }

    /**
     * 添加单条高级检索条件
     *
     * @param condition 检索条件
     * @return 当前请求对象
     */
    public EsSearchRequest condition(Condition condition) {
        if (this.conditions == null) {
            this.conditions = new ArrayList<>();
        }
        this.conditions.add(condition);
        return this;
    }

    /**
     * 设置排序配置列表
     *
     * @param sorts 排序配置列表
     * @return 当前请求对象
     */
    public EsSearchRequest sorts(List<Sort> sorts) {
        this.sorts = sorts;
        return this;
    }

    /**
     * 添加单条排序配置
     *
     * @param sort 排序配置
     * @return 当前请求对象
     */
    public EsSearchRequest sort(Sort sort) {
        if (this.sorts == null) {
            this.sorts = new ArrayList<>();
        }
        this.sorts.add(sort);
        return this;
    }

    /**
     * 设置当前页码
     *
     * @param pageNum 页码
     * @return 当前请求对象
     */
    public EsSearchRequest pageNum(Integer pageNum) {
        this.pageNum = pageNum;
        return this;
    }

    /**
     * 设置每页大小
     *
     * @param pageSize 每页条数
     * @return 当前请求对象
     */
    public EsSearchRequest pageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    /**
     * 设置返回字段白名单
     *
     * @param sourceIncludes 返回字段白名单
     * @return 当前请求对象
     */
    public EsSearchRequest sourceIncludes(List<String> sourceIncludes) {
        this.sourceIncludes = sourceIncludes;
        return this;
    }

    /**
     * 设置返回字段黑名单
     *
     * @param sourceExcludes 返回字段黑名单
     * @return 当前请求对象
     */
    public EsSearchRequest sourceExcludes(List<String> sourceExcludes) {
        this.sourceExcludes = sourceExcludes;
        return this;
    }

    /**
     * 设置高亮配置
     *
     * @param highlight 高亮配置
     * @return 当前请求对象
     */
    public EsSearchRequest highlight(Highlight highlight) {
        this.highlight = highlight;
        return this;
    }

    /**
     * 设置 nested inner_hits 配置列表
     *
     * @param innerHits innerHits 配置列表
     * @return 当前请求对象
     */
    public EsSearchRequest innerHits(List<InnerHits> innerHits) {
        this.innerHits = innerHits;
        return this;
    }

    /**
     * 设置 nested inner_hits 配置列表（支持单个或多个）
     *
     * @param innerHits innerHits 可变参数
     * @return 当前请求对象
     */
    public EsSearchRequest innerHits(InnerHits... innerHits) {
        if (innerHits != null) {
            this.innerHits = new ArrayList<>(List.of(innerHits));
        } else {
            this.innerHits = null;
        }
        return this;
    }

    /**
     * 添加单条 nested inner_hits 配置
     *
     * @param innerHits 单条 innerHits 配置
     * @return 当前请求对象
     */
    public EsSearchRequest addInnerHits(InnerHits innerHits) {
        if (this.innerHits == null) {
            this.innerHits = new ArrayList<>();
        }
        if (innerHits != null) {
            this.innerHits.add(innerHits);
        }
        return this;
    }

    /**
     * 设置脚本排序配置
     *
     * @param scriptSort 脚本排序配置
     * @return 当前请求对象
     */
    public EsSearchRequest scriptSort(ScriptSort scriptSort) {
        this.scriptSort = scriptSort;
        return this;
    }

    /**
     * 设置聚合后过滤条件
     *
     * @param postFilters 聚合后过滤条件列表
     * @return 当前请求对象
     */
    public EsSearchRequest postFilters(List<Condition> postFilters) {
        this.postFilters = postFilters;
        return this;
    }

    /**
     * 设置聚合节点配置树
     *
     * @param aggregations 聚合节点列表
     * @return 当前请求对象
     */
    public EsSearchRequest aggregations(List<AggNode> aggregations) {
        this.aggregations = aggregations;
        return this;
    }

    /**
     * 添加单条聚合节点
     *
     * @param aggregation 聚合节点
     * @return 当前请求对象
     */
    public EsSearchRequest aggregation(AggNode aggregation) {
        if (this.aggregations == null) {
            this.aggregations = new ArrayList<>();
        }
        this.aggregations.add(aggregation);
        return this;
    }

    /**
     * 设置是否仅返回 buckets
     *
     * @param returnBucketsOnly 是否仅返回 buckets
     * @return 当前请求对象
     */
    public EsSearchRequest returnBucketsOnly(Boolean returnBucketsOnly) {
        this.returnBucketsOnly = returnBucketsOnly;
        return this;
    }

    // ==================== 内部类定义 ====================

    /**
     * 高级检索条件
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Condition {

        /**
         * 条件关系：must / should / must_not / filter，默认 must
         */
        private String relation = "must";

        /**
         * 字段名，以 - 开头表示导航前缀检索字段
         */
        private String field;

        /**
         * 检索关键词
         */
        private String keyword;

        /**
         * 匹配方式：term / fuzzy / range / terms / wildcard / prefix
         */
        private String match;

        /**
         * 多值检索值（match=terms 时使用）
         */
        private List<String> values;

        /**
         * 范围下界，含（match=range 时使用）
         */
        private String gte;

        /**
         * 范围上界，含（match=range 时使用）
         */
        private String lte;

        /**
         * nested 路径（可选，当字段为 nested 对象子属性时指定）
         */
        private String nestedPath;

        /**
         * 静态工厂方法构造检索条件
         *
         * @param field   字段名
         * @param match   匹配类型
         * @param keyword 关键词
         * @return Condition 实例
         */
        public static Condition of(String field, String match, String keyword) {
            Condition condition = new Condition();
            condition.setField(field);
            condition.setMatch(match);
            condition.setKeyword(keyword);
            return condition;
        }

        /**
         * 设置 nested 路径
         *
         * @param nestedPath nested 路径
         * @return 当前 Condition 对象
         */
        public Condition nestedPath(String nestedPath) {
            this.nestedPath = nestedPath;
            return this;
        }

        /**
         * 设置逻辑关系
         *
         * @param relation 逻辑关系（must / should / must_not）
         * @return 当前 Condition 对象
         */
        public Condition relation(String relation) {
            this.relation = relation;
            return this;
        }

        /**
         * 设置多值检索列表
         *
         * @param values 多值列表
         * @return 当前 Condition 对象
         */
        public Condition values(List<String> values) {
            this.values = values;
            return this;
        }

        /**
         * 设置范围下界
         *
         * @param gte 范围下界
         * @return 当前 Condition 对象
         */
        public Condition gte(String gte) {
            this.gte = gte;
            return this;
        }

        /**
         * 设置范围上界
         *
         * @param lte 范围上界
         * @return 当前 Condition 对象
         */
        public Condition lte(String lte) {
            this.lte = lte;
            return this;
        }
    }

    /**
     * 排序配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sort {

        /**
         * 排序字段
         */
        private String field;

        /**
         * 排序方向，默认 DESC
         */
        private SortOrder order = SortOrder.DESC;

        /**
         * 升序排序
         *
         * @param field 排序字段
         * @return Sort 实例
         */
        public static Sort asc(String field) {
            return new Sort(field, SortOrder.ASC);
        }

        /**
         * 降序排序
         *
         * @param field 排序字段
         * @return Sort 实例
         */
        public static Sort desc(String field) {
            return new Sort(field, SortOrder.DESC);
        }

        /**
         * 指定方向排序
         *
         * @param field 排序字段
         * @param order 排序方向
         * @return Sort 实例
         */
        public static Sort of(String field, SortOrder order) {
            return new Sort(field, order != null ? order : SortOrder.DESC);
        }
    }

    /**
     * 高亮配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Highlight {

        /**
         * 高亮字段列表
         */
        private List<String> fields;

        /**
         * 高亮前置标签
         */
        private String preTag = "<span style='color:red;font-weight:bold'>";

        /**
         * 高亮后置标签
         */
        private String postTag = "</span>";

        /**
         * 片段长度
         */
        private Integer fragmentSize = 150;

        /**
         * 片段数量
         */
        private Integer numberOfFragments = 3;

        /**
         * 设置高亮字段
         *
         * @param fields 字段列表
         * @return 当前 Highlight 对象
         */
        public Highlight fields(List<String> fields) {
            this.fields = fields;
            return this;
        }

        /**
         * 设置高亮前置标签
         *
         * @param preTag 前置标签
         * @return 当前 Highlight 对象
         */
        public Highlight preTag(String preTag) {
            this.preTag = preTag;
            return this;
        }

        /**
         * 设置高亮后置标签
         *
         * @param postTag 后置标签
         * @return 当前 Highlight 对象
         */
        public Highlight postTag(String postTag) {
            this.postTag = postTag;
            return this;
        }

        /**
         * 设置片段长度
         *
         * @param fragmentSize 片段长度
         * @return 当前 Highlight 对象
         */
        public Highlight fragmentSize(Integer fragmentSize) {
            this.fragmentSize = fragmentSize;
            return this;
        }

        /**
         * 设置片段数量
         *
         * @param numberOfFragments 片段数量
         * @return 当前 Highlight 对象
         */
        public Highlight numberOfFragments(Integer numberOfFragments) {
            this.numberOfFragments = numberOfFragments;
            return this;
        }
    }

    /**
     * nested inner_hits 高亮与子文档返回配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InnerHits {

        /**
         * inner_hits 名称（未指定时默认使用 nested path 或 inner）
         */
        private String name;

        /**
         * nested 路径（可选，当未指定时默认从 highlightFields 推导或作为通用标识）
         */
        private String path;

        /**
         * 静态工厂方法：指定 nested path
         *
         * @param path nested 路径
         * @return InnerHits 实例
         */
        public static InnerHits of(String path) {
            InnerHits ih = new InnerHits();
            ih.setPath(path);
            return ih;
        }

        /**
         * 静态工厂方法：指定 nested path 和高亮字段
         *
         * @param path            nested 路径
         * @param highlightFields 高亮字段列表
         * @return InnerHits 实例
         */
        public static InnerHits of(String path, String... highlightFields) {
            InnerHits ih = new InnerHits();
            ih.setPath(path);
            if (highlightFields != null) {
                ih.setHighlightFields(new ArrayList<>(List.of(highlightFields)));
            }
            return ih;
        }

        /**
         * 片段数量
         */
        private Integer numberOfFragments = 1;

        /**
         * 片段长度
         */
        private Integer fragmentSize = 150;

        /**
         * 需要高亮的 nested 字段列表
         */
        private List<String> highlightFields;

        /**
         * 设置 inner_hits 名称
         *
         * @param name inner_hits 名称
         * @return 当前 InnerHits 对象
         */
        public InnerHits name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置 nested 路径
         *
         * @param path nested 路径
         * @return 当前 InnerHits 对象
         */
        public InnerHits path(String path) {
            this.path = path;
            return this;
        }

        /**
         * 设置片段数量
         *
         * @param numberOfFragments 片段数量
         * @return 当前 InnerHits 对象
         */
        public InnerHits numberOfFragments(Integer numberOfFragments) {
            this.numberOfFragments = numberOfFragments;
            return this;
        }

        /**
         * 设置片段长度
         *
         * @param fragmentSize 片段长度
         * @return 当前 InnerHits 对象
         */
        public InnerHits fragmentSize(Integer fragmentSize) {
            this.fragmentSize = fragmentSize;
            return this;
        }

        /**
         * 设置高亮字段列表
         *
         * @param highlightFields 高亮字段列表
         * @return 当前 InnerHits 对象
         */
        public InnerHits highlightFields(List<String> highlightFields) {
            this.highlightFields = highlightFields;
            return this;
        }

        /**
         * 添加高亮字段
         *
         * @param field 高亮字段
         * @return 当前 InnerHits 对象
         */
        public InnerHits highlight(String field) {
            if (this.highlightFields == null) {
                this.highlightFields = new ArrayList<>();
            }
            this.highlightFields.add(field);
            return this;
        }
    }

    /**
     * 脚本排序配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptSort {

        /**
         * 排序字段
         */
        private String field;

        /**
         * 基准日（一年中的第几天，用于月日邻近度排序）
         */
        private Integer baseDayOfYear;

        /**
         * 自定义 Painless 脚本源
         */
        private String scriptSource;

        /**
         * 排序方向，默认 ASC
         */
        private SortOrder order = SortOrder.ASC;

        /**
         * 设置排序字段
         *
         * @param field 排序字段
         * @return 当前 ScriptSort 对象
         */
        public ScriptSort field(String field) {
            this.field = field;
            return this;
        }

        /**
         * 设置基准年积日
         *
         * @param baseDayOfYear 年积日（1-365）
         * @return 当前 ScriptSort 对象
         */
        public ScriptSort baseDayOfYear(Integer baseDayOfYear) {
            this.baseDayOfYear = baseDayOfYear;
            return this;
        }

        /**
         * 设置自定义 Painless 脚本源
         *
         * @param scriptSource 脚本源码
         * @return 当前 ScriptSort 对象
         */
        public ScriptSort scriptSource(String scriptSource) {
            this.scriptSource = scriptSource;
            return this;
        }

        /**
         * 设置排序方向
         *
         * @param order 排序方向
         * @return 当前 ScriptSort 对象
         */
        public ScriptSort order(SortOrder order) {
            this.order = order;
            return this;
        }
    }

    /**
     * 聚合节点配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggNode {

        /**
         * 聚合名称
         */
        private String name;

        /**
         * 聚合类型
         */
        private AggType type = AggType.TERMS;

        /**
         * 聚合字段
         */
        private String field;

        /**
         * 桶数 / TopN 数量
         */
        private Integer size;

        /**
         * 桶排序键（_key / _count）
         */
        private String orderKey;

        /**
         * 桶排序方向
         */
        private SortOrder orderDir;

        /**
         * topHits 内部排序
         */
        private List<Sort> sorts;

        /**
         * topHits 返回字段
         */
        private List<String> sourceIncludes;

        /**
         * 脚本源（cardinality / valueCount 用）
         */
        private String scriptSource;

        /**
         * 子聚合列表
         */
        private List<AggNode> children;

        /**
         * 构造聚合节点
         *
         * @param name  聚合名称
         * @param type  聚合类型
         * @param field 聚合字段
         */
        public AggNode(String name, AggType type, String field) {
            this.name = name;
            this.type = type;
            this.field = field;
        }

        /**
         * 静态工厂方法：构建嵌套聚合节点 (nested)
         *
         * @param name 聚合名称
         * @param path nested 路径
         * @return AggNode 实例
         */
        public static AggNode nested(String name, String path) {
            return new AggNode(name, AggType.NESTED, path);
        }

        /**
         * 静态工厂方法：构建分桶聚合节点 (terms)
         *
         * @param name  聚合名称
         * @param field 聚合字段
         * @return AggNode 实例
         */
        public static AggNode terms(String name, String field) {
            return new AggNode(name, AggType.TERMS, field);
        }

        /**
         * 静态工厂方法：构建基数去重节点 (cardinality)
         *
         * @param name  聚合名称
         * @param field 聚合字段
         * @return AggNode 实例
         */
        public static AggNode cardinality(String name, String field) {
            return new AggNode(name, AggType.CARDINALITY, field);
        }

        /**
         * 静态工厂方法：构建值计数节点 (value_count)
         *
         * @param name  聚合名称
         * @param field 聚合字段
         * @return AggNode 实例
         */
        public static AggNode valueCount(String name, String field) {
            return new AggNode(name, AggType.VALUE_COUNT, field);
        }

        /**
         * 静态工厂方法：构建 top_hits 聚合节点
         *
         * @param name 聚合名称
         * @return AggNode 实例
         */
        public static AggNode topHits(String name) {
            return new AggNode(name, AggType.TOP_HITS, null);
        }

        /**
         * 设置桶大小
         *
         * @param size 桶数
         * @return 当前 AggNode 对象
         */
        public AggNode size(Integer size) {
            this.size = size;
            return this;
        }

        /**
         * 设置聚合桶排序
         *
         * @param orderKey 排序键（_key / _count）
         * @param orderDir 排序方向
         * @return 当前 AggNode 对象
         */
        public AggNode order(String orderKey, SortOrder orderDir) {
            this.orderKey = orderKey;
            this.orderDir = orderDir;
            return this;
        }

        /**
         * 设置 topHits 内部排序
         *
         * @param sorts 排序配置
         * @return 当前 AggNode 对象
         */
        public AggNode sorts(List<Sort> sorts) {
            this.sorts = sorts;
            return this;
        }

        /**
         * 设置 topHits 返回字段白名单
         *
         * @param sourceIncludes 返回字段列表
         * @return 当前 AggNode 对象
         */
        public AggNode sourceIncludes(List<String> sourceIncludes) {
            this.sourceIncludes = sourceIncludes;
            return this;
        }

        /**
         * 设置聚合脚本源码
         *
         * @param scriptSource 脚本源码
         * @return 当前 AggNode 对象
         */
        public AggNode scriptSource(String scriptSource) {
            this.scriptSource = scriptSource;
            return this;
        }

        /**
         * 设置子聚合节点列表
         *
         * @param children 子聚合列表
         * @return 当前 AggNode 对象
         */
        public AggNode children(List<AggNode> children) {
            this.children = children;
            return this;
        }

        /**
         * 添加单个子聚合节点
         *
         * @param child 子聚合节点
         * @return 当前 AggNode 对象
         */
        public AggNode child(AggNode child) {
            if (this.children == null) {
                this.children = new ArrayList<>();
            }
            this.children.add(child);
            return this;
        }
    }
}
