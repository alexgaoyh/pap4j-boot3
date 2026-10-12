package cn.net.pap.elasticsearch.builder;

import cn.net.pap.elasticsearch.dto.AggType;
import cn.net.pap.elasticsearch.dto.EsSearchAction;
import cn.net.pap.elasticsearch.dto.EsSearchRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 Elasticsearch 查询 DSL 构建器
 * <p>
 * 特性：
 * 1. 多个高级检索条件按传入顺序「左结合 (Left-associative)」组合；
 * 2. 导航前缀条件（字段名以 - 开头）作为全局 filter 提取；
 * 3. 关键词检索（支持常规字段与 nested 字段联合 should 检索）；
 * 4. nested 字段 inner_hits 配置与独立高亮；
 * 5. ScriptSort 脚本排序（含 baseDayOfYear 邻近度排序）；
 * 6. 多层递归聚合树构建（TERMS、CARDINALITY 脚本、VALUE_COUNT、TOP_HITS）。
 */
public class EsQueryBuilder {

    /**
     * 构建完整的 ES 查询请求体 Map
     *
     * @param request 统一检索入参
     * @return ES DSL 请求体
     */
    public static Map<String, Object> buildSearchDsl(EsSearchRequest request) {
        Map<String, Object> dsl = new LinkedHashMap<>();

        // 1. 分页与大小
        if (request.getAction() == EsSearchAction.AGGREGATE) {
            dsl.put("size", 0);
        } else {
            int pageNum = request.getPageNum() != null && request.getPageNum() > 0 ? request.getPageNum() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() >= 0 ? request.getPageSize() : 10;
            dsl.put("from", (pageNum - 1) * pageSize);
            dsl.put("size", pageSize);
        }

        // 2. 返回字段过滤 _source
        buildSourceFiltering(request, dsl);

        // 3. 构建核心 Query (关键词 + 左结合条件 + 导航过滤)
        Map<String, Object> query = buildRootQuery(request);
        if (!query.isEmpty()) {
            dsl.put("query", query);
        }

        // 4. 排序 (Sorts & ScriptSort)
        if (request.getAction() != EsSearchAction.AGGREGATE) {
            List<Object> sortList = buildSorts(request);
            if (!sortList.isEmpty()) {
                dsl.put("sort", sortList);
            }
        }

        // 5. 高亮 Highlight
        if (request.getHighlight() != null && !CollectionUtils.isEmpty(request.getHighlight().getFields())) {
            dsl.put("highlight", buildHighlight(request.getHighlight()));
        }

        // 6. 聚合 Aggregations
        if (!CollectionUtils.isEmpty(request.getAggregations())) {
            dsl.put("aggs", buildAggregations(request.getAggregations()));
        }

        // 7. PostFilter (AGGREGATE 专用)
        if (!CollectionUtils.isEmpty(request.getPostFilters())) {
            Map<String, Object> postFilterQuery = buildLeftAssociativeQuery(request.getPostFilters(), null);
            if (postFilterQuery != null && !postFilterQuery.isEmpty()) {
                dsl.put("post_filter", postFilterQuery);
            }
        }

        return dsl;
    }

    /**
     * 构建根 Bool 查询
     * @param request
     * @return Map<String, Object>
     */
    private static Map<String, Object> buildRootQuery(EsSearchRequest request) {
        List<Object> mustClauses = new ArrayList<>();
        List<Object> filterClauses = new ArrayList<>();

        // 提取导航条件与普通检索条件
        List<EsSearchRequest.Condition> navConditions = new ArrayList<>();
        List<EsSearchRequest.Condition> normalConditions = new ArrayList<>();

        if (!CollectionUtils.isEmpty(request.getConditions())) {
            for (EsSearchRequest.Condition condition : request.getConditions()) {
                if (condition.getField() != null && condition.getField().startsWith("-")) {
                    navConditions.add(condition);
                } else {
                    normalConditions.add(condition);
                }
            }
        }

        // 导航条件放入 filter
        for (EsSearchRequest.Condition navCond : navConditions) {
            Map<String, Object> leaf = buildLeafQuery(navCond, null);
            if (leaf != null) {
                filterClauses.add(leaf);
            }
        }

        // 普通检索条件：按传入顺序「左结合 (Left-associative)」构建复合查询
        if (!normalConditions.isEmpty()) {
            Map<String, Object> leftAssocQuery = buildLeftAssociativeQuery(normalConditions, request.getInnerHits());
            if (leftAssocQuery != null) {
                mustClauses.add(leftAssocQuery);
            }
        }

        // 关键词检索
        if (StringUtils.hasText(request.getKeyword())) {
            Map<String, Object> keywordQuery = buildKeywordQuery(request.getKeyword(), request.getKeywordFields(), request.getInnerHits());
            if (keywordQuery != null) {
                mustClauses.add(keywordQuery);
            }
        }

        Map<String, Object> boolBody = new LinkedHashMap<>();
        if (!mustClauses.isEmpty()) {
            boolBody.put("must", mustClauses);
        }
        if (!filterClauses.isEmpty()) {
            boolBody.put("filter", filterClauses);
        }

        if (boolBody.isEmpty()) {
            return Collections.singletonMap("match_all", Collections.emptyMap());
        }

        return Collections.singletonMap("bool", boolBody);
    }

