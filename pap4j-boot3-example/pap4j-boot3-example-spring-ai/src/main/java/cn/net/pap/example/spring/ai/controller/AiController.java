package cn.net.pap.example.spring.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${ai.assistant.persona:Java 开发架构师}")
    private String defaultPersona;

    public AiController(ChatClient customChatClient, VectorStore vectorStore) {
        this.chatClient = customChatClient;
        this.vectorStore = vectorStore;
    }

    /**
     * 响应式流式对话接口 (POST 方式)
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        String chatId = request.chatId() != null ? request.chatId() : "default-chat-id";

        // 1. 手动检索知识库 (RAG)
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.prompt())
                        .topK(3)
                        .build()
        );
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 2. 构造 System提示词 (SystemPromptTemplate)
        String systemTemplateText = """
                你是一个资深的{persona}。请参考提供的上下文回答用户的开发问题。
                如果没有找到确切答案，请基于你的知识合理推断并说明。
                """;
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemTemplateText);
        String renderedSystemPrompt = systemPromptTemplate.render(Map.of("persona", defaultPersona));

        // 3. 构造 User提示词 (PromptTemplate)
        String userTemplateText = """
                使用以下提供的上下文信息来回答用户的问题。
                ---------------------
                上下文:
                {context}
                ---------------------
                用户问题: {question}
                """;
        PromptTemplate userPromptTemplate = new PromptTemplate(userTemplateText);
        String renderedUserPrompt = userPromptTemplate.render(Map.of(
                "context", context,
                "question", request.prompt()
        ));

        // 4. 调用 ChatClient 流式接口 (使用默认配置的模型与温度)
        return chatClient.prompt()
                .system(renderedSystemPrompt)
                .user(renderedUserPrompt)
                // 动态指定会话 ID
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    /**
     * 对话请求 DTO
     */
    public record ChatRequest(String prompt, String chatId) {}
}