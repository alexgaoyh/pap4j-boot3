package cn.net.pap.example.dynamic.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Mock API 配置实体。
 * 数据持久化在 SQLite 中，表名为 mock_api_rule。
 */
@Entity
@Table(name = "mock_api_rule")
@Data
public class MockApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url; // 匹配路径，例如 /users/list

    @Column(nullable = false)
    private String method; // GET, POST, ANY, * 等

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false)
    private Integer responseStatus = 200;

    @Column(nullable = false)
    private String contentType = "application/json;charset=UTF-8";

    // --- 请求匹配过滤字段 ---

    @Column(columnDefinition = "TEXT")
    private String requestHeaders; // 期望请求头 (JSON Map，存储键均为小写)

    @Column(columnDefinition = "TEXT")
    private String requestParams; // 期望 Query 字符串 (如 a=1&b=2)

    @Column(columnDefinition = "TEXT")
    private String requestBody; // 期望请求体

    @Column(columnDefinition = "TEXT")
    private String curlCommand; // 原始 cURL 命令行
}
