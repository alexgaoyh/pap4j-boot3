package cn.net.pap.elasticsearch.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 配置属性
 */
@Data
@ToString(exclude = "password")
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    /**
     * 是否启用 Elasticsearch 客户端
     */
    private boolean enabled = true;

    /**
     * ES 节点地址列表
     */
    private List<String> uris = new ArrayList<>();

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 默认索引名称（可选）
     */
    private String defaultIndex;

    /**
     * 连接超时时间（毫秒）
     */
    private int connectionTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int socketTimeout = 30000;

    /**
     * HTTP 连接池最大连接数（默认 30）
     */
    private int maxConnTotal = 30;

    /**
     * 每个路由最大并发连接数（默认 10）
     */
    private int maxConnPerRoute = 10;

    /**
     * 默认每页条数（默认 10）
     */
    private int defaultPageSize = 10;

    /**
     * 每页最大条数限制（默认 100）
     */
    private int maxPageSize = 100;

    /**
     * 深分页保护阈值（对应 ES max_result_window）：from + size 超过该值时直接拒绝，
     * 避免在千万级索引上触发跨分片全量排序的高代价查询。默认 10000，与 ES 默认值一致。
     */
    private int maxResultWindow = 10000;
}
