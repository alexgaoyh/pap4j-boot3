package cn.net.pap.example.spring.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块统一配置属性类 (基于 JDK 17 Record 实现)
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        KnowledgeConfig knowledge,
        AssistantConfig assistant,
        ModelConfig mainLlm,
        ModelConfig rewriteSlm,
        ModelConfig embeddingModel
) {
    /**
     * 知识库配置
     */
    public record KnowledgeConfig(
            String docsLocation,
            String vectorStorePath
    ) {}

    /**
     * 助手设定配置
     */
    public record AssistantConfig(
            String persona
    ) {}

    /**
     * 统一的模型配置实体，用不到的字段在绑定时会自动为 null
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
     */
    public record OnnxConfig(
            String modelUri,
            String tokenizerUri
    ) {}
}
