package cn.net.pap.example.spring.ai;

import cn.net.pap.example.spring.ai.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * <p>
 * 多模态文档解析测试类，用于验证 Vision-Language Model (VLM) 对文档图像进行结构化解析的能力。
 * </p>
 * <p>
 * 本测试主要读取类路径下的文档图片（例如 {@code test_doc_page.jpg}），并将其输入给支持多模态的
 * 大语言模型进行转译，验证其输出干净的 Markdown 格式及对表格、标题层级的解析效果。
 * </p>
 * <p>
 * <b>重试机制验证说明：</b><br>
 * 本类内嵌了一个 {@link MockRetryServer} 模拟服务器，用于重现和验证大模型请求的失败重试逻辑：
 * </p>
 * <ol>
 *   <li><b>启动模拟服务：</b>在 IDE 中直接运行内嵌静态类 <code>MockRetryServer</code> 的 <code>main</code> 方法（会监听本地 9999 端口并返回 500 错误）。</li>
 *   <li><b>修改配置指向：</b>在 <code>application.yml</code> 中将大模型的 <code>base-url</code> 临时修改为 <code>http://localhost:9999/</code>。</li>
 *   <li><b>调整重试次数：</b>在 {@code AiAssistantConfig#createNoRetryTemplate()} 中将最大重试次数修改为 2 或 3 次。</li>
 *   <li><b>运行本测试：</b>运行 {@link #testMultimodalDocumentParsing()}。此时可在控制台看到 Retry 监听器打印的重试日志，并在 MockRetryServer 控制台确认重试请求次数。</li>
 * </ol>
 *
 */
@SpringBootTest
public class MultimodalDocumentParsingTest {

    private static final Logger log = LoggerFactory.getLogger(MultimodalDocumentParsingTest.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiProperties aiProperties;
    private boolean isLlmServiceAccessible;

    @Autowired
    public MultimodalDocumentParsingTest(@Qualifier("customChatClient") ChatClient chatClient,
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

    /**
     * <p>
     * 验证多模态 VLM 模型解析文档页面图片并提取为 Markdown 格式。
     * using matpool Qwen3-VL-Plus model
     * send test_doc_page.jpg get test_doc_page.md
     * </p>
     */
    @Test
    @DisplayName("验证多模态 VLM 模型解析文档页面图片并提取为 Markdown 格式")
    public void testMultimodalDocumentParsing() {
        assumeTrue(isLlmServiceAccessible, "Skipping test because LLM service is offline or inaccessible.");

        // 1. 读取测试图片资源
        Resource imageResource = new ClassPathResource("test_doc_page.jpg");
        assertTrue(imageResource.exists(), "测试图片 test_doc_page.jpg 不存在于类路径中！");

        String chatId = "test-multimodal-document-session";
        // 清理旧对话历史，防止历史对话样式偏见（Memory Bias）导致模型重复旧格式输出
        chatMemory.clear(chatId);

        // 2. 构造解析 Prompt，要求大模型输出标准的 Markdown
        String prompt = """
                你是一个专门的文档解析助手。
                任务：分析并提取输入图片中的文本和表格，将其转换为规范的 Markdown 格式。
                要求：
                1. 【层级标题】：识别页面中所有视觉上的标题和章节大纲（包括带有数字编号的级次标题、大字号文本、明显加粗的小标题等）。根据它们在视觉上的字号大小、字重粗细、排版大纲或逻辑层级结构，合理且规范地将其映射转换为 Markdown 的对应级别标题（如主大纲/一级标题使用 `#` 或 `##`，二级标题使用 `###`，三级标题使用 `####`）。
                2. 【内容还原】：忠实还原图片中所有的文本段落、标题和表格内容，不得进行任何形式的总结、省略、改写或词汇替换。
                3. 【表格转换】：所有表格必须识别并转换为标准的 Markdown 表格形式（| 栏 1 | 栏 2 |）。对于包含跨行合并或跨列合并的复杂单元格表格，允许并推荐使用标准的 HTML 表格标签（如 <table>, <tr>, <td>, rowspan, colspan）进行表达。
                4. 【格式限制】：只返回干净的 Markdown 文本，不要用 ```markdown 进行包裹，也不要输出任何解释性前言、后记或说明性文字。
                """;

        log.info("【VLM 解析测试】开始发送多模态文档解析请求，图片文件: {}", imageResource.getFilename());
        long startTime = System.currentTimeMillis();

        // 3. 调用 ChatClient 执行多模态推理
        String markdownResult = chatClient.prompt()
                .user(u -> u.text(prompt)
                            .media(MimeTypeUtils.IMAGE_JPEG, imageResource))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();

        long costTime = System.currentTimeMillis() - startTime;
        log.info("【VLM 解析测试】解析完成，耗时: {} ms", costTime);

        // 4. 验证并输出结果
        assertNotNull(markdownResult, "模型返回的 Markdown 内容不能为 null");
        log.info("\n==================== 模型返回的 Markdown ====================\n" 
                + markdownResult 
                + "\n============================================================");
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
            log.warn("TCP 探活失败 ({})，将跳过本组多模态解析单元测试。错误: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * 本地模拟服务器，用作单元测试的错误终点，重现大模型请求失败下的重试逻辑。
     */
    public static class MockRetryServer {
        public static void main(String[] args) throws Exception {
            com.sun.net.httpserver.HttpServer server = 
                    com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(9999), 0);
            java.util.concurrent.atomic.AtomicInteger hitCounter = new java.util.concurrent.atomic.AtomicInteger(0);

            server.createContext("/v1/chat/completions", new com.sun.net.httpserver.HttpHandler() {
                @Override
                public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
                    // 读取请求体，防止连接被强行关闭报错
                    try (java.io.InputStream is = exchange.getRequestBody()) {
                        byte[] buffer = new byte[4096];
                        while (is.read(buffer) != -1) {
                        }
                    }

                    int hits = hitCounter.incrementAndGet();
                    System.out.println("【Mock 服务端】收到第 " + hits + " 次请求尝试！");

                    String response = "{\"error\": \"Mock 500 Internal Server Error\"}";
                    byte[] bytes = response.getBytes();
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, bytes.length);

                    try (java.io.OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    exchange.close();
                }
            });

            server.start();
            System.out.println("🚀 Mock 500 模拟服务端已成功在 9999 端口启动...");
        }
    }
}
