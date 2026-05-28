package cn.net.pap.example.spring.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

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

        // 2. 构造增强后的 Prompt (手动拼接，避开 PromptTemplate 解析问题)
        String augmentedPrompt = """
                使用以下提供的上下文信息来回答用户的问题。
                ---------------------
                上下文:
                %s
                ---------------------
                用户问题: %s
                """.formatted(context, request.prompt());

        // 3. 调用 ChatClient
        return chatClient.prompt()
                .user(augmentedPrompt)
                // 动态指定会话 ID
                .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }

    /**
     * 对话请求 DTO
     */
    public record ChatRequest(String prompt, String chatId) {}
}