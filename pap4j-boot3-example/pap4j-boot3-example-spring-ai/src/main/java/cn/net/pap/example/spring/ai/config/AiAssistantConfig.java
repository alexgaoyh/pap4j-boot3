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
import java.util.ArrayList;
import java.util.List;

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
     * 3. 预置携带聊天记忆能力的 ChatClient
     */
    @Bean
    public ChatClient customChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {

        // 1. ChatMemory 顾问：维护对话上下文
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        // 2. 注入到 ChatClient 中
        return builder
                .defaultAdvisors(memoryAdvisor)
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
                allDocuments.addAll(textReader.get());
            }

            // 【RAG 优化】：由于每个 MD 文件均小于 1.5KB，已属于理想的独立语义单元，
            // 绕过 TokenTextSplitter 分块器直接导入向量库，避免切分造成上下文割裂。
            vectorStore.add(allDocuments);

        } catch (IOException e) {
            throw new RuntimeException("读取本地知识文档失败", e);
        }
    }
}