    /**
     * 将多个检索条件按照传入顺序执行「左结合 (Left-associative)」折叠组合：
     * E_0 = buildLeaf(C_0)
     * E_i = combine(E_{i-1}, buildLeaf(C_i), C_i.relation)
     *
     * @param conditions 条件列表
     * @param innerHits  innerHits 配置（可选）
     * @return 左结合组合后的 Query Map
     */
    public static Map<String, Object> buildLeftAssociativeQuery(List<EsSearchRequest.Condition> conditions, EsSearchRequest.InnerHits innerHits) {
        if (CollectionUtils.isEmpty(conditions)) {
            return null;
        }

        Map<String, Object> currentExpr = null;

        for (EsSearchRequest.Condition condition : conditions) {
            Map<String, Object> leafQuery = buildLeafQuery(condition, innerHits);
            if (leafQuery == null) {
                continue;
            }

            if (currentExpr == null) {
                currentExpr = leafQuery;
            } else {
                String relation = condition.getRelation();
                if (relation == null) {
                    relation = "must";
                }
                relation = relation.trim().toLowerCase(Locale.ROOT);

                Map<String, Object> newBool = new LinkedHashMap<>();
                if ("should".equals(relation) || "or".equals(relation)) {
                    List<Object> shouldList = new ArrayList<>(2);
                    shouldList.add(currentExpr);
                    shouldList.add(leafQuery);
                    newBool.put("should", shouldList);
                    newBool.put("minimum_should_match", 1);
                } else if ("must_not".equals(relation) || "not".equals(relation)) {
                    List<Object> mustList = new ArrayList<>(1);
                    mustList.add(currentExpr);
                    newBool.put("must", mustList);
                    newBool.put("must_not", Collections.singletonList(leafQuery));
                } else {
                    List<Object> mustList = new ArrayList<>(2);
                    mustList.add(currentExpr);
                    mustList.add(leafQuery);
                    newBool.put("must", mustList);
                }

                currentExpr = Collections.singletonMap("bool", newBool);
            }
        }

        return currentExpr;
    }

    /**
     * 构建单条 Leaf 查询
     * @param condition condition
     * @param innerHits innerHits
     * @return Map<String, Object>
     */
    private static Map<String, Object> buildLeafQuery(EsSearchRequest.Condition condition, EsSearchRequest.InnerHits innerHits) {
        if (condition == null || !StringUtils.hasText(condition.getField())) {
            return null;
        }

        String rawField = condition.getField().trim();
        String field;
        if (rawField.startsWith("-")) {
            String base = rawField.substring(1);
            field = base.endsWith(".keyword") ? base : base + ".keyword";
        } else {
            field = rawField;
        }

        String match = condition.getMatch();
        if (match == null) {
            match = "fuzzy";
        }
        match = match.trim().toLowerCase(Locale.ROOT);

        Map<String, Object> leaf = new LinkedHashMap<>();

        switch (match) {
            case "term":
                leaf.put("term", Collections.singletonMap(field, condition.getKeyword()));
                break;
            case "terms":
                List<String> values = condition.getValues();
                if (values == null) {
                    values = Collections.emptyList();
                }
                leaf.put("terms", Collections.singletonMap(field, values));
                break;
            case "range":
                Map<String, Object> rangeBody = new LinkedHashMap<>();
                if (StringUtils.hasText(condition.getGte())) {
                    rangeBody.put("gte", condition.getGte());
                }
                if (StringUtils.hasText(condition.getLte())) {
                    rangeBody.put("lte", condition.getLte());
                }
                leaf.put("range", Collections.singletonMap(field, rangeBody));
                break;
            case "wildcard":
                leaf.put("wildcard", Collections.singletonMap(field, condition.getKeyword()));
                break;
            case "prefix":
                leaf.put("prefix", Collections.singletonMap(field, condition.getKeyword()));
                break;
            case "fuzzy":
            default:
                leaf.put("match_phrase", Collections.singletonMap(field, condition.getKeyword()));
                break;
        }

        // 通用 nested 路径判断
        String nestedPath = resolveNestedPath(condition, field, innerHits);
        if (StringUtils.hasText(nestedPath)) {
            return wrapNested(nestedPath, leaf, innerHits);
        }

        return leaf;
    }

