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
}
