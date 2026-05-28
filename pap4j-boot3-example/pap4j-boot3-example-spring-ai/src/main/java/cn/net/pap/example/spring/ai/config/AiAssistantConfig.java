package cn.net.pap.example.spring.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
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
        // 【核心修改点】：使用 builder 模式进行实例化
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();

        File storeFile = new File(vectorStorePath);

        // 如果已经有向量化好的本地文件，直接加载
        if (storeFile.exists() && storeFile.length() > 0) {
            vectorStore.load(storeFile);
            System.out.println("成功从本地加载知识库向量文件");
        } else {
            // 初始化知识库：读取 Markdown/Txt 并写入 VectorStore
            loadAndVectorizeKnowledge(vectorStore);
            // 保存到本地磁盘
            storeFile.getParentFile().mkdirs();
            vectorStore.save(storeFile);
            System.out.println("知识库初始化并向量化完成！");
        }
        return vectorStore;
    }

    /**
     * 2. 预置携带 RAG 能力的 ChatClient
     */
    @Bean
    public ChatClient customChatClient(ChatClient.Builder builder, VectorStore vectorStore) {

        // 1. 核心修改点：使用 Builder 模式重写 SearchRequest 和 QuestionAnswerAdvisor
        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                // 设定检索策略，去 VectorStore 搜 Top-3 最相关的知识点
                .searchRequest(SearchRequest.builder().topK(3).build())
                .build();

        // 2. 注入到 ChatClient 中
        return builder
                .defaultSystem("你是一个资深 Java 开发架构师。请严格参考提供的上下文（Context）中的 QLExpress4 和 JSON-Path 扩展函数规则，回答用户的开发问题或编写脚本。如果没有在上下文中找到确切答案，请基于你的代码知识合理推断，但要标明。")
                // 注入重构后的 RAG 顾问
                .defaultAdvisors(ragAdvisor)
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