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
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
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
     * 1. 声明知识库向量数据库 Bean
     */
    @Bean(name = "knowledgeVectorStore")
    @Primary
    public VectorStore knowledgeVectorStore(@org.springframework.beans.factory.annotation.Qualifier("customEmbeddingModel") EmbeddingModel embeddingModel) {
        String type = aiProperties.knowledge().storeType();
        if ("elasticsearch".equalsIgnoreCase(type)) {
            return createElasticsearchStore(embeddingModel, "knowledge");
        } else if ("simple".equalsIgnoreCase(type)) {
            return createSimpleStore(embeddingModel, "knowledge");
        } else {
            throw new IllegalArgumentException("未知的向量库类型配置: " + type + "，仅支持 'simple' 或 'elasticsearch'。");
        }
    }

    /**
     * 1b. 声明 Emoji 向量数据库 Bean
     */
    @Bean(name = "emojiVectorStore")
    public VectorStore emojiVectorStore(@org.springframework.beans.factory.annotation.Qualifier("customEmbeddingModel") EmbeddingModel embeddingModel) {
        String type = aiProperties.knowledge().storeType();
        if ("elasticsearch".equalsIgnoreCase(type)) {
            return createElasticsearchStore(embeddingModel, "emoji");
        } else if ("simple".equalsIgnoreCase(type)) {
            return createSimpleStore(embeddingModel, "emoji");
        } else {
            throw new IllegalArgumentException("未知的向量库类型配置: " + type + "，仅支持 'simple' 或 'elasticsearch'。");
        }
    }

    private VectorStore createElasticsearchStore(EmbeddingModel embeddingModel, String storeKey) {
        log.info("【VectorStore】初始化 Elasticsearch {} 存储...", storeKey);
        
        org.elasticsearch.client.RestClient restClient = createElasticsearchRestClient(aiProperties.elasticsearch());
        String indexName = "spring-ai-" + storeKey + "-index";

        org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions options = 
                new org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions();
        options.setIndexName(indexName);
        
        org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore esStore = 
                org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore.builder(restClient, embeddingModel)
                        .options(options)
                        .initializeSchema(true)
                        .build();
        
        checkAndInitElasticsearch(esStore, storeKey, indexName, "开发");
        return esStore;
    }

    private void checkAndInitElasticsearch(
            org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore esStore, 
            String storeKey, String indexName, String testQuery) {
        try {
            log.info("【Elasticsearch-{}】检查数据状态 (Index: {})...", storeKey, indexName);
            List<Document> results = esStore.similaritySearch(SearchRequest.builder().query(testQuery).topK(1).build());
            if (results.isEmpty()) {
                log.info("【Elasticsearch-{}】索引为空，开始导入初始数据...", storeKey);
                initStoreData(esStore, storeKey);
                log.info("【Elasticsearch-{}】数据导入完成！", storeKey);
            } else {
                log.info("【Elasticsearch-{}】已存在数据，跳过导入。", storeKey);
            }
        } catch (Exception e) {
            log.info("【Elasticsearch-{}】未检测到有效索引（{}），开始重建并导入...", storeKey, e.getMessage());
            try {
                initStoreData(esStore, storeKey);
                log.info("【Elasticsearch-{}】数据重建导入完成！", storeKey);
            } catch (Exception ex) {
                log.error("【Elasticsearch-{}】数据导入失败", storeKey, ex);
            }
        }
    }

    private VectorStore createSimpleStore(EmbeddingModel embeddingModel, String storeKey) {

        log.info("【VectorStore】激活本地 SimpleVectorStore 内存 {} 向量数据库...", storeKey);
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        // 【优化点】：为了保证 YAML 知识库修改能立即生效，这里强制执行一次知识库向量化。
        // 在生产环境建议改回判断 storeFile.exists() 以提升启动速度。
        File storeFile = new File(aiProperties.knowledge().vectorStorePath() + "-" + storeKey);

        log.info("正在刷新并向量化本地 {} 库...", storeKey);
        initStoreData(vectorStore, storeKey);
        storeFile.getParentFile().mkdirs();
        vectorStore.save(storeFile);
        log.info("{} 库刷新并向量化完成！向量文件位置: {}", storeKey, storeFile.getAbsolutePath());
        
        return vectorStore;
    }

    private void initStoreData(VectorStore vectorStore, String storeKey) {
        if(storeKey.equals("knowledge")) {
            loadAndVectorizeKnowledge(vectorStore);
        }
        if(storeKey.equals("emoji")) {
            loadAndVectorizeEmojis(vectorStore);
        }
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
     * 5. 声明向量化模型 Bean，支持 ONNX / Ollama / OpenAI / Mock 四模动态切换。
     * <p>
     * 【配置与装配关系说明】：
     * 1. 禁用官方默认装配：
     *    在 {@code application.yml} 中配置了 {@code spring.ai.model.embedding: none}，
     *    用以硬性关闭 Spring AI 官方默认的本地 ONNX 向量自动装配，防止其在启动时自动从 GitHub 下载默认模型权重并加载。
     * 
     * 2. 自定义装配实现：
     *    本 Bean 作为系统中唯一生效的 {@link EmbeddingModel}，通过 {@link org.springframework.context.annotation.Primary} 注入。
     *    它会根据运行时参数 {@code ai.embedding-model.provider} 的设定，自适应实例化对应的向量化计算引擎。
     *    - 当配置为 'onnx' 时，才会在本方法中实例化本地模型并初始化本地 C++ 计算引擎（产生约 400MB 物理内存开销）；
     *    - 当配置为 'openai'、'ollama' 时，本地 C++ 引擎完全不会被装载，保持低开销。
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
    private void loadAndVectorizeKnowledge(VectorStore vectorStore) {
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
            // 【开发注意】：此处会直接进行全量批量向量化（Batching）。若后续迁移为云端三方向量 API（如 OpenAI）
            // 并在冷启动时遇到速率限制（HTTP 429 / TPM），需在此处重构为“分批（Partition）+ 间隔睡眠”模式。
            vectorStore.add(allDocuments);

        } catch (IOException e) {
            throw new RuntimeException("读取本地知识文档失败", e);
        }
    }

    /**
     * 私有方法：解析 annotations.json 导入到向量库中
     */
    private void loadAndVectorizeEmojis(VectorStore vectorStore) {
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
            // 【开发注意】：由于 Emoji 数量较多（1000+ 条），此处会发起全量批量向量化请求以提升 CPU/GPU 推理效率。
            // 若后续切换为云端三方向量 API 并触发频率限制（Rate Limit / TPM），
            // 须在此处对 emojiDocs 列表进行分段分批（如每批 100 条）并引入短暂睡眠（Thread.sleep）。
            vectorStore.add(emojiDocs);
            log.info("Emoji 向量导入完成！");

        } catch (IOException e) {
            log.error("加载 annotations.json 向量化失败", e);
            throw new RuntimeException("读取 Emoji 字典失败", e);
        }
    }

    /**
     * 根据自定义属性，局部实例化 Elasticsearch RestClient，规避全局连接探测
     */
    private org.elasticsearch.client.RestClient createElasticsearchRestClient(AiProperties.ElasticsearchConfig config) {
        if (config == null || !org.springframework.util.StringUtils.hasText(config.uris())) {
            throw new IllegalArgumentException("Elasticsearch URIs 配置不能为空！");
        }
        
        org.elasticsearch.client.RestClientBuilder builder = org.elasticsearch.client.RestClient.builder(
                org.apache.http.HttpHost.create(config.uris())
        ).setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(5000)
                .setSocketTimeout(30000)
        );

        if (org.springframework.util.StringUtils.hasText(config.username()) && org.springframework.util.StringUtils.hasText(config.password())) {
            final org.apache.http.client.CredentialsProvider credentialsProvider = new org.apache.http.impl.client.BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    org.apache.http.auth.AuthScope.ANY,
                    new org.apache.http.auth.UsernamePasswordCredentials(config.username(), config.password())
            );
            builder.setHttpClientConfigCallback(httpClientBuilder -> 
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        return builder.build();
    }
}