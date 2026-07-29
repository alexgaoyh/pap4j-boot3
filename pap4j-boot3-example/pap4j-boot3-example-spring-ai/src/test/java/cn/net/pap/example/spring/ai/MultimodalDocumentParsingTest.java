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
     * temperature: 0.5
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
            你是一个专业的文档图像解析助手。
    
            任务：
            分析输入图片，完整提取图片中的所有文字内容、标题、段落、列表以及表格结构，并转换为规范 Markdown 文档。
    
            核心原则：
            1. 必须忠实还原图片内容。
            2. 不允许总结、推理、改写、遗漏或添加图片中不存在的信息。
            3. 必须保持原始阅读顺序。
            4. 输出结果必须只包含 Markdown 文本，不输出任何解释、分析过程、提示语或代码块。
    
            一、标题识别规则：
    
            根据图片中的视觉层级识别标题：
    
            - 大字号文字；
            - 加粗文字；
            - 独立居中的章节名称；
            - 编号形式的章节标题。
    
            转换规则：
    
            一级标题：
            使用 #。
    
            二级标题：
            使用 ##。
    
            三级标题：
            使用 ###。
    
            注意：
            - 普通正文、表格内容、普通加粗字段不得识别为标题。
            - 不允许因为字体较大就错误生成标题，必须结合上下文判断。
    
    
            二、文本内容还原规则：
    
            1. 必须完整保留图片中的所有文字。
            2. 保持原文顺序。
            3. 保留数字、编号、日期、单位、符号。
            4. 不得合并段落。
            5. 不得将连续文本重新组织成摘要。
    
    
            三、表格解析规则（最高优先级）：
    
            当发现图片中存在表格时，必须按照以下步骤处理：
    
            第一步：
            分析真实表格结构。
    
            必须识别：
    
            - 总行数；
            - 总列数；
            - 表头结构；
            - 普通单元格；
            - 横向合并单元格（colspan）；
            - 纵向合并单元格（rowspan）；
            - 多级表头；
            - 分类层级关系。
    
    
            第二步：
            展开所有合并单元格。
    
            Markdown 不支持 rowspan 和 colspan，因此必须进行结构展开。
    
            展开规则：
    
            1. 横向合并单元格：
            将该单元格内容复制到该单元格覆盖的所有列。
    
            2. 纵向合并单元格：
            将该单元格内容复制到该单元格覆盖的所有行。
    
            3. 同时存在横向和纵向合并：
            将内容复制填充到所有对应的数据位置。
    
            4. 禁止使用以下方式表示合并：
    
            - 留空；
            - 使用空字符串；
            - 使用 "-"；
            - 使用 "同上"；
            - 使用省略号。
    
    
            第三步：
            强制填充所有空白单元格。
    
            最终 Markdown 表格中：
    
            任何数据行中的任何单元格都禁止为空。
    
            如果图片中某个单元格因为视觉排版原因为空：
    
            必须根据以下规则补全：
    
            1. 如果属于 rowspan 合并：
               填充原合并单元格的内容。
    
            2. 如果属于分类列、层级列、父级类别列：
               填充所属分类名称。
    
            3. 如果属于编号、名称、属性等上下文关联字段：
               根据所在行和上下文恢复完整内容。
    
            4. 每一行必须成为独立完整的数据记录。
    
            即：
    
            用户查看 Markdown 表格中的任意一行时，
            不需要结合上一行内容，
            也能够完整理解该行所属分类、层级和业务含义。
    
    
            特别要求：
    
            最左侧列、分类列、层级列、主键列：
    
            即使原图片中由于合并显示为空白，
            也必须在 Markdown 中重复填写。
    
            绝对禁止输出：
    
            | 分类 | 内容 |
            |      | 内容 |
    
            必须输出：
    
            | 分类 | 内容 |
            | 分类名称 | 内容 |
    
    
            四、Markdown 表格输出规则：
    
            所有识别出的表格必须转换为标准 Markdown 表格。
    
            必须满足：
    
            1. 第一行必须为表头。
            2. 第二行为 Markdown 分隔线。
            3. 每一行列数量必须完全一致。
            4. 所有数据行列数量必须与表头一致。
            5. 不允许缺少任何列。
            6. 不允许增加未存在的列。
            7. 不允许出现空白数据单元格。
            8. 不允许使用合并符号表示结构。
    
    
            如果原始图片存在复杂表头：
    
            必须先还原真实业务字段关系，
            再生成最终 Markdown 表格。
    
            表头必须能够解释所有数据列。
    
    
            五、最终输出要求：
    
            1. 只输出 Markdown。
            2. 不输出说明文字。
            3. 不输出处理过程。
            4. 不输出 JSON。
            5. 不输出代码块。
            6. 所有表格必须已经完成合并单元格展开和空白填充。
            7. 输出结果必须可以直接保存为 Markdown 文件使用。
    
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
