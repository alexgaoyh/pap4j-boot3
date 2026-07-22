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
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class AiAssistantConfig {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantConfig.class);

    @Value("${ai.knowledge.vector-store-path}")
    private String vectorStorePath;

    @Value("${ai.knowledge.docs-location}")
    private String docsLocation;

    /**
     * 1. 声明纯内存+文件持久化的向量数据库
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        File storeFile = new File(vectorStorePath);

        // 【优化点】：为了保证 YAML 知识库修改能立即生效，这里强制执行一次知识库向量化。
        // 在生产环境建议改回判断 storeFile.exists() 以提升启动速度。
        log.info("正在刷新并向量化本地知识库...");
        loadAndVectorizeKnowledge(vectorStore);
        loadAndVectorizeEmojis(vectorStore);
        // 保存到本地磁盘（覆盖旧版本）
        storeFile.getParentFile().mkdirs();
        vectorStore.save(storeFile);
        log.info("知识库刷新并向量化完成！向量文件位置: {}", vectorStorePath);
        
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
     * 3. 预置主大模型 ChatClient（从 ai.main-llm 读取专属 base-url, model, temperature，搭载多轮记忆）
     */
    @Bean(name = "customChatClient")
    @org.springframework.context.annotation.Primary
    public ChatClient customChatClient(
            @Value("${ai.main-llm.base-url}") String baseUrl,
            @Value("${ai.main-llm.model}") String modelName,
            @Value("${ai.main-llm.temperature:0.7}") Double temperature,
            ChatMemory chatMemory) {

        org.springframework.ai.ollama.api.OllamaApi ollamaApi = org.springframework.ai.ollama.api.OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        org.springframework.ai.ollama.OllamaChatModel chatModel = org.springframework.ai.ollama.OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return ChatClient.builder(chatModel)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * 4. 预置专用于 Query 改写的极速 ChatClient（从 ai.rewrite-slm 读取专属 base-url, model, temperature）
     */
    @Bean(name = "queryRewriteChatClient")
    public ChatClient queryRewriteChatClient(
            @Value("${ai.rewrite-slm.base-url}") String baseUrl,
            @Value("${ai.rewrite-slm.model}") String modelName,
            @Value("${ai.rewrite-slm.temperature:0.1}") Double temperature) {

        org.springframework.ai.ollama.api.OllamaApi ollamaApi = org.springframework.ai.ollama.api.OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        org.springframework.ai.ollama.OllamaChatModel chatModel = org.springframework.ai.ollama.OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .build())
                .build();

        String rewriteSystemPrompt = """
                你是一个专业检索 Query 改写助手。
                任务：结合历史对话上下文，将用户的最新提问改写为【包含完整主谓宾的独立提问】（消解“它”、“这个”等代词）。
                规则：只输出改写后的最终提问文本，绝对不要包含任何解释说明或额外标点。
                """;

        return ChatClient.builder(chatModel)
                .defaultSystem(rewriteSystemPrompt)
                .build();
    }

    /**
     * 私有方法：读取 classpath 下的知识文档并切块
     */
    private void loadAndVectorizeKnowledge(SimpleVectorStore vectorStore) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(docsLocation + "*.md");

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