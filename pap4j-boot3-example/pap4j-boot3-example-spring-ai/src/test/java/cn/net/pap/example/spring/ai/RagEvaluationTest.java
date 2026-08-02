package cn.net.pap.example.spring.ai;

import cn.net.pap.example.spring.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>RAG 回答质量自动化评估单元测试类</h1>
 * <p>
 * 本类旨在对 RAG 系统的回答进行量化审计，采用 <b>LLM-as-a-judge</b>（大模型裁判）模式进行评估。
 * 评估主要包含两个核心维度：
 * </p>
 * <ul>
 *   <li><b>相关性评估 (Relevancy)</b>：使用 {@link RelevancyEvaluator} 评估生成的回答是否切合用户的提问以及检索出的上下文。</li>
 *   <li><b>真实性评估 (Faithfulness)</b>：使用 {@link FactCheckingEvaluator} 进行幻觉检测，评估回答是否完全忠实于上下文，防止模型捏造事实。</li>
 * </ul>
 * <p>
 * 为了保证构建与 CI/CD 流水线的稳定性，本测试类集成了以下健壮性设计：
 * </p>
 * <ol>
 *   <li><b>轻量级 TCP 探活</b>：在 {@link #setUp()} 中对配置的 baseUrl 进行 1000ms 超时的 TCP 连接探测，若端点不可达或未配置有效 API-Key，则优雅地跳过测试而不会导致构建失败。</li>
 *   <li><b>裁判模型动态构建</b>：根据 {@link AiProperties#mainLlm()} 的配置动态创建专属 {@link ChatModel}，避免由于容器中默认自动装配的 OpenAI 实例而导致测试请求漂移至外部接口。</li>
 * </ol>
 *
 */
@SpringBootTest
public class RagEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(RagEvaluationTest.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    private RelevancyEvaluator relevancyEvaluator;
    private FactCheckingEvaluator factCheckingEvaluator;
    private boolean isLlmServiceAccessible = false;

    private record TestCase(String question, String desc) {
    }

    // 沿用已有的知识库测试用例
    private static final List<TestCase> TEST_CASES = List.of(
            new TestCase("如何进行高精度除法计算以计算点击率或费率？", "高精度除法 (DIV2)"),
            new TestCase("系统根据什么规则把表达式分发路由给 JsonPath 或 QLExpress 引擎？", "引擎路由策略"),
            new TestCase("怎样判断一个变量是空值或者仅仅由空格组成？", "空值校验 (ISBLANK)")
    );

    @Autowired
    public RagEvaluationTest(@Qualifier("knowledgeVectorStore") VectorStore vectorStore,
                             @Qualifier("customChatClient") ChatClient chatClient,
                             AiProperties aiProperties) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.aiProperties = aiProperties;
    }

    @BeforeEach
    public void setUp() {
        isLlmServiceAccessible = checkLlmAccessibility(aiProperties.mainLlm());

        if (isLlmServiceAccessible) {
            // 根据用户的自定义配置动态构建评测裁判模型，确保地址与模型配置与 application.yml 的 main-llm 完全一致，
            // 避免因容器中默认自动装配的 OpenAI ChatModel (默认指向 api.openai.com) 导致测试请求漂移。
            ChatModel judgeModel = createChatModel(aiProperties.mainLlm());
            log.info("已选定并动态构建评测裁判模型, Provider: {}, Model: {}, BaseUrl: {}",
                    aiProperties.mainLlm().provider(), aiProperties.mainLlm().model(), aiProperties.mainLlm().baseUrl());
            
            ChatClient.Builder judgeBuilder = ChatClient.builder(judgeModel);
            this.relevancyEvaluator = new RelevancyEvaluator(judgeBuilder);
            this.factCheckingEvaluator = FactCheckingEvaluator.builder(judgeBuilder).build();
        } else {
            log.warn("大模型服务端点不可达或未配置，本组评测测试将被跳过。");
        }
    }

    /**
     * 动态构建与配置一致的 ChatModel 实例
     */
    private ChatModel createChatModel(AiProperties.ModelConfig config) {
        String provider = config.provider();
        String modelName = config.model();
        Double temperature = config.temperature() != null ? config.temperature() : 0.7;

        if ("openai".equalsIgnoreCase(provider)) {
            org.springframework.ai.openai.api.OpenAiApi openAiApi = org.springframework.ai.openai.api.OpenAiApi.builder()
                    .baseUrl(config.baseUrl())
                    .apiKey(config.apiKey())
                    .build();

            return org.springframework.ai.openai.OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();
        } else {
            org.springframework.ai.ollama.api.OllamaApi ollamaApi = org.springframework.ai.ollama.api.OllamaApi.builder()
                    .baseUrl(config.baseUrl())
                    .build();

            return org.springframework.ai.ollama.OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();
        }
    }

    private boolean checkLlmAccessibility(AiProperties.ModelConfig modelConfig) {
        if (modelConfig == null) {
            return false;
        }

        String provider = modelConfig.provider();
        if (provider == null || provider.isBlank()) {
            return false;
        }

        // 1. 如果是 OpenAI 类型的 provider，检查 apiKey 是否是哑值或为空
        if ("openai".equalsIgnoreCase(provider)) {
            String apiKey = modelConfig.apiKey();
            if (apiKey == null || apiKey.isBlank() || apiKey.contains("dummy")) {
                log.warn("OpenAI API key 为空或为哑值，跳过连通性测试。");
                return false;
            }
        }

        // 2. 解析 baseUrl 进行快速 TCP 端口探活，防止 HTTP 客户端重试或超时挂起
        String baseUrl = modelConfig.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }

        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    port = 443;
                } else {
                    port = 80;
                }
            }
            if (host == null || host.isBlank()) {
                return false;
            }

            log.info("正在对大模型端点执行轻量级 TCP 探活: {}:{}", host, port);
            try (java.net.Socket socket = new java.net.Socket()) {
                // 设置 1000ms 超时时间，防止重试和挂起
                socket.connect(new java.net.InetSocketAddress(host, port), 1000);
                log.info("TCP 探活成功: {}:{}", host, port);
                return true;
            }
        } catch (Exception e) {
            log.warn("TCP 探活失败 ({})，将跳过本组评测单元测试。", baseUrl, e);
            return false;
        }
    }

    @Test
    @DisplayName("Evaluate RAG Answer Relevancy and Faithfulness")
    public void testRagAnswerEvaluation() {
        org.junit.jupiter.api.Assumptions.assumeTrue(isLlmServiceAccessible,
                "Skipping test because LLM service is offline or inaccessible.");

        log.info("开始 RAG 回答质量 (相关性与真实性) 评测...");

        for (TestCase tc : TEST_CASES) {
            // 1. 模拟 RAG 流程：从向量库检索上下文
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(tc.question())
                            .topK(3)
                            .build()
            );

            String context = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));

            // 2. 生成 RAG 回答 (这里模拟 Controller 中的 System Prompt)
            // customChatClient 默认挂载了 MessageChatMemoryAdvisor，需显式传入 conversationId 参数防止抛出 IllegalArgumentException
            String answer = chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", "test-eval-conversation-" + tc.desc()))
                    .system(promptSystemSpec -> promptSystemSpec
                            .text("""
                                    你是一个资深的 Java 开发架构师。请参考提供的上下文回答用户的开发问题。
                                    如果没有找到确切答案，请基于你的知识合理推断并说明。
                                    
                                    [Context]
                                    {context}
                                    """)
                            .param("context", context))
                    .user(tc.question())
                    .call()
                    .content();

            log.info("==================================================");
            log.info("测试用例: {}", tc.desc());
            log.info("用户提问: \"{}\"", tc.question());
            log.info("RAG回答 : \n{}", answer);

            // 3. 构造评估请求 (输入 Query, Context Docs, Answer)
            EvaluationRequest evalRequest = new EvaluationRequest(tc.question(), docs, answer);

            // 3.1 评估相关性 (Relevancy)
            EvaluationResponse relevanceResponse = relevancyEvaluator.evaluate(evalRequest);
            log.info("相关性评估结果: 通关={} | 判定详情: {}",
                     relevanceResponse.isPass(), relevanceResponse.getFeedback());

            // 3.2 评估真实性 (Faithfulness/Fact-Checking)
            EvaluationResponse faithfulnessResponse = factCheckingEvaluator.evaluate(evalRequest);
            log.info("真实性(防幻觉)评估结果: 通关={} | 判定详情: {}",
                     faithfulnessResponse.isPass(), faithfulnessResponse.getFeedback());

            // 4. 断言验证
            assertTrue(relevanceResponse.isPass(), "相关性验证失败！原因：" + relevanceResponse.getFeedback());
            assertTrue(faithfulnessResponse.isPass(), "真实性验证失败（疑似幻觉）！原因：" + faithfulnessResponse.getFeedback());
        }

        log.info("==================================================");
        log.info("RAG 回答质量评测完成！");
    }
}
