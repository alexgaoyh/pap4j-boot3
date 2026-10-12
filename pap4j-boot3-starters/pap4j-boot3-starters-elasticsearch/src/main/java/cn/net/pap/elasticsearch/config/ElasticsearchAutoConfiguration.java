package cn.net.pap.elasticsearch.config;

import cn.net.pap.elasticsearch.service.EsSearchService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;

/**
 * Elasticsearch 自动配置类
 */
@AutoConfiguration
@EnableConfigurationProperties(ElasticsearchProperties.class)
@ConditionalOnProperty(prefix = "elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchAutoConfiguration.class);

    /**
     * 构建 Elasticsearch 低阶 RestClient 实例
     *
     * @param properties ES 配置属性
     * @return RestClient 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RestClient restClient(ElasticsearchProperties properties) {
        List<String> uris = properties.getUris();
        if (uris == null || uris.isEmpty()) {
            uris = List.of("http://localhost:9200");
        }

        HttpHost[] httpHosts = uris.stream()
                .map(uriStr -> {
                    URI uri = URI.create(uriStr);
                    return new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
                })
                .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(httpHosts);

        // 配置认证凭据
        if (StringUtils.hasText(properties.getUsername())) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(properties.getUsername(), properties.getPassword()));

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        // 配置超时参数
        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(properties.getConnectionTimeout())
                        .setSocketTimeout(properties.getSocketTimeout())
        );

        // 脱敏打印 URI
        List<String> maskedUris = uris.stream()
                .map(u -> {
                    try {
                        URI parsed = URI.create(u);
                        if (parsed.getUserInfo() != null) {
                            return u.replace(parsed.getUserInfo(), "***:***");
                        }
                    } catch (Exception ignored) {
                        return "***";
                    }
                    return u;
                })
                .toList();
        log.info("[ES-Client] Initialized RestClient with uris: {}", maskedUris);
        return builder.build();
    }

    /**
     * 构建默认 ObjectMapper 实例
     *
     * @return ObjectMapper 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return om;
    }

    /**
     * 构建通用 Elasticsearch 检索服务实例
     *
     * @param restClient   ES 底层客户端
     * @param objectMapper JSON 序列化工具
     * @param properties   ES 配置属性
     * @return EsSearchService 检索服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public EsSearchService esSearchService(RestClient restClient,
                                           ObjectMapper objectMapper,
                                           ElasticsearchProperties properties) {
        return new EsSearchService(restClient, objectMapper, properties);
    }
}
