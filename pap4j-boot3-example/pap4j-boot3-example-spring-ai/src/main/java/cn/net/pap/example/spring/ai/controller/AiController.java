package cn.net.pap.example.spring.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 助手测试接口", description = "提供基于 Spring AI 的响应式 RAG 对话检索和 Emoji 语义检索功能接口")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final ChatClient chatClient;
    private final ChatClient queryRewriteChatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    @Value("${ai.assistant.persona:Java 开发架构师}")
    private String defaultPersona;

    public AiController(@Qualifier("customChatClient") ChatClient customChatClient,
                        @Qualifier("queryRewriteChatClient") ChatClient queryRewriteChatClient,
                        ChatMemory chatMemory,
                        VectorStore vectorStore,
                        ObjectMapper objectMapper) {
        this.chatClient = customChatClient;
        this.queryRewriteChatClient = queryRewriteChatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 响应式流式对话接口 (POST 方式)
     */
    @Operation(summary = "响应式流式对话接口 (RAG)")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return Flux.defer(() -> {
            String chatId = request.chatId() != null ? request.chatId() : "default-chat-id";

            // 0. 从 ChatMemory 获取多轮历史上下文 (取最近 4 条消息用于 Query 改写)
            List<org.springframework.ai.chat.messages.Message> fullHistory = chatMemory.get(chatId);
            List<org.springframework.ai.chat.messages.Message> history = (fullHistory != null && fullHistory.size() > 4)
                    ? fullHistory.subList(fullHistory.size() - 4, fullHistory.size())
                    : (fullHistory != null ? fullHistory : java.util.Collections.emptyList());
            String searchQuery = request.prompt();
            String bm25Keywords = "";

            if (!history.isEmpty()) {
                String modelResult = null;
                try {
                    String historyText = history.stream()
                            .map(m -> m.getMessageType() + ": " + m.getText())
                            .collect(Collectors.joining("\n"));

                    modelResult = queryRewriteChatClient.prompt()
                            .user(u -> u.text("历史上下文:\n{history}\n\n最新提问: {query}")
                                        .param("history", historyText)
                                        .param("query", request.prompt()))
                            .call()
                            .content();

                    ProcessResult result = parseModelResult(modelResult);
                    if (result != null) {
                        searchQuery = result.rewrittenQuery().isEmpty() ? request.prompt() : result.rewrittenQuery();
                        bm25Keywords = result.bm25Keywords();
                        log.info("Query 改写与提炼成功 - chatId: {}, 原始: '{}' -> 改写: '{}' | 关键词: '{}'",
                                chatId, request.prompt(), searchQuery, bm25Keywords);
                    } else {
                        log.warn("Query 改写与提炼 JSON 解析失败，降级不改写直接回答 - chatId: {}, 原始: '{}', 模型返回: {}",
                                chatId, request.prompt(), modelResult);
                        searchQuery = request.prompt();
                        bm25Keywords = "";
                    }
                } catch (Exception e) {
                    log.warn("Query 改写提炼异常，降级不改写直接回答 - chatId: {}, 原始: '{}', 模型返回: {}, 异常: {}",
                            chatId, request.prompt(), modelResult, e.getMessage());
                    searchQuery = request.prompt();
                    bm25Keywords = "";
                }
            } else {
                // 无历史会话时，免去模型调用，不改写也不提炼关键字
                bm25Keywords = "";
                log.info("无历史会话 - chatId: {}, 原始: '{}'", chatId, request.prompt());
            }

            // 1. 第一次检索：使用改写后的 searchQuery 进行语义召回 (RAG)，过滤 type == 'knowledge' 的文档
            List<Document> firstDocs = new ArrayList<>(vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(searchQuery)
                            .topK(3)
                            .filterExpression("type == 'knowledge'")
                            .build()
            ));

            // 2. 第二次检索：从已召回文档中提取跨文件链接，使用元数据（Metadata）进行精确过滤召回，规避语义检索漂移
            List<String> linkedFiles = extractMarkdownLinkFiles(firstDocs);
            List<Document> extraDocs = new ArrayList<>();
            List<Document> docs = new ArrayList<>(firstDocs);

            if (!linkedFiles.isEmpty()) {
                // 构建多文件精准过滤表达式，如：type == 'knowledge' && (source == 'file1.md' || source == 'file2.md')
                String fileConditions = linkedFiles.stream()
                        .map(file -> "source == '" + file + "'")
                        .collect(Collectors.joining(" || "));
                String filterExpr = "type == 'knowledge' && (" + fileConditions + ")";

                extraDocs.addAll(vectorStore.similaritySearch(
                        SearchRequest.builder()
                                // 因元数据已实现精确过滤，query 使用原始 Prompt 以作格式占位即可，核心由 Metadata Filter 负责硬召回
                                .query(request.prompt())
                                .topK(linkedFiles.size())
                                .filterExpression(filterExpr)
                                .build()
                ));

                // 按文本内容去重合并
                Set<String> seenTexts = firstDocs.stream().map(Document::getText).collect(Collectors.toSet());
                for (Document extra : extraDocs) {
                    if (!seenTexts.contains(extra.getText())) {
                        docs.add(extra);
                        seenTexts.add(extra.getText());
                    }
                }
            }

            // 3. 记录 RAG 检索细节结构化日志
            logRagTrace(chatId, request.prompt(), searchQuery, bm25Keywords, firstDocs, linkedFiles, extraDocs, docs.size());

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
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Emoji 语义检索接口 (利用提示词工程将业务词翻译为物理事物词，再通过向量数据库召回)
     *
     * 【优化提示】：
     * 关于提高 Emoji 语义检索召回率与命中率，有以下三种可选架构思路：
     * 1. 方案 A：多词拆分并行语义检索
     *    - 原理：将 LLM 翻译出的多个关键词拆开，并行发起多次向量数据库 similaritySearch，最后在内存中以 Max-Pooling 策略进行排重，并保留最高相似度得分进行排序。
     *    - 优缺点：解决多词堆叠时的 Attention 退化与稀释问题，语义联想范围广；但由于增加了 N 次向量化计算与 N 次向量数据库检索，在大并发或 CPU 受限下有性能开销。
     * 2. 方案 B：双路混合检索 (字面精确匹配优先 + 向量语义匹配兜底)
     *    - 原理：将所有词合并成一句话计算 1 次向量作为语义兜底；同时，在 JVM 内存中缓存倒排索引，并进行字符串精确碰撞算分。最后将向量相关性得分与字面匹配得分进行线性加权混合排序。
     *    - 优缺点：计算开销极低，且对于具有强字面意图的场景（如精确关键词）可确保 100% 精确命中（字面匹配排在最前），弥补了稠密向量匹配对符号或特定词语不敏感的局限。
     * 3. 方案 C：交叉编码器重排 (Cross-Encoder / Reranker)
     *    - 原理：第一阶段使用向量检索（方案A或B）大范围召回候选集（如前 50 个），第二阶段使用重排模型（如 bge-reranker）将用户查询词与候选集中的 Emoji 文本描述拼接后一起输入神经网络，利用多层交叉自注意力机制强行让每个字符进行对比对齐，算出最终的对齐匹配概率并重排。
     *    - 优缺点：精准度与相关性在所有方案中最高，对细微词义差别捕捉极佳；但二阶段的模型推理会显著增加检索的整体响应延迟，适合对准确度有极致要求的场景。
     */
    @Operation(summary = "Emoji 语义检索接口")
    @PostMapping("/emoji")
    public Mono<List<EmojiResponse>> searchEmoji(@RequestBody EmojiRequest request) {
        return Mono.fromCallable(() -> {
            String systemPrompt = """
                你是 Unicode Emoji 语义检索关键词生成器。
                你的任务：
                    根据用户输入的 UI 功能、按钮名称、业务场景或操作意图，
                    生成最适合从 Unicode Emoji 注解（CLDR annotations）中检索的关键词。
                注意：
                    你的输出会直接用于向量数据库检索 Emoji，
                    目标是提高 Emoji 召回率，而不是解释业务。
                生成规则：
                1. 必须保留用户原始输入中的核心含义。
                2. 输出内容只能包含以下类型：
                    - Emoji 官方注解中可能出现的词
                    - 具体物体名称
                    - 动作名称
                    - 状态描述
                    - 符号名称
                    - 人物或自然元素
                    - 常见同义词
                3. 禁止输出：
                    - 页面名称
                    - 菜单名称
                    - 系统模块名称
                    - 管理后台术语
                    - 数据库字段名称
                    - 产品功能树
                    - 业务流程名称
                例如禁止：
                订单管理
                用户管理
                支付管理
                优惠券设置
                广告管理
                会员中心
                4. 不要把一个业务场景扩展成大量业务模块。
                5. 输出数量：
                    - 简单场景：8~15个关键词
                    - 复杂场景：15~25个关键词
                6. 输出格式：
                    - 只输出关键词
                    - 使用空格分隔
                    - 不换行
                    - 不解释
                    - 不输出 Emoji 字符
                示例：
                    输入：
                        设置
                    输出：
                        设置 配置 齿轮 工具 调整 选项 开关 控制 参数
                    输入：
                        支付付款
                    输出：
                        支付 付款 钱 金钱 现金 钞票 钱包 信用卡 银行卡 卡片 交易 货币 财务
                    输入：
                        写邮件
                    输出：
                        邮件 信件 信封 文字 通信 发送 接收 信箱 文件
                    输入：
                        我的账号
                    输出：
                        用户 个人 账号 账户 头像 人物 身份 资料 会员
                    输入：
                        返回上一页
                    输出：
                        返回 回退 后退 箭头 左方向 方向 标记
                    输入：
                        删除内容
                    输出：
                        删除 清除 移除 垃圾桶 废弃 叉号 禁止
                """;

            String rewrittenQuery = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.prompt())
                    .call()
                    .content();

            log.info("用户 Emoji 查询: '{}', 提示词工程转换后的物理词汇: '{}'", request.prompt(), rewrittenQuery);

            int limit = request.topK() > 0 ? request.topK() : 10;

            // 使用转换后的词语去向量库中检索具有 type == "emoji" 属性 of 文档
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(rewrittenQuery)
                            .topK(limit)
                            .filterExpression("type == 'emoji'")
                            .build()
            );

            // 进行内存双重过滤以保证绝对可靠，并拼装返回结果
            return docs.stream()
                    .filter(doc -> "emoji".equals(doc.getMetadata().get("type")))
                    .map(doc -> new EmojiResponse(
                            (String) doc.getMetadata().get("emoji"),
                            doc.getText(),
                            doc.getScore() != null ? doc.getScore().doubleValue() : 0.0
                    ))
                    .collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Emoji 检索请求 DTO
     */
    public record EmojiRequest(String prompt, int topK) {}

    /**
     * Emoji 检索响应 DTO
     */
    public record EmojiResponse(String emoji, String description, double score) {}

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
    private List<String> extractMarkdownLinkFiles(List<Document> docs) {
        Pattern pattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+\\.md)\\)");
        return docs.stream()
                .map(Document::getText)
                .flatMap(text -> {
                    Matcher matcher = pattern.matcher(text);
                    List<String> files = new ArrayList<>();
                    while (matcher.find()) {
                        // 提取链接的实际指向文件名，用于元数据精准召回
                        files.add(matcher.group(2));
                    }
                    return files.stream();
                })
                .distinct()
                .collect(Collectors.toList());
    }

    private record RagTrace(String chatId, String query, String rewrittenQuery, String bm25Keywords, List<DocTrace> firstPassDocs, List<String> extractedLinks,
                            List<DocTrace> secondPassDocs, int finalDocCount) {
    }

    private record DocTrace(String id, Map<String, Object> metadata, String content, Boolean merged) {
    }

    private void logRagTrace(String chatId, String query, String rewrittenQuery, String bm25Keywords, List<Document> firstDocs, List<String> linkedTexts, List<Document> extraDocs, int finalCount) {
        try {
            List<DocTrace> firstTraces = firstDocs.stream().map(doc -> new DocTrace(doc.getId(), doc.getMetadata(), doc.getText(), null)).toList();

            List<DocTrace> secondTraces = new ArrayList<>();
            if (!extraDocs.isEmpty()) {
                Set<String> firstTexts = firstDocs.stream().map(Document::getText).collect(Collectors.toSet());
                for (Document doc : extraDocs) {
                    boolean isMerged = !firstTexts.contains(doc.getText());
                    secondTraces.add(new DocTrace(doc.getId(), doc.getMetadata(), doc.getText(), isMerged));
                }
            }

            RagTrace trace = new RagTrace(chatId, query, rewrittenQuery, bm25Keywords, firstTraces, linkedTexts, secondTraces, finalCount);
            String json = objectMapper.writeValueAsString(trace);
            log.info("RAG Trace Detail - chatId: {}, trace: {}", chatId, json);
        } catch (Exception e) {
            log.error("Failed to serialize RAG trace detail for chatId: {}", chatId, e);
        }
    }

    private record ProcessResult(String rewrittenQuery, String bm25Keywords) {}

    private ProcessResult parseModelResult(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            return null;
        }
        try {
            String cleanJson = jsonResponse.trim();
            // 去除 ```json 和 ``` 标记
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(cleanJson);
            String rewritten = node.path("rewrittenQuery").asText("").trim();
            String keywords = node.path("bm25Keywords").asText("").trim();
            return new ProcessResult(rewritten, keywords);
        } catch (Exception e) {
            log.warn("【Query 解析】解析模型 JSON 失败，返回原文本: {}, 异常信息: {}", jsonResponse, e.getMessage());
            return null;
        }
    }

}