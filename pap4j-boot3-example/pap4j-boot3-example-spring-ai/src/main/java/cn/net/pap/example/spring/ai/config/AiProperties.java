package cn.net.pap.example.spring.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块统一配置属性类 (基于 JDK 17 Record 实现)
 * @param knowledge
 * @param assistant
 * @param mainLlm
 * @param rewriteSlm
 * @param embeddingModel
 * @param elasticsearch
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        KnowledgeConfig knowledge,
        AssistantConfig assistant,
        ModelConfig mainLlm,
        ModelConfig rewriteSlm,
        ModelConfig embeddingModel,
        ElasticsearchConfig elasticsearch
) {
    /**
     * 知识库配置
     * @param docsLocation
     * @param vectorStorePath
     * @param storeType
     */
    public record KnowledgeConfig(
            String docsLocation,
            String vectorStorePath,
            String storeType
    ) {}

    /**
     * 助手设定配置
     * @param persona
     */
    public record AssistantConfig(
            String persona
    ) {}

    /**
     * 统一的模型配置实体，用不到的字段在绑定时会自动为 null
     * @param provider
     * @param baseUrl
     * @param apiKey
     * @param model
     * @param temperature
     * @param onnx
     */
    public record ModelConfig(
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            Double temperature,
            OnnxConfig onnx
    ) {}

    /**
     * ONNX 专属配置
     * @param modelUri
     * @param tokenizerUri
     */
    public record OnnxConfig(
            String modelUri,
            String tokenizerUri
    ) {}

    /**
     * Elasticsearch 专属配置
     * @param uris
     * @param username
     * @param password
     */
    public record ElasticsearchConfig(
            String uris,
            String username,
            String password
    ) {}
}
