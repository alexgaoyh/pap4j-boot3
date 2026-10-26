package cn.net.pap.elasticsearch;

import cn.net.pap.elasticsearch.builder.EsQueryBuilder;
import cn.net.pap.elasticsearch.config.ElasticsearchAutoConfiguration;
import cn.net.pap.elasticsearch.config.ElasticsearchProperties;
import cn.net.pap.elasticsearch.dto.AggType;
import cn.net.pap.elasticsearch.dto.EsSearchAction;
import cn.net.pap.elasticsearch.dto.EsSearchRequest;
import cn.net.pap.elasticsearch.dto.EsSearchRequest.AggNode;
import cn.net.pap.elasticsearch.dto.EsSearchRequest.Condition;
import cn.net.pap.elasticsearch.dto.EsSearchRequest.Highlight;
import cn.net.pap.elasticsearch.dto.EsSearchRequest.InnerHits;
import cn.net.pap.elasticsearch.dto.EsSearchRequest.ScriptSort;
import cn.net.pap.elasticsearch.dto.EsSearchRequest.Sort;
import cn.net.pap.elasticsearch.dto.EsSearchResult;
import cn.net.pap.elasticsearch.dto.SortOrder;
import cn.net.pap.elasticsearch.service.EsSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EsSearchService} 单元测试。
 * <p>
 * 启动时依据给定 settings/mappings 创建索引并批量写入测试数据，全部用例执行完毕后删除索引。
 * EsSearchService 是唯一对外入口，其依赖的 EsQueryBuilder 与各 DTO 均通过这些用例间接覆盖。
 * Elasticsearch 不可达时整体跳过，不影响离线构建。
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class EsSearchServiceTest {

    private static final String TEST_INDEX = "t";

    private static final String ES_URI = System.getProperty("es.uri", "");

    private static final String ES_USERNAME = System.getProperty("es.username", "");

    private static final String ES_PASSWORD = System.getProperty("es.password", "");

    private static final int MAX_RESULT_WINDOW = 10000;

    private static final String INDEX_CONFIG = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0
              },
              "mappings": {
                "properties": {
                  "rid": { "type": "keyword" },
                  "title": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "summary": { "type": "text" },
                  "category": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "publish_date": { "type": "keyword" },
                  "status": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 }
                    }
                  },
                  "view_count": { "type": "integer" },
                  "attachments": {
                    "type": "nested",
                    "properties": {
                      "file_id": { "type": "keyword" },
                      "file_name": { "type": "text" },
                      "file_type": { "type": "keyword" },
                      "caption": {
                        "type": "text",
                        "fields": {
                          "keyword": { "type": "keyword", "ignore_above": 256 }
                        }
                      }
                    }
                  },
                  "comments": {
                    "type": "nested",
                    "properties": {
                      "comment_id": { "type": "keyword" },
                      "author": { "type": "keyword" },
                      "content": { "type": "text" }
                    }
                  },
                  "departments": {
                    "type": "nested",
                    "properties": {
                      "dept_id": { "type": "keyword" },
                      "dept_name": { "type": "keyword" },
                      "members": {
                        "type": "nested",
                        "properties": {
                          "member_id": { "type": "keyword" },
                          "member_name": {
                            "type": "text",
                            "fields": {
                              "keyword": { "type": "keyword", "ignore_above": 256 }
                            }
                          },
                          "role": { "type": "keyword" },
                          "bio": { "type": "text" }
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

    private static final List<String> DOCUMENTS = List.of(
            """
                    {"rid":"doc-001","title":"Elasticsearch 全文检索实战","summary":"深入讲解 Elasticsearch 查询 DSL 与性能优化",\
                    "category":"技术","publish_date":"20240115","status":"published","view_count":120,\
                    "attachments":[{"file_id":"f1","file_name":"ES入门.pdf","file_type":"pdf","caption":"入门指南"},\
                    {"file_id":"f2","file_name":"ES进阶.pdf","file_type":"pdf","caption":"进阶实战"}],\
                    "comments":[{"comment_id":"c1","author":"alice","content":"非常好的文章"},\
                    {"comment_id":"c2","author":"bob","content":"学习了"}],\
                    "departments":[{"dept_id":"d1","dept_name":"研发部","members":[\
                    {"member_id":"m1","member_name":"张三","role":"工程师","bio":"专注搜索"},\
                    {"member_id":"m2","member_name":"李四","role":"架构师","bio":"负责架构"}]},\
                    {"dept_id":"d2","dept_name":"算法部","members":[\
                    {"member_id":"m3","member_name":"王五","role":"算法工程师","bio":"NLP方向"}]}]}
                    """,
            """
                    {"rid":"doc-002","title":"Spring Boot 3 微服务架构","summary":"Spring Boot 3 与 Spring Cloud 微服务实践",\
                    "category":"技术","publish_date":"20240620","status":"published","view_count":88,\
                    "attachments":[{"file_id":"f3","file_name":"spring.pdf","file_type":"pdf","caption":"架构图"}],\
                    "comments":[{"comment_id":"c3","author":"carol","content":"架构清晰"}],\
                    "departments":[{"dept_id":"d1","dept_name":"研发部","members":[\
                    {"member_id":"m1","member_name":"张三","role":"工程师","bio":"后端开发"}]}]}
                    """,
            """
                    {"rid":"doc-003","title":"产品运营方法论","summary":"如何做好产品运营与用户增长",\
                    "category":"运营","publish_date":"20241231","status":"draft","view_count":15,\
                    "attachments":[{"file_id":"f4","file_name":"运营.docx","file_type":"docx","caption":"运营方案"}],\
                    "comments":[],\
                    "departments":[{"dept_id":"d3","dept_name":"运营部","members":[\
                    {"member_id":"m4","member_name":"赵六","role":"运营经理","bio":"用户增长"}]}]}
                    """,
            """
                    {"rid":"doc-004","title":"产品设计之道","summary":"产品设计与用户体验",\
                    "category":"产品","publish_date":"20240310","status":"archived","view_count":200,\
                    "attachments":[{"file_id":"f5","file_name":"prd.docx","file_type":"docx","caption":"产品需求文档"}],\
                    "comments":[{"comment_id":"c4","author":"dave","content":"产品思路不错"}],\
                    "departments":[]}
                    """,
            """
                    {"rid":"doc-005","title":"大数据分析入门","summary":"Hadoop 与 Spark 数据分析",\
                    "category":"技术","publish_date":"20250101","status":"published","view_count":60,\
                    "attachments":[],\
                    "comments":[{"comment_id":"c5","author":"alice","content":"大数据很有前景"},\
                    {"comment_id":"c6","author":"eve","content":"需要更多例子"}],\
                    "departments":[{"dept_id":"d2","dept_name":"算法部","members":[\
                    {"member_id":"m3","member_name":"王五","role":"算法工程师","bio":"大数据分析"}]}]}
                    """,
            """
                    {"rid":"doc-006","title":"Elasticsearch 聚合分析","summary":"聚合与嵌套查询详解",\
                    "category":"技术","publish_date":"20240901","status":"published","view_count":45,\
                    "attachments":[{"file_id":"f6","file_name":"aggs.md","file_type":"md","caption":"聚合示例"}],\
                    "comments":[{"comment_id":"c7","author":"frank","content":"聚合讲解到位"}],\
                    "departments":[{"dept_id":"d1","dept_name":"研发部","members":[\
                    {"member_id":"m5","member_name":"钱七","role":"工程师","bio":"搜索聚合"}]}]}
                    """
    );

    private EsSearchService esSearchService;

    private RestClient restClient;

    private ObjectMapper objectMapper;

    private ElasticsearchProperties properties;

    @BeforeAll
    void setUp() throws IOException {
        properties = new ElasticsearchProperties();
        properties.setUris(List.of(ES_URI));
        properties.setUsername(ES_USERNAME);
        properties.setPassword(ES_PASSWORD);
        properties.setDefaultIndex(TEST_INDEX);
        properties.setMaxResultWindow(MAX_RESULT_WINDOW);
        if (properties.getUris().get(0).equals("")) {
            Assumptions.assumeTrue(false, "Elasticsearch is not reachable, skip tests");
        }

        objectMapper = new ObjectMapper();

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(ES_USERNAME, ES_PASSWORD));
        restClient = RestClient.builder(HttpHost.create(ES_URI))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(5000)
                        .setSocketTimeout(30000))
                .build();
        esSearchService = new EsSearchService(restClient, objectMapper, properties);

        Assumptions.assumeTrue(isEsReachable(), "Elasticsearch is not reachable, skip tests");
        recreateIndex();
        bulkInsert();
    }

    @AfterAll
    void tearDown() throws IOException {
        if (restClient != null) {
            deleteIndex();
            restClient.close();
        }
    }

    @Test
    void test01_configurationLoaded() {
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getUris()).containsExactly(ES_URI);
        assertThat(properties.getUsername()).isEqualTo(ES_USERNAME);
        assertThat(properties.getDefaultIndex()).isEqualTo(TEST_INDEX);
        assertThat(properties.getMaxResultWindow()).isEqualTo(MAX_RESULT_WINDOW);
    }

    @Test
    void test02_executeSearchAll() {
        EsSearchResult result = esSearchService.execute(EsSearchRequest.search().indexName(TEST_INDEX));

        assertThat(result.getTotal()).isEqualTo(6L);
        assertThat(result.getHitRows()).hasSize(6);
    }

    @Test
    void test03_executeRecordFound() {
        EsSearchResult result = esSearchService.execute(EsSearchRequest.record("doc-001").indexName(TEST_INDEX));

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecord()).containsEntry("rid", "doc-001");
    }

    @Test
    void test04_executeRecordNotFound() {
        EsSearchResult result = esSearchService.execute(EsSearchRequest.record("missing-rid").indexName(TEST_INDEX));

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecord()).isNull();
    }

    @Test
    void test05_executeAggregate() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.terms("by_category", "category.keyword"));

        EsSearchResult result = esSearchService.execute(request);

        assertThat(result.getAggregations()).containsKey("by_category");
        assertThat(buckets(result, "by_category")).hasSize(3);
    }

    @Test
    void test06_searchKeywordSingleField() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .keyword("Elasticsearch")
                .keywordFields(List.of("title"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getHitRows().get(0).getHighlight()).isNotNull();
    }

    @Test
    void test07_searchKeywordSingleFieldUsingDefaultIndex() {
        EsSearchRequest request = EsSearchRequest.search()
                .keyword("Elasticsearch")
                .keywordFields(List.of("title"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test08_searchKeywordMultipleFields() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .keyword("产品")
                .keywordFields(List.of("title", "summary"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test09_searchKeywordWithoutFieldsUsesMultiMatch() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .keyword("Elasticsearch");

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void test10_searchKeywordWithNestedFieldsAndInnerHits() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .keyword("非常好")
                .keywordFields(List.of("title", "comments.content"))
                .innerHits(InnerHits.of("comments", "comments.content"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getHitRows().get(0).getInnerHits()).containsKey("comments");
    }

    @Test
    void test11_searchTermCondition() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("category", "term", "技术"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(4L);
    }

    @Test
    void test12_searchTermsCondition() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("status", "terms", null).values(List.of("published", "draft")));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(5L);
    }

    @Test
    void test13_searchRangeCondition() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("view_count", "range", null).gte("50").lte("150"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(3L);
    }

    @Test
    void test14_searchWildcardCondition() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("title.keyword", "wildcard", "Elasticsearch*"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test15_searchPrefixCondition() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("title", "prefix", "Spring"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void test16_searchFuzzyCondition() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("title", "fuzzy", "全文检索"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void test17_searchNavigationField() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("-category", "term", "技术"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(4L);
    }

    @Test
    void test18_searchNavigationAndNormalConditions() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("-status.keyword", "term", "published"))
                .condition(Condition.of("category", "term", "技术"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(4L);
    }

    @Test
    void test19_searchMustConditions() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("category", "term", "技术"))
                .condition(Condition.of("view_count", "range", null).gte("100").relation("must"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(1L);
    }

    @Test
    void test20_searchShouldConditions() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("category.keyword", "term", "运营"))
                .condition(Condition.of("category.keyword", "term", "产品").relation("should"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test21_searchMustNotConditions() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("view_count", "range", null).gte("0"))
                .condition(Condition.of("category.keyword", "term", "技术").relation("must_not"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test22_searchNestedComments() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("comments.author", "term", "alice").nestedPath("comments"));
        request.setAutoHighlight(Boolean.FALSE);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test23_searchNestedAttachments() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("attachments.file_type", "term", "pdf").nestedPath("attachments"));
        request.setAutoHighlight(Boolean.FALSE);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test24_searchNestedDepartments() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("departments.dept_name", "term", "研发部").nestedPath("departments"));
        request.setAutoHighlight(Boolean.FALSE);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(3L);
    }

    @Test
    void test25_searchMultiLevelNested() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("departments.members.member_name.keyword", "term", "王五")
                        .nestedPath("departments.members"));
        request.setAutoHighlight(Boolean.FALSE);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test26_searchNestedWithInnerHits() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("comments.author", "term", "alice"))
                .innerHits(InnerHits.of("comments", "comments.content"));
        request.setAutoHighlight(Boolean.FALSE);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(2L);
        assertThat(result.getHitRows().get(0).getInnerHits()).containsKey("comments");
    }

    @Test
    void test27_searchAutoHighlightDisabled() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .keyword("Elasticsearch")
                .keywordFields(List.of("title"));
        request.setAutoHighlight(Boolean.FALSE);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getHitRows()).allSatisfy(row -> assertThat(row.getHighlight()).isNull());
    }

    @Test
    void test28_searchManualHighlight() {
        Highlight highlight = new Highlight().fields(List.of("summary")).preTag("<b>").postTag("</b>");
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .keyword("Elasticsearch")
                .keywordFields(List.of("summary"))
                .highlight(highlight);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getHitRows().get(0).getHighlight()).containsKey("summary");
    }

    @Test
    void test29_searchSourceIncludes() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("category", "term", "技术"))
                .sourceIncludes(List.of("rid", "title"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(4L);
        assertThat(result.getHitRows()).allSatisfy(row -> {
            assertThat(row.getSource()).containsKeys("rid", "title").doesNotContainKey("summary");
        });
    }

    @Test
    void test30_searchSourceExcludes() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .condition(Condition.of("category", "term", "技术"))
                .sourceExcludes(List.of("attachments", "comments", "departments"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(4L);
        assertThat(result.getHitRows()).allSatisfy(row ->
                assertThat(row.getSource()).doesNotContainKey("attachments"));
    }

    @Test
    void test31_searchSortAsc() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .sort(Sort.asc("view_count"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(6L);
        assertThat(result.getHitRows().get(0).getSource()).containsEntry("view_count", 15);
        assertThat(result.getHitRows().get(5).getSource()).containsEntry("view_count", 200);
    }

    @Test
    void test32_searchSortDesc() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .sort(Sort.desc("view_count"));

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(6L);
        assertThat(result.getHitRows().get(0).getSource()).containsEntry("view_count", 200);
    }

    @Test
    void test33_searchScriptSortAutoGenerated() {
        ScriptSort scriptSort = new ScriptSort().field("publish_date").baseDayOfYear(15);
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX).scriptSort(scriptSort);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(6L);
        assertThat(result.getHitRows().get(0).getId()).isEqualTo("doc-001");
    }

    @Test
    void test34_searchScriptSortCustomSource() {
        ScriptSort scriptSort = new ScriptSort()
                .scriptSource("return doc['view_count'].value + params.baseDayOfYear;")
                .baseDayOfYear(10)
                .order(SortOrder.DESC);
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX).scriptSort(scriptSort);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getTotal()).isEqualTo(6L);
        assertThat(result.getHitRows().get(0).getId()).isEqualTo("doc-004");
    }

    @Test
    void test35_searchPageSizeCapped() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX).pageSize(500);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getPageSize()).isEqualTo(100);
        assertThat(result.getTotal()).isEqualTo(6L);
    }

    @Test
    void test36_searchPageNormalization() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX).pageNum(0).pageSize(2);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getPageNum()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(2);
        assertThat(result.getHitRows()).hasSize(2);
        assertThat(result.getPages()).isEqualTo(3L);
    }

    @Test
    void test37_deepPaginationRejected() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX).pageNum(200).pageSize(100);

        assertThatThrownBy(() -> esSearchService.search(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Deep pagination");
    }

    @Test
    void test38_recordBlankRidRejected() {
        assertThatThrownBy(() -> esSearchService.record(TEST_INDEX, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test39_executeNullRejected() {
        assertThatThrownBy(() -> esSearchService.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test40_indexNameRequiredWithoutDefault() {
        EsSearchService serviceWithoutDefault = new EsSearchService(restClient, objectMapper,
                new ElasticsearchProperties());

        assertThatThrownBy(() -> serviceWithoutDefault.search(EsSearchRequest.search()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test41_aggregateTermsWithOrder() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.terms("by_category", "category.keyword").size(10).order("_count", SortOrder.DESC));

        EsSearchResult result = esSearchService.aggregate(request);

        List<Map<String, Object>> buckets = buckets(result, "by_category");
        assertThat(buckets).hasSize(3);
        assertThat(buckets.get(0)).containsEntry("key", "技术").containsEntry("count", 4);
    }

    @Test
    void test42_aggregateCardinality() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.cardinality("distinct_category", "category.keyword"));

        EsSearchResult result = esSearchService.aggregate(request);

        assertThat(((Number) result.getAggregations().get("distinct_category")).intValue()).isEqualTo(3);
    }

    @Test
    void test43_aggregateCardinalityWithScript() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.cardinality("distinct_views", null).scriptSource("doc['view_count'].value"));

        EsSearchResult result = esSearchService.aggregate(request);

        assertThat(result.getAggregations().get("distinct_views")).isInstanceOf(Number.class);
    }

    @Test
    void test44_aggregateValueCount() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.valueCount("total_views", "view_count"));

        EsSearchResult result = esSearchService.aggregate(request);

        assertThat(((Number) result.getAggregations().get("total_views")).intValue()).isEqualTo(6);
    }

    @Test
    void test45_aggregateTopHits() {
        AggNode node = AggNode.topHits("top_docs").size(2)
                .sorts(List.of(Sort.desc("view_count")))
                .sourceIncludes(List.of("rid", "title"));
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX).aggregation(node);

        EsSearchResult result = esSearchService.aggregate(request);

        List<Map<String, Object>> top = asList(result.getAggregations().get("top_docs"));
        assertThat(top).hasSize(2);
        assertThat(top.get(0)).containsEntry("id", "doc-004");
    }

    @Test
    void test46_aggregateNestedTerms() {
        AggNode node = AggNode.nested("comments_agg", "comments")
                .child(AggNode.terms("by_author", "comments.author"));
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX).aggregation(node);

        EsSearchResult result = esSearchService.aggregate(request);

        Map<String, Object> nested = asMap(result.getAggregations().get("comments_agg"));
        assertThat(nested).containsKey("by_author");
        assertThat(asList(nested.get("by_author"))).isNotEmpty();
    }

    @Test
    void test47_aggregateNestedWithoutChildren() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.nested("comments_count", "comments"));

        EsSearchResult result = esSearchService.aggregate(request);

        assertThat(((Number) result.getAggregations().get("comments_count")).intValue()).isEqualTo(7);
    }

    @Test
    void test48_aggregateMultiLevelNested() {
        AggNode node = AggNode.nested("dept_agg", "departments")
                .child(AggNode.nested("members_agg", "departments.members")
                        .child(AggNode.terms("by_member", "departments.members.member_name.keyword")));
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX).aggregation(node);

        EsSearchResult result = esSearchService.aggregate(request);

        Map<String, Object> deptAgg = asMap(result.getAggregations().get("dept_agg"));
        Map<String, Object> memberAgg = asMap(deptAgg.get("members_agg"));
        assertThat(memberAgg).containsKey("by_member");
        assertThat(asList(memberAgg.get("by_member"))).isNotEmpty();
    }

    @Test
    void test49_aggregateWithPostFilter() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.terms("by_category", "category.keyword"))
                .postFilters(List.of(Condition.of("status.keyword", "term", "published")));

        EsSearchResult result = esSearchService.aggregate(request);

        assertThat(result.getTotal()).isEqualTo(4L);
        assertThat(buckets(result, "by_category")).hasSize(3);
    }

    @Test
    void test50_aggregateWithQueryCondition() {
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .condition(Condition.of("category", "term", "技术"))
                .aggregation(AggNode.terms("by_status", "status.keyword"));

        EsSearchResult result = esSearchService.aggregate(request);

        assertThat(buckets(result, "by_status")).hasSize(1);
    }

    @Test
    void test51_recordWithExplicitIndex() {
        EsSearchResult result = esSearchService.record(TEST_INDEX, "doc-006");

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecord()).containsEntry("rid", "doc-006");
    }

    @Test
    void test52_executeSearchWithPagination() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX)
                .pageNum(2).pageSize(4)
                .sort(Sort.asc("view_count"));

        EsSearchResult result = esSearchService.execute(request);

        assertThat(result.getTotal()).isEqualTo(6L);
        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getHitRows()).hasSize(2);
    }

    @Test
    void test53_aggregateTermsWithChildAggregation() {
        AggNode node = AggNode.terms("by_category", "category.keyword")
                .child(AggNode.terms("by_status", "status.keyword"));
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX).aggregation(node);

        EsSearchResult result = esSearchService.aggregate(request);

        List<Map<String, Object>> buckets = buckets(result, "by_category");
        assertThat(buckets).isNotEmpty();
        assertThat(buckets.get(0)).containsKey("by_status");
    }

    @Test
    void test54_searchNullPageParamsUseDefaults() {
        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX);
        request.setPageNum(null);
        request.setPageSize(null);

        EsSearchResult result = esSearchService.search(request);

        assertThat(result.getPageNum()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotal()).isEqualTo(6L);
    }

    // ==================== 补充：EsSearchService 解析与异常分支 ====================

    @Test
    void test55_parseNumericTotal() throws IOException {
        EsSearchResult result = mockedService("{\"hits\":{\"total\":42,\"hits\":[]}}")
                .search(EsSearchRequest.search().indexName(TEST_INDEX));

        assertThat(result.getTotal()).isEqualTo(42L);
        assertThat(result.getHitRows()).isEmpty();
    }

    @Test
    void test56_parseMissingHits() throws IOException {
        EsSearchResult result = mockedService("{}")
                .search(EsSearchRequest.search().indexName(TEST_INDEX));

        assertThat(result.getTotal()).isZero();
        assertThat(result.getHitRows()).isEmpty();
    }

    @Test
    void test57_parseNonNumericMapTotal() throws IOException {
        EsSearchResult result = mockedService("{\"hits\":{\"total\":{\"value\":\"x\"},\"hits\":[]}}")
                .search(EsSearchRequest.search().indexName(TEST_INDEX));

        assertThat(result.getTotal()).isZero();
    }

    @Test
    void test58_recordFoundFalse() throws IOException {
        EsSearchResult result = mockedService("{\"found\":false}").record(TEST_INDEX, "doc-x");

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecord()).isNull();
    }

    @Test
    void test59_recordIoError() throws IOException {
        assertThatThrownBy(() -> mockedServiceThrowing(new IOException("boom")).record(TEST_INDEX, "doc-x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ES IO error");
    }

    @Test
    void test60_searchIoError() throws IOException {
        assertThatThrownBy(() -> mockedServiceThrowing(new IOException("boom"))
                .search(EsSearchRequest.search().indexName(TEST_INDEX)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ES search execution failed");
    }

    @Test
    void test61_aggregateRawAggsWithoutNodes() throws IOException {
        EsSearchResult result = mockedService("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},"
                                              + "\"aggregations\":{\"raw\":{\"value\":1}}}")
                .aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX));

        assertThat(result.getAggregations()).containsKey("raw");
    }

    @Test
    void test62_aggregateSkipsNullAndBlankNodes() throws IOException {
        EsSearchResult result = mockedService("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},"
                                              + "\"aggregations\":{\"by_category\":{\"buckets\":[{\"key\":\"tech\",\"doc_count\":1}]}}}")
                .aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX)
                        .aggregation(null)
                        .aggregation(new AggNode())
                        .aggregation(AggNode.terms("by_category", "category.keyword")));

        assertThat(result.getAggregations()).containsKey("by_category");
    }

    @Test
    void test63_aggregateSkipsNonMapRawAgg() throws IOException {
        EsSearchResult result = mockedService("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},"
                                              + "\"aggregations\":{\"weird\":123}}")
                .aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX)
                        .aggregation(AggNode.terms("weird", "category.keyword")));

        assertThat(result.getAggregations()).isEmpty();
    }

    @Test
    void test64_aggregateParsesAllTypes() throws IOException {
        String json = "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},\"aggregations\":{"
                      + "\"card\":{\"value\":3},\"vc\":{\"value\":6},"
                      + "\"top\":{\"hits\":{\"hits\":[{\"_id\":\"d1\",\"_source\":{\"rid\":\"d1\"}}]}},"
                      + "\"nested_agg\":{\"doc_count\":7,\"by_author\":{\"buckets\":[{\"key\":\"alice\",\"doc_count\":2}]}},"
                      + "\"nested_no_child\":{\"doc_count\":7}}}";
        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.cardinality("card", "category.keyword"))
                .aggregation(AggNode.valueCount("vc", "view_count"))
                .aggregation(AggNode.topHits("top"))
                .aggregation(AggNode.nested("nested_agg", "comments")
                        .child(AggNode.terms("by_author", "comments.author")))
                .aggregation(AggNode.nested("nested_no_child", "comments"));

        EsSearchResult result = mockedService(json).aggregate(request);

        assertThat(result.getAggregations()).containsKeys("card", "vc", "top", "nested_agg", "nested_no_child");
        assertThat((List<?>) result.getAggregations().get("top")).hasSize(1);
        assertThat(result.getAggregations().get("nested_no_child")).isEqualTo(7);
    }

    @Test
    void test65_aggregateNullTypeDefaultsToTerms() throws IOException {
        AggNode node = new AggNode();
        node.setName("by_x");
        node.setField("category.keyword");

        EsSearchResult result = mockedService("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},"
                                              + "\"aggregations\":{\"by_x\":{\"buckets\":[{\"key\":\"k\",\"doc_count\":1}]}}}")
                .aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX).aggregation(node));

        assertThat((List<?>) result.getAggregations().get("by_x")).hasSize(1);
    }

    @Test
    void test66_topHitsMalformedBranches() throws IOException {
        String json = "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},\"aggregations\":{"
                      + "\"top1\":{\"hits\":\"not-a-map\"},"
                      + "\"top2\":{\"hits\":{\"hits\":\"not-a-list\"}},"
                      + "\"top3\":{\"hits\":{\"hits\":[123]}}}}";

        EsSearchResult result = mockedService(json).aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.topHits("top1"))
                .aggregation(AggNode.topHits("top2"))
                .aggregation(AggNode.topHits("top3")));

        assertThat((List<?>) result.getAggregations().get("top1")).isEmpty();
        assertThat((List<?>) result.getAggregations().get("top2")).isEmpty();
        assertThat((List<?>) result.getAggregations().get("top3")).isEmpty();
    }

    @Test
    void test67_termsMalformedBuckets() throws IOException {
        String json = "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}},\"aggregations\":{"
                      + "\"t1\":{\"buckets\":\"not-a-list\"},\"t2\":{\"buckets\":[123]}}}";

        EsSearchResult result = mockedService(json).aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.terms("t1", "category.keyword"))
                .aggregation(AggNode.terms("t2", "category.keyword")));

        assertThat((List<?>) result.getAggregations().get("t1")).isEmpty();
        assertThat((List<?>) result.getAggregations().get("t2")).isEmpty();
    }

    @Test
    void test68_executeNullActionDefaultsToSearch() throws IOException {
        EsSearchRequest request = new EsSearchRequest();
        request.setAction(null);
        request.setIndexName(TEST_INDEX);

        EsSearchResult result = mockedService("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}")
                .execute(request);

        assertThat(result.getTotal()).isZero();
    }

    @Test
    void test69_aggregateMissingHitsWrapper() throws IOException {
        EsSearchResult result = mockedService("{\"aggregations\":{\"raw\":{\"value\":1}}}")
                .aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX));

        assertThat(result.getTotal()).isZero();
        assertThat(result.getAggregations()).containsKey("raw");
    }

    @Test
    void test70_searchZeroPageSizeDefaultsToTen() {
        EsSearchResult result = esSearchService.search(EsSearchRequest.search().indexName(TEST_INDEX).pageSize(0));

        assertThat(result.getPageSize()).isEqualTo(10);
    }

    // ==================== 补充：EsQueryBuilder 分支 ====================

    @Test
    void test71_builderDefaultPagination() {
        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(new EsSearchRequest());

        assertThat(dsl).containsEntry("from", 0).containsEntry("size", 10);
        assertThat(asMap(dsl.get("query"))).containsKey("match_all");
    }

    @Test
    void test72_builderLeftAssociativeNullAndInvalidLeaves() {
        EsSearchRequest reqNull = EsSearchRequest.search().conditions(null);
        assertThat(EsQueryBuilder.buildSearchDsl(reqNull).get("query"))
                .isEqualTo(Collections.singletonMap("match_all", Collections.emptyMap()));

        EsSearchRequest reqEmpty = EsSearchRequest.search().conditions(List.of());
        assertThat(EsQueryBuilder.buildSearchDsl(reqEmpty).get("query"))
                .isEqualTo(Collections.singletonMap("match_all", Collections.emptyMap()));

        List<Condition> conditions = new ArrayList<>();
        conditions.add(new Condition());
        conditions.add(null);
        EsSearchRequest reqInvalid = EsSearchRequest.search().conditions(conditions);
        assertThat(EsQueryBuilder.buildSearchDsl(reqInvalid).get("query"))
                .isEqualTo(Collections.singletonMap("match_all", Collections.emptyMap()));
    }

    @Test
    void test73_builderRelationDefaultsAndOrNot() {
        Condition first = Condition.of("a.keyword", "term", "x");
        Condition second = Condition.of("b.keyword", "term", "y");

        second.setRelation(null);
        EsSearchRequest reqMust = EsSearchRequest.search().conditions(List.of(first, second));
        assertThat(asMap(firstMustLeaf(reqMust)).get("bool")).isNotNull();
        assertThat(asMap(asMap(firstMustLeaf(reqMust)).get("bool"))).containsKey("must");

        second.setRelation("or");
        EsSearchRequest reqOr = EsSearchRequest.search().conditions(List.of(first, second));
        assertThat(asMap(firstMustLeaf(reqOr)).get("bool")).isNotNull();
        assertThat(asMap(asMap(firstMustLeaf(reqOr)).get("bool"))).containsKey("should");

        second.setRelation("not");
        EsSearchRequest reqNot = EsSearchRequest.search().conditions(List.of(first, second));
        assertThat(asMap(firstMustLeaf(reqNot)).get("bool")).isNotNull();
        assertThat(asMap(asMap(firstMustLeaf(reqNot)).get("bool"))).containsKey("must_not");
    }

    @Test
    void test74_builderLeafMatchDefaults() {
        Map<String, Object> defaultMatch = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.of("title", null, "abc")));
        assertThat(asMap(defaultMatch.get("match_phrase"))).containsEntry("title", "abc");

        Map<String, Object> termsNoValues = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.of("status", "terms", null)));
        assertThat((List<?>) asMap(termsNoValues.get("terms")).get("status.keyword")).isEmpty();

        Map<String, Object> rangeOnlyLte = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.of("view_count", "range", null).lte("5")));
        assertThat(asMap(asMap(rangeOnlyLte.get("range")).get("view_count"))).containsOnlyKeys("lte");

        // raw() 与 autoKeyword(false) 避免自动拼装 .keyword
        Map<String, Object> rawTerm = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.of("age", "term", "18").raw()));
        assertThat(asMap(rawTerm.get("term"))).containsEntry("age", "18");

        Map<String, Object> rawTerms = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.raw("rid", "terms", null).values(List.of("doc-1", "doc-2"))));
        assertThat(stringList(asMap(rawTerms.get("terms")).get("rid"))).containsExactly("doc-1", "doc-2");

        Map<String, Object> rawWildcard = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.of("sku_code", "wildcard", "SKU*").raw()));
        assertThat(asMap(rawWildcard.get("wildcard"))).containsEntry("sku_code", "SKU*");

        Map<String, Object> rawPrefix = firstMustLeaf(
                EsSearchRequest.search().condition(Condition.of("path", "prefix", "/api/").autoKeyword(false)));
        assertThat(asMap(rawPrefix.get("prefix"))).containsEntry("path", "/api/");

        EsSearchRequest navRawReq = EsSearchRequest.search()
                .condition(Condition.of("-status", "term", "active").raw());
        Map<String, Object> navRawDsl = EsQueryBuilder.buildSearchDsl(navRawReq);
        List<?> filters = (List<?>) asMap(asMap(navRawDsl.get("query")).get("bool")).get("filter");
        assertThat(asMap(asMap(filters.get(0)).get("term"))).containsEntry("status", "active");

        EsSearchRequest hlRawReq = EsSearchRequest.search()
                .condition(Condition.of("rid", "term", "doc-001").raw());
        Map<String, Object> hlDsl = EsQueryBuilder.buildSearchDsl(hlRawReq);
        assertThat(asMap(asMap(hlDsl.get("highlight")).get("fields"))).containsKey("rid");
    }

    @Test
    void test75_builderNestedPathResolution() {
        Map<String, Object> fromPath = firstMustLeaf(EsSearchRequest.search()
                .condition(Condition.of("comments.author", "term", "alice"))
                .innerHits(InnerHits.of("comments")));
        assertThat(asMap(fromPath.get("nested"))).containsEntry("path", "comments");

        InnerHits highlightOnly = new InnerHits();
        highlightOnly.setHighlightFields(List.of("attachments.caption"));
        Map<String, Object> fromHighlight = firstMustLeaf(EsSearchRequest.search()
                .condition(Condition.of("attachments.caption", "fuzzy", "x"))
                .innerHits(highlightOnly));
        assertThat(asMap(fromHighlight.get("nested"))).containsEntry("path", "attachments");

        List<InnerHits> noMatchList = new ArrayList<>();
        noMatchList.add(null);
        noMatchList.add(InnerHits.of("attachments"));
        EsSearchRequest noMatch = EsSearchRequest.search()
                .condition(Condition.of("comments.author", "term", "alice"));
        noMatch.setInnerHits(noMatchList);
        Map<String, Object> noMatchLeaf = firstMustLeaf(noMatch);
        assertThat(noMatchLeaf).containsKey("term").doesNotContainKey("nested");
    }

    @Test
    void test76_builderInnerHitsDefaultsAndName() {
        InnerHits defaults = InnerHits.of("comments", "comments.content");
        defaults.setFragmentSize(null);
        defaults.setNumberOfFragments(null);
        Map<String, Object> nested = asMap(firstMustLeaf(EsSearchRequest.search()
                .condition(Condition.of("comments.content", "fuzzy", "x"))
                .innerHits(defaults)).get("nested"));
        Map<String, Object> innerHitsDsl = asMap(nested.get("inner_hits"));
        Map<String, Object> fieldConf = asMap(asMap(asMap(innerHitsDsl.get("highlight")).get("fields"))
                .get("comments.content"));
        assertThat(fieldConf).containsEntry("fragment_size", 150).containsEntry("number_of_fragments", 1);

        InnerHits named = InnerHits.of("comments", "comments.content").name("custom");
        Map<String, Object> namedNested = asMap(firstMustLeaf(EsSearchRequest.search()
                .condition(Condition.of("comments.content", "fuzzy", "x"))
                .innerHits(named)).get("nested"));
        assertThat(asMap(namedNested.get("inner_hits"))).containsEntry("name", "custom");
    }

    @Test
    void test77_builderMultiLevelNestedWithInnerHits() {
        EsSearchRequest request = EsSearchRequest.search()
                .condition(Condition.of("departments.members.member_name.keyword", "term", "王五")
                        .nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name"));

        Map<String, Object> outer = asMap(firstMustLeaf(request).get("nested"));

        assertThat(outer).containsEntry("path", "departments").containsKey("inner_hits");
        assertThat(asMap(asMap(outer.get("query")).get("nested"))).containsEntry("path", "departments.members");
    }

    @Test
    void test78_builderKeywordNestedFields() {
        EsSearchRequest singleNested = EsSearchRequest.search().keyword("x")
                .keywordFields(List.of("comments.content"))
                .innerHits(InnerHits.of("comments", "comments.content"));
        Map<String, Object> singleWrapped = asMap(firstMustLeaf(singleNested).get("nested"));
        assertThat(asMap(singleWrapped.get("query"))).containsKey("match_phrase");

        EsSearchRequest multiNested = EsSearchRequest.search().keyword("x")
                .keywordFields(List.of("comments.content", "comments.author"))
                .innerHits(InnerHits.of("comments", "comments.content"));
        Map<String, Object> multiWrapped = asMap(firstMustLeaf(multiNested).get("nested"));
        Map<String, Object> innerBool = asMap(asMap(multiWrapped.get("query")).get("bool"));
        assertThat(innerBool).containsEntry("minimum_should_match", 1);
        assertThat((List<?>) innerBool.get("should")).hasSize(2);
    }

    @Test
    void test79_builderHighlightDerivationBranches() {
        assertThat(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search().keyword("x")
                .keywordFields(List.of("comments.content")))).doesNotContainKey("highlight");

        assertThat(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search()
                .condition(Condition.of("comments.content", "fuzzy", "x")))).doesNotContainKey("highlight");

        assertThat(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search()
                .condition(new Condition()))).doesNotContainKey("highlight");

        assertThat(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search()
                .condition(Condition.of("-", "term", "x")))).doesNotContainKey("highlight");

        Map<String, Object> matchNull = asMap(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search()
                .condition(Condition.of("category", null, "x"))).get("highlight"));
        assertThat(asMap(matchNull.get("fields"))).containsKey("category");
    }

    @Test
    void test80_builderHighlightDefaults() {
        Highlight highlight = new Highlight();
        highlight.setFields(List.of("title"));
        highlight.setPreTag(null);
        highlight.setPostTag(null);
        highlight.setFragmentSize(25);
        highlight.setNumberOfFragments(null);

        Map<String, Object> hl = asMap(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search()
                .keyword("x").keywordFields(List.of("title")).highlight(highlight)).get("highlight"));

        assertThat((List<String>) hl.get("pre_tags"))
                .containsExactly("<span style='color:red;font-weight:bold'>");
        assertThat((List<String>) hl.get("post_tags")).containsExactly("</span>");
        Map<String, Object> fieldConf = asMap(asMap(hl.get("fields")).get("title"));
        assertThat(fieldConf).containsEntry("fragment_size", 25).containsEntry("number_of_fragments", 3);
    }

    @Test
    void test81_builderSortBranches() {
        assertThat(EsQueryBuilder.buildSearchDsl(EsSearchRequest.search().scriptSort(new ScriptSort())))
                .doesNotContainKey("sort");

        ScriptSort customOrderNull = new ScriptSort();
        customOrderNull.setScriptSource("return 1;");
        customOrderNull.setOrder(null);
        List<?> customSort = (List<?>) EsQueryBuilder.buildSearchDsl(
                EsSearchRequest.search().scriptSort(customOrderNull)).get("sort");
        Map<String, Object> customScript = asMap(asMap(customSort.get(0)).get("_script"));
        assertThat(customScript).containsEntry("order", "asc");
        assertThat(asMap(customScript.get("script"))).doesNotContainKey("params");

        List<?> nullOrderSort = (List<?>) EsQueryBuilder.buildSearchDsl(
                EsSearchRequest.search().sort(new Sort("view_count", null))).get("sort");
        assertThat(asMap(asMap(nullOrderSort.get(0)).get("view_count"))).containsEntry("order", "desc");
    }

    @Test
    void test82_builderAggregationDefaultBranches() {
        AggNode nullType = new AggNode();
        nullType.setName("nt");
        nullType.setField("category.keyword");

        List<AggNode> nodes = new ArrayList<>();
        nodes.add(null);
        nodes.add(new AggNode());
        nodes.add(nullType);
        nodes.add(AggNode.terms("terms_default", "category.keyword").order("_key", null));
        nodes.add(AggNode.topHits("top"));
        nodes.add(AggNode.topHits("top_sized").size(5).sorts(List.of(new Sort("view_count", null)))
                .sourceIncludes(List.of("rid")));

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(EsSearchRequest.search().aggregations(nodes));
        Map<String, Object> aggs = asMap(dsl.get("aggs"));

        assertThat(aggs).containsKeys("nt", "terms_default", "top", "top_sized");
        Map<String, Object> terms = asMap(asMap(aggs.get("terms_default")).get("terms"));
        assertThat(asMap(terms.get("order"))).containsEntry("_key", "desc");
        Map<String, Object> topHits = asMap(asMap(aggs.get("top")).get("top_hits"));
        assertThat(topHits).containsEntry("size", 1).doesNotContainKey("sort").doesNotContainKey("_source");
        Map<String, Object> sizedTopHits = asMap(asMap(aggs.get("top_sized")).get("top_hits"));
        assertThat(sizedTopHits).containsEntry("size", 5);
        assertThat((List<?>) sizedTopHits.get("sort")).hasSize(1);
        assertThat(asMap(sizedTopHits.get("_source"))).containsEntry("includes", List.of("rid"));
    }

    // ==================== 补充：DTO 与自动配置 ====================

    @Test
    void test83_dtoEnumsAndChainMethods() {
        assertThat(AggType.fromString(null)).isEqualTo(AggType.TERMS);
        assertThat(AggType.fromString(" nested ")).isEqualTo(AggType.NESTED);
        assertThat(EsSearchAction.fromString(null)).isEqualTo(EsSearchAction.SEARCH);
        assertThat(EsSearchAction.fromString("aggregate")).isEqualTo(EsSearchAction.AGGREGATE);
        assertThat(EsSearchAction.fromString("record")).isEqualTo(EsSearchAction.RECORD);
        assertThat(SortOrder.fromString(null)).isEqualTo(SortOrder.DESC);
        assertThat(SortOrder.fromString("asc")).isEqualTo(SortOrder.ASC);
        assertThat(SortOrder.fromString("xyz")).isEqualTo(SortOrder.DESC);
        assertThat(SortOrder.ASC.getValue()).isEqualTo("asc");
        assertThat(SortOrder.DESC.getValue()).isEqualTo("desc");

        EsSearchRequest request = EsSearchRequest.record("r1").rid("r2");
        request.conditions(List.of(Condition.of("a", "term", "b")));
        request.sorts(List.of(Sort.asc("view_count")));
        request.aggregations(List.of(AggNode.terms("x", "y")));
        request.returnBucketsOnly(Boolean.FALSE);
        request.innerHits(new ArrayList<>(List.of(InnerHits.of("comments"))));
        request.addInnerHits(InnerHits.of("attachments"));
        assertThat(request.getRid()).isEqualTo("r2");
        assertThat(request.getInnerHits()).hasSize(2);

        EsSearchRequest fresh = EsSearchRequest.search().sort(Sort.asc("f"));
        fresh.addInnerHits(InnerHits.of("comments"));
        assertThat(fresh.getInnerHits()).hasSize(1);
        fresh.innerHits((InnerHits[]) null);
        assertThat(fresh.getInnerHits()).isNull();

        assertThat(Sort.of("f", null).getOrder()).isEqualTo(SortOrder.DESC);
        assertThat(Sort.of("f", SortOrder.ASC).getOrder()).isEqualTo(SortOrder.ASC);

        InnerHits innerHits = new InnerHits();
        innerHits.highlight("g");
        innerHits.name("n").path("p").numberOfFragments(2).fragmentSize(30);
        assertThat(innerHits.getName()).isEqualTo("n");
        assertThat(innerHits.getHighlightFields()).containsExactly("g");
        innerHits.highlightFields(List.of("h"));
        assertThat(innerHits.getHighlightFields()).containsExactly("h");

        AggNode node = AggNode.terms("x", "y").children(List.of(AggNode.terms("c", "d")));
        assertThat(node.getChildren()).hasSize(1);

        Highlight highlight = new Highlight();
        highlight.fields(List.of("title")).preTag("<").postTag(">")
                .fragmentSize(10).numberOfFragments(1);
        assertThat(highlight.getFields()).contains("title");
        assertThat(highlight.getFragmentSize()).isEqualTo(10);
    }

    @Test
    void test84_autoConfigurationBranches() throws IOException {
        ElasticsearchAutoConfiguration autoConfiguration = new ElasticsearchAutoConfiguration();

        ElasticsearchProperties noAuth = new ElasticsearchProperties();
        noAuth.setUris(List.of(ES_URI));
        try (RestClient client = autoConfiguration.restClient(noAuth)) {
            assertThat(client.getNodes()).hasSize(1);
        }

        ElasticsearchProperties withAuth = new ElasticsearchProperties();
        withAuth.setUris(List.of(ES_URI));
        withAuth.setUsername(ES_USERNAME);
        withAuth.setPassword(ES_PASSWORD);
        try (RestClient client = autoConfiguration.restClient(withAuth)) {
            assertThat(client.getNodes()).hasSize(1);
        }

        ElasticsearchProperties withUserInfo = new ElasticsearchProperties();
        withUserInfo.setUris(List.of("http://elastic:secret@123.161.179.32:51080"));
        try (RestClient client = autoConfiguration.restClient(withUserInfo)) {
            assertThat(client.getNodes()).hasSize(1);
        }

        ElasticsearchProperties emptyUris = new ElasticsearchProperties();
        emptyUris.setUris(List.of());
        try (RestClient client = autoConfiguration.restClient(emptyUris)) {
            assertThat(client.getNodes()).hasSize(1);
        }

        ElasticsearchProperties nullUris = new ElasticsearchProperties();
        nullUris.setUris(null);
        try (RestClient client = autoConfiguration.restClient(nullUris)) {
            assertThat(client.getNodes()).hasSize(1);
        }

        assertThat(autoConfiguration.elasticsearchObjectMapper()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();

        try (RestClient client = autoConfiguration.restClient(noAuth)) {
            assertThat(autoConfiguration.esSearchService(client, new ObjectMapper(), noAuth)).isNotNull();
        }
    }

    @Test
    void test85_recordNonNotFoundResponseError() throws IOException {
        Response errorResponse = mock(Response.class);
        when(errorResponse.getRequestLine())
                .thenReturn(new BasicRequestLine("GET", "/" + TEST_INDEX + "/_doc/doc-x", HttpVersion.HTTP_1_1));
        when(errorResponse.getHost()).thenReturn(HttpHost.create(ES_URI));
        when(errorResponse.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 500, "Error"));
        when(errorResponse.hasWarnings()).thenReturn(false);
        when(errorResponse.getEntity()).thenReturn(new StringEntity("{}", ContentType.APPLICATION_JSON));
        ResponseException responseException = new ResponseException(errorResponse);

        RestClient stub = mock(RestClient.class);
        when(stub.performRequest(any(Request.class))).thenThrow(responseException);
        EsSearchService service = new EsSearchService(stub, new ObjectMapper(), defaultProperties());

        assertThatThrownBy(() -> service.record(TEST_INDEX, "doc-x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ES query record failed");
    }

    @Test
    void test86_builderRemainingBranches() {
        EsSearchRequest navNested = EsSearchRequest.search()
                .condition(Condition.of("-comments.author", "term", "alice").nestedPath("comments"));
        Map<String, Object> navBool =
                asMap(asMap(EsQueryBuilder.buildSearchDsl(navNested).get("query")).get("bool"));
        Map<String, Object> navNestedLeaf = asMap(((List<?>) navBool.get("filter")).get(0));
        assertThat(asMap(navNestedLeaf.get("nested"))).doesNotContainKey("inner_hits");

        EsSearchRequest multiNavNested = EsSearchRequest.search()
                .condition(Condition.of("-departments.members.member_name", "term", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("-departments.members.role", "term", "架构师")
                        .nestedPath("departments.members"));
        Map<String, Object> multiNavBool =
                asMap(asMap(EsQueryBuilder.buildSearchDsl(multiNavNested).get("query")).get("bool"));
        List<?> filterList = (List<?>) multiNavBool.get("filter");
        assertThat(filterList).hasSize(1);
        Map<String, Object> mergedNestedFilter = asMap(filterList.get(0));
        assertThat(mergedNestedFilter).containsKey("nested");
        Map<String, Object> nestedObj = asMap(mergedNestedFilter.get("nested"));
        assertThat(nestedObj).doesNotContainKey("inner_hits");

        EsSearchRequest navAutoDeduce = EsSearchRequest.search()
                .condition(Condition.of("-comments.author", "term", "alice"))
                .innerHits(List.of(InnerHits.of("comments")));
        Map<String, Object> autoDeduceBool =
                asMap(asMap(EsQueryBuilder.buildSearchDsl(navAutoDeduce).get("query")).get("bool"));
        List<?> autoDeduceFilters = (List<?>) autoDeduceBool.get("filter");
        assertThat(autoDeduceFilters).hasSize(1);
        assertThat(asMap(autoDeduceFilters.get(0))).containsKey("nested");

        InnerHits noDot = new InnerHits();
        noDot.setHighlightFields(List.of("title"));
        EsSearchRequest noDotReq = EsSearchRequest.search()
                .condition(Condition.of("comments.author", "term", "alice"));
        noDotReq.setInnerHits(List.of(noDot));
        assertThat(firstMustLeaf(noDotReq)).doesNotContainKey("nested");

        List<InnerHits> two = new ArrayList<>();
        two.add(InnerHits.of("attachments"));
        two.add(InnerHits.of("comments"));
        EsSearchRequest twoReq = EsSearchRequest.search()
                .condition(Condition.of("comments.author", "term", "alice"));
        twoReq.setInnerHits(two);
        Map<String, Object> twoNested = asMap(firstMustLeaf(twoReq).get("nested"));
        assertThat(asMap(twoNested.get("inner_hits"))).containsEntry("name", "comments");

        EsSearchRequest mismatch = EsSearchRequest.search()
                .condition(Condition.of("comments.content", "fuzzy", "x").nestedPath("comments"));
        mismatch.setInnerHits(List.of(InnerHits.of("attachments")));
        Map<String, Object> mismatchNested = asMap(firstMustLeaf(mismatch).get("nested"));
        assertThat(mismatchNested).containsEntry("path", "comments").doesNotContainKey("inner_hits");

        EsSearchRequest threeLevel = EsSearchRequest.search()
                .condition(Condition.of("a.b.c.d", "term", "x").nestedPath("a.b.c"));
        Map<String, Object> level1 = asMap(firstMustLeaf(threeLevel).get("nested"));
        Map<String, Object> level2 = asMap(asMap(level1.get("query")).get("nested"));
        Map<String, Object> level3 = asMap(asMap(level2.get("query")).get("nested"));
        assertThat(level1).containsEntry("path", "a");
        assertThat(level2).containsEntry("path", "a.b");
        assertThat(level3).containsEntry("path", "a.b.c");
    }

    // ==================== 业务组合场景与功能缺口 ====================

    /**
     * 一次检索同时带有：关键词、must/should 条件、导航 filter 条件、range 条件、
     * nested inner_hits、多字段排序、分页、_source 过滤、手动高亮。
     */
    @Test
    void test87_buildSearchDslFullBusinessCombination() {
        EsSearchRequest request = EsSearchRequest.search()
                .indexName("biz_index")
                .keyword("搜索")
                .keywordFields(List.of("title", "comments.content"))
                .condition(Condition.of("category", "term", "技术"))
                .condition(Condition.of("comments.content", "fuzzy", "很好").nestedPath("comments").relation("should"))
                .condition(Condition.of("-status.keyword", "term", "published"))
                .condition(Condition.of("view_count", "range", null).gte("10").lte("1000"))
                .innerHits(InnerHits.of("comments", "comments.content"))
                .sort(Sort.desc("view_count"))
                .sort(Sort.asc("publish_date"))
                .pageNum(2)
                .pageSize(20)
                .sourceIncludes(List.of("rid", "title", "view_count"))
                .sourceExcludes(List.of("summary"))
                .highlight(new Highlight().fields(List.of("title", "summary")).preTag("<em>").postTag("</em>"));

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);

        assertThat(dsl).containsEntry("from", 20).containsEntry("size", 20);

        Map<String, Object> source = asMap(dsl.get("_source"));
        assertThat(stringList(source.get("includes"))).containsExactly("rid", "title", "view_count");
        assertThat(stringList(source.get("excludes"))).containsExactly("summary");

        Map<String, Object> highlight = asMap(dsl.get("highlight"));
        assertThat(stringList(highlight.get("pre_tags"))).containsExactly("<em>");
        assertThat(stringList(highlight.get("post_tags"))).containsExactly("</em>");
        assertThat(asMap(highlight.get("fields"))).containsKeys("title", "summary");

        Map<String, Object> bool = asMap(asMap(dsl.get("query")).get("bool"));
        List<?> must = (List<?>) bool.get("must");
        assertThat(must).hasSize(2);
        assertThat(asMap(must.get(0))).containsKey("bool");
        assertThat(asMap(must.get(1))).containsKey("bool");

        List<?> filter = (List<?>) bool.get("filter");
        assertThat(filter).hasSize(1);
        assertThat(asMap(asMap(filter.get(0)).get("term"))).containsEntry("status.keyword", "published");

        List<?> sort = (List<?>) dsl.get("sort");
        assertThat(sort).hasSize(2);
        assertThat(asMap(asMap(sort.get(0)).get("view_count"))).containsEntry("order", "desc");
        assertThat(asMap(asMap(sort.get(1)).get("publish_date"))).containsEntry("order", "asc");
    }

    /**
     * 一次聚合同时带有：查询条件、post_filter、多级 nested 子聚合、top_hits。
     */
    @Test
    void test88_buildSearchDslAggregateCombination() {
        EsSearchRequest request = EsSearchRequest.aggregate()
                .indexName("biz_index")
                .condition(Condition.of("category", "term", "技术"))
                .postFilters(List.of(Condition.of("status.keyword", "term", "published")))
                .aggregation(AggNode.nested("dept_agg", "departments")
                        .child(AggNode.terms("by_dept", "departments.dept_name")
                                .child(AggNode.cardinality("member_cnt", "departments.members.member_id"))))
                .aggregation(AggNode.topHits("top_docs").size(3)
                        .sorts(List.of(Sort.desc("view_count")))
                        .sourceIncludes(List.of("rid", "title")));

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);

        assertThat(dsl).containsEntry("size", 0).doesNotContainKey("sort");
        assertThat(dsl).containsKey("post_filter").containsKey("aggs");
        assertThat(asMap(asMap(dsl.get("post_filter")).get("term"))).containsEntry("status.keyword", "published");

        Map<String, Object> aggs = asMap(dsl.get("aggs"));
        assertThat(aggs).containsKeys("dept_agg", "top_docs");
        Map<String, Object> deptAgg = asMap(aggs.get("dept_agg"));
        assertThat(asMap(deptAgg.get("nested"))).containsEntry("path", "departments");
        Map<String, Object> byDept = asMap(asMap(deptAgg.get("aggs")).get("by_dept"));
        assertThat(asMap(byDept.get("terms"))).containsEntry("field", "departments.dept_name");
        assertThat(asMap(byDept.get("aggs"))).containsKey("member_cnt");
        assertThat(asMap(asMap(aggs.get("top_docs")).get("top_hits"))).containsEntry("size", 3);
    }

    /**
     * 多个 nested 路径各自携带 inner_hits；nested 条件的高亮由 inner_hits 承载，不污染顶层 highlight。
     */
    @Test
    void test89_buildSearchDslNestedPathsAndHighlightRules() {
        EsSearchRequest multiNested = EsSearchRequest.search()
                .keyword("x")
                .keywordFields(List.of("comments.content", "attachments.caption"))
                .innerHits(List.of(
                        InnerHits.of("comments", "comments.content"),
                        InnerHits.of("attachments", "attachments.caption")));
        List<?> should = (List<?>) asMap(firstMust(EsQueryBuilder.buildSearchDsl(multiNested)).get("bool")).get("should");
        assertThat(should).hasSize(2);
        List<String> paths = new ArrayList<>();
        List<String> innerHitsNames = new ArrayList<>();
        for (Object item : should) {
            Map<String, Object> nested = asMap(asMap(item).get("nested"));
            paths.add((String) nested.get("path"));
            innerHitsNames.add((String) asMap(nested.get("inner_hits")).get("name"));
        }
        assertThat(paths).containsExactly("comments", "attachments");
        assertThat(innerHitsNames).containsExactly("comments", "attachments");

        EsSearchRequest mixed = EsSearchRequest.search()
                .keyword("x")
                .keywordFields(List.of("title", "comments.content"))
                .innerHits(InnerHits.of("comments", "comments.content"));
        List<?> mixedShould = (List<?>) asMap(firstMust(EsQueryBuilder.buildSearchDsl(mixed)).get("bool")).get("should");
        assertThat(mixedShould).hasSize(2);
        assertThat(asMap(mixedShould.get(0))).containsKey("match_phrase");
        assertThat(asMap(mixedShould.get(1))).containsKey("nested");

        EsSearchRequest nestedCondition = EsSearchRequest.search()
                .condition(Condition.of("departments.dept_name", "term", "研发部").nestedPath("departments"))
                .innerHits(InnerHits.of("departments"));
        assertThat(EsQueryBuilder.buildSearchDsl(nestedCondition)).doesNotContainKey("highlight");

        EsSearchRequest nestedKeywordField = EsSearchRequest.search()
                .keyword("x")
                .keywordFields(List.of("departments.dept_name.keyword"))
                .innerHits(InnerHits.of("departments"));
        assertThat(EsQueryBuilder.buildSearchDsl(nestedKeywordField)).doesNotContainKey("highlight");
    }

    /**
     * 多条件左结合折叠、导航条件进入 filter、多字段排序。
     */
    @Test
    void test90_buildSearchDslLeftAssociativeNavigationAndSort() {
        EsSearchRequest three = EsSearchRequest.search()
                .condition(Condition.of("a.keyword", "term", "1"))
                .condition(Condition.of("b.keyword", "term", "2").relation("should"))
                .condition(Condition.of("c.keyword", "term", "3").relation("must_not"));
        Map<String, Object> outer = asMap(firstMust(EsQueryBuilder.buildSearchDsl(three)).get("bool"));
        assertThat(outer).containsKeys("must", "must_not");
        assertThat(asMap(asMap(((List<?>) outer.get("must")).get(0)).get("bool"))).containsKey("should");

        EsSearchRequest nav = EsSearchRequest.search()
                .condition(Condition.of("-status.keyword", "term", "published"))
                .condition(Condition.of("-title", "wildcard", "Elasticsearch*"))
                .condition(Condition.of("-publish_date", "range", null).gte("20240101"));
        List<?> filter = (List<?>) asMap(asMap(EsQueryBuilder.buildSearchDsl(nav).get("query")).get("bool")).get("filter");
        assertThat(filter).hasSize(3);
        assertThat(asMap(filter.get(0))).containsKey("term");
        assertThat(asMap(filter.get(1))).containsKey("wildcard");
        assertThat(asMap(filter.get(2))).containsKey("range");

        EsSearchRequest sorted = EsSearchRequest.search()
                .sort(Sort.desc("view_count"))
                .sort(Sort.asc("publish_date"))
                .sort(Sort.desc("status.keyword"));
        List<?> sort = (List<?>) EsQueryBuilder.buildSearchDsl(sorted).get("sort");
        assertThat(sort).hasSize(3);
        assertThat(asMap(asMap(sort.get(1)).get("publish_date"))).containsEntry("order", "asc");
    }

    /**
     * 综合响应解析：分页、total、高亮、inner_hits 同时出现，并填充 rows（含合并高亮）。
     */
    @Test
    void test91_searchParsesCombinationResponseAndPopulatesRows() throws IOException {
        String json = """
                {"hits":{"total":{"value":5,"relation":"eq"},"hits":[
                  {"_id":"d1","_score":2.1,"_source":{"rid":"d1","title":"ES 入门"},
                   "highlight":{"title":["<em>ES</em> 入门"]},
                   "inner_hits":{"comments":{"hits":{"total":{"value":1,"relation":"eq"},
                     "hits":[{"_id":"d1-c1","_source":{"content":"很好"}}]}}}},
                  {"_id":"d2","_score":1.2,"_source":{"rid":"d2","title":"ES 进阶"}}]}}
                """;

        EsSearchRequest request = EsSearchRequest.search().indexName(TEST_INDEX).pageNum(2).pageSize(1);
        EsSearchResult result = mockedService(json).search(request);

        assertThat(result.getTotal()).isEqualTo(5L);
        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(1);
        assertThat(result.getPages()).isEqualTo(5L);

        assertThat(result.getHitRows()).hasSize(2);
        assertThat(result.getHitRows().get(0).getScore()).isEqualTo(2.1);
        assertThat(result.getHitRows().get(0).getHighlight()).containsKey("title");
        assertThat(result.getHitRows().get(0).getInnerHits()).containsKey("comments");

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0)).containsEntry("rid", "d1").containsKey("highlight");
    }

    /**
     * 空结果集、命中缺少 _source 时均安全返回。
     */
    @Test
    void test92_searchHandlesEmptyAndSourceLessResponses() throws IOException {
        EsSearchResult empty = mockedService(
                "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}")
                .search(EsSearchRequest.search().indexName(TEST_INDEX));

        assertThat(empty.getTotal()).isZero();
        assertThat(empty.getPages()).isZero();
        assertThat(empty.getRows()).isEmpty();
        assertThat(empty.getHitRows()).isEmpty();

        String sourceLess = """
                {"hits":{"total":{"value":1,"relation":"eq"},"hits":[{"_id":"d1","_score":1.0}]}}
                """;
        EsSearchResult result = mockedService(sourceLess).search(EsSearchRequest.search().indexName(TEST_INDEX));

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0)).isEmpty();
    }

    /**
     * returnBucketsOnly：默认返回简化桶结构，置 false 返回 ES 原始 aggregations；
     * 配置了聚合节点但响应无 aggregations 时返回空 Map。
     */
    @Test
    void test93_aggregateReturnBucketsOnlySemantics() throws IOException {
        String json = """
                {"hits":{"total":{"value":0,"relation":"eq"}},"aggregations":{
                  "by_cat":{"doc_count_error_upper_bound":0,"sum_other_doc_count":0,
                    "buckets":[{"key":"a","doc_count":2},{"key":"b","doc_count":1}]}}}
                """;

        EsSearchRequest request = EsSearchRequest.aggregate().indexName(TEST_INDEX)
                .aggregation(AggNode.terms("by_cat", "category.keyword"));
        EsSearchResult simplified = mockedService(json).aggregate(request);
        List<Map<String, Object>> buckets = asList(simplified.getAggregations().get("by_cat"));
        assertThat(buckets).hasSize(2);
        assertThat(buckets.get(0)).containsEntry("key", "a").containsEntry("count", 2);

        request.setReturnBucketsOnly(Boolean.FALSE);
        EsSearchResult raw = mockedService(json).aggregate(request);
        assertThat(asMap(raw.getAggregations().get("by_cat")))
                .containsKey("buckets").containsKey("sum_other_doc_count");

        EsSearchResult missing = mockedService("{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"}}}")
                .aggregate(EsSearchRequest.aggregate().indexName(TEST_INDEX)
                        .aggregation(AggNode.terms("by_cat", "category.keyword")));
        assertThat(missing.getAggregations()).isEmpty();
    }

    /**
     * ES 拒绝请求时保留 HTTP 状态码与原始原因；record 未命中(404)返回一致的分页字段。
     */
    @Test
    void test94_esErrorsPreserveStatusAndReason() throws IOException {
        assertThatThrownBy(() -> mockedServiceThrowing(responseException(400, "{\"error\":\"unknown field [foo]\"}"))
                .search(EsSearchRequest.search().indexName(TEST_INDEX)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ES search execution failed")
                .hasMessageContaining("status=400")
                .hasMessageContaining("unknown field [foo]");

        assertThatThrownBy(() -> mockedServiceThrowing(responseException(500, "{\"error\":\"boom\"}"))
                .record(TEST_INDEX, "doc-x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ES query record failed")
                .hasMessageContaining("status=500")
                .hasMessageContaining("boom");

        EsSearchResult notFound = mockedServiceThrowing(responseException(404, "{\"found\":false}"))
                .record(TEST_INDEX, "missing");
        assertThat(notFound.getTotal()).isZero();
        assertThat(notFound.getRecord()).isNull();
        assertThat(notFound.getPageNum()).isEqualTo(1);
        assertThat(notFound.getPageSize()).isEqualTo(1);

        // 错误响应体为空时回退到异常自带的 message
        assertThatThrownBy(() -> mockedServiceThrowing(responseException(503, ""))
                .search(EsSearchRequest.search().indexName(TEST_INDEX)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("status=503");
    }

    @Test
    void test95_smartGroupingSameObjectMust() {
        // 1. 验证 DSL 结构：同 Path 多个 must 条件被归拢到单一 nested 内部的 bool.must，且只有一个 inner_hits
        EsSearchRequest dslReq = EsSearchRequest.search()
                .condition(Condition.of("departments.members.member_name.keyword", "term", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("departments.members.role", "term", "架构师")
                        .nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name",
                        "departments.members.role"));

        Map<String, Object> firstMust = asMap(firstMustLeaf(dslReq).get("nested"));
        assertThat(firstMust).containsEntry("path", "departments");
        Map<String, Object> innerHits = asMap(firstMust.get("inner_hits"));
        assertThat(innerHits).containsEntry("name", "departments.members");

        Map<String, Object> level2 = asMap(asMap(firstMust.get("query")).get("nested"));
        assertThat(level2).containsEntry("path", "departments.members");
        Map<String, Object> innerBool = asMap(asMap(level2.get("query")).get("bool"));
        List<?> innerMustList = (List<?>) innerBool.get("must");
        assertThat(innerMustList).hasSize(2);

        // 2. 真实 ES 执行：doc-001 中张三是工程师、李四是架构师（非同一人），同人匹配应判定为 0 命中（彻底杜绝假阳性）
        EsSearchResult noMatchResult = esSearchService.search(dslReq);
        assertThat(noMatchResult.getTotal()).isZero();

        // 3. 真实 ES 执行：查张三 + 工程师（真实同一人），应准确命中 doc-001 和 doc-002
        EsSearchRequest matchReq = EsSearchRequest.search()
                .condition(Condition.of("departments.members.member_name.keyword", "term", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("departments.members.role", "term", "工程师")
                        .nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name",
                        "departments.members.role"));
        EsSearchResult matchResult = esSearchService.search(matchReq);
        assertThat(matchResult.getTotal()).isEqualTo(2L);
        assertThat(matchResult.getHitRows()).hasSize(2);
        assertThat(matchResult.getHitRows().get(0).getInnerHits()).containsKey("departments.members");
    }

    @Test
    void test96_smartGroupingSameObjectShould() {
        // 验证同 Path 连续 should 条件归拢为内部 bool.should
        EsSearchRequest req = EsSearchRequest.search()
                .condition(Condition.of("comments.author", "term", "alice").nestedPath("comments"))
                .condition(Condition.of("comments.author", "term", "bob").relation("should").nestedPath("comments"))
                .innerHits(InnerHits.of("comments", "comments.author"));

        Map<String, Object> nested = asMap(firstMustLeaf(req).get("nested"));
        assertThat(nested).containsEntry("path", "comments");
        assertThat(asMap(nested.get("inner_hits"))).containsEntry("name", "comments");

        Map<String, Object> innerBool = asMap(asMap(nested.get("query")).get("bool"));
        assertThat(innerBool).containsEntry("minimum_should_match", 1);
        List<?> shouldList = (List<?>) innerBool.get("should");
        assertThat(shouldList).hasSize(2);

        // 真实 ES 执行：doc-001 (alice & bob) 和 doc-005 (alice) 均满足
        EsSearchResult result = esSearchService.search(req);
        assertThat(result.getTotal()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void test97_smartGroupingSameObjectMustNot() {
        // 验证同 Path 属性排除：张三 且 角色不是架构师
        EsSearchRequest req = EsSearchRequest.search()
                .condition(Condition.of("departments.members.member_name.keyword", "term", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("departments.members.role", "term", "架构师")
                        .relation("must_not").nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name"));

        Map<String, Object> level1 = asMap(firstMustLeaf(req).get("nested"));
        Map<String, Object> level2 = asMap(asMap(level1.get("query")).get("nested"));
        Map<String, Object> innerBool = asMap(asMap(level2.get("query")).get("bool"));
        assertThat(innerBool).containsKeys("must", "must_not");

        // 真实 ES 执行：张三为工程师（满足），同事李四是架构师不影响张三命中
        EsSearchResult result = esSearchService.search(req);
        assertThat(result.getTotal()).isEqualTo(2L);
    }

    @Test
    void test98_smartGroupingInterleavedNormalAndNestedAllMust() {
        // 验证普通字段与 nested 字段穿插（全 must），同 Path 自动集中归拢
        EsSearchRequest req = EsSearchRequest.search()
                .condition(Condition.of("title", "fuzzy", "实战"))
                .condition(Condition.of("departments.members.member_name.keyword", "term", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("status", "term", "published"))
                .condition(Condition.of("departments.members.role", "term", "工程师")
                        .nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name"));

        EsSearchResult result = esSearchService.search(req);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getHitRows().get(0).getId()).isEqualTo("doc-001");
    }

    @Test
    void test99_dynamicSuffixFallbackOnCrossFieldShould() {
        // 验证跨字段 should 隔开时，不可折叠的同 path nested 条件自动追加 #2 后缀，杜绝 400
        EsSearchRequest req = EsSearchRequest.search()
                .condition(Condition.of("departments.members.member_name.keyword", "term", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("status", "term", "archived").relation("should"))
                .condition(Condition.of("departments.members.role", "term", "工程师")
                        .relation("must").nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name"));

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(req);
        assertThat(dsl.toString()).contains("departments.members#2");

        // 真实 ES 执行：两组 inner_hits 互不冲突，执行顺利不抛 400 异常
        EsSearchResult result = esSearchService.search(req);
        assertThat(result.getTotal()).isGreaterThanOrEqualTo(1L);
        Map<String, Object> innerHitsMap = result.getHitRows().get(0).getInnerHits();
        assertThat(innerHitsMap).containsKeys("departments.members", "departments.members#2");
    }

    @Test
    void test100_dynamicSuffixFallbackKeywordAndConditionCoexist() {
        // 验证关键词检索与高级条件命中同一 nestedPath 时，动态后缀自动避免 400 报错
        EsSearchRequest req = EsSearchRequest.search()
                .keyword("专注搜索")
                .keywordFields(List.of("departments.members.bio"))
                .condition(Condition.of("departments.members.member_name.keyword", "term", "张三")
                        .nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name",
                        "departments.members.bio"));

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(req);
        assertThat(dsl.toString()).contains("departments.members#2");

        EsSearchResult result = esSearchService.search(req);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getHitRows().get(0).getId()).isEqualTo("doc-001");
    }

    @Test
    void test101_transparentMergedInnerHitsAcrossSuffixes() {
        // 验证带有动态后缀的 inner_hits 在响应解析时自动透明合并回基础路径
        // 场景：跨字段 should 导致生成 departments.members 和 departments.members#2 两个 inner_hits 块
        EsSearchRequest req = EsSearchRequest.search()
                .condition(Condition.of("departments.members.member_name", "fuzzy", "张三")
                        .nestedPath("departments.members"))
                .condition(Condition.of("status", "term", "archived").relation("should"))
                .condition(Condition.of("departments.members.role", "term", "工程师")
                        .relation("must").nestedPath("departments.members"))
                .innerHits(InnerHits.of("departments.members", "departments.members.member_name",
                        "departments.members.role"));

        EsSearchResult result = esSearchService.search(req);
        assertThat(result.getTotal()).isGreaterThanOrEqualTo(1L);

        EsSearchResult.HitRow doc1 = result.getHitRows().stream()
                .filter(row -> "doc-001".equals(row.getId()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> innerHitsMap = doc1.getInnerHits();
        // 1. 兼容性保证：原生的带 #2 后缀 key 依然完整保留在 Map 中供特定排查使用
        assertThat(innerHitsMap).containsKeys("departments.members", "departments.members#2");

        // 2. 核心透明合并保证：业务调用方直接读取基础 key，即可拿到合并了所有子命中与全部高亮的结果
        Map<String, Object> baseWrapper = asMap(innerHitsMap.get("departments.members"));
        assertThat(baseWrapper).isNotNull();
        Map<String, Object> hitsBlock = asMap(baseWrapper.get("hits"));
        List<?> subHits = (List<?>) hitsBlock.get("hits");
        assertThat(subHits).isNotEmpty();

        Map<String, Object> firstSubHit = asMap(subHits.get(0));
        Map<String, Object> subHighlight = asMap(firstSubHit.get("highlight"));
        assertThat(subHighlight).isNotNull();
        assertThat(subHighlight).containsKeys("departments.members.member_name", "departments.members.role");
    }

    @Test
    void test102_mergeInnerHitsSuffixesEdgeCases() throws IOException {
        // 验证 Mock 响应下的分支覆盖：非数字后缀忽略、新子文档追加、多片段高亮合并等
        String mockResponseJson = """
                {
                  "hits": {
                    "total": { "value": 1 },
                    "hits": [
                      {
                        "_id": "doc-mock",
                        "inner_hits": {
                          "dept#abc": { "hits": { "hits": [] } },
                          "dept": {
                            "hits": {
                              "total": { "value": 1 },
                              "hits": [
                                {
                                  "_id": "sub-1",
                                  "highlight": { "title": ["<em>A</em>"] }
                                }
                              ]
                            }
                          },
                          "dept#2": {
                            "hits": {
                              "total": { "value": 2 },
                              "hits": [
                                {
                                  "_id": "sub-1",
                                  "highlight": { "title": ["<em>B</em>"], "author": ["<em>Alice</em>"] }
                                },
                                {
                                  "_id": "sub-2",
                                  "highlight": { "role": ["<em>Admin</em>"] }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ]
                  }
                }
                """;
        EsSearchService service = mockedService(mockResponseJson);
        EsSearchResult result = service.search(EsSearchRequest.search().indexName("test"));
        assertThat(result.getTotal()).isEqualTo(1L);

        Map<String, Object> innerHits = result.getHitRows().get(0).getInnerHits();
        assertThat(innerHits).containsKeys("dept", "dept#2", "dept#abc");

        Map<String, Object> baseWrapper = asMap(innerHits.get("dept"));
        Map<String, Object> hitsBlock = asMap(baseWrapper.get("hits"));
        List<?> subHits = (List<?>) hitsBlock.get("hits");
        assertThat(subHits).hasSize(2);

        Map<String, Object> sub1 = asMap(subHits.get(0));
        Map<String, Object> sub1Hl = asMap(sub1.get("highlight"));
        assertThat(sub1Hl).containsKeys("title", "author");
        List<String> titleFrags = stringList(sub1Hl.get("title"));
        assertThat(titleFrags).containsExactly("<em>A</em>", "<em>B</em>");

        Map<String, Object> sub2 = asMap(subHits.get(1));
        Map<String, Object> sub2Hl = asMap(sub2.get("highlight"));
        assertThat(sub2Hl).containsKey("role");
    }

    @Test
    void test103_builderAndServiceRemainingBoundaryBranches() throws IOException {
        // 1. EsQueryBuilder 边界分支覆盖：空条件跳过、单元素 nested 解包、空白关键字、空条件兜底
        EsSearchRequest blankReq = EsSearchRequest.search()
                .sort(Sort.asc("   "))
                .condition(Condition.of("   ", "term", "blank_field"))
                .condition(Condition.of("title", "term", "hello").relation(null))
                .condition(Condition.of("comments.author", "term", "alice").nestedPath("comments"));
        Map<String, Object> blankDsl = EsQueryBuilder.buildSearchDsl(blankReq);
        assertThat(blankDsl.toString()).contains("comments");
        assertThat(blankDsl).containsKey("sort");

        EsSearchRequest emptyKwReq = EsSearchRequest.search().keyword("   ");
        Map<String, Object> emptyKwDsl = EsQueryBuilder.buildSearchDsl(emptyKwReq);
        assertThat(emptyKwDsl.toString()).contains("match_all");

        // 2. EsSearchService 兼容与防御性分支：total 为纯数字、子文档仅依靠 _source 比对
        String mockNumTotalJson = """
                {
                  "hits": {
                    "total": 5,
                    "hits": [
                      {
                        "_id": "doc-source-match",
                        "inner_hits": {
                          "nested_field": {
                            "hits": {
                              "total": 1,
                              "hits": [
                                {
                                  "_source": { "name": "source_item" },
                                  "highlight": { "name": ["<em>A</em>"] }
                                }
                              ]
                            }
                          },
                          "nested_field#2": {
                            "hits": {
                              "total": 1,
                              "hits": [
                                {
                                  "_source": { "name": "source_item" },
                                  "highlight": { "name": ["<em>B</em>"] }
                                }
                              ]
                            }
                          }
                        }
                      }
                    ]
                  }
                }
                """;
        EsSearchService service = mockedService(mockNumTotalJson);
        EsSearchResult result = service.search(EsSearchRequest.search().indexName("test"));
        assertThat(result.getTotal()).isEqualTo(5L);

        Map<String, Object> innerHits = result.getHitRows().get(0).getInnerHits();
        Map<String, Object> baseWrapper = asMap(innerHits.get("nested_field"));
        List<?> subHits = (List<?>) asMap(baseWrapper.get("hits")).get("hits");
        assertThat(subHits).hasSize(1);
        Map<String, Object> firstHl = asMap(asMap(subHits.get(0)).get("highlight"));
        List<String> nameHls = stringList(firstHl.get("name"));
        assertThat(nameHls).containsExactly("<em>A</em>", "<em>B</em>");
    }

    @Test
    void test104_deepDefensiveCoverage() throws Exception {
        // 1. EsQueryBuilder 空条件、空分支与导航路径分支
        // 1.1 ConditionUnit 含 nestedPath 但 field 为空 -> innerQuery == null (L198) & innerMustList.isEmpty() (L374)
        EsSearchRequest req1 = EsSearchRequest.search()
                .condition(Condition.of("   ", "term", "val").nestedPath("dept"));
        Map<String, Object> dsl1 = EsQueryBuilder.buildSearchDsl(req1);
        assertThat(dsl1.toString()).doesNotContain("dept");

        // 1.2 ConditionUnit 无 nestedPath 但 field 为空 -> unitQuery == null (L207)
        EsSearchRequest req2 = EsSearchRequest.search()
                .condition(Condition.of("   ", "term", "val"));
        Map<String, Object> dsl2 = EsQueryBuilder.buildSearchDsl(req2);
        assertThat(dsl2.toString()).contains("match_all");

        // 1.3 同 nestedPath 下 1 正常 1 空白字段 -> innerMustList.size() == 1 (L377)
        EsSearchRequest req3 = EsSearchRequest.search()
                .condition(Condition.of("dept.name", "term", "tech").nestedPath("dept"))
                .condition(Condition.of("   ", "term", "val").nestedPath("dept"));
        Map<String, Object> dsl3 = EsQueryBuilder.buildSearchDsl(req3);
        assertThat(dsl3.toString()).contains("dept.name");

        // 1.4 同 nestedPath 下 relation=should 且 1 空白条件 -> raw == null (L388)
        EsSearchRequest req4 = EsSearchRequest.search()
                .condition(Condition.of("dept.name", "term", "tech").nestedPath("dept"))
                .condition(Condition.of("   ", "term", "val").nestedPath("dept").relation("should"));
        Map<String, Object> dsl4 = EsQueryBuilder.buildSearchDsl(req4);
        assertThat(dsl4.toString()).contains("dept.name");

        // 1.5 导航条件（以 '-' 开头）且为 nested 字段 -> enableInnerHits 为 false (L535)
        EsSearchRequest req5 = EsSearchRequest.search()
                .condition(Condition.of("-dept.name", "term", "tech").nestedPath("dept"));
        Map<String, Object> dsl5 = EsQueryBuilder.buildSearchDsl(req5);
        assertThat(dsl5.toString()).contains("dept");
        assertThat(dsl5.toString()).doesNotContain("inner_hits");

        // 2. 模拟 ES 500 且读取错误响应流失败 -> L430-431
        HttpEntity initialEntity = new StringEntity("{\"error\":\"mock\"}", ContentType.APPLICATION_JSON);
        HttpEntity brokenEntity = mock(HttpEntity.class);
        when(brokenEntity.getContent()).thenThrow(new IOException("Simulated entity read error"));
        Response errResp = mock(Response.class);
        when(errResp.getRequestLine()).thenReturn(
                new BasicRequestLine("POST", "/test/_search", HttpVersion.HTTP_1_1));
        when(errResp.getHost()).thenReturn(HttpHost.create("http://localhost:9200"));
        when(errResp.getStatusLine()).thenReturn(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 500, "Server Error"));
        when(errResp.getEntity()).thenReturn(initialEntity, brokenEntity);
        ResponseException rex = new ResponseException(errResp);
        EsSearchService brokenEntityService = mockedServiceThrowing(rex);
        assertThatThrownBy(() -> brokenEntityService.search(EsSearchRequest.search().indexName("test")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("status=500");

        // 3. Mock 返回测试 inner_hits 边界分支：空 key、非 Map 结构、缺少 hits 等
        String complexInnerHitsJson = """
                {
                  "hits": {
                    "total": { "value": 1 },
                    "hits": [
                      {
                        "_id": "doc-complex",
                        "_source": {
                          "tags": [["deepA", "deepB"]]
                        },
                        "inner_hits": {
                          "   ": { "hits": { "hits": [] } },
                          "corrupt#2": "not_a_map",
                          "no_hits_block": { "other": 1 },
                          "no_hits_block#2": { "hits": { "hits": [] } },
                          "other_not_map": { "hits": { "hits": [] } },
                          "other_not_map#2": "not_a_map",
                          "other_no_hits": { "hits": { "hits": [] } },
                          "other_no_hits#2": { "other": 1 },
                          "other_null_subhits": { "hits": { "hits": [] } },
                          "other_null_subhits#2": { "hits": { "hits": null } },
                          "other_corrupt_subhits": { "hits": { "hits": [] } },
                          "other_corrupt_subhits#2": { "hits": { "hits": ["not_a_subhit_map"] } },
                          "no_match_subhit": { "hits": { "hits": [{ "custom": "A" }] } },
                          "no_match_subhit#2": { "hits": { "hits": [{ "custom": "B" }] } }
                        }
                      }
                    ]
                  }
                }
                """;
        EsSearchService complexService = mockedService(complexInnerHitsJson);
        EsSearchResult complexResult = complexService.search(EsSearchRequest.search().indexName("test"));
        assertThat(complexResult.getHitRows()).hasSize(1);

        // 4. 反射覆盖底层防御性死分支与 null 保护
        // 4.1 EsQueryBuilder 反射
        Method buildGroupInnerQueryMethod = EsQueryBuilder.class.getDeclaredMethod(
                "buildGroupInnerQuery", List.class);
        buildGroupInnerQueryMethod.setAccessible(true);
        assertThat(buildGroupInnerQueryMethod.invoke(null, Collections.emptyList())).isNull();

        Method combineExprMethod = EsQueryBuilder.class.getDeclaredMethod(
                "combineExpressions", Map.class, Map.class, String.class);
        combineExprMethod.setAccessible(true);
        Map<String, Object> dummy = Collections.singletonMap("match_all", Collections.emptyMap());
        assertThat(combineExprMethod.invoke(null, null, dummy, "must")).isEqualTo(dummy);
        assertThat(combineExprMethod.invoke(null, dummy, null, "must")).isEqualTo(dummy);

        Method buildRawLeafMethod = EsQueryBuilder.class.getDeclaredMethod(
                "buildRawLeafQuery", Condition.class);
        buildRawLeafMethod.setAccessible(true);
        assertThat(buildRawLeafMethod.invoke(null, (Object) null)).isNull();

        Method buildLeafQueryMethod = EsQueryBuilder.class.getDeclaredMethod(
                "buildLeafQuery", Condition.class, List.class, boolean.class, Map.class);
        buildLeafQueryMethod.setAccessible(true);
        assertThat(buildLeafQueryMethod.invoke(null, (Object) null, null, false, null)).isNull();

        Condition nestedCond = Condition.of("dept.name", "term", "tech").nestedPath("dept");
        List<InnerHits> ihList = Collections.singletonList(InnerHits.of("dept", "dept.name"));
        Map<String, Integer> nameCounts = new java.util.LinkedHashMap<>();
        Object leafWithInnerHits = buildLeafQueryMethod.invoke(null, nestedCond, ihList, true, nameCounts);
        assertThat(leafWithInnerHits.toString()).contains("inner_hits");

        Object leafWithoutInnerHits = buildLeafQueryMethod.invoke(null, nestedCond, ihList, false, nameCounts);
        assertThat(leafWithoutInnerHits.toString()).doesNotContain("inner_hits");

        Method wrapNestedMethod = EsQueryBuilder.class.getDeclaredMethod(
                "wrapNested", String.class, Map.class, InnerHits.class, Map.class);
        wrapNestedMethod.setAccessible(true);
        assertThat(wrapNestedMethod.invoke(null, "", dummy, null, null)).isEqualTo(dummy);

        // 4.2 EsSearchService 反射
        Method isSameSubHitMethod = EsSearchService.class.getDeclaredMethod("isSameSubHit", Map.class, Map.class);
        isSameSubHitMethod.setAccessible(true);
        assertThat(isSameSubHitMethod.invoke(complexService, null, dummy)).isEqualTo(false);

        Method deepCopyMapMethod = EsSearchService.class.getDeclaredMethod("deepCopyMap", Map.class);
        deepCopyMapMethod.setAccessible(true);
        assertThat(deepCopyMapMethod.invoke(complexService, (Object) null)).isNull();

        Method deepCopyListMethod = EsSearchService.class.getDeclaredMethod("deepCopyList", List.class);
        deepCopyListMethod.setAccessible(true);
        assertThat(deepCopyListMethod.invoke(complexService, (Object) null)).isNull();
        assertThat(deepCopyListMethod.invoke(complexService, List.of(List.of("nestedItem")))).isNotNull();

        // 4.3 ElasticsearchAutoConfiguration maskUri 分支覆盖
        ElasticsearchAutoConfiguration autoConfig = new ElasticsearchAutoConfiguration();
        Method maskUriMethod = ElasticsearchAutoConfiguration.class.getDeclaredMethod("maskUri", String.class);
        maskUriMethod.setAccessible(true);
        assertThat(maskUriMethod.invoke(autoConfig, "http://user:pass@localhost:9200"))
                .isEqualTo("http://***:***@localhost:9200");
        assertThat(maskUriMethod.invoke(autoConfig, ":::invalid-uri")).isEqualTo("***");
    }

    @Test
    void test105_queryBuilderDefaultAppendsKeyword() {
        EsSearchRequest request = EsSearchRequest.search()
                .condition(Condition.of("status", "term", "published"))
                .condition(Condition.of("category", "terms", null).values(List.of("tech", "news")))
                .condition(Condition.of("title", "wildcard", "test*"))
                .condition(Condition.of("prefix_field", "prefix", "pre_"));

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);
        String dslString = dsl.toString();

        assertThat(dslString).contains("status.keyword");
        assertThat(dslString).contains("category.keyword");
        assertThat(dslString).contains("title.keyword");
        assertThat(dslString).contains("prefix_field.keyword");
    }

    @Test
    void test106_queryBuilderRawDisablesKeywordAppend() {
        // 1. term 查询原生数值或原生 keyword 字段
        EsSearchRequest reqTerm = EsSearchRequest.search()
                .condition(Condition.of("age", "term", "18").raw());
        Map<String, Object> dslTerm = EsQueryBuilder.buildSearchDsl(reqTerm);
        assertThat(dslTerm.toString()).contains("age=").doesNotContain("age.keyword");

        // 2. 静态工厂 Condition.raw()
        EsSearchRequest reqTerms = EsSearchRequest.search()
                .condition(Condition.raw("rid", "terms", null).values(List.of("doc-1", "doc-2")));
        Map<String, Object> dslTerms = EsQueryBuilder.buildSearchDsl(reqTerms);
        assertThat(dslTerms.toString()).contains("rid=").doesNotContain("rid.keyword");

        // 3. wildcard / prefix 查询禁用 .keyword
        EsSearchRequest reqWildcard = EsSearchRequest.search()
                .condition(Condition.of("sku_code", "wildcard", "SKU*").raw())
                .condition(Condition.of("api_path", "prefix", "/v1/").autoKeyword(false));
        Map<String, Object> dslWildcard = EsQueryBuilder.buildSearchDsl(reqWildcard);
        assertThat(dslWildcard.toString()).contains("sku_code=").doesNotContain("sku_code.keyword");
        assertThat(dslWildcard.toString()).contains("api_path=").doesNotContain("api_path.keyword");
    }

    @Test
    void test107_queryBuilderNavigationConditionWithRaw() {
        // 默认情况：带 '-' 前缀且无 .keyword 时自动追加
        EsSearchRequest reqDefault = EsSearchRequest.search()
                .condition(Condition.of("-status", "term", "active"));
        Map<String, Object> dslDefault = EsQueryBuilder.buildSearchDsl(reqDefault);
        assertThat(dslDefault.toString()).contains("status.keyword");

        // raw() 标记：去除 '-' 前缀后保持原名
        EsSearchRequest reqRaw = EsSearchRequest.search()
                .condition(Condition.of("-status", "term", "active").raw());
        Map<String, Object> dslRaw = EsQueryBuilder.buildSearchDsl(reqRaw);
        assertThat(dslRaw.toString()).contains("status=").doesNotContain("status.keyword");
    }

    @Test
    void test108_queryBuilderAutoHighlightWithRaw() {
        // 默认情况高亮字段会被转为 .keyword
        EsSearchRequest reqDefault = EsSearchRequest.search()
                .condition(Condition.of("title", "term", "test"));
        Map<String, Object> dslDefault = EsQueryBuilder.buildSearchDsl(reqDefault);
        Map<String, Object> hlDefault = asMap(dslDefault.get("highlight"));
        assertThat(asMap(hlDefault.get("fields"))).containsKey("title.keyword");

        // raw() 情况下高亮字段名保持原样
        EsSearchRequest reqRaw = EsSearchRequest.search()
                .condition(Condition.of("rid", "term", "doc-1").raw());
        Map<String, Object> dslRaw = EsQueryBuilder.buildSearchDsl(reqRaw);
        Map<String, Object> hlRaw = asMap(dslRaw.get("highlight"));
        assertThat(asMap(hlRaw.get("fields"))).containsKey("rid").doesNotContainKey("rid.keyword");
    }

    @Test
    void test109_queryBuilderMixedNormalAndRawConditions() {
        EsSearchRequest request = EsSearchRequest.search()
                .condition(Condition.of("category", "term", "tech")) // 文本多字段 -> category.keyword
                .condition(Condition.of("view_count", "term", "100").raw()) // 数值 -> view_count
                .condition(Condition.raw("is_deleted", "term", "false")); // 布尔 -> is_deleted

        Map<String, Object> dsl = EsQueryBuilder.buildSearchDsl(request);
        String dslString = dsl.toString();

        assertThat(dslString).contains("category.keyword");
        assertThat(dslString).contains("view_count=");
        assertThat(dslString).doesNotContain("view_count.keyword");
        assertThat(dslString).contains("is_deleted=");
        assertThat(dslString).doesNotContain("is_deleted.keyword");
    }

    private EsSearchService mockedService(String json) throws IOException {
        RestClient stub = mock(RestClient.class);
        Response stubResponse = mock(Response.class);
        when(stub.performRequest(any(Request.class))).thenReturn(stubResponse);
        when(stubResponse.getEntity()).thenReturn(new StringEntity(json, ContentType.APPLICATION_JSON));
        return new EsSearchService(stub, new ObjectMapper(), defaultProperties());
    }

    private EsSearchService mockedServiceThrowing(IOException exception) throws IOException {
        RestClient stub = mock(RestClient.class);
        when(stub.performRequest(any(Request.class))).thenThrow(exception);
        return new EsSearchService(stub, new ObjectMapper(), defaultProperties());
    }

    private ElasticsearchProperties defaultProperties() {
        ElasticsearchProperties props = new ElasticsearchProperties();
        props.setDefaultIndex(TEST_INDEX);
        return props;
    }

    private Map<String, Object> firstMustLeaf(EsSearchRequest request) {
        Map<String, Object> query = asMap(EsQueryBuilder.buildSearchDsl(request).get("query"));
        if (query.containsKey("bool")) {
            List<?> must = (List<?>) asMap(query.get("bool")).get("must");
            return asMap(must.get(0));
        }
        return query;
    }

    private List<Map<String, Object>> buckets(EsSearchResult result, String aggName) {
        return asList(result.getAggregations().get(aggName));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        return (List<String>) value;
    }

    private Map<String, Object> firstMust(Map<String, Object> dsl) {
        Map<String, Object> query = asMap(dsl.get("query"));
        List<?> must = (List<?>) asMap(query.get("bool")).get("must");
        return asMap(must.get(0));
    }

    private ResponseException responseException(int status, String body) throws IOException {
        Response errorResponse = mock(Response.class);
        when(errorResponse.getRequestLine())
                .thenReturn(new BasicRequestLine("POST", "/" + TEST_INDEX + "/_search", HttpVersion.HTTP_1_1));
        when(errorResponse.getHost()).thenReturn(HttpHost.create(ES_URI));
        when(errorResponse.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, status, "Error"));
        when(errorResponse.hasWarnings()).thenReturn(false);
        when(errorResponse.getEntity()).thenReturn(new StringEntity(body, ContentType.APPLICATION_JSON));
        return new ResponseException(errorResponse);
    }

    private boolean isEsReachable() {
        try {
            restClient.performRequest(new Request("GET", "/"));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void recreateIndex() throws IOException {
        deleteIndex();
        Request create = new Request("PUT", "/" + TEST_INDEX);
        create.setJsonEntity(INDEX_CONFIG);
        restClient.performRequest(create);
    }

    private void deleteIndex() throws IOException {
        if (restClient == null) {
            return;
        }
        try {
            restClient.performRequest(new Request("DELETE", "/" + TEST_INDEX));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }
    }

    private void bulkInsert() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String document : DOCUMENTS) {
            String rid = objectMapper.readTree(document).path("rid").asText();
            builder.append("{\"index\":{\"_index\":\"").append(TEST_INDEX)
                    .append("\",\"_id\":\"").append(rid).append("\"}}\n");
            builder.append(document).append('\n');
        }
        Request request = new Request("POST", "/" + TEST_INDEX + "/_bulk?refresh=true");
        request.setJsonEntity(builder.toString());
        Response response = restClient.performRequest(request);
        Map<String, Object> body = objectMapper.readValue(response.getEntity().getContent(),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(body.get("errors")).isEqualTo(false);
    }
}
