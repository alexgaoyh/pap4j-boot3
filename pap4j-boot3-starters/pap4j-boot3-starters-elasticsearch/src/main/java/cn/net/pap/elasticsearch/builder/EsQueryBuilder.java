package cn.net.pap.elasticsearch.builder;

import cn.net.pap.elasticsearch.dto.AggType;
import cn.net.pap.elasticsearch.dto.EsSearchAction;
import cn.net.pap.elasticsearch.dto.EsSearchRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        Map<String, Object> highlightDsl = resolveHighlight(request);
        if (highlightDsl != null && !highlightDsl.isEmpty()) {
            dsl.put("highlight", highlightDsl);
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
     *
     * @param request 统一检索请求
     * @return 根查询 DSL Map
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
            // 导航属于 filter 过滤，不需要返回子文档细节和高亮（不挂载 inner_hits，但允许通过 innerHits 推导 nested 路径）
            Map<String, Object> leaf = buildLeafQuery(navCond, request.getInnerHits(), false);
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
     * @param conditions    条件列表
     * @param innerHitsList innerHits 配置列表（可选）
     * @return 左结合组合后的 Query Map
     */
    public static Map<String, Object> buildLeftAssociativeQuery(List<EsSearchRequest.Condition> conditions, List<EsSearchRequest.InnerHits> innerHitsList) {
        if (CollectionUtils.isEmpty(conditions)) {
            return null;
        }

        Map<String, Object> currentExpr = null;

        for (EsSearchRequest.Condition condition : conditions) {
            Map<String, Object> leafQuery = buildLeafQuery(condition, innerHitsList, true);
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
     *
     * @param condition       检索条件
     * @param innerHitsList   innerHits 配置列表
     * @param enableInnerHits 是否挂载 inner_hits
     * @return 叶子查询 DSL Map
     */
    private static Map<String, Object> buildLeafQuery(EsSearchRequest.Condition condition, List<EsSearchRequest.InnerHits> innerHitsList, boolean enableInnerHits) {
        if (condition == null || !StringUtils.hasText(condition.getField())) {
            return null;
        }

        String rawField = condition.getField().trim();
        String match = condition.getMatch();
        if (match == null) {
            match = "fuzzy";
        }
        match = match.trim().toLowerCase(Locale.ROOT);

        String field;
        if (rawField.startsWith("-")) {
            String base = rawField.substring(1);
            field = base.endsWith(".keyword") ? base : base + ".keyword";
        } else if ("term".equals(match) || "terms".equals(match) || "wildcard".equals(match) || "prefix".equals(match)) {
            field = (rawField.endsWith(".keyword") || isNestedField(rawField)) ? rawField : rawField + ".keyword";
        } else {
            field = rawField;
        }

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
        String nestedPath = resolveNestedPath(condition, field, innerHitsList);
        if (StringUtils.hasText(nestedPath)) {
            EsSearchRequest.InnerHits matchedInnerHits = enableInnerHits ? findInnerHitsByPath(nestedPath, innerHitsList) : null;
            return wrapNested(nestedPath, leaf, matchedInnerHits);
        }

        return leaf;
    }

    /**
     * 通用推导 nested 路径
     *
     * @param condition     检索条件
     * @param field         字段名
     * @param innerHitsList innerHits 配置列表
     * @return nested 路径字符串，非 nested 字段时返回 null
     */
    private static String resolveNestedPath(EsSearchRequest.Condition condition, String field, List<EsSearchRequest.InnerHits> innerHitsList) {
        if (StringUtils.hasText(condition.getNestedPath())) {
            return condition.getNestedPath();
        }
        if (!CollectionUtils.isEmpty(innerHitsList)) {
            for (EsSearchRequest.InnerHits ih : innerHitsList) {
                String path = resolvePathFromInnerHits(ih);
                if (StringUtils.hasText(path) && field.startsWith(path + ".")) {
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * 从 innerHits 提取 nested 路径（优先取 path，兜底从首个高亮字段截取）
     */
    private static String resolvePathFromInnerHits(EsSearchRequest.InnerHits ih) {
        if (ih == null) {
            return null;
        }
        if (StringUtils.hasText(ih.getPath())) {
            return ih.getPath();
        }
        if (!CollectionUtils.isEmpty(ih.getHighlightFields())) {
            String firstHl = ih.getHighlightFields().get(0);
            int dotIdx = firstHl.indexOf('.');
            if (dotIdx > 0) {
                return firstHl.substring(0, dotIdx);
            }
        }
        return null;
    }

    /**
     * 根据 path 匹配对应的 InnerHits 配置
     */
    private static EsSearchRequest.InnerHits findInnerHitsByPath(String path, List<EsSearchRequest.InnerHits> innerHitsList) {
        if (CollectionUtils.isEmpty(innerHitsList) || !StringUtils.hasText(path)) {
            return null;
        }
        for (EsSearchRequest.InnerHits ih : innerHitsList) {
            if (path.equals(resolvePathFromInnerHits(ih))) {
                return ih;
            }
        }
        return null;
    }

    /**
     * 包装为 nested 查询（支持单层以及多级 nested 嵌套洋葱模型包装）
     *
     * @param path       nested 路径（可为单级如 comments，也可为多级如 dept.employee）
     * @param innerQuery 内部查询 DSL
     * @param innerHits  innerHits 配置
     * @return 包装后的 nested 查询 DSL Map
     */
    private static Map<String, Object> wrapNested(String path, Map<String, Object> innerQuery, EsSearchRequest.InnerHits innerHits) {
        if (!StringUtils.hasText(path)) {
            return innerQuery;
        }

        int dotIdx = path.indexOf('.');
        if (dotIdx <= 0) {
            return wrapSingleNested(path, innerQuery, innerHits);
        }

        String[] parts = path.split("\\.");
        List<String> levels = new ArrayList<>(parts.length);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(".");
            }
            sb.append(part);
            levels.add(sb.toString());
        }

        String deepestPath = levels.get(levels.size() - 1);
        Map<String, Object> currentNested = wrapSingleNested(deepestPath, innerQuery, null);

        for (int i = levels.size() - 2; i >= 0; i--) {
            boolean isTopLevel = (i == 0);
            currentNested = wrapSingleNested(levels.get(i), currentNested, isTopLevel ? innerHits : null);
        }

        return currentNested;
    }

    /**
     * 单层 nested 包装
     */
    private static Map<String, Object> wrapSingleNested(String path, Map<String, Object> innerQuery, EsSearchRequest.InnerHits innerHits) {
        Map<String, Object> nestedBody = new LinkedHashMap<>();
        nestedBody.put("path", path);
        nestedBody.put("query", innerQuery);

        if (innerHits != null) {
            nestedBody.put("inner_hits", buildInnerHits(innerHits, path));
        }

        return Collections.singletonMap("nested", nestedBody);
    }

    /**
     * 构建 inner_hits
     */
    private static Map<String, Object> buildInnerHits(EsSearchRequest.InnerHits innerHits, String defaultPath) {
        Map<String, Object> ih = new LinkedHashMap<>();
        String name = StringUtils.hasText(innerHits.getName())
                ? innerHits.getName()
                : (StringUtils.hasText(defaultPath) ? defaultPath : "inner");
        ih.put("name", name);
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
            highlight.put("pre_tags", Collections.singletonList("<span style='color:red;font-weight:bold'>"));
            highlight.put("post_tags", Collections.singletonList("</span>"));
            highlight.put("fields", hlFields);
            ih.put("highlight", highlight);
        }

        return ih;
    }

    /**
     * 构建关键词检索 Query (支持普通字段与多个 nested 字段)
     */
    private static Map<String, Object> buildKeywordQuery(String keyword, List<String> keywordFields, List<EsSearchRequest.InnerHits> innerHitsList) {
        if (CollectionUtils.isEmpty(keywordFields)) {
            // 未指定具体字段时无法构建 nested 块，故 innerHits 不生效，退化为全局 multi_match 检索（匹配映射中所有符合条件的默认字段）
            return Collections.singletonMap("multi_match", Collections.singletonMap("query", keyword));
        }

        // 1. 构建 path -> InnerHits 映射表
        Map<String, EsSearchRequest.InnerHits> pathToConfigMap = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(innerHitsList)) {
            for (EsSearchRequest.InnerHits ih : innerHitsList) {
                String path = resolvePathFromInnerHits(ih);
                if (StringUtils.hasText(path)) {
                    pathToConfigMap.put(path, ih);
                }
            }
        }

        // 2. 字段分流：普通字段 vs 属于各个 nested path 的字段
        List<String> normalFields = new ArrayList<>();
        Map<String, List<String>> nestedFieldsByPath = new LinkedHashMap<>();

        for (String f : keywordFields) {
            String matchedPath = null;
            for (String path : pathToConfigMap.keySet()) {
                if (f.startsWith(path + ".")) {
                    matchedPath = path;
                    break;
                }
            }
            if (matchedPath != null) {
                nestedFieldsByPath.computeIfAbsent(matchedPath, k -> new ArrayList<>()).add(f);
            } else {
                normalFields.add(f);
            }
        }

        List<Object> shouldList = new ArrayList<>();

        // 3. 构建普通字段检索
        if (!normalFields.isEmpty()) {
            for (String f : normalFields) {
                shouldList.add(Collections.singletonMap("match_phrase", Collections.singletonMap(f, keyword)));
            }
        }

        // 4. 每个 nested 路径独立构建并用 wrapNested 包装
        for (Map.Entry<String, List<String>> entry : nestedFieldsByPath.entrySet()) {
            String path = entry.getKey();
            List<String> fields = entry.getValue();
            EsSearchRequest.InnerHits ih = pathToConfigMap.get(path);

            List<Object> nestedShouldList = new ArrayList<>();
            for (String f : fields) {
                nestedShouldList.add(Collections.singletonMap("match_phrase", Collections.singletonMap(f, keyword)));
            }
            Map<String, Object> nestedQuery;
            if (nestedShouldList.size() == 1) {
                nestedQuery = castToMap(nestedShouldList.get(0));
            } else {
                Map<String, Object> nb = new LinkedHashMap<>();
                nb.put("should", nestedShouldList);
                nb.put("minimum_should_match", 1);
                nestedQuery = Collections.singletonMap("bool", nb);
            }
            shouldList.add(wrapNested(path, nestedQuery, ih));
        }

        if (shouldList.isEmpty()) {
            return Collections.singletonMap("match_all", Collections.emptyMap());
        }

        if (shouldList.size() == 1) {
            return castToMap(shouldList.get(0));
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("should", shouldList);
        bool.put("minimum_should_match", 1);
        return Collections.singletonMap("bool", bool);
    }

    /**
     * 解析推导高亮配置
     *
     * @param request 检索请求
     * @return 高亮 Map 配置，若无高亮则返回 null
     */
    private static Map<String, Object> resolveHighlight(EsSearchRequest request) {
        if (request.getHighlight() != null && !CollectionUtils.isEmpty(request.getHighlight().getFields())) {
            return buildHighlight(request.getHighlight());
        }
        if (Boolean.FALSE.equals(request.getAutoHighlight())) {
            return null;
        }
        Set<String> hlFields = new LinkedHashSet<>();
        if (StringUtils.hasText(request.getKeyword()) && !CollectionUtils.isEmpty(request.getKeywordFields())) {
            for (String field : request.getKeywordFields()) {
                if (StringUtils.hasText(field) && !isNestedPathField(field.trim(), request.getInnerHits())) {
                    hlFields.add(field.trim());
                }
            }
        }
        if (!CollectionUtils.isEmpty(request.getConditions())) {
            for (EsSearchRequest.Condition condition : request.getConditions()) {
                if (condition != null && StringUtils.hasText(condition.getNestedPath())) {
                    // nested 字段的高亮应由 inner_hits 承载，不注入顶层 highlight
                    continue;
                }
                String condField = resolveConditionHighlightField(condition);
                if (condField != null && !isNestedPathField(condField, request.getInnerHits())) {
                    hlFields.add(condField);
                }
            }
        }
        if (!hlFields.isEmpty()) {
            EsSearchRequest.Highlight autoHl = new EsSearchRequest.Highlight();
            autoHl.setFields(new ArrayList<>(hlFields));
            autoHl.setNumberOfFragments(0);
            return buildHighlight(autoHl);
        }
        return null;
    }

    private static String resolveConditionHighlightField(EsSearchRequest.Condition condition) {
        if (condition == null || !StringUtils.hasText(condition.getField())) {
            return null;
        }
        String field = condition.getField().trim();
        boolean navigationField = field.startsWith("-");
        String normalizedField = navigationField ? field.substring(1) : field;
        if (!StringUtils.hasText(normalizedField)) {
            return null;
        }
        String match = condition.getMatch();
        if (match != null) {
            match = match.trim().toLowerCase(Locale.ROOT);
        }
        if (!navigationField && "range".equals(match)) {
            return null;
        }
        if (navigationField || "term".equals(match) || "terms".equals(match)
            || "wildcard".equals(match) || "prefix".equals(match)) {
            return normalizedField.endsWith(".keyword") ? normalizedField : normalizedField + ".keyword";
        }
        return normalizedField;
    }

    /**
     * 判断字段是否为 nested 嵌套字段路径
     *
     * @param field 字段名
     * @return true 如果是 nested 嵌套字段路径
     */
    private static boolean isNestedField(String field) {
        int separator = field.lastIndexOf('.');
        if (separator <= 0) {
            return false;
        }
        return !"keyword".equals(field.substring(separator + 1));
    }

    /**
     * 判断字段是否属于 nested 检索范畴：优先依据 innerHits 声明的路径前缀，其次回退到字段名启发式判断。
     *
     * @param field         字段名
     * @param innerHitsList innerHits 配置列表
     * @return true 表示属于 nested 字段
     */
    private static boolean isNestedPathField(String field, List<EsSearchRequest.InnerHits> innerHitsList) {
        if (!CollectionUtils.isEmpty(innerHitsList)) {
            for (EsSearchRequest.InnerHits ih : innerHitsList) {
                String path = resolvePathFromInnerHits(ih);
                if (StringUtils.hasText(path) && field.startsWith(path + ".")) {
                    return true;
                }
            }
        }
        return isNestedField(field);
    }

    /**
     * 构建高亮 Highlight 配置
     *
     * @param highlight 高亮配置对象
     * @return 高亮 Map 配置
     */
    private static Map<String, Object> buildHighlight(EsSearchRequest.Highlight highlight) {
        Map<String, Object> hl = new LinkedHashMap<>();
        String preTag = StringUtils.hasText(highlight.getPreTag())
                ? highlight.getPreTag() : "<span style='color:red;font-weight:bold'>";
        String postTag = StringUtils.hasText(highlight.getPostTag())
                ? highlight.getPostTag() : "</span>";
        hl.put("pre_tags", Collections.singletonList(preTag));
        hl.put("post_tags", Collections.singletonList(postTag));

        Map<String, Object> fieldsMap = new LinkedHashMap<>();
        for (String f : highlight.getFields()) {
            Map<String, Object> conf = new LinkedHashMap<>();
            if (highlight.getFragmentSize() != null) {
                conf.put("fragment_size", highlight.getFragmentSize());
            }
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
            if (!StringUtils.hasText(scriptSource)
                    && scriptSort.getBaseDayOfYear() != null
                    && StringUtils.hasText(scriptSort.getField())) {
                scriptSource = buildDefaultDateSortScript(scriptSort.getField());
            }

            if (StringUtils.hasText(scriptSource)) {
                Map<String, Object> scriptObj = new LinkedHashMap<>();
                scriptObj.put("type", "number");

                Map<String, Object> scriptDetail = new LinkedHashMap<>();
                scriptDetail.put("source", scriptSource);
                scriptDetail.put("lang", "painless");

                if (scriptSort.getBaseDayOfYear() != null) {
                    scriptDetail.put("params", Collections.singletonMap("baseDayOfYear",
                            scriptSort.getBaseDayOfYear()));
                }
                scriptObj.put("script", scriptDetail);
                String orderVal = scriptSort.getOrder() != null
                        ? scriptSort.getOrder().getValue() : "asc";
                scriptObj.put("order", orderVal);

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
                        String orderDir = (node.getOrderDir() != null)
                                ? node.getOrderDir().getValue() : "desc";
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
                            String dir = (sort.getOrder() != null)
                                    ? sort.getOrder().getValue() : "desc";
                            sortList.add(Collections.singletonMap(sort.getField(),
                                    Collections.singletonMap("order", dir)));
                        }
                        topHits.put("sort", sortList);
                    }
                    if (!CollectionUtils.isEmpty(node.getSourceIncludes())) {
                        topHits.put("_source", Collections.singletonMap("includes", node.getSourceIncludes()));
                    }
                    aggBody.put("top_hits", topHits);
                    break;

                case NESTED:
                    Map<String, Object> nested = new LinkedHashMap<>();
                    nested.put("path", node.getField());
                    aggBody.put("nested", nested);
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

    private static String buildDefaultDateSortScript(String field) {
        return "String d = doc['" + field + "'].value; "
                + "if (d == null || d.length() < 8) return 999; "
                + "int m = Integer.parseInt(d.substring(4, 6)); "
                + "int day = Integer.parseInt(d.substring(6, 8)); "
                + "int[] daysBefore = new int[]{0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334}; "
                + "int doy = daysBefore[m - 1] + day; "
                + "double diff = Math.abs(doy - params.baseDayOfYear); "
                + "return Math.min(diff, 365.0 - diff);";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
