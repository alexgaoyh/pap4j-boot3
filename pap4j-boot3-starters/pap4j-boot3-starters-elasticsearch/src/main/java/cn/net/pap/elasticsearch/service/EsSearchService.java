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
     * 每页最大条数（与参照实现保持一致）
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 默认每页条数（与参照实现保持一致）
     */
    private static final int DEFAULT_PAGE_SIZE = 10;

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
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                log.info("[ES-Record] Document not found for rid: {} in index: {}", rid, targetIndex);
                return EsSearchResult.builder().total(0L).record(null).build();
            }
            log.error("[ES-Record] Failed to query document rid: {} in index: {}", rid, targetIndex, e);
            throw new RuntimeException("ES query record failed on index: " + targetIndex);
        } catch (IOException e) {
            log.error("[ES-Record] IO error while querying document rid: {} in index: {}", rid, targetIndex, e);
            throw new RuntimeException("ES IO error on index: " + targetIndex);
        }
    }

    /**
     * 分页多条件全文检索
     *
     * @param request 检索入参
     * @return 检索结果
     */
    public EsSearchResult search(EsSearchRequest request) {
        int pageNum = normalizePageNum(request.getPageNum());
        int pageSize = normalizePageSize(request.getPageSize());
        validateResultWindow(pageNum, pageSize);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);

        String targetIndex = resolveIndexName(request.getIndexName());
        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);

        Map<String, Object> respMap = executeSearchRequest(targetIndex, dsl);
        return parseSearchResponse(request, respMap);
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
        List<Map<String, Object>> sourceRows = Collections.emptyList();
        List<EsSearchResult.HitRow> rowList = new ArrayList<>();

        if (hitsWrapper != null) {
            Object totalObj = hitsWrapper.get("total");
            if (totalObj instanceof Map) {
                Object val = ((Map<String, Object>) totalObj).get("value");
                if (val instanceof Number) {
                    total = ((Number) val).longValue();
                }
            } else if (totalObj instanceof Number) {
                total = ((Number) totalObj).longValue();
            }

            List<Map<String, Object>> hitsList = (List<Map<String, Object>>) hitsWrapper.get("hits");
            if (hitsList != null) {
                for (Map<String, Object> hit : hitsList) {
                    String id = (String) hit.get("_id");
                    Double score = hit.get("_score") instanceof Number ? ((Number) hit.get("_score")).doubleValue() : null;
                    Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                    Map<String, List<String>> highlight = (Map<String, List<String>>) hit.get("highlight");
                    Map<String, Object> innerHits = (Map<String, Object>) hit.get("inner_hits");

                    rowList.add(EsSearchResult.HitRow.builder()
                            .id(id)
                            .score(score)
                            .source(source)
                            .highlight(highlight)
                            .innerHits(innerHits)
                            .build());
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
        Map<String, Object> aggs = parseAggregations(rawAggs, request.getAggregations());

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
            default -> aggData;
        };
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
     * 深分页保护：from + size 不得超过 max_result_window，否则 ES 需跳过分片排序并按偏移量回溯，代价极高
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     */
    private void validateResultWindow(int pageNum, int pageSize) {
        long maxResultWindow = properties.getMaxResultWindow();
        long from = (long) (pageNum - 1) * pageSize;
        if (from + pageSize > maxResultWindow) {
            throw new IllegalArgumentException(
                    "Deep pagination rejected: from + size (" + (from + pageSize)
                            + ") exceeds elasticsearch.max-result-window (" + maxResultWindow
                            + "); pageNum=" + pageNum + ", pageSize=" + pageSize);
        }
    }

    private int normalizePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
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
}
