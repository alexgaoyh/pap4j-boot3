package cn.net.pap.example.spring.ai;

import cn.net.pap.example.spring.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * <p>
 * 两阶段多模态表格解析单元测试类，将原 {@code TwoStageMultimodalDocumentParsingTest} 的前置功能并入本类，
 * 并以「行序二维数组 (array-of-arrays)」契约完成表格内容解析：
 * <ul>
 *   <li><b>Stage 1（前置，见 {@link #testStage1TableLocationDetection()}）</b>：整页版面分析，
 *       返回表格的 {@code <table_location bbox="[xmin,ymin,xmax,ymax]"/>} 坐标占位符（不解析表格内部文字）。</li>
 *   <li><b>Stage 2（见 {@link #testTwoDimensionalTableGridJson()}）</b>：对纯表格图输出严格二维 JSON
 *       {@code {"rows":[["表头1","表头2"],["内容1","内容2"]]}}。</li>
 * </ul>
 * 本类脱胎于 {@link MultimodalDocumentParsingTest}，针对其踩坑记录给出「模型只读字、结构由 Java 兜底」的替代契约。
 * </p>
 *
 * <h3>一、原 TwoStage 类的不可用问题（Stage 2 已并入本类改造）</h3>
 * <ul>
 *   <li><b>Stage 2 不可用</b>：原 {@code TwoStageMultimodalDocumentParsingTest} 的 Stage 2 要求模型逐格输出
 *       「文字 + bbox 坐标」（坐标密集输出），在 matpool Qwen3-VL 网关约 60s 生成硬超时下稳定 504 被掐断，无法产出结果。</li>
 *   <li><b>Stage 1 可用</b>：其 Stage 1（整页版面分析 → 表格 bbox 坐标）正常工作，故保留为本类的前置测试方法
 *       {@link #testStage1TableLocationDetection()}，返回携带表格坐标的结果。</li>
 *   <li><b>表格内容解析改用 array-of-arrays 契约</b>：不再让模型输出坐标，改由 Stage 2 输出纯文字二维数组，既避开超时又保住结构。</li>
 * </ul>
 *
 * <h3>二、背景与踩坑结论（源自 {@link MultimodalDocumentParsingTest} 及本类实测）</h3>
 * <ul>
 *   <li><b>坐标密集输出不可行</b>：matpool Qwen3-VL 网关存在约 60s 生成硬超时；逐格输出 bbox 坐标（每格 4~6 个数字）稳定 504 被掐断，纯文本输出才能 fit 60s。</li>
 *   <li><b>HTML rowspan 记账不可靠</b>：模型对 colspan/rowspan 的计数常错且重试不收敛，长表底部还会出现行错位。</li>
 *   <li><b>图像清晰度 / DPI 不影响识别质量</b>：对比 q0.75 / q1.0 JPEG 及 Java ImageIO / ImageMagick 两种编码的裁切子图，识别结果基本一致，排除了「裁切变糊」假说。</li>
 *   <li><b>模型感知层面的固有短板</b>：小字号上标（如 m³ 的 ³）会被模型输出成 ?；分级子标签行（父项下缩进）容易行漂移；
 *       且 PASS^N 纠错轮可能用「删除内容」来满足列数约束（实测 21 行被压到 19 行）——结构校验通过但语义丢失。</li>
 * </ul>
 *
 * <h3>三、array-of-arrays 契约设计（Stage 2）</h3>
 * <ul>
 *   <li><b>输入</b>：{@code src/test/resources/cropped_table_1.jpg} —— 由 ImageMagick 按 Stage 1 bbox
 *       {@code [109,158,887,594]} + 1% 宽高冗余裁切、并保留原图 DPI 216x216 的纯表格图片。</li>
 *   <li><b>输出</b>：严格二维 JSON —— {@code {"rows":[["表头1","表头2"],["内容1","内容2"]]}}。</li>
 *   <li><b>契约规则</b>：每行单元格数量完全相等；合并单元格用重复内容表达；空单元格输出 {@code ""}；不输出任何几何坐标。</li>
 * </ul>
 *
 * <h3>四、还原度与执行时长的统一思路</h3>
 * <ul>
 *   <li><b>模型只承担「按读序读字」</b>：不数坐标、不记账 rowspan，输出 token 量级比坐标方案低一个数量级，执行时长从超时降到 ~15s。</li>
 *   <li><b>结构保真由确定性规则兜底</b>：行宽相等校验 + PASS^N 重试（见 {@link #testTwoDimensionalTableGridJson()}），错误可被廉价发现并重试收敛。</li>
 *   <li><b>警惕纠错轮「删内容换列数」</b>：二次校验需比对行数防止静默丢数据（详见第二节实测结论）。</li>
 * </ul>
 */
@SpringBootTest
public class MultimodalTwoStageTableParsingTest {

    private static final Logger log = LoggerFactory.getLogger(MultimodalTwoStageTableParsingTest.class);

    /** 最大尝试次数（PASS^N：二维校验失败则带上错误信息重试，最多 N 轮收敛） */
    private static final int MAX_ATTEMPTS = 3;

    /** 二维表格契约 Prompt：模型只输出二维数组，零几何、零合并语法 */
    private static final String PROMPT_GRID = """
        你是一个专业的表格结构化识别助手。

        任务：
        分析输入图片中的表格，识别所有文字内容，并按照表头定义的逻辑字段输出二维 JSON 数据。

        注意：
        输出结果不是简单复制图片中的视觉单元格布局，
        而是根据表格结构、表头含义和上下文关系恢复真实的逻辑表格。

        严格规则：

        1. 输出格式：

           只输出一个 JSON 对象：

           {
             "rows": [
               ["表头1", "表头2", "表头3"],
               ["内容1", "内容2", "内容3"]
             ]
           }

           rows 包含：
           - 表头行；
           - 所有数据行。

        2. 二维结构要求：

           - rows 必须是严格二维数组。
           - 每一行的单元格数量必须完全一致。
           - 不允许输出 rowspan、colspan、bbox 等额外结构信息。
           - 不允许输出 Markdown 表格。

        3. 表头驱动的逻辑字段恢复：

           - 首先识别表格的表头区域。
           - 表头决定每一列的业务含义。
           - 所有数据必须按照表头对应关系进行归属。
           - 不要简单按照视觉上的空白区域、换行位置或文字位置划分字段。
           - 如果视觉布局与逻辑字段不一致，应优先恢复逻辑字段。

        4. 合并单元格与逻辑字段恢复：

           - 表格中的合并单元格不要输出 rowspan、colspan。
           - 不要简单复制视觉单元格结构。
           - 对于由于合并单元格、缩进、换行导致的多个连续文字区域，
             需要判断这些文字是否属于同一个逻辑字段。

           - 如果多个视觉区域属于同一个逻辑字段：
             * 必须保留所有文字；
             * 必须将相关文字组合到同一个字段中；
             * 不允许只保留主要标题而丢弃后续文字；
             * 不允许将属于同一字段的补充内容识别为新的同级字段。

           - 以下类型的文字，即使显示在合并区域的下一行，也不能直接丢弃：
             * 条件描述；
             * 规格描述；
             * 等级描述；
             * 分类描述；
             * 参数范围；
             * 适用范围。

           示例：

           图片视觉：

           字段A
              内容A
              子内容1
              子内容2


           正确逻辑：

           [
             ["字段A", "内容A 子内容1"],
             ["字段A", "内容A 子内容2"]
           ]


           错误逻辑：

           [
             ["字段A", "内容A"],
             ["字段A", ""],
             ["字段A", ""]
           ]


           说明：
           - 子内容1、子内容2属于内容A的展开信息；
           - 不应被删除；
           - 不应因为视觉换行或缩进而认为它们不是表格内容。

        5. 数据完整性要求：

           - 表格区域内出现的所有文字必须在输出结果中保留。
           - 不允许删除、总结、归纳、改写任何内容。
           - 不允许因为文字位于缩进区域、换行区域、合并区域而忽略。
           - 不允许自行判断某些文字是备注、说明而删除。
           - 如果一个逻辑字段包含多个连续文字片段，必须完整组合保存。

        6. 行识别要求：

           - 每一个真实的数据行都必须输出。
           - 判断数据行不能只依据某一列是否为空。
           - 如果一行存在文字、数值、单位、条件或其他表格内容，
             即使部分列为空，也需要保留该行。
           - 不允许为了减少行数而合并多个真实数据行。

        7. 空单元格处理：

           - 只有图片中确实为空，并且无法根据表格结构推断内容时，才输出 ""。
           - 不要因为视觉上的合并、换行或布局空白而输出空字符串。
           - 对于可以根据上下文恢复的信息，应优先恢复。

        8. 文本保持：

           - 所有文字必须逐字保留。
           - 保留：
             * 数字；
             * 单位；
             * 标点；
             * 大小写；
             * 特殊符号。

           示例：
             m³、H₂O、≤、≥、±、%、℃ 等必须保持。

        9. 输出限制：

           - 只输出 JSON。
           - 不输出任何解释。
           - 不输出分析过程。
           - 不输出代码块。

        """;

    /** Stage 1 Prompt：整页版面分析，只定位表格 bbox 坐标占位符，不解析表格内部文字 */
    private static final String PROMPT_STAGE1 = """
        你是一个专业的文档图像版面分析与结构化解析助手。

        任务：
        分析输入图片，完整提取其中的所有标题、正文段落、列表文本，并识别图片中所有表格的位置坐标。

        核心规则：
        1. 正文与标题还原：
           - 忠实还原所有文本内容，不得遗漏、总结、缩减或改写任何非表格文字。
           - 保持原始阅读顺序与段落结构。
           - 按照视觉层级保留 Markdown 标题（一级标题 # ，二级标题 ## ，三级标题 ### ）。

        2. 表格定位（不直接解析表格内部文字）：
           - 当遇到表格时，不要提取表格内部具体单元格文字。
           - 在表格所在位置输出统一格式的占位标签：<table_location id="表格序号" bbox="[xmin, ymin, xmax, ymax]"/>
           - 格式示例：<table_location id="1" bbox="[109, 158, 887, 594]"/>
           - 坐标说明：归一化到 [0, 1000] 的整数区间，顺序必须为 [左, 上, 右, 下]，注意包含左右中括号 []。
           - 边界保护：识别范围必须完全覆盖表格四周的所有外边框线条与表头，允许适度扩展，切勿压线或裁剪掉边框线。

        3. 最终输出要求：
           - 只输出包含上述占位标签的规范 Markdown 文本。
           - 不输出任何解释、分析过程、提示语或代码块包裹。
        """;

    /** 表格占位标签的容错正则：容忍引号差异、缺失右中括号等，提取 id 与 bbox 坐标 */
    private static final Pattern TABLE_LOCATION_PATTERN = Pattern.compile(
            "<table_location\\s+id=[\"']?([^\"'\\s>]+)[\"']?\\s+bbox=[\"']?\\[?([0-9\\s,]+)\\]?[\"']?\\s*/?>");

    /** Stage 1 定位到的表格坐标（id + 归一化 bbox） */
    private record TableLocation(String id, String bbox) {
        @Override
        public String toString() {
            return "id=" + id + ", bbox=" + bbox;
        }
    }

    /** 二维表格解析错误类型枚举 */
    public enum TableErrorType {
        JSON_SYNTAX("JSON 语法错误", "检查括号匹配（确保每对 [ ] 与 { } 闭合）、引号转义与多余逗号。"),
        ROW_COL_MISMATCH("行列数不一致", "核对每一行的单元格数量。所有行的列数必须与首行列数完全相同！合并单元格必须在覆盖的所有格子中重复填入文字，切勿漏列或擅自删减列数。"),
        STRUCTURE_ERROR("数据结构错误", "确保输出格式为 {\"rows\": [[...], [...]]} 的严格二维字符串数组，单元格内必须是纯文本或数字，不能嵌套对象或数组。");

        private final String title;
        private final String guidance;

        TableErrorType(String title, String guidance) {
            this.title = title;
            this.guidance = guidance;
        }

        public String getTitle() {
            return title;
        }

        public String getGuidance() {
            return guidance;
        }
    }

    /** 二维表格解析异常，携带明确的错误类型 */
    public static class GridParseException extends RuntimeException {
        private final TableErrorType errorType;

        public GridParseException(TableErrorType errorType, String message) {
            super(message);
            this.errorType = errorType;
        }

        public TableErrorType getErrorType() {
            return errorType;
        }
    }


    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AiProperties aiProperties;
    private boolean isLlmServiceAccessible;

    @Autowired
    public MultimodalTwoStageTableParsingTest(@Qualifier("customChatClient") ChatClient chatClient,
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
     * 前置（Stage 1）：整页版面分析，返回携带表格坐标（&lt;table_location bbox&gt;）的结果。
     * 验证模型能定位表格并输出归一化 bbox 坐标；表格内容解析交给 {@link #testTwoDimensionalTableGridJson()}。
     */
    @Test
    @DisplayName("前置 Stage 1：整页版面分析定位表格坐标 → <table_location> bbox")
    public void testStage1TableLocationDetection() throws IOException {
        assumeTrue(isLlmServiceAccessible, "Skipping test because LLM service is offline or inaccessible.");

        // 1. 读取整页图片
        Resource pageResource = new ClassPathResource("test_doc_page.jpg");
        assertTrue(pageResource.exists(), "测试图片 test_doc_page.jpg 不存在于类路径中！");
        byte[] pageBytes = pageResource.getContentAsByteArray();
        log.info("【Stage 1 表格定位】输入整页图片: classpath:test_doc_page.jpg ({} bytes)", pageBytes.length);

        String chatId = "test-stage1-location-session";
        chatMemory.clear(chatId);

        // 2. 调用模型做版面分析，输出含 <table_location> 占位标签的结果
        long startTime = System.currentTimeMillis();
        String stage1Result = chatClient.prompt()
                .user(u -> u.text(PROMPT_STAGE1)
                            .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(pageBytes)))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .content();
        long cost = System.currentTimeMillis() - startTime;
        assertNotNull(stage1Result, "Stage 1 模型返回结果不能为 null");
        log.info("【Stage 1 表格定位】模型返回完成，耗时: {} ms", cost);
        log.info("\n========== Stage 1 版面分析输出（含表格占位符） ==========\n{}\n==================================================================", stage1Result);

        // 3. 解析表格坐标并输出
        List<TableLocation> locations = parseTableLocations(stage1Result);
        log.info("【Stage 1 表格定位】共识别到 {} 个表格：{}", locations.size(), locations);
        assertTrue(!locations.isEmpty(), "Stage 1 未识别到任何 <table_location> 表格坐标，请检查模型输出");
        for (TableLocation loc : locations) {
            log.info("   表格 [id={}] bbox={}", loc.id(), loc.bbox());
        }
    }

    /**
     * 验证纯表格图片 → 严格二维 JSON 表格的解析契约。
     * 输入图片固定取 {@code src/test/resources/cropped_table_1.jpg}。
     */
    @Test
    @DisplayName("验证 array-of-arrays 二维表格契约：纯表格图 → 严格二维 JSON")
    public void testTwoDimensionalTableGridJson() throws IOException {
        assumeTrue(isLlmServiceAccessible, "Skipping test because LLM service is offline or inaccessible.");

        // 1. 读取纯表格裁切图：resources 下的 cropped_table_1.jpg（ImageMagick 按 bbox + 1% 冗余裁切、保留原图 DPI）
        Resource croppedResource = new ClassPathResource("cropped_table_1.jpg");
        assertTrue(croppedResource.exists(), "类路径下未找到 cropped_table_1.jpg（纯表格裁切图）");
        byte[]  imageBytes = croppedResource.getContentAsByteArray();
        String imageDesc = "classpath:" + croppedResource.getFilename();
        log.info("【二维表格契约】输入纯表格图片: {} ({} bytes)", imageDesc, imageBytes.length);

        String chatId = "test-grid-json-table-session" + System.currentTimeMillis();
        chatMemory.clear(chatId);

        // 2. PASS^N：调用模型 → 二维校验 → 失败则带上错误信息重试
        TableErrorType lastErrorType = null;
        String lastError = null;
        String lastResult = null;
        JsonNode rows = null;
        long totalStartTime = System.currentTimeMillis();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 重试前必须清空 ChatMemory，切断模型对上一轮错误回答的复读惯性
            chatMemory.clear(chatId);

            String prompt;
            if (attempt == 1) {
                prompt = PROMPT_GRID;
            } else {
                // 根据与 parseGrid 匹配的错误类型，精准动态渲染修复指导 Prompt
                String errorGuidance = (lastErrorType != null)
                        ? String.format("【%s】：%s", lastErrorType.getTitle(), lastErrorType.getGuidance())
                        : "请仔细检查上一次的输出并严格按照规则重新输出规范的二维表格 JSON。";

                prompt = PROMPT_GRID + String.format("""

                    --------------------------------------------------
                    【重要提示：你上一轮的解析输出未通过系统校验，请针对性修正】

                    上一轮你的原始输出为：
                    %s

                    未通过校验的具体错误原因：
                    %s

                    针对性修复指导：
                    %s

                    请仔细对照上一轮输出与错误原因，重新输出修正后的二维表格 JSON（只输出合法 JSON，不要包含 Markdown 代码块或任何解释）。
                    --------------------------------------------------
                    """,
                        (lastResult != null && !lastResult.isBlank()) ? lastResult : "(无输出)",
                        lastError,
                        errorGuidance
                );
            }

            long startTime = System.currentTimeMillis();
            String result = chatClient.prompt()
                    .user(u -> u.text(prompt)
                                .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .content();
            long cost = System.currentTimeMillis() - startTime;

            log.info("【二维表格契约】第 {} 次调用完成，耗时: {} ms", attempt, cost);
            log.info("\n========== 第 {} 次模型原始输出 ==========\n{}\n======================================", attempt, result);

            if (result == null || result.isBlank()) {
                lastError = "模型返回为空";
                lastResult = null;
                lastErrorType = null;
                continue;
            }
            try {
                rows = parseGrid(result);
                log.info("【二维表格契约】第 {} 次输出通过严格二维校验", attempt);
                break;
            } catch (GridParseException e) {
                lastErrorType = e.getErrorType();
                lastError = e.getMessage();
                lastResult = result;
                log.warn("【二维表格契约】第 {} 次输出未通过二维校验 [{}]: {}", attempt, lastErrorType.getTitle(), lastError);
            } catch (IllegalArgumentException e) {
                lastErrorType = TableErrorType.STRUCTURE_ERROR;
                lastError = e.getMessage();
                lastResult = result;
                log.warn("【二维表格契约】第 {} 次输出未通过二维校验: {}", attempt, lastError);
            }
        }

        long totalCost = System.currentTimeMillis() - totalStartTime;

        // 3. 断言与最终结果输出
        assertNotNull(rows, "连续 " + MAX_ATTEMPTS + " 次调用均未产生合法的二维表格 JSON，最后错误: " + lastError);

        int rowCount = rows.size();
        int colCount = rows.get(0).size();
        log.info("\n==== 最终解析结果：纯二维表格 ({} 行 x {} 列)，总耗时 {} ms ====\n{}",
                rowCount, colCount, totalCost, formatGrid(rows));

        assertTrue(rowCount >= 1, "二维表格至少应包含 1 行");
        assertTrue(colCount >= 1, "二维表格至少应包含 1 列");
    }

    /**
     * 从 Stage 1 输出中解析所有 {@code <table_location>} 表格坐标占位符。
     * 返回 id + 归一化 bbox 列表；输出不含任何占位标签时返回空列表。
     */
    private List<TableLocation> parseTableLocations(String stage1Result) {
        List<TableLocation> locations = new ArrayList<>();
        if (stage1Result == null || stage1Result.isBlank()) {
            return locations;
        }
        Matcher matcher = TABLE_LOCATION_PATTERN.matcher(stage1Result);
        while (matcher.find()) {
            String id = matcher.group(1);
            String rawBbox = matcher.group(2).trim();
            String bbox = (rawBbox.startsWith("[") && rawBbox.endsWith("]")) ? rawBbox : "[" + rawBbox + "]";
            locations.add(new TableLocation(id, bbox));
        }
        return locations;
    }

    /**
     * 将模型原始输出解析为严格二维数组，并执行二维校验。
     * 兼容 {@code {"rows":[...]}} 与直接输出二维数组两种形式，容忍 Markdown 代码块包裹。
     *
     * @return 校验通过后的 rows 节点
     * @throws GridParseException 输出不是合法二维表格时抛出，携带具体的错误分类与原因
     */
    private JsonNode parseGrid(String rawContent) {
        String content = stripCodeFences(rawContent);
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(content);
        } catch (Exception e) {
            throw new GridParseException(TableErrorType.JSON_SYNTAX, "无法解析为 JSON: " + e.getMessage());
        }

        if (root == null || (!root.isArray() && !root.has("rows"))) {
            throw new GridParseException(TableErrorType.STRUCTURE_ERROR, "输出不是合法的二维表格 JSON，期望 {\"rows\":[...]} 或直接的二维数组");
        }
        JsonNode rows = root.isArray() ? root : root.path("rows");
        if (!rows.isArray() || rows.size() == 0) {
            throw new GridParseException(TableErrorType.STRUCTURE_ERROR, "rows 必须是非空数组");
        }

        // 严格二维校验：每一行都是数组、行宽完全一致、单元格为标量
        int colCount = -1;
        List<String> errors = new ArrayList<>();
        TableErrorType firstErrorType = null;
        for (int r = 0; r < rows.size(); r++) {
            JsonNode row = rows.get(r);
            if (!row.isArray() || row.size() == 0) {
                errors.add("第 " + r + " 行不是非空数组");
                if (firstErrorType == null) firstErrorType = TableErrorType.STRUCTURE_ERROR;
                continue;
            }
            if (colCount == -1) {
                colCount = row.size();
            } else if (row.size() != colCount) {
                errors.add("第 " + r + " 行列数=" + row.size() + " 与首行列数=" + colCount + " 不一致");
                if (firstErrorType == null) firstErrorType = TableErrorType.ROW_COL_MISMATCH;
            }
            for (int c = 0; c < row.size(); c++) {
                if (!row.get(c).isValueNode()) {
                    errors.add("第 " + r + " 行第 " + c + " 列不是标量值");
                    if (firstErrorType == null) firstErrorType = TableErrorType.STRUCTURE_ERROR;
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new GridParseException(
                    firstErrorType != null ? firstErrorType : TableErrorType.STRUCTURE_ERROR,
                    String.join("; ", errors));
        }
        return rows;
    }

    /** 剥离模型可能输出的 Markdown 代码块包裹（```json ... ```） */
    private String stripCodeFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) {
                t = t.substring(nl + 1);
            }
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    /** 将二维数组格式化为一目了然的表格文本，便于日志核验还原度 */
    private String formatGrid(JsonNode rows) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            List<String> cells = new ArrayList<>();
            for (JsonNode cell : rows.get(r)) {
                cells.add(cell.isTextual() ? cell.asText() : cell.toString());
            }
            sb.append("  ").append(r).append(": [").append(String.join(", ", cells)).append("]\n");
        }
        return sb.toString();
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
            URI uri = URI.create(baseUrl);
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
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000);
                log.info("TCP 探活成功: {}:{}", host, port);
                return true;
            }
        } catch (Exception e) {
            log.warn("TCP 探活失败 ({})，将跳过本组多模态解析单元测试。", baseUrl, e);
            return false;
        }
    }
}
