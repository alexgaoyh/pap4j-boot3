package cn.net.pap.elasticsearch.service;

import cn.net.pap.elasticsearch.builder.EsQueryBuilder;
import cn.net.pap.elasticsearch.config.ElasticsearchProperties;
import cn.net.pap.elasticsearch.dto.AggType;
import cn.net.pap.elasticsearch.dto.EsSearchAction;
import cn.net.pap.elasticsearch.dto.EsSearchRequest;
import cn.net.pap.elasticsearch.dto.EsSearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 Elasticsearch 检索服务
 */
public class EsSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsSearchService.class);

    /**
     * 缺省兜底每页最大条数限制
     */
    private static final int FALLBACK_MAX_PAGE_SIZE = 100;

    /**
     * 缺省兜底默认每页条数
     */
    private static final int FALLBACK_DEFAULT_PAGE_SIZE = 10;

    /**
     * 缺省兜底深分页单批拉取条数
     */
    private static final int FALLBACK_DEEP_BATCH_SIZE = 1000;

    /**
     * 缺省兜底分页策略切换阈值（与 ES 默认 index.max_result_window 一致）
     */
    private static final long FALLBACK_MAX_RESULT_WINDOW = 10000L;

    /**
     * ES 响应反序列化类型，静态复用，避免每次请求创建匿名 TypeReference 实例
     */
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ElasticsearchProperties properties;

    /**
     * 构造通用 Elasticsearch 检索服务
     *
     * @param restClient   ES 底层客户端
     * @param objectMapper JSON 序列化工具
     * @param properties   ES 配置属性
     */
    public EsSearchService(RestClient restClient, ObjectMapper objectMapper, ElasticsearchProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 统一入口方法：根据 action 执行检索、聚合或单条获取
     *
     * @param request 统一检索入参
     * @return 检索结果
     */
    public EsSearchResult execute(EsSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("EsSearchRequest must not be null");
        }

        EsSearchAction action = request.getAction() != null ? request.getAction() : EsSearchAction.SEARCH;
        return switch (action) {
            case RECORD -> record(resolveIndexName(request.getIndexName()), request.getRid());
            case AGGREGATE -> aggregate(request);
            case SEARCH -> search(request);
        };
    }

    /**
     * 单条记录明细查询（按主键 rid）
     *
     * @param indexName 索引名
     * @param rid       文档 ID
     * @return 结果
     */
    public EsSearchResult record(String indexName, String rid) {
        if (!StringUtils.hasText(rid)) {
            throw new IllegalArgumentException("rid must not be null or empty for RECORD action");
        }

        String targetIndex = resolveIndexName(indexName);
        String endpoint = "/" + targetIndex + "/_doc/" + rid.trim();

        try {
            Request req = new Request("GET", endpoint);
            Response res = restClient.performRequest(req);
            Map<String, Object> respMap = readResponse(res);

            Boolean found = (Boolean) respMap.get("found");
            if (Boolean.TRUE.equals(found)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) respMap.get("_source");
                return EsSearchResult.builder()
                        .total(1L)
                        .pageNum(1)
                        .pageSize(1)
                        .record(source)
                        .rawResponse(respMap)
                        .build();
            } else {
                return EsSearchResult.builder()
                        .total(0L)
                        .pageNum(1)
                        .pageSize(1)
                        .record(null)
                        .rawResponse(respMap)
                        .build();
            }
        } catch (ResponseException e) {
            int status = e.getResponse().getStatusLine().getStatusCode();
            if (status == 404) {
                log.info("[ES-Record] Document not found for rid: {} in index: {}", rid, targetIndex);
                return EsSearchResult.builder()
                        .total(0L)
                        .pageNum(1)
                        .pageSize(1)
                        .record(null)
                        .build();
            }
            log.error("[ES-Record] Failed to query document rid: {} in index: {}", rid, targetIndex, e);
            throw new RuntimeException("ES query record failed on index: " + targetIndex
                    + ", status=" + status + ", reason=" + extractFailureReason(e));
        } catch (IOException e) {
            log.error("[ES-Record] IO error while querying document rid: {} in index: {}", rid, targetIndex, e);
            throw new RuntimeException("ES IO error on index: " + targetIndex);
        }
    }

    /**
     * 分页多条件全文检索
     * <p>
     * from + size 在 max_result_window 以内时保持原生 from/size 语义；
     * 超出时内部自动切换为 search_after 游标翻页，因此**不再因为超过 10000 而拒绝请求**，
     * 对外返回结果结构与语义保持一致。
     *
     * @param request 检索入参
     * @return 检索结果
     */
    public EsSearchResult search(EsSearchRequest request) {
        int pageNum = normalizePageNum(request.getPageNum());
        int pageSize = normalizePageSize(request.getPageSize());
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);

        long from = ((long) pageNum - 1) * pageSize;
        long maxResultWindow = resolveMaxResultWindow();
        if (from + pageSize > maxResultWindow) {
            return deepSearch(request, from, pageSize, maxResultWindow);
        }

        String targetIndex = resolveIndexName(request.getIndexName());
        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);

        Map<String, Object> respMap = executeSearchRequest(targetIndex, dsl);
        return parseSearchResponse(request, respMap);
    }

    /**
     * 深分页检索：内部使用 search_after 游标逐批回溯定位目标页，避免 from + size 触达 max_result_window。
     * <p>
     * 对外契约与普通检索保持一致：total / pageNum / pageSize / pages / rows / hitRows / aggregations / rawResponse
     * 均与同一请求用 from(size) 取数时的结构相同，仅内部请求方式发生变化。
     *
     * @param request         检索入参
     * @param from            目标页起始偏移
     * @param pageSize        每页条数
     * @param maxResultWindow 分页策略切换阈值
     * @return 检索结果
     */
    private EsSearchResult deepSearch(EsSearchRequest request, long from, int pageSize, long maxResultWindow) {
        String targetIndex = resolveIndexName(request.getIndexName());
        int batchSize = resolveDeepBatchSize(pageSize, maxResultWindow);
        long targetEnd = from + pageSize;

        List<Map<String, Object>> pageHits = new ArrayList<>(pageSize);
        List<Object> searchAfter = null;
        Map<String, Object> firstRespMap = null;
        Map<String, Object> lastRespMap = null;
        long offset = 0L;

        while (offset < targetEnd) {
            Map<String, Object> dsl = EsQueryBuilder.buildSearchAfterDsl(request, batchSize, searchAfter);
            Map<String, Object> respMap = executeSearchRequest(targetIndex, dsl);
            if (firstRespMap == null) {
                firstRespMap = respMap;
            }
            lastRespMap = respMap;

            List<Map<String, Object>> batchHits = extractHits(respMap);
            if (batchHits.isEmpty()) {
                break;
            }

            for (Map<String, Object> hit : batchHits) {
                if (offset >= from && pageHits.size() < pageSize) {
                    pageHits.add(hit);
                }
                offset++;
            }

            List<Object> cursor = extractSortValues(batchHits.get(batchHits.size() - 1));
            if (cursor == null || cursor.isEmpty()) {
                break;
            }
            searchAfter = cursor;

            if (batchHits.size() < batchSize) {
                break;
            }
        }

        return parseSearchResponse(request, assemblePagedResponse(firstRespMap, lastRespMap, pageHits));
    }

    /**
     * 分页策略切换阈值，配置非法时回退到 ES 默认的 10000
     */
    private long resolveMaxResultWindow() {
        long configured = properties != null ? properties.getMaxResultWindow() : 0L;
        return configured > 0 ? configured : FALLBACK_MAX_RESULT_WINDOW;
    }

    /**
     * 计算深分页单批拉取条数：取配置值与 max_result_window 的较小值，且不小于单页条数
     */
    private int resolveDeepBatchSize(int pageSize, long maxResultWindow) {
        int configured = (properties != null && properties.getDeepPaginationBatchSize() > 0)
                ? properties.getDeepPaginationBatchSize() : FALLBACK_DEEP_BATCH_SIZE;
        long upperBound = Math.min(configured, maxResultWindow);
        int batchSize = (int) Math.min(upperBound, Integer.MAX_VALUE);
        return Math.max(batchSize, pageSize);
    }

    /**
     * 将深分页游标多批返回的命中重新拼装成一次 _search 响应的形态，保证后续解析逻辑与普通检索完全一致
     */
    private Map<String, Object> assemblePagedResponse(
            Map<String, Object> firstRespMap,
            Map<String, Object> lastRespMap,
            List<Map<String, Object>> pageHits) {
        Map<String, Object> base = lastRespMap != null ? lastRespMap
                : (firstRespMap != null ? firstRespMap : new LinkedHashMap<>());
        Map<String, Object> assembled = new LinkedHashMap<>(base);

        Map<String, Object> hitsWrapper = new LinkedHashMap<>();
        Map<String, Object> firstHitsWrapper = firstRespMap != null ? asMap(firstRespMap.get("hits")) : null;
        if (firstHitsWrapper != null && firstHitsWrapper.get("total") != null) {
            hitsWrapper.put("total", firstHitsWrapper.get("total"));
        }
        hitsWrapper.put("hits", new ArrayList<>(pageHits));
        assembled.put("hits", hitsWrapper);
        return assembled;
    }

    /**
     * 从 _search 响应中提取命中列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractHits(Map<String, Object> respMap) {
        Map<String, Object> hitsWrapper = respMap != null ? asMap(respMap.get("hits")) : null;
        Object hitsList = hitsWrapper != null ? hitsWrapper.get("hits") : null;
        return hitsList instanceof List ? (List<Map<String, Object>>) hitsList : Collections.emptyList();
    }

    /**
     * 从命中的 sort 字段提取 search_after 游标值
     */
    @SuppressWarnings("unchecked")
    private List<Object> extractSortValues(Map<String, Object> hit) {
        if (hit == null) {
            return null;
        }
        Object sortValues = hit.get("sort");
        return sortValues instanceof List ? (List<Object>) sortValues : null;
    }

    /**
     * 从 _search 响应中提取命中总数，无法识别时返回 -1
     */
    @SuppressWarnings("unchecked")
    private long extractTotalHits(Map<String, Object> respMap) {
        Map<String, Object> hitsWrapper = respMap != null ? asMap(respMap.get("hits")) : null;
        if (hitsWrapper == null) {
            return -1L;
        }
        Object totalObj = hitsWrapper.get("total");
        if (totalObj instanceof Map) {
            Object val = ((Map<String, Object>) totalObj).get("value");
            if (val instanceof Number) {
                return ((Number) val).longValue();
            }
        } else if (totalObj instanceof Number) {
            return ((Number) totalObj).longValue();
        }
        return -1L;
    }

    /**
     * 聚合统计查询
     *
     * @param request 聚合入参
     * @return 聚合结果
     */
    public EsSearchResult aggregate(EsSearchRequest request) {
        request.setAction(EsSearchAction.AGGREGATE);
        request.setPageSize(0);

        String targetIndex = resolveIndexName(request.getIndexName());
        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);

        Map<String, Object> respMap = executeSearchRequest(targetIndex, dsl);
        return parseAggregateResponse(request, respMap);
    }

    /**
     * 底层执行 _search POST 请求
     */
    private Map<String, Object> executeSearchRequest(String indexName, Map<String, Object> dsl) {
        String endpoint = "/" + indexName + "/_search";
        try {
            String requestBody = objectMapper.writeValueAsString(dsl);
            log.info("[ES-Search] Executing search on {}: {}", endpoint, requestBody);

            Request req = new Request("POST", endpoint);
            req.setJsonEntity(requestBody);

            Response res = restClient.performRequest(req);
            return readResponse(res);
        } catch (ResponseException e) {
            log.error("[ES-Search] Elasticsearch rejected the search on index: {}", indexName, e);
            throw new RuntimeException("ES search execution failed on index: " + indexName
                    + ", status=" + e.getResponse().getStatusLine().getStatusCode()
                    + ", reason=" + extractFailureReason(e));
        } catch (IOException e) {
            log.error("[ES-Search] Failed to execute search query on index: {}", indexName, e);
            throw new RuntimeException("ES search execution failed on index: " + indexName);
        }
    }

    /**
     * 解析检索响应
     *
     * @param request 检索请求
     * @param respMap ES 响应 Map
     * @return 检索结果
     */
    @SuppressWarnings("unchecked")
    private EsSearchResult parseSearchResponse(EsSearchRequest request, Map<String, Object> respMap) {
        Map<String, Object> hitsWrapper = (Map<String, Object>) respMap.get("hits");
        long total = 0L;
        List<Map<String, Object>> sourceRows = new ArrayList<>();
        List<EsSearchResult.HitRow> rowList = new ArrayList<>();

        if (hitsWrapper != null) {
            long totalHits = extractTotalHits(respMap);
            if (totalHits >= 0) {
                total = totalHits;
            }

            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hitsWrapper.get("hits");
            if (hitsList != null) {
                for (Map<String, Object> hit : hitsList) {
                    String id = (String) hit.get("_id");
                    Double score = hit.get("_score") instanceof Number ? ((Number) hit.get("_score")).doubleValue() : null;
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    Map<String, List<String>> highlight = (Map<String, List<String>>) hit.get("highlight");
                    Map<String, Object> innerHits = mergeInnerHitsSuffixes((Map<String, Object>) hit.get("inner_hits"));

                    rowList.add(EsSearchResult.HitRow.builder()
                            .id(id)
                            .score(score)
                            .source(source)
                            .highlight(highlight)
                            .innerHits(innerHits)
                            .build());

                    Map<String, Object> row = source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
                    if (!CollectionUtils.isEmpty(highlight)) {
                        row.put("highlight", highlight);
                    }
                    sourceRows.add(row);
                }
            }
        }

        Map<String, Object> aggs = (Map<String, Object>) respMap.get("aggregations");

        int pageNum = request.getPageNum() != null && request.getPageNum() > 0 ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() >= 0 ? request.getPageSize() : 10;
        long pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;

        return EsSearchResult.builder()
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(pages)
                .rows(sourceRows)
                .hitRows(rowList)
                .aggregations(aggs)
                .rawResponse(respMap)
                .build();
    }

    /**
     * 解析聚合响应
     *
     * @param request 聚合入参
     * @param respMap ES 原始返回 Map
     * @return 结构化的检索聚合结果
     */
    @SuppressWarnings("unchecked")
    private EsSearchResult parseAggregateResponse(EsSearchRequest request, Map<String, Object> respMap) {
        Map<String, Object> hitsWrapper = (Map<String, Object>) respMap.get("hits");
        long total = 0L;
        if (hitsWrapper != null) {
            Object totalObj = hitsWrapper.get("total");
            if (totalObj instanceof Map) {
                Object val = ((Map<String, Object>) totalObj).get("value");
                if (val instanceof Number) {
                    total = ((Number) val).longValue();
                }
            }
        }

        Map<String, Object> rawAggs = (Map<String, Object>) respMap.get("aggregations");
        Map<String, Object> aggs;
        if (Boolean.FALSE.equals(request.getReturnBucketsOnly())) {
            aggs = rawAggs != null ? rawAggs : new LinkedHashMap<>();
        } else {
            aggs = parseAggregations(rawAggs, request.getAggregations());
        }

        return EsSearchResult.builder()
                .total(total)
                .pageNum(1)
                .pageSize(0)
                .rows(Collections.emptyList())
                .aggregations(aggs)
                .rawResponse(respMap)
                .build();
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAggregations(Map<String, Object> aggsMap, List<EsSearchRequest.AggNode> nodes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(aggsMap) || CollectionUtils.isEmpty(nodes)) {
            return aggsMap != null ? aggsMap : result;
        }
        for (EsSearchRequest.AggNode node : nodes) {
            if (node == null || !StringUtils.hasText(node.getName())) {
                continue;
            }
            Object rawAgg = aggsMap.get(node.getName());
            if (rawAgg instanceof Map) {
                result.put(node.getName(), parseSingleAggregation((Map<String, Object>) rawAgg, node));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object parseSingleAggregation(Map<String, Object> aggData, EsSearchRequest.AggNode node) {
        AggType type = node.getType() == null ? AggType.TERMS : node.getType();
        return switch (type) {
            case CARDINALITY, VALUE_COUNT -> aggData.get("value");
            case TOP_HITS -> parseTopHits(aggData);
            case TERMS -> parseTermsBuckets(aggData, node);
            case NESTED -> parseNestedAgg(aggData, node);
        };
    }

    @SuppressWarnings("unchecked")
    private Object parseNestedAgg(Map<String, Object> aggData, EsSearchRequest.AggNode node) {
        if (CollectionUtils.isEmpty(node.getChildren())) {
            return aggData.get("doc_count");
        }
        return parseAggregations(aggData, node.getChildren());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseTermsBuckets(Map<String, Object> aggData, EsSearchRequest.AggNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        Object bucketsObj = aggData.get("buckets");
        if (bucketsObj instanceof List) {
            List<?> buckets = (List<?>) bucketsObj;
            for (Object b : buckets) {
                if (b instanceof Map) {
                    Map<String, Object> bucketMap = (Map<String, Object>) b;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("key", bucketMap.get("key"));
                    item.put("count", bucketMap.get("doc_count"));
                    if (!CollectionUtils.isEmpty(node.getChildren())) {
                        item.putAll(parseAggregations(bucketMap, node.getChildren()));
                    }
                    list.add(item);
                }
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseTopHits(Map<String, Object> aggData) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> hitsWrapper = asMap(aggData.get("hits"));
        if (hitsWrapper != null) {
            Object hitsList = hitsWrapper.get("hits");
            if (hitsList instanceof List) {
                for (Object h : (List<?>) hitsList) {
                    Map<String, Object> hit = asMap(h);
                    if (hit != null) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", hit.get("_id"));
                        item.put("source", hit.get("_source"));
                        list.add(item);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 读取并解析 ES 响应体，直接流式反序列化，避免整段响应字符串拷贝
     *
     * @param res ES 响应
     * @return 响应 Map
     * @throws IOException 读取失败
     */
    private Map<String, Object> readResponse(Response res) throws IOException {
        try (InputStream content = res.getEntity().getContent()) {
            return objectMapper.readValue(content, RESPONSE_TYPE);
        }
    }

    /**
     * 尽力从 ES 错误响应中提取原因正文，避免把 400/404/401 等业务可诊断的信息吞成一句笼统错误。
     *
     * @param e ES 响应异常
     * @return 原始错误正文；若不可读则回退到异常 message
     */
    private String extractFailureReason(ResponseException e) {
        try {
            String body = EntityUtils.toString(e.getResponse().getEntity());
            if (StringUtils.hasText(body)) {
                return body;
            }
        } catch (IOException | RuntimeException ex) {
            log.error("[ES] Failed to read Elasticsearch error response body", ex);
        }
        return e.getMessage();
    }

    private int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        int defaultPageSize = (properties != null && properties.getDefaultPageSize() > 0)
                ? properties.getDefaultPageSize() : FALLBACK_DEFAULT_PAGE_SIZE;
        int maxPageSize = (properties != null && properties.getMaxPageSize() > 0)
                ? properties.getMaxPageSize() : FALLBACK_MAX_PAGE_SIZE;

        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        return Math.min(pageSize, maxPageSize);
    }

    private String resolveIndexName(String indexName) {
        if (StringUtils.hasText(indexName)) {
            return indexName.trim();
        }
        if (StringUtils.hasText(properties.getDefaultIndex())) {
            return properties.getDefaultIndex().trim();
        }
        throw new IllegalArgumentException("Elasticsearch indexName must be specified");
    }

    /**
     * 将带有动态序号后缀的 inner_hits（如 departments.members#2）透明合并归拢至基础路径（如 departments.members）
     *
     * @param rawInnerHits ES 原始返回的 inner_hits Map
     * @return 合并归拢后的 inner_hits Map（基础路径聚合了所有相同子文档的命中和高亮，原有的带后缀 key 亦完整保留兼容）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeInnerHitsSuffixes(Map<String, Object> rawInnerHits) {
        if (CollectionUtils.isEmpty(rawInnerHits)) {
            return rawInnerHits;
        }

        boolean hasSuffix = false;
        for (String key : rawInnerHits.keySet()) {
            if (isSuffixKey(key)) {
                hasSuffix = true;
                break;
            }
        }
        if (!hasSuffix) {
            return rawInnerHits;
        }

        Map<String, Object> merged = new LinkedHashMap<>(rawInnerHits);
        Map<String, List<String>> baseToKeys = new LinkedHashMap<>();
        for (String key : rawInnerHits.keySet()) {
            String baseKey = extractBaseKey(key);
            baseToKeys.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(key);
        }

        for (Map.Entry<String, List<String>> entry : baseToKeys.entrySet()) {
            String baseKey = entry.getKey();
            List<String> keys = entry.getValue();
            if (keys.size() <= 1 && keys.contains(baseKey)) {
                continue;
            }
            Map<String, Object> mergedBaseHitWrapper = mergeSingleBaseInnerHits(baseKey, keys, rawInnerHits);
            merged.put(baseKey, mergedBaseHitWrapper);
        }

        return merged;
    }

    private boolean isSuffixKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        int hashIdx = key.lastIndexOf('#');
        if (hashIdx <= 0 || hashIdx >= key.length() - 1) {
            return false;
        }
        String suffix = key.substring(hashIdx + 1);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String extractBaseKey(String key) {
        if (isSuffixKey(key)) {
            return key.substring(0, key.lastIndexOf('#'));
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeSingleBaseInnerHits(
            String baseKey,
            List<String> keys,
            Map<String, Object> rawInnerHits) {
        String primaryKey = rawInnerHits.containsKey(baseKey) ? baseKey : keys.get(0);
        Map<String, Object> baseWrapper = deepCopyMap(asMap(rawInnerHits.get(primaryKey)));
        if (baseWrapper == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> baseHitsBlock = asMap(baseWrapper.get("hits"));
        if (baseHitsBlock == null) {
            return baseWrapper;
        }

        List<Map<String, Object>> mergedSubHits = new ArrayList<>();
        List<?> rawBaseSubHits = (List<?>) baseHitsBlock.get("hits");
        if (rawBaseSubHits != null) {
            for (Object sub : rawBaseSubHits) {
                if (sub instanceof Map) {
                    mergedSubHits.add(deepCopyMap((Map<String, Object>) sub));
                }
            }
        }

        for (String k : keys) {
            if (k.equals(primaryKey)) {
                continue;
            }
            Object obj = rawInnerHits.get(k);
            if (!(obj instanceof Map)) {
                continue;
            }
            Map<String, Object> otherWrapper = (Map<String, Object>) obj;
            Map<String, Object> otherHitsBlock = asMap(otherWrapper.get("hits"));
            if (otherHitsBlock == null) {
                continue;
            }
            List<?> otherSubHits = (List<?>) otherHitsBlock.get("hits");
            if (otherSubHits == null) {
                continue;
            }

            for (Object otherItem : otherSubHits) {
                if (!(otherItem instanceof Map)) {
                    continue;
                }
                Map<String, Object> otherSubHit = deepCopyMap((Map<String, Object>) otherItem);

                boolean matched = false;
                for (Map<String, Object> existingSubHit : mergedSubHits) {
                    if (isSameSubHit(existingSubHit, otherSubHit)) {
                        mergeSubHitHighlight(existingSubHit, otherSubHit);
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    mergedSubHits.add(otherSubHit);
                }
            }
        }

        baseHitsBlock.put("hits", mergedSubHits);
        Object totalObj = baseHitsBlock.get("total");
        if (totalObj instanceof Map) {
            ((Map<String, Object>) totalObj).put("value", mergedSubHits.size());
        } else if (totalObj instanceof Number) {
            baseHitsBlock.put("total", mergedSubHits.size());
        }

        return baseWrapper;
    }

    private boolean isSameSubHit(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) {
            return false;
        }
        Object nestedA = a.get("_nested");
        Object nestedB = b.get("_nested");
        if (nestedA != null && nestedB != null) {
            return java.util.Objects.equals(nestedA, nestedB);
        }
        Object idA = a.get("_id");
        Object idB = b.get("_id");
        if (idA != null && idB != null) {
            return java.util.Objects.equals(idA, idB);
        }
        Object sourceA = a.get("_source");
        Object sourceB = b.get("_source");
        if (sourceA != null && sourceB != null) {
            return java.util.Objects.equals(sourceA, sourceB);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void mergeSubHitHighlight(Map<String, Object> existingSubHit, Map<String, Object> otherSubHit) {
        Map<String, Object> otherHl = asMap(otherSubHit.get("highlight"));
        if (CollectionUtils.isEmpty(otherHl)) {
            return;
        }
        Map<String, Object> existingHl = asMap(existingSubHit.get("highlight"));
        if (existingHl == null) {
            existingHl = new LinkedHashMap<>();
            existingSubHit.put("highlight", existingHl);
        }
        for (Map.Entry<String, Object> entry : otherHl.entrySet()) {
            if (!existingHl.containsKey(entry.getKey())) {
                existingHl.put(entry.getKey(), entry.getValue());
            } else {
                Object exVal = existingHl.get(entry.getKey());
                Object otVal = entry.getValue();
                if (exVal instanceof List && otVal instanceof List) {
                    List<Object> combined = new ArrayList<>((List<?>) exVal);
                    for (Object frag : (List<?>) otVal) {
                        if (!combined.contains(frag)) {
                            combined.add(frag);
                        }
                    }
                    existingHl.put(entry.getKey(), combined);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> original) {
        if (original == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : original.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                copy.put(e.getKey(), deepCopyMap((Map<String, Object>) v));
            } else if (v instanceof List) {
                copy.put(e.getKey(), deepCopyList((List<?>) v));
            } else {
                copy.put(e.getKey(), v);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private List<Object> deepCopyList(List<?> original) {
        if (original == null) {
            return null;
        }
        List<Object> copy = new ArrayList<>(original.size());
        for (Object item : original) {
            if (item instanceof Map) {
                copy.add(deepCopyMap((Map<String, Object>) item));
            } else if (item instanceof List) {
                copy.add(deepCopyList((List<?>) item));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }
}
