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
 *
 * <h3>💡 一、 大模型 Agent 经验真理总结</h3>
 * <ul>
 *   <li><b>注重过程，注重执行轨迹，注重thinking措词：</b>不要只盯着结果。记录耗时、实际模型及 Token 消耗。</li>
 *   <li><b>能代码执行的就走原链路，不要都丢给大模型：</b>大模型不擅长复杂的格式微调，建议能用 Java 后置清洗解决的（如反引号包裹符剥离、表格跨行展开等）就用 Java 处理，死守核心以对抗大模型的不确定性。</li>
 *   <li><b>PASS^N 远远优于 PASS at N：</b>建立静态规则校验，当模型偶发性输出格式损坏时，应自动结合 Chat Memory 追加第二轮纠错提问，在 $N$ 次尝试内收敛并判定 PASS。</li>
 *   <li><b>死磕单位时间内的有效迭代次数：</b>睁眼看世界，终结肉眼比对。应建立本地黄金样本评测集，用编辑距离（Levenshtein）自动算出与 Expected 答案的相似度百分比。</li>
 * </ul>
 *
 * <h3>🤝 二、 Agent Handoff 交接与未解决问题（Pending Tasks）</h3>
 * <ol>
 *   <li>
 *     <b>【已选型】客户端后置处理技术路线确定：</b>
 *     对合并单元格的平铺与填充逻辑，已确定在 Java 客户端采用 <b>Jsoup + 虚拟坐标网格算法</b> 进行确定性工程清洗。
 *     对比 Python 栈的 Pandas 方案（成熟但有跨语言调用/进程开销），原生的 Java 算法可以做到零运行时依赖、纯内存计算且微秒级延迟，最符合 Spring Boot 项目的自包含架构。
 *   </li>
 *   <li>
 *     <b>【未决问题】大模型与工程代码职能剥离：</b>当前 Prompt 长达 170 行，混合了跨行跨列单元格展开及空白填充等逻辑，极易产生规则互斥。
 *     <br><i>优化路径：</i>改为让大模型仅负责结构化 HTML Table（保留 colspan/rowspan）的提取，接着由 Java 层独立执行矩阵平铺与向上/前向数据填充，可使 Prompt 瘦身 80%。
 *   </li>
 *   <li>
 *     <b>【未决问题】基于 Chat Memory 的自纠错（PASS^N）落地：</b>当前测试仅为单次提取（PASS at 1）。
 *     <br><i>优化路径：</i>编写 `validateMarkdownStructure` 校验器方法检测表格各行列数是否齐平，若校验失败，则自动启动第 2 轮会话要求大模型重新修正。
 *   </li>
 *   <li>
 *     <b>【未决问题】黄金测试集与自动化评测度量：</b>，但尚未针对大量 JPG 提取任务建立自动化评测机制。
 *     <br><i>优化路径：</i>收集 10 张代表性 JPG 并标注 Ground Truth，使用 JUnit 参数化测试一键跑批统计相似度平均分。
 *   </li>
 * </ol>
 *
 * <h3>📚 三、 业界关于表格后置处理的共识实践</h3>
 * <ul>
 *   <li><b>大模型与解析器提取标准 HTML &lt;table&gt;：</b>大模型（VLM）和成熟 OCR 在空间网格计算（拼接 Markdown 竖线）上极易发生空间幻觉。业界的共识是第一阶段只做结构化提取输出 HTML <code>&lt;table&gt;</code>（完美保留 colspan 与 rowspan 结构，成功率接近 100%）。</li>
 *   <li><b>客户端双指针虚拟矩阵填充算法 (Grid Normalization)：</b>在客户端中使用 Jsoup (Java) 或 BeautifulSoup (Python) 开辟二维坐标网格，计算 Span 并将父单元格内容横向/纵向复制平铺，从而将非确定性黑盒转化为确定性工程逻辑。</li>
 *   <li><b>Pandas 方案（Python 生态最佳）：</b>在 Python 生态中，直接通过 <code>pd.read_html()</code> 配合 <code>ffill(axis=0)</code> 可三行代码搞定，但跨语言调用到 Java 时会有额外性能和部署开销。</li>
 * </ul>
 *
 * <h3>🧪 四、 表格提取表示方案实验总结（V2 → Canvas → 基元）——均未达预期，踩坑记录</h3>
 *
 * <p>
 * 为克服本测试"单次提取（PASS at 1）+ 长 Prompt"的不足，按【两阶段 + PASS^N + 转换器】的思路
 * 先后实现并验证了三种表格表示方案，均未完全满足要求。现将结论与踩坑沉淀于此，避免后续重复试错。
 * </p>
 *
 * <h4>📌 1. 实验背景与目标</h4>
 * <ul>
 *   <li><b>目标</b>：两阶段处理（Stage 1 版面分析返回表格 bbox → Stage 2 聚焦提取）；
 *       PASS^N 自纠错（最多 3 轮）；由 Java 后置确定性清洗替代模型长 Prompt 微调。</li>
 *   <li><b>目标子模块</b>：<code>pap4j-boot3-example/pap4j-boot3-example-spring-ai</code>（测试类）。</li>
 * </ul>
 *
 * <h4>🟢 2. 已完成的工作与实测结果 (Completed Work & Results)</h4>
 * <ol>
 *   <li>
 *     <b>实验 1：两阶段 + PASS^N + HTML rowspan</b>
 *     <ul>
 *       <li><b>方案</b>：Stage 1 版面分析 → 模型输出保留 colspan/rowspan 的 HTML  静态校验 → 失败追加上下文重试（PASS^N ≤3）→ 转换器转 Markdown。</li>
 *       <li><b>结果</b>：链路机制跑通、测试通过，但模型 <b>rowspan/colspan 记账不可靠</b>：
 *         <ul>
 *           <li>偶发 rowspan 越界（某列值中途变化时仍被一个 rowspan 罩住）会把网格撑宽一列，导致其下所有行
 *               级联错位（集中在表格底部）；此时 PASS^N 3 轮纠错<b>不收敛</b>——校验报错定位到表头行而非肇事行。</li>
 *           <li>校验通过也不代表语义正确：底部行分类标签可能留空或错归属（结构性校验管不住语义）。</li>
 *           <li>改用"扁平网格"Prompt（禁 colspan/rowspan、标签逐行重复）后，模型<b>无视指令仍输出 rowspan</b>，
 *               说明提示词劝不动 Qwen3-VL 的 HTML 输出习惯。</li>
 *         </ul>
 *       </li>
 *     </ul>
 *   </li>
 *   <li>
 *     <b>实验 2：单元格坐标矩形</b>
 *     <ul>
 *       <li><b>方案</b>：模型输出每个单元格归一化坐标矩形，Java 按 y/x 聚类还原网格。</li>
 *       <li><b>结果</b>：<b>两次均在约 60s 处被 matpool 网关 504 掐断</b>；即使精简为"最小原子格 + 前向填充"仍超时。
 *           → 该网关存在约 60s 生成硬超时，坐标密集输出（每格 6 个坐标）不可行。</li>
 *     </ul>
 *   </li>
 *   <li>
 *     <b>实验 3：线段 + 文字基元</b>
 *     <ul>
 *       <li><b>方案</b>：模型输出表格边框线段 + 文字块坐标（PDF/报纸式矢量版式），Java 从线段几何重建网格。</li>
 *       <li><b>结果</b>：同样在约 60s 处 504 超时——本质仍是坐标密集输出超出网关生成上限。</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h4>⚠️ 3. 关键结论与阻塞点 (Notes & Blockers)</h4>
 * <ul>
 *   <li><b>网关硬约束</b>：matpool Qwen3-VL 下<b>只有纯文本输出能 fit 60s</b>（HTML 表格约 30s 完成）；
 *       任何坐标表示（canvas 单元格 / 线段+文字基元）均 504 超时。</li>
 *   <li><b>布局与速度不可兼得</b>：纯文本中唯一保布局的表示是 HTML rowspan，但模型记账不可靠；
 *       扁平化（JSON 数组）保速度不保布局；坐标保布局但超时——三者无法同时满足。</li>
 *   <li><b>底部错位是模型感知缺陷</b>：最后几行分类标签错位在三种表示下均出现，且与表示方式无关；
 *       bbox 截断假说已证伪（Stage 1 的 bbox [109, 158, 887, 593] ≈ [0.109, 0.158, 0.887, 0.593] 准确覆盖全表）。</li>
 *   <li><b>Stage 1 bbox 未按 [0,1] 归一化</b>：实测输出为 ~100 量级（[109, 158, 887, 593]），提示词合规性欠佳。</li>
 * </ul>
 *
 * <h4>🔮 4. 建议的后续方向（未决 Pending Tasks）</h4>
 * <ol>
 *   <li><b>JSON 单元格带显式行列索引（r, c）</b>：纯文本（小整数 + 文字，fit 60s）、布局显式（合并靠逐格重复）、
 *       无 rowspan 语法（模型退无可退）——最契合"快 + 保布局 + 不错乱"三重要求。</li>
 *   <li><b>表格垂直分块提取</b>：按行切块、每块独立提取，改善底部行注意力。</li>
 *   <li><b>黄金样本 + 编辑距离评测</b>：建立本地黄金样本集，用 Levenshtein 相似度作为质量门（见本 Javadoc 第二节规划）。</li>
 * </ol>
 *
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
        log.info("\n==================== 模型返回的 Markdown ====================\n{}\n============================================================", markdownResult);
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
            log.warn("TCP 探活失败 ({})，将跳过本组多模态解析单元测试。", baseUrl, e);
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
                    log.info("【Mock 服务端】收到第 {} 次请求尝试！", hits);

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
            log.info("🚀 Mock 500 模拟服务端已成功在 9999 端口启动...");
        }
    }
}