    /**
     * 通用推导 nested 路径
     * @param condition condition
     * @param field field
     * @param innerHits innerHits
     * @return String
     */
    private static String resolveNestedPath(EsSearchRequest.Condition condition, String field, EsSearchRequest.InnerHits innerHits) {
        if (StringUtils.hasText(condition.getNestedPath())) {
            return condition.getNestedPath();
        }
        if (innerHits != null && StringUtils.hasText(innerHits.getPath()) && field.startsWith(innerHits.getPath() + ".")) {
            return innerHits.getPath();
        }
        return null;
    }

    /**
     * 包装为 nested 查询
     * @param path path
     * @param innerQuery innerQuery
     * @param innerHits innerHits
     * @return Map<String, Object>
     */
    private static Map<String, Object> wrapNested(String path, Map<String, Object> innerQuery, EsSearchRequest.InnerHits innerHits) {
        Map<String, Object> nestedBody = new LinkedHashMap<>();
        nestedBody.put("path", path);
        nestedBody.put("query", innerQuery);

        if (innerHits != null) {
            nestedBody.put("inner_hits", buildInnerHits(innerHits));
        }

        return Collections.singletonMap("nested", nestedBody);
    }

    /**
     * 构建 inner_hits
     */
    private static Map<String, Object> buildInnerHits(EsSearchRequest.InnerHits innerHits) {
        Map<String, Object> ih = new LinkedHashMap<>();
        ih.put("name", StringUtils.hasText(innerHits.getName()) ? innerHits.getName() : "inner");
        ih.put("size", 1);

        if (!CollectionUtils.isEmpty(innerHits.getHighlightFields())) {
            Map<String, Object> hlFields = new LinkedHashMap<>();
            for (String field : innerHits.getHighlightFields()) {
                Map<String, Object> fieldConf = new LinkedHashMap<>();
                fieldConf.put("fragment_size", innerHits.getFragmentSize() != null ? innerHits.getFragmentSize() : 150);
                fieldConf.put("number_of_fragments", innerHits.getNumberOfFragments() != null ? innerHits.getNumberOfFragments() : 1);
                hlFields.put(field, fieldConf);
            }
            Map<String, Object> highlight = new LinkedHashMap<>();
            highlight.put("fields", hlFields);
            ih.put("highlight", highlight);
        }

        return ih;
    }

