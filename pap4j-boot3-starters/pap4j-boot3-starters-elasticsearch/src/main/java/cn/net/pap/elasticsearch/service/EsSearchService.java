package cn.net.pap.elasticsearch.service;

import cn.net.pap.elasticsearch.builder.EsQueryBuilder;
import cn.net.pap.elasticsearch.config.ElasticsearchProperties;
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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 通用 Elasticsearch 检索服务
 */
public class EsSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsSearchService.class);

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
            String json = EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8);
            Map<String, Object> respMap = objectMapper.readValue(json, new TypeReference<>() {
            });

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
            throw new RuntimeException("ES query record error: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("[ES-Record] IO error while querying document rid: {} in index: {}", rid, targetIndex, e);
            throw new RuntimeException("ES IO error: " + e.getMessage(), e);
        }
    }

    /**
     * 分页多条件全文检索
     *
     * @param request 检索入参
     * @return 检索结果
     */
    public EsSearchResult search(EsSearchRequest request) {
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
            log.debug("[ES-Search] Executing search on {}: {}", endpoint, requestBody);

            Request req = new Request("POST", endpoint);
            req.setJsonEntity(requestBody);

            Response res = restClient.performRequest(req);
            String respBody = EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8);
            return objectMapper.readValue(respBody, new TypeReference<>() {
            });
        } catch (IOException e) {
            log.error("[ES-Search] Failed to execute search query on index: {}", indexName, e);
            throw new RuntimeException("ES search execution error: " + e.getMessage(), e);
        }
    }

    /**
     * 解析检索响应
     */
    @SuppressWarnings("unchecked")
    private EsSearchResult parseSearchResponse(EsSearchRequest request, Map<String, Object> respMap) {
        Map<String, Object> hitsWrapper = (Map<String, Object>) respMap.get("hits");
        long total = 0L;
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

        return EsSearchResult.builder()
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .rows(rowList)
                .aggregations(aggs)
                .rawResponse(respMap)
                .build();
    }

    /**
     * 解析聚合响应
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

        Map<String, Object> aggs = (Map<String, Object>) respMap.get("aggregations");
        if (aggs == null) {
            aggs = Collections.emptyMap();
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
