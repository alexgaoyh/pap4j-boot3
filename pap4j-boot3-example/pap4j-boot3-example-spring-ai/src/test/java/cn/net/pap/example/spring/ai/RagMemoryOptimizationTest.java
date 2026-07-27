package cn.net.pap.example.spring.ai;

import cn.net.pap.example.spring.ai.config.AiProperties;
import cn.net.pap.example.spring.ai.advisor.RagContextAdvisor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
public class RagMemoryOptimizationTest {

    private static final Logger log = LoggerFactory.getLogger(RagMemoryOptimizationTest.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiProperties aiProperties;
    private boolean isLlmServiceAccessible;

    @Autowired
    public RagMemoryOptimizationTest(@Qualifier("customChatClient") ChatClient chatClient,
                                     ChatMemory chatMemory,
                                     AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.aiProperties = aiProperties;
    }

    @BeforeEach
    public void setUp() {
        this.isLlmServiceAccessible = checkLlmAccessibility(aiProperties.mainLlm());
    }

    @Test
    @DisplayName("验证多轮对话中 RAG 上下文与 ChatMemory 的解耦（防止上下文膨胀，保证前缀一致性）")
    public void testMultiTurnMemoryAndPrefixConsistency() {
        assumeTrue(isLlmServiceAccessible, "Skipping test because LLM service is offline or inaccessible.");

        String chatId = "test-multiturn-session";
        // 清理旧对话历史
        chatMemory.clear(chatId);

        // 创建请求捕获器 Advisor 并放置在链的末尾 (order = 200)
        RequestCaptorAdvisor captor = new RequestCaptorAdvisor();
        String systemPrompt = "你是一个资深的 Java 开发架构师。";

        // 第一轮：发送原始提问 1 并携带上下文 1
        String q1 = "如何使用高精度除法？";
        String context1 = "【知识库文档1】：DIV2高精度除法使用 BigDecimal.divide(..., RoundingMode.HALF_UP) 实现。";

        log.info("第一轮对话 - 用户提问: {}, 上下文: {}", q1, context1);
        String a1 = chatClient.prompt()
                .system(systemPrompt)
                .user(q1)
                .advisors(captor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)
                                .param(RagContextAdvisor.RAG_CONTEXT_KEY, context1))
                .call()
                .content();
        log.info("第一轮回复: {}", a1);

        // 获取第一轮发给 LLM 的最终请求
        ChatClientRequest request1 = captor.getCapturedRequest();
        assertNotNull(request1, "第一轮请求未能成功捕获");
        List<Message> messages1 = request1.prompt().getInstructions();
        
        // 第一轮发送的消息应该是：System Message + User Message (含 context1)
        assertEquals(2, messages1.size(), "第一轮消息条数不符");
        Message sysMsg1 = messages1.get(0);
        Message userMsg1 = messages1.get(1);
        assertEquals(MessageType.SYSTEM, sysMsg1.getMessageType());
        assertEquals(MessageType.USER, userMsg1.getMessageType());
        assertTrue(userMsg1.getText().contains(context1), "第一轮发送的 User 消息应包含 Context 1");

        // 第二轮：发送原始提问 2 并携带上下文 2
        captor.clear(); // 清空捕获器
        String q2 = "那默认保留几位小数？";
        String context2 = "【知识库文档2】：DIV2 函数默认保留 10 位小数，可以通过 scale 参数自定义。";

        log.info("第二轮对话 - 用户提问: {}, 上下文: {}", q2, context2);
        String a2 = chatClient.prompt()
                .system(systemPrompt)
                .user(q2)
                .advisors(captor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId)
                                .param(RagContextAdvisor.RAG_CONTEXT_KEY, context2))
                .call()
                .content();
        log.info("第二轮回复: {}", a2);

        // 获取第二轮发给 LLM 的最终请求
        ChatClientRequest request2 = captor.getCapturedRequest();
        assertNotNull(request2, "第二轮请求未能成功捕获");
        List<Message> messages2 = request2.prompt().getInstructions();