    /**
     * 构建关键词检索 Query (支持普通字段与 nested 字段)
     */
    private static Map<String, Object> buildKeywordQuery(String keyword, List<String> keywordFields, EsSearchRequest.InnerHits innerHits) {
        if (CollectionUtils.isEmpty(keywordFields)) {
            return Collections.singletonMap("multi_match", Collections.singletonMap("query", keyword));
        }

        String nestedPath = null;
        if (innerHits != null) {
            if (StringUtils.hasText(innerHits.getPath())) {
                nestedPath = innerHits.getPath();
            } else if (!CollectionUtils.isEmpty(innerHits.getHighlightFields())) {
                String firstHl = innerHits.getHighlightFields().get(0);
                int dotIdx = firstHl.indexOf('.');
                if (dotIdx > 0) {
                    nestedPath = firstHl.substring(0, dotIdx);
                }
            }
        }

        List<String> normalFields = new ArrayList<>();
        List<String> nestedFields = new ArrayList<>();

        for (String f : keywordFields) {
            if (nestedPath != null && f.startsWith(nestedPath + ".")) {
                nestedFields.add(f);
            } else {
                normalFields.add(f);
            }
        }

        List<Object> shouldList = new ArrayList<>();

        if (!normalFields.isEmpty()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("query", keyword);
            mm.put("fields", normalFields);
            shouldList.add(Collections.singletonMap("multi_match", mm));
        }

        if (!nestedFields.isEmpty()) {
            Map<String, Object> nestedMatch;
            if (nestedFields.size() == 1) {
                nestedMatch = Collections.singletonMap("match", Collections.singletonMap(nestedFields.get(0), keyword));
            } else {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("query", keyword);
                mm.put("fields", nestedFields);
                nestedMatch = Collections.singletonMap("multi_match", mm);
            }
            shouldList.add(wrapNested(nestedPath, nestedMatch, innerHits));
        }

        if (shouldList.isEmpty()) {
            return Collections.singletonMap("match_all", Collections.emptyMap());
        }

        if (shouldList.size() == 1) {
            return (Map<String, Object>) shouldList.get(0);
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("should", shouldList);
        bool.put("minimum_should_match", 1);
        return Collections.singletonMap("bool", bool);
    }

    /**
     * 构建高亮 Highlight 配置
     */
    private static Map<String, Object> buildHighlight(EsSearchRequest.Highlight highlight) {
        Map<String, Object> hl = new LinkedHashMap<>();
        hl.put("pre_tags", Collections.singletonList(highlight.getPreTag()));
        hl.put("post_tags", Collections.singletonList(highlight.getPostTag()));

        Map<String, Object> fieldsMap = new LinkedHashMap<>();
        for (String f : highlight.getFields()) {
            Map<String, Object> conf = new LinkedHashMap<>();
            conf.put("fragment_size", highlight.getFragmentSize() != null ? highlight.getFragmentSize() : 150);
            conf.put("number_of_fragments", highlight.getNumberOfFragments() != null ? highlight.getNumberOfFragments() : 3);
            fieldsMap.put(f, conf);
        }
        hl.put("fields", fieldsMap);
        return hl;
    }

    /**
     * 构建排序配置 (普通排序 + 通用脚本排序)
     */
    private static List<Object> buildSorts(EsSearchRequest request) {
        List<Object> sortList = new ArrayList<>();

        // 脚本排序
        if (request.getScriptSort() != null) {
            EsSearchRequest.ScriptSort scriptSort = request.getScriptSort();
            String scriptSource = scriptSort.getScriptSource();

            // 若未提供自定义脚本，但提供了 baseDayOfYear 和 目标排序字段，则自动生成通用月日邻近度排序脚本
            if (!StringUtils.hasText(scriptSource) && scriptSort.getBaseDayOfYear() != null && StringUtils.hasText(scriptSort.getField())) {
                scriptSource = "String d = doc['" + scriptSort.getField() + "'].value; " +
                               "if (d == null || d.length() < 8) return 999; " +
                               "int m = Integer.parseInt(d.substring(4, 6)); " +
                               "int day = Integer.parseInt(d.substring(6, 8)); " +
                               "int[] daysBefore = new int[]{0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334}; " +
                               "int doy = daysBefore[m - 1] + day; " +
                               "double diff = Math.abs(doy - params.baseDayOfYear); " +
                               "return Math.min(diff, 365.0 - diff);";
            }

            if (StringUtils.hasText(scriptSource)) {
                Map<String, Object> scriptObj = new LinkedHashMap<>();
                scriptObj.put("type", "number");

                Map<String, Object> scriptDetail = new LinkedHashMap<>();
                scriptDetail.put("source", scriptSource);
                scriptDetail.put("lang", "painless");

                if (scriptSort.getBaseDayOfYear() != null) {
                    scriptDetail.put("params", Collections.singletonMap("baseDayOfYear", scriptSort.getBaseDayOfYear()));
                }
                scriptObj.put("script", scriptDetail);
                scriptObj.put("order", scriptSort.getOrder() != null ? scriptSort.getOrder().getValue() : "asc");

                sortList.add(Collections.singletonMap("_script", scriptObj));
            }
        }

        // 普通字段排序
        if (!CollectionUtils.isEmpty(request.getSorts())) {
            for (EsSearchRequest.Sort sort : request.getSorts()) {
                String field = sort.getField();
                String order = sort.getOrder() != null ? sort.getOrder().getValue() : "desc";
                sortList.add(Collections.singletonMap(field, Collections.singletonMap("order", order)));
            }
        }

        return sortList;
    }

    /**
     * 构建返回字段过滤
     */
    private static void buildSourceFiltering(EsSearchRequest request, Map<String, Object> dsl) {
        if (!CollectionUtils.isEmpty(request.getSourceIncludes()) || !CollectionUtils.isEmpty(request.getSourceExcludes())) {
            Map<String, Object> source = new LinkedHashMap<>();
            if (!CollectionUtils.isEmpty(request.getSourceIncludes())) {
                source.put("includes", request.getSourceIncludes());
            }
            if (!CollectionUtils.isEmpty(request.getSourceExcludes())) {
                source.put("excludes", request.getSourceExcludes());
            }
            dsl.put("_source", source);
        }
    }

    private EsQueryBuilder() {
    }

    /**
     * 递归构建聚合配置树
     *
     * @param aggNodes 聚合节点配置列表
     * @return 聚合 DSL Map
     */
    public static Map<String, Object> buildAggregations(List<EsSearchRequest.AggNode> aggNodes) {
        Map<String, Object> aggsMap = new LinkedHashMap<>();

        for (EsSearchRequest.AggNode node : aggNodes) {
            if (node == null || !StringUtils.hasText(node.getName())) {
                continue;
            }

            AggType type = node.getType() != null ? node.getType() : AggType.TERMS;
            Map<String, Object> aggBody = new LinkedHashMap<>();

            switch (type) {
                case TERMS:
                    Map<String, Object> terms = new LinkedHashMap<>();
                    terms.put("field", node.getField());
                    terms.put("size", node.getSize() != null ? node.getSize() : 10);
                    if (StringUtils.hasText(node.getOrderKey())) {
                        String orderDir = node.getOrderDir() != null ? node.getOrderDir().getValue() : "desc";
                        terms.put("order", Collections.singletonMap(node.getOrderKey(), orderDir));
                    }
                    aggBody.put("terms", terms);
                    break;

                case CARDINALITY:
                    Map<String, Object> cardinality = new LinkedHashMap<>();
                    if (StringUtils.hasText(node.getScriptSource())) {
                        Map<String, Object> script = new LinkedHashMap<>();
                        script.put("source", node.getScriptSource());
                        script.put("lang", "painless");
                        cardinality.put("script", script);
                    } else {
                        cardinality.put("field", node.getField());
                    }
                    aggBody.put("cardinality", cardinality);
                    break;

                case VALUE_COUNT:
                    Map<String, Object> valueCount = new LinkedHashMap<>();
                    valueCount.put("field", node.getField());
                    aggBody.put("value_count", valueCount);
                    break;

                case TOP_HITS:
                    Map<String, Object> topHits = new LinkedHashMap<>();
                    topHits.put("size", node.getSize() != null ? node.getSize() : 1);
                    if (!CollectionUtils.isEmpty(node.getSorts())) {
                        List<Object> sortList = new ArrayList<>();
                        for (EsSearchRequest.Sort sort : node.getSorts()) {
                            String dir = sort.getOrder() != null ? sort.getOrder().getValue() : "desc";
                            sortList.add(Collections.singletonMap(sort.getField(), Collections.singletonMap("order", dir)));
                        }
                        topHits.put("sort", sortList);
                    }
                    if (!CollectionUtils.isEmpty(node.getSourceIncludes())) {
                        topHits.put("_source", Collections.singletonMap("includes", node.getSourceIncludes()));
                    }
                    aggBody.put("top_hits", topHits);
                    break;
            }

            // 递归构建子聚合
            if (!CollectionUtils.isEmpty(node.getChildren())) {
                aggBody.put("aggs", buildAggregations(node.getChildren()));
            }

            aggsMap.put(node.getName(), aggBody);
        }

        return aggsMap;
    }
}
