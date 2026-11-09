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
     * 检索分页策略切换阈值（对应 ES 默认 index.max_result_window = 10000）：
     * from + size 小于等于该值时直接用 from/size 取数；超出时内部自动切为 search_after 游标翻页，
     * 因此请求不会再因为超过 10000 而被拒绝。默认 10000，与 ES 默认值一致。
     * <p>
     * 如对索引改过 index.max_result_window，可同步调整该值以减少内部回溯次数。
     * </p>
     */
    private int maxResultWindow = 10000;

    /**
     * 深分页 search_after 游标翻页时单批拉取的文档数（默认 1000）。
     * <p>
     * 仅影响内部逐批回溯的次数，不影响对外返回的分页大小；实际取值受 maxResultWindow 约束。
     * </p>
     */
    private int deepPaginationBatchSize = 1000;
}
