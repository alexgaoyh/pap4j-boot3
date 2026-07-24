package cn.net.pap.example.spring.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiAssistantConfig {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantConfig.class);

    private final AiProperties aiProperties;

    public AiAssistantConfig(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    /**
     * 1. 声明纯内存+文件持久化的向量数据库，显式通过 Qualifier 注入我们自定义的动态 Bean
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(@org.springframework.beans.factory.annotation.Qualifier("customEmbeddingModel") EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        File storeFile = new File(aiProperties.knowledge().vectorStorePath());

        // 【优化点】：为了保证 YAML 知识库修改能立即生效，这里强制执行一次知识库向量化。
        // 在生产环境建议改回判断 storeFile.exists() 以提升启动速度。
        log.info("正在刷新并向量化本地知识库...");
        loadAndVectorizeKnowledge(vectorStore);
        loadAndVectorizeEmojis(vectorStore);
        // 保存到本地磁盘（覆盖旧版本）
        storeFile.getParentFile().mkdirs();
        vectorStore.save(storeFile);
        log.info("知识库刷新并向量化完成！向量文件位置: {}", aiProperties.knowledge().vectorStorePath());
        
        return vectorStore;
    }

    /**
     * 2. 声明聊天记忆组件（内存型）
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    /**
     * 3. 预置主大模型 ChatClient（搭载多轮记忆）
     */
    @Bean(name = "customChatClient")
    @Primary
    public ChatClient customChatClient(ChatMemory chatMemory) {
        org.springframework.ai.chat.model.ChatModel chatModel = createChatModel(aiProperties.mainLlm(), "主模型", 0.7);
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * 4. 预置专用于 Query 改写与提炼的极速 ChatClient
     */
    @Bean(name = "queryRewriteChatClient")
    public ChatClient queryRewriteChatClient() {
        org.springframework.ai.chat.model.ChatModel chatModel = createChatModel(aiProperties.rewriteSlm(), "改写提炼模型", 0.1);

        String rewriteSystemPrompt = """
                你是一个专业的检索 Query 处理器。
                任务：结合历史对话上下文，处理用户的最新提问。
                请同时输出以下两个字段：
                1. rewrittenQuery: 结合历史上下文，将最新提问改写为【包含完整主谓宾的独立提问】（消解“它”、“这个”等代词）。
                2. bm25Keywords: 去除口语化废话（如“请帮我找下”、“怎么实现”），精准提取出高价值的核心实体和技术名词作为检索关键词（空格分隔）。
                
                规则：
                - 你必须且只能输出一个合法的 JSON 对象，绝对不要包含任何 Markdown 格式标记（如 ```json）、解释说明或额外标点。
                格式必须为：
                {
                  "rewrittenQuery": "...",
                  "bm25Keywords": "..."
                }
                """;

        return ChatClient.builder(chatModel)
                .defaultSystem(rewriteSystemPrompt)
                .build();
    }

    /**
     * 5. 声明向量化模型 Bean，支持 ONNX / Ollama / OpenAI(含DeepSeek) 三模动态切换。
     * <p>
     * 【重要命名与内存开销说明】：
     * 我们将该 Bean 显式命名为 "customEmbeddingModel" 并标注为 @Primary，以避免与 Spring Boot 自动配置的 "embeddingModel" 产生命名冲突。
     * 由于 classpath 下引入了 `spring-ai-starter-model-transformers` 依赖以支持本地离线 ONNX 模式，Spring AI 默认的自动配置类
     * TransformersEmbeddingModelAutoConfiguration 依然会强行运行并注册其底层的 "embeddingModel" Bean（加载本地 ONNX 引擎与 model.onnx 资源文件），
     * 即使您在配置中将 provider 设定为 ollama 或 openai 也是如此，这会额外占用数百兆 JVM 内存。
     * 如果您后续决定完全迁移到云端大模型接口/互联网服务（不再需要本地离线计算），为了最大化节省内存，您应当从 pom.xml 中彻底剔除
     * `spring-ai-starter-model-transformers` 依赖，这样 Spring AI 自动配置就会彻底退避，不再加载本地模型。
     */
    @Bean(name = "customEmbeddingModel")
    @Primary
    public EmbeddingModel customEmbeddingModel() throws Exception {
        AiProperties.ModelConfig config = aiProperties.embeddingModel();
        String provider = config.provider();

        if ("openai".equalsIgnoreCase(provider)) {
            log.info("【Embedding】切换为 OpenAI 标准 API 向量服务，地址: {}, 模型: {}", config.baseUrl(), config.model());
            return new org.springframework.ai.openai.OpenAiEmbeddingModel(createOpenAiApi(config),
                    org.springframework.ai.document.MetadataMode.EMBED,
                    org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                            .model(config.model())
                            .build());
        } else if ("ollama".equalsIgnoreCase(provider)) {
            log.info("【Embedding】切换为 Ollama 向量服务，地址: {}, 模型: {}", config.baseUrl(), config.model());
            return org.springframework.ai.ollama.OllamaEmbeddingModel.builder()
                    .ollamaApi(createOllamaApi(config))
                    .defaultOptions(org.springframework.ai.ollama.api.OllamaEmbeddingOptions.builder()
                            .model(config.model())
                            .build())
                    .build();
        } else {
            AiProperties.OnnxConfig onnx = config.onnx();
            String onnxModelUri = onnx != null ? onnx.modelUri() : null;
            String onnxTokenizerUri = onnx != null ? onnx.tokenizerUri() : null;
            log.info("【Embedding】切换为本地内嵌 ONNX 向量模型，Model: {}, Tokenizer: {}", onnxModelUri, onnxTokenizerUri);
            org.springframework.ai.transformers.TransformersEmbeddingModel embeddingModel = 
                    new org.springframework.ai.transformers.TransformersEmbeddingModel();
            embeddingModel.setModelResource(onnxModelUri);
            embeddingModel.setTokenizerResource(onnxTokenizerUri);
            embeddingModel.afterPropertiesSet();
            return embeddingModel;
        }
    }

    /**
     * 统一创建 OpenAI API 客户端
     */
    private org.springframework.ai.openai.api.OpenAiApi createOpenAiApi(AiProperties.ModelConfig config) {
        return org.springframework.ai.openai.api.OpenAiApi.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .build();
    }

    /**
     * 统一创建 Ollama API 客户端
     */
    private org.springframework.ai.ollama.api.OllamaApi createOllamaApi(AiProperties.ModelConfig config) {
        return org.springframework.ai.ollama.api.OllamaApi.builder()
                .baseUrl(config.baseUrl())
                .build();
    }

    /**
     * 根据配置动态创建 ChatModel 实例
     */
    private org.springframework.ai.chat.model.ChatModel createChatModel(
            AiProperties.ModelConfig config, String logLabel, Double defaultTemp) {
        String provider = config.provider();
        String modelName = config.model();
        Double temperature = config.temperature() != null ? config.temperature() : defaultTemp;

        if ("openai".equalsIgnoreCase(provider)) {
            log.info("【ChatClient - {}】切换为 OpenAI 标准 API 服务，地址: {}, 模型: {}", logLabel, config.baseUrl(), modelName);
            return org.springframework.ai.openai.OpenAiChatModel.builder()
                    .openAiApi(createOpenAiApi(config))
                    .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();
        } else {
            log.info("【ChatClient - {}】切换为 Ollama API 服务，地址: {}, 模型: {}", logLabel, config.baseUrl(), modelName);
            return org.springframework.ai.ollama.OllamaChatModel.builder()
                    .ollamaApi(createOllamaApi(config))
                    .defaultOptions(org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .build())
                    .build();
        }
    }

    /**
     * 私有方法：读取 classpath 下的知识文档并切块
     */
    private void loadAndVectorizeKnowledge(SimpleVectorStore vectorStore) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(aiProperties.knowledge().docsLocation() + "*.md");

            List<Document> allDocuments = new ArrayList<>();
            for (Resource resource : resources) {
                TextReader textReader = new TextReader(resource);
                for (Document doc : textReader.get()) {
                    java.util.Map<String, Object> metadata = new java.util.HashMap<>(doc.getMetadata());
                    metadata.put("type", "knowledge");
                    // 写入来源文件名，用于后续关联链接的元数据精准匹配召回
                    metadata.put("source", resource.getFilename());
                    allDocuments.add(new Document(doc.getId(), doc.getText(), metadata));
                }
            }

            // 【RAG 优化】：由于每个 MD 文件均小于 1.5KB，已属于理想的独立语义单元，
            // 绕过 TokenTextSplitter 分块器直接导入向量库，避免切分造成上下文割裂。
            vectorStore.add(allDocuments);

        } catch (IOException e) {
            throw new RuntimeException("读取本地知识文档失败", e);
        }
    }

    /**
     * 私有方法：解析 annotations.json 导入到向量库中
     */
    private void loadAndVectorizeEmojis(SimpleVectorStore vectorStore) {
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("annotations.json");
            if (!resource.exists()) {
                log.warn("ClassPath 下未找到 annotations.json 资源文件，跳过 Emoji 向量化。");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode;
            try (InputStream is = resource.getInputStream()) {
                rootNode = mapper.readTree(is);
            }
            JsonNode annotationsNode = rootNode.path("annotations").path("annotations");

            List<Document> emojiDocs = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = annotationsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String emojiChar = field.getKey();
                JsonNode valueNode = field.getValue();

                Set<String> keywords = new HashSet<>();

                // 解析 default
                JsonNode defaultNode = valueNode.path("default");
                if (defaultNode.isArray()) {
                    for (JsonNode node : defaultNode) {
                        keywords.add(node.asText());
                    }
                }

                // 解析 tts
                JsonNode ttsNode = valueNode.path("tts");
                if (ttsNode.isArray()) {
                    for (JsonNode node : ttsNode) {
                        keywords.add(node.asText());
                    }
                }

                if (!keywords.isEmpty()) {
                    String combinedText = String.join(" ", keywords);
                    // 为 Emoji 创建专门的 Document，通过 metadata 进行类别区分
                    Document doc = new Document(
                            combinedText,
                            Map.of("type", "emoji", "emoji", emojiChar)
                    );
                    emojiDocs.add(doc);
                }
            }

            log.info("从 annotations.json 成功加载了 {} 个 Emoji 注解条目，正在进行向量生成与导入...", emojiDocs.size());
            vectorStore.add(emojiDocs);
            log.info("Emoji 向量导入完成！");

        } catch (IOException e) {
            log.error("加载 annotations.json 向量化失败", e);
            throw new RuntimeException("读取 Emoji 字典失败", e);
        }
    }
}