        // 第二轮发送的消息应该是：System Message + User Message (q1) + Assistant Message (a1) + User Message (含 context2)
        assertEquals(4, messages2.size(), "第二轮发送给模型的最终 Prompt 消息条数不符");

        Message sysMsg2 = messages2.get(0);
        Message histUserMsg2 = messages2.get(1);
        Message histAssMsg2 = messages2.get(2);
        Message currUserMsg2 = messages2.get(3);

        assertEquals(MessageType.SYSTEM, sysMsg2.getMessageType());
        assertEquals(MessageType.USER, histUserMsg2.getMessageType());
        assertEquals(MessageType.ASSISTANT, histAssMsg2.getMessageType());
        assertEquals(MessageType.USER, currUserMsg2.getMessageType());

        // 验证前缀一致性（100% 静态部分）
        // 1. 验证 System Prompt 完全一致
        assertEquals(sysMsg1.getText(), sysMsg2.getText(), "两轮 System Prompt 不一致，无法命中 Prompt Caching！");

        // 2. 关键断言：第二轮发给模型的历史 User 消息必须是原始提问，且绝不能包含 context1 里面的任何文本！
        assertEquals(q1, histUserMsg2.getText(), "第二轮发送的历史 User 消息收到了 RAG 上下文 1 的污染！");
        assertFalse(histUserMsg2.getText().contains("DIV2"), "前缀历史消息受到 RAG 污染");

        // 3. 验证历史 Assistant 消息与第一轮得到的回复一致
        assertEquals(a1, histAssMsg2.getText(), "第二轮发送的历史 Assistant 消息不一致");

        // 4. 验证当前轮次的 User 消息被注入了 context2
        assertTrue(currUserMsg2.getText().contains(context2), "第二轮发送的当前 User 消息应包含 Context 2");

        log.info("【前缀一致性校验成功】:");
        log.info(" -> 静态前缀 [System] 保持一致");
        log.info(" -> 静态前缀 [User(1)] 是干净的原始提问: '{}'", histUserMsg2.getText());
        log.info(" -> 静态前缀 [Assistant(1)] 是干净的回复");
        log.info(" -> 动态上下文仅出现在最后一个 User 消息中。");
        log.info("多轮会话 Prompt Caching 前缀一致性完美保留！");
    }

    private boolean checkLlmAccessibility(AiProperties.ModelConfig modelConfig) {
        if (modelConfig == null) {
            return false;
        }

        String provider = modelConfig.provider();
        if (provider == null || provider.isBlank()) {
            return false;
        }

        if ("openai".equalsIgnoreCase(provider)) {
            String apiKey = modelConfig.apiKey();
            if (apiKey == null || apiKey.isBlank() || apiKey.contains("dummy")) {
                log.warn("OpenAI API key 为空或为哑值，跳过连通性测试。");
                return false;
            }
        }

        String baseUrl = modelConfig.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }

        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    port = 443;
                } else {
                    port = 80;
                }
            }
            if (host == null || host.isBlank()) {
                return false;
            }

            log.info("正在对大模型端点执行轻量级 TCP 探活: {}:{}", host, port);
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(host, port), 1000);
                log.info("TCP 探活成功: {}:{}", host, port);
                return true;
            }
        } catch (Exception e) {
            log.warn("TCP 探活失败 ({})，将跳过本组评测单元测试。错误: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 捕获并记录最终发给 LLM 的 ChatClientRequest 的自定义 Advisor
     */
    public static class RequestCaptorAdvisor implements CallAdvisor, StreamAdvisor {
        private ChatClientRequest capturedRequest;

        public ChatClientRequest getCapturedRequest() {
            return capturedRequest;
        }

        public void clear() {
            this.capturedRequest = null;
        }

        @Override
        public String getName() {
            return "RequestCaptorAdvisor";
        }

        @Override
        public int getOrder() {
            // 在 RagContextAdvisor (order = 100) 之后执行，以捕获包含 RAG 上下文的最完整请求
            return 200;
        }

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            this.capturedRequest = request;
            return chain.nextCall(request);
        }

        @Override
        public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
            this.capturedRequest = request;
            return chain.nextStream(request);
        }
    }
}
