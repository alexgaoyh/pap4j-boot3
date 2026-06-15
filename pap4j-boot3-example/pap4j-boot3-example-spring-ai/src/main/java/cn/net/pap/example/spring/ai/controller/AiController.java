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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

        // 1. 第一次检索：基于用户问题语义召回 (RAG)
        List<Document> docs = new ArrayList<>(vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(request.prompt())
                        .topK(3)
                        .build()
        ));

        // 2. 第二次检索：从已召回文档中提取跨文件链接，跟随链接补召关联知识
        List<String> linkedTexts = extractMarkdownLinkTexts(docs);
        if (!linkedTexts.isEmpty()) {
            String linkQuery = String.join(" ", linkedTexts);
            List<Document> extraDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(linkQuery)
                            .topK(linkedTexts.size())
                            .build()
            );
            // 按文本内容去重合并
            Set<String> seenTexts = docs.stream().map(Document::getText).collect(Collectors.toSet());
            for (Document extra : extraDocs) {
                if (!seenTexts.contains(extra.getText())) {
                    docs.add(extra);
                    seenTexts.add(extra.getText());
                }
            }
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

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

    /**
     * 从文档列表中提取所有 markdown 跨文件链接的显示文本。
     * 例如 "详见 [DIV2 — 高精度舍入除法函数](function_div2.md)" 会提取出 "DIV2 — 高精度舍入除法函数"。
     * 这些显示文本描述了被链接文档的核心内容，将作为二次检索的查询词，
     * 从而把关联文档也召回到上下文中。
     */
    private List<String> extractMarkdownLinkTexts(List<Document> docs) {
        Pattern pattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+\\.md)\\)");
        return docs.stream()
                .map(Document::getText)
                .flatMap(text -> {
                    Matcher matcher = pattern.matcher(text);
                    List<String> texts = new ArrayList<>();
                    while (matcher.find()) {
                        // 取链接的显示文本作为语义查询词，比用文件路径更具语义
                        texts.add(matcher.group(1));
                    }
                    return texts.stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }

}