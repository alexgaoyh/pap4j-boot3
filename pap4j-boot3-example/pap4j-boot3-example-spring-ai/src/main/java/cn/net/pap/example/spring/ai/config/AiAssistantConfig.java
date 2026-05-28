package cn.net.pap.example.spring.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
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
        System.out.println("正在刷新并向量化本地知识库...");
        loadAndVectorizeKnowledge(vectorStore);
        
        // 保存到本地磁盘（覆盖旧版本）
        storeFile.getParentFile().mkdirs();
        vectorStore.save(storeFile);
        System.out.println("知识库刷新并向量化完成！向量文件位置: " + vectorStorePath);
        
        return vectorStore;
    }

    /**
     * 2. 声明聊天记忆组件（内存型）
     */
    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * 3. 预置携带聊天记忆能力的 ChatClient
     */
    @Bean
    public ChatClient customChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {

        // 1. ChatMemory 顾问：维护对话上下文
        MessageChatMemoryAdvisor memoryAdvisor = new MessageChatMemoryAdvisor(chatMemory);

        // 2. 注入到 ChatClient 中
        return builder
                .defaultSystem("你是一个资深 Java 开发架构师。请参考提供的上下文回答用户的开发问题。如果没有找到确切答案，请基于你的知识合理推断并说明。")
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * 私有方法：读取 classpath 下的知识文档并切块
     */
    private void loadAndVectorizeKnowledge(SimpleVectorStore vectorStore) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(docsLocation + "*.yml");

            List<Document> allDocuments = new ArrayList<>();
            for (Resource resource : resources) {
                TextReader textReader = new TextReader(resource);
                allDocuments.addAll(textReader.get());
            }

            // 对文本进行切块，避免超出模型的输入上下文窗口
            TokenTextSplitter splitter = new TokenTextSplitter(800, 400, 10, 10000, true);
            List<Document> splitDocuments = splitter.apply(allDocuments);

            // 加入向量库（此时会自动调用 EmbeddingModel 将文本转为数字向量）
            vectorStore.add(splitDocuments);

        } catch (IOException e) {
            throw new RuntimeException("读取本地知识文档失败", e);
        }
    }
}