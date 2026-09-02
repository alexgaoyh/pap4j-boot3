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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li><b>Stage 3（新增，见 {@link #testTwoDimensionalTableGridJsonWithOcrCoverage()}）</b>：rows + ocr 全量可见文字双输出，
 *       由 Java 校验 rows 拼接文本是否整体遗漏 ocr 文字，防止「结构校验通过但整块内容被丢弃」；
 *       修复轮采用「先判因再补」：模型须对每段遗漏文字在 repair 计划中判因（merge 并入已有单元格 / new_row 补成新行 / ignore 豁免），
 *       Java 校验计划与输出自洽——新增行数不得超出 new_row 计划数，未分类片段判定修复计划不完整，从而杜绝「为补字凭空加行」。</li>
 *   <li><b>离线回归评分（见 {@link #testTwoDimensionalTableGridJsonWithOcrCoverage()} 末段）</b>：
 *       对最终 rows 与 {@code cropped_table_1.json} 期望网格做纯确定性对比，
 *       输出结构相似度 / 内容精确率·召回率·F1 / 编辑距离相似度五指标，
 *       替代肉眼看日志对账（bootstrap 基线，不设硬断言，仅作回归参照）。</li>
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

    /** 精简 Prompt（新增）：rows 结构化二维数组 + ocr 全量可见文字 双输出契约，面向不同表格图像通用 */
    private static final String PROMPT_GRID_OCR = """
        你是表格 OCR 结构化助手。对图片中的表格，只输出如下严格 JSON（不输出解释、不输出代码块、不输出 Markdown 表格）：

        {
          "rows": [
            ["表头1", "表头2"],
            ["内容1", "内容2"]
          ],
          "ocr": [
            "表头1",
            "内容1",
            "内容2"
          ]
        }

        rows 规则：
        - 结构化二维字符串数组，含表头行与全部数据行，每行列数必须完全一致。
        - 合并单元格在覆盖的每个格子重复填入文字；空单元格填 ""。
        - 若一个单元格含多行/多段文字（如指标名称下方还有工艺说明、适用条件），必须把全部内容合并进该单元格（用空格分隔），
          合并进哪一格以图片实际位置为准，不得丢弃、不得拆成独立行。
        - 逐字保留所有文字，不删改、不总结、不归纳。

        ocr 规则：
        - 按阅读顺序逐条列出表格内所有可见文字片段（每个单元格/每段文字一条），作为 rows 的全量底账。
        - 只列图片中确实可见的文字，不得虚构、联想或猜测。
        - 不遗漏、不合并、不省略。

        完整性约束：
        - rows 必须完整覆盖 ocr 中列出的每一段文字，任何一段遗漏都视为不合格。
        - 输出前请自查：逐条比对 ocr 与 rows，确保无遗漏。

        """;

    /**
     * 修复轮 Prompt（模板，${ROWS} / ${MISSING} 在运行时替换）：
     * 先内嵌当前 rows（带行号列号）供模型定位，要求 merge 声明目标单元格 (row,col)，
     * 并必须真的把遗漏文字拼进该单元格（示范合并结果），杜绝「声明 merge 却原样复读」。
     */
    private static final String PROMPT_REPAIR = """
        你是表格 OCR 结构化助手。上一轮输出未通过完整性校验：rows 遗漏了部分可见文字。
        这些文字属于已有单元格中的多行/多段内容，必须并入对应单元格，不得丢弃、不得新增行。

        当前 rows（行号从 0 开始，第 0 行为表头；单元格内容完整展示）：
        ${ROWS}

        遗漏文字如下。每条都是独立的文字片段，必须分别并入各自所属的单元格：
        - 不得把多条合并成一段；
        - 不得整批塞进同一个单元格；
        - 每段文字在图片中位于哪个单元格，就并入哪个单元格（以图片实际位置为准）。
        ${MISSING}

        请完成两步：
        1. 在图片中定位每段遗漏文字所属的单元格（以当前 rows 的行号/列号为准）；
        2. 输出修正后的 rows + ocr + repair 计划。

        修正后的 rows 必须真的改变目标单元格：把遗漏文字拼进该单元格已有内容之后。
        目标单元格以图片实际位置为准：遗漏文字视觉上位于哪一格，就并入哪一格
        （可能是指标名称格、数值格或其他列，请以图片为准，不要仅凭下文示例猜测列位置）。
        行数保持与当前 rows 完全一致（除非声明 new_row）。

        【一致性强制校验】系统会逐条核对 repair 计划与输出 rows 是否一致：
        - repair 中每条 merge 声明了 (row,col)，则输出 rows 中该单元格必须已经包含该 fragment
          （即「原单元格内容 + fragment」，fragment 拼在末尾）；
        - 若声明的单元格里没有该 fragment，本次输出判定为「未执行 merge」，不合格并重试；
        - 输出 rows 不得与当前 rows 完全相同（完全一样 = 没有执行任何 merge）。

        repair 计划示例（仅为 JSON 格式示范，merge 的 row/col 必须按图片判断，勿照抄）：
        {
          "repair": [
            {"fragment": "某段遗漏文字", "action": "merge", "row": 3, "col": 1},
            {"fragment": "另一段遗漏文字", "action": "ignore"}
          ]
        }

        repair 规则：
        - 每段遗漏文字必须恰好出现一次，fragment 与遗漏文字逐字一致；
        - action 只能是 merge / new_row / ignore 三者之一；
        - merge：必须给出 row 与 col，且修正后 rows 中该单元格必须逐字包含 fragment；
        - new_row：确属一整行独立数据才用，补出的行保持等列数；
        - ignore：核对图片后确认不在表格内。

        只输出 JSON，不输出解释、不输出代码块。
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

    /** OCR 覆盖度兜底判定的语义 token：连续的中文/字母/数字（含上/下标数字，如 ²³₁） */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

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
        STRUCTURE_ERROR("数据结构错误", "确保输出格式为 {\"rows\": [[...], [...]]} 的严格二维字符串数组，单元格内必须是纯文本或数字，不能嵌套对象或数组。"),
        OCR_CONTENT_MISSING("OCR 可见文字遗漏", "rows 未能完整覆盖图片中的全部可见文字，必须把遗漏的文字补回正确位置：优先并入所属的已有单元格；仅当某段遗漏文字确实构成一整行独立数据时才可补成新行；禁止删除行、重排行或凭空添加与遗漏无关的行。"),
        ROW_COUNT_DRIFT("修复后行数漂移", "修复轮新增行数必须与 repair 计划中 new_row 的数量一致：计划未声明 new_row 的不得加行，每段 new_row 最多对应新增一行。"),
        REPAIR_PLAN_INVALID("修复计划不完整", "每一段遗漏文字都必须在 repair 计划中分类（merge / new_row / ignore），action 只能取三者之一，fragment 必须与遗漏文字逐字一致。");

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

    /** 最近一次解析的结果记录（含错误标志），供外部获取相对最好的一次结果 */
    private TableParseResult lastParseResult;

    @Autowired
    public MultimodalTwoStageTableParsingTest(@Qualifier("customChatClient") ChatClient chatClient,
                                              ChatMemory chatMemory,
                                              AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.aiProperties = aiProperties;
    }

    /** 获取最近一次解析的相对最好结果（未完全收敛时 converged=false 并携带遗漏清单与错误信息） */
    public TableParseResult getLastParseResult() {
        return lastParseResult;
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
                log.error("【二维表格契约】第 {} 次输出未通过二维校验 [{}]: {}", attempt, lastErrorType.getTitle(), lastError, e);
            } catch (IllegalArgumentException e) {
                lastErrorType = TableErrorType.STRUCTURE_ERROR;
                lastError = e.getMessage();
                lastResult = result;
                log.error("【二维表格契约】第 {} 次输出未通过二维校验: {}", attempt, lastError, e);
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
     * <p>新增实验方法：rows 二维结构 + OCR 全量可见文字 双输出契约。</p>
     * <p>背景：{@link #testTwoDimensionalTableGridJson()} 的结构校验只保证「行宽一致」，
     * 无法发现「整块内容被静默丢弃」（如为满足列数约束而删行/删列文字）。
     * 本方法让模型在 rows 之外再输出一份 {@code ocr} 全量可见文字清单，
     * 由 Java 校验 rows 的拼接文本是否完整覆盖 ocr：发现遗漏即把具体缺字反馈给模型重试，
     * 从而协助视觉模型补全内容，而非仅仅满足结构约束。</p>
     * <p><b>Step 5（离线评分）</b>：收敛后与黄金样本 {@code cropped_table_1.json} 的期望网格对比，
     * 输出结构相似度 / 内容精确率·召回率·F1 / 归一化编辑距离相似度五指标
     * （实现见类尾「黄金样本评分逻辑」{@link #evaluate(java.util.List, java.util.List)}），
     * 并对照参考门槛打 PASS/WARN 日志供人工回归对账。
     * 期望网格为 bootstrap 基线（取自模型收敛输出，非人工真值），故此处不设硬断言。</p>
     */
    @Test
    @DisplayName("新增：rows 二维结构 + OCR 全量可见文字 双输出，校验 rows 是否整体遗漏 OCR 文字")
    public void testTwoDimensionalTableGridJsonWithOcrCoverage() throws IOException {
        assumeTrue(isLlmServiceAccessible, "Skipping test because LLM service is offline or inaccessible.");

        // 1. 读取纯表格裁切图
        Resource croppedResource = new ClassPathResource("cropped_table_1.jpg");
        assertTrue(croppedResource.exists(), "类路径下未找到 cropped_table_1.jpg（纯表格裁切图）");
        byte[] imageBytes = croppedResource.getContentAsByteArray();
        log.info("【rows+OCR 双输出契约】输入纯表格图片: classpath:{} ({} bytes)", croppedResource.getFilename(), imageBytes.length);

        String chatId = "test-grid-json-ocr-table-session" + System.currentTimeMillis();
        chatMemory.clear(chatId);

        // 2. PASS^N：结构校验 + OCR 覆盖校验 双重判定，失败则携带错误信息（含具体遗漏文字）重试
        TableErrorType lastErrorType = null;
        String lastError = null;
        List<String> lastMissingOcr = null;
        JsonNode rows = null;
        List<String> ocrList = new ArrayList<>();
        List<String> baselineOcr = new ArrayList<>(); // 首轮锁定的全量底账，后续覆盖校验一律以此为准，防修复轮缩水 ocr 绕过
        int baselineRowCount = -1; // 首次结构合法输出的行数基准，修复轮行数变化必须可解释
        boolean converged = false; // 是否在某一轮通过全部校验（结构 + OCR 覆盖）
        long totalStartTime = System.currentTimeMillis();

        // 相对最好结果的跟踪（缺字最少优先），未完全收敛时返回它并附错误标志
        JsonNode bestRows = null;
        List<String> bestMissing = new ArrayList<>();
        int bestMissingCount = Integer.MAX_VALUE;
        int bestAttempt = 0;
        String bestError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            // 重试前清空 ChatMemory，切断模型对上一轮错误回答的复读惯性
            chatMemory.clear(chatId);

            String prompt;
            if (attempt == 1) {
                prompt = PROMPT_GRID_OCR;
            } else if (lastErrorType == TableErrorType.JSON_SYNTAX
                    || lastErrorType == TableErrorType.ROW_COL_MISMATCH
                    || lastErrorType == TableErrorType.STRUCTURE_ERROR) {
                // 结构类错误：回滚到基础契约 + 结构修复指导
                String errorGuidance = String.format("【%s】：%s", lastErrorType.getTitle(), lastErrorType.getGuidance());
                prompt = PROMPT_GRID_OCR + String.format("""

                    --------------------------------------------------
                    【重要提示：你上一轮的解析输出未通过系统校验，请针对性修正】

                    未通过校验的具体错误原因：
                    %s

                    修复指导：
                    %s

                    请重新输出修正后的 rows + ocr JSON（只输出合法 JSON，不要包含 Markdown 代码块或任何解释）。
                    --------------------------------------------------
                    """,
                        lastError,
                        errorGuidance);
            } else {
                // 遗漏/修复计划类错误：统一走「先判因再定位再补」的 PROMPT_REPAIR，
                // 内嵌当前 rows（带行号列号）供模型定位 merge 目标单元格
                String missingList;
                if (lastMissingOcr != null && !lastMissingOcr.isEmpty()) {
                    // 逐条编号渲染，避免 `[a, b, c]` 一整块被模型当成单个 fragment
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < lastMissingOcr.size(); i++) {
                        sb.append("  ").append(i + 1).append(". ").append(lastMissingOcr.get(i)).append("\n");
                    }
                    missingList = sb.toString();
                } else {
                    missingList = "(无)";
                }
                StringBuilder repairPrompt = new StringBuilder(
                        PROMPT_REPAIR.replace("${ROWS}", formatRowsWithIndices(rows))
                                     .replace("${MISSING}", missingList));
                if (lastErrorType != TableErrorType.OCR_CONTENT_MISSING) {
                    // 修复计划/行数一致性失败：附上校验失败原因，要求严格按 repair 规则重新定位与补字
                    repairPrompt.append(String.format("""

                        【你上一轮的输出未通过系统校验】
                        %s
                        请严格按照上述 repair 规则，重新定位每段遗漏文字的目标单元格（merge 声明 row/col）后再输出修正结果。
                        """,
                            lastError));
                }
                prompt = repairPrompt.toString();
            }

            long startTime = System.currentTimeMillis();
            String result = chatClient.prompt()
                    .user(u -> u.text(prompt)
                                .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageBytes)))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                    .call()
                    .content();
            long cost = System.currentTimeMillis() - startTime;

            log.info("【rows+OCR 双输出契约】第 {} 次调用完成，耗时: {} ms", attempt, cost);
            log.info("\n========== 第 {} 次模型原始输出 ==========\n{}\n======================================", attempt, result);

            if (result == null || result.isBlank()) {
                lastError = "模型返回为空";
                lastErrorType = null;
                lastMissingOcr = null;
                continue;
            }

            // 3a. 结构校验：解析 rows 二维数组（复用 parseGrid，仅校验 rows，容忍多余的 ocr / repair 字段）
            try {
                rows = parseGrid(result);
                ocrList = parseOcrList(result);
            } catch (GridParseException e) {
                lastErrorType = e.getErrorType();
                lastError = e.getMessage();
                lastMissingOcr = null;
                log.error("【rows+OCR 双输出契约】第 {} 次输出未通过二维校验 [{}]: {}", attempt, lastErrorType.getTitle(), lastError, e);
                continue;
            }

            if (ocrList.isEmpty()) {
                log.warn("【rows+OCR 双输出契约】第 {} 次模型未返回 ocr 字段，覆盖校验退化为空（无法判定完整性）。", attempt);
            }

            // 记录首次结构合法输出作为行数基准 + 锁定基准 ocr（覆盖校验统一以基准为准，防修复轮缩水 ocr 绕过）
            if (baselineRowCount == -1) {
                baselineRowCount = rows.size();
                baselineOcr = new ArrayList<>(ocrList);
                log.info("【rows+OCR 双输出契约】已锁定基准 ocr（{} 条），后续覆盖校验均以此为准。", baselineOcr.size());
            } else if (ocrList.size() < baselineOcr.size() * 0.8) {
                log.warn("【rows+OCR 双输出契约】第 {} 次模型 ocr 缩水（{} 条 < 基准 {} 条），覆盖校验仍按基准 ocr 执行，防止绕过。",
                        attempt, ocrList.size(), baselineOcr.size());
            }

            // 3b. 修复计划解析（修复轮：模型须对遗漏文字判因），校验 action 合法性
            List<RepairPlanItem> plan = parseRepairPlan(result);
            boolean hasInvalidAction = false;
            Set<String> exemptFragments = new HashSet<>();
            int planNewRowCount = 0;
            for (RepairPlanItem item : plan) {
                if (!isValidAction(item.action())) {
                    hasInvalidAction = true;
                } else if ("ignore".equals(item.action())) {
                    exemptFragments.add(item.fragment());
                } else if ("new_row".equals(item.action())) {
                    planNewRowCount++;
                }
            }
            if (hasInvalidAction) {
                lastErrorType = TableErrorType.REPAIR_PLAN_INVALID;
                lastError = "repair 计划存在非法 action（只允许 merge / new_row / ignore）";
                log.warn("【rows+OCR 双输出契约】第 {} 次输出修复计划非法，准备重试: {}", attempt, lastError);
                continue;
            }

            // 3c. OCR 覆盖校验（模型判为 ignore 的片段豁免覆盖要求；始终按基准 ocr 比对）
            OcrCoverageReport report = checkOcrCoverage(rows, baselineOcr, exemptFragments);
            log.info("\n===== 第 {} 次 OCR 覆盖度校验报告 =====", attempt);
            log.info("OCR 片段总数: {}, 完整覆盖: {}, ignore 豁免: {}, 分段差异(非缺失): {}, 确定遗漏: {}",
                    report.total(), report.covered(), report.ignored(), report.segmentedDiff().size(), report.missing().size());
            if (!report.missing().isEmpty()) {
                log.warn("确定遗漏（rows 整体缺字）:");
                report.missing().forEach(f -> log.warn("   - {}", f));
            }
            if (!report.segmentedDiff().isEmpty()) {
                log.info("分段差异（rows 已含全部 token，仅拆分/合并方式不同）: {}", report.segmentedDiff());
            }

            // 3d. 修复计划完整性：仍遗漏且未在计划中分类的文字 → 计划不完整（已修复的片段不作要求）
            if (attempt > 1 && lastMissingOcr != null && !lastMissingOcr.isEmpty()) {
                Set<String> planFragments = new HashSet<>();
                for (RepairPlanItem item : plan) {
                    planFragments.add(normalizeWhitespace(item.fragment()));
                }
                Set<String> stillMissingSet = new HashSet<>();
                for (String m : report.missing()) {
                    stillMissingSet.add(normalizeWhitespace(m));
                }
                boolean planIncomplete = false;
                String planError = null;
                for (String frag : lastMissingOcr) {
                    if (!planFragments.contains(normalizeWhitespace(frag))
                            && stillMissingSet.contains(normalizeWhitespace(frag))) {
                        planIncomplete = true;
                        planError = "遗漏文字未在 repair 计划中分类: " + frag;
                        break;
                    }
                }
                if (planIncomplete) {
                    lastErrorType = TableErrorType.REPAIR_PLAN_INVALID;
                    lastError = planError;
                    log.warn("【rows+OCR 双输出契约】第 {} 次输出修复计划不完整，准备重试: {}", attempt, lastError);
                    continue;
                }
            }

            // 3d2. merge 目标校验（硬校验）：声明的 (row,col) 必须存在，且该单元格必须已包含 fragment——
            //      只声明位置、rows 里却没有该文字 = 未真正执行 merge，判定不合格重试（配合提示词的一致性强制校验）
            boolean mergeTargetInvalid = false;
            String mergeTargetError = null;
            for (RepairPlanItem item : plan) {
                if (!"merge".equals(item.action())) {
                    continue;
                }
                if (item.row() < 0 || item.row() >= rows.size()
                        || item.col() < 0 || item.col() >= rows.get(item.row()).size()) {
                    mergeTargetInvalid = true;
                    mergeTargetError = "merge 目标越界: " + item.fragment() + " 声明 (" + item.row() + "," + item.col() + ") 超出当前 rows 范围";
                    break;
                }
                JsonNode cellNode = rows.get(item.row()).get(item.col());
                String cellText = normalizeWhitespace(cellNode.isTextual() ? cellNode.asText() : cellNode.toString());
                if (!cellText.contains(normalizeWhitespace(item.fragment()))) {
                    mergeTargetInvalid = true;
                    mergeTargetError = "merge 未执行: fragment[" + item.fragment() + "] 未出现在声明的单元格 (" + item.row() + "," + item.col() + ")，必须把该文字真正拼进该单元格";
                    break;
                }
            }
            if (mergeTargetInvalid) {
                lastErrorType = TableErrorType.REPAIR_PLAN_INVALID;
                lastError = mergeTargetError;
                log.warn("【rows+OCR 双输出契约】第 {} 次输出 merge 未执行，准备重试: {}", attempt, lastError);
                continue;
            }

            // 3e. 行数一致性：新增行数不得超过 repair 计划中 new_row 的数量
            if (attempt > 1 && rows.size() > baselineRowCount
                    && (rows.size() - baselineRowCount) > planNewRowCount) {
                lastErrorType = TableErrorType.ROW_COUNT_DRIFT;
                lastError = String.format("新增行数=%d 超过 repair 计划中 new_row 数量=%d，计划未声明 new_row 的不得加行。",
                        rows.size() - baselineRowCount, planNewRowCount);
                lastMissingOcr = report.missing().isEmpty() ? null : report.missing();
                log.warn("【rows+OCR 双输出契约】第 {} 次输出行数漂移（超出计划），准备重试: {}", attempt, lastError);
                continue;
            }
            if (attempt > 1 && rows.size() < baselineRowCount) {
                // 行数减少：可能是纠正首轮多余的错行，覆盖率通过则放行（仅告警，请人工复核）
                log.warn("【rows+OCR 双输出契约】第 {} 次行数减少 {} → {}，按「纠正首轮多余行」处理，请人工复核。",
                        attempt, baselineRowCount, rows.size());
            }

            // 3e2. 记录相对最好的一次结果（缺字最少优先），供未完全收敛时返回
            int miss = report.missing().size();
            if (miss < bestMissingCount) {
                bestMissingCount = miss;
                bestRows = rows;
                bestMissing = new ArrayList<>(report.missing());
                bestAttempt = attempt;
                bestError = "rows 遗漏了 OCR 可见文字 " + miss + " 处: " + report.missing();
            }

            // 3f. 收敛判定
            if (report.missing().isEmpty()) {
                converged = true;
                log.info("【rows+OCR 双输出契约】第 {} 次输出通过全部校验（结构 + 修复计划 + OCR 覆盖）", attempt);
                break;
            }

            // 3g. 存在确定遗漏 → 记录为 OCR_CONTENT_MISSING，携带遗漏文字进入下一轮重试
            lastErrorType = TableErrorType.OCR_CONTENT_MISSING;
            lastMissingOcr = report.missing();
            lastError = "rows 遗漏了 OCR 可见文字 " + report.missing().size() + " 处: " + report.missing();
            log.warn("【rows+OCR 双输出契约】第 {} 次输出存在 OCR 遗漏，准备重试: {}", attempt, lastError);
        }

        long totalCost = System.currentTimeMillis() - totalStartTime;

        // 4. 组装最终结果记录：未完全收敛时返回相对最好的一次，附错误标志与错误信息
        TableParseResult finalResult;
        if (bestRows != null) {
            finalResult = new TableParseResult(bestRows, baselineOcr, bestRows.size(), bestRows.get(0).size(),
                    converged, bestMissing, converged ? null : bestError, bestAttempt);
        } else {
            finalResult = null;
        }
        this.lastParseResult = finalResult;

        // 只有一轮合法 JSON 都没有时才断言失败；否则记录最好结果 + 错误标志
        assertNotNull(finalResult, "连续 " + MAX_ATTEMPTS + " 次调用均未产生合法的 rows+ocr 二维表格 JSON，最后错误: " + lastError);
        assertTrue(finalResult.rowCount() >= 1, "二维表格至少应包含 1 行");
        assertTrue(finalResult.colCount() >= 1, "二维表格至少应包含 1 列");

        if (finalResult.converged()) {
            log.info("\n==== 收敛结果：rows ({} 行 x {} 列)，总耗时 {} ms ====\n{}",
                    finalResult.rowCount(), finalResult.colCount(), totalCost, formatGrid(finalResult.rows()));
        } else {
            log.warn("\n==== 未完全收敛：已记录相对最好的一次结果（第 {} 次，缺 {} 段）====\n错误信息：{}\nrows ({} 行 x {} 列)，总耗时 {} ms：\n{}",
                    finalResult.attemptsUsed(), finalResult.missingOcr().size(), finalResult.errorMessage(),
                    finalResult.rowCount(), finalResult.colCount(), totalCost, formatGrid(finalResult.rows()));
        }
        if (finalResult.baselineOcr() != null && !finalResult.baselineOcr().isEmpty()) {
            log.info("==== 基准 OCR 全量可见文字 ({} 条，覆盖校验以此为准) ====\n{}",
                    finalResult.baselineOcr().size(), formatOcrList(finalResult.baselineOcr()));
        }
        // 5. 黄金样本对比：期望网格 vs 解析结果 → 结构 / 内容 / 编辑距离 三层量化指标。
        //    【定位】离线回归评分：把每次解析的最终结果与人工标注的期望网格对比成数字，
        //    用于验证后续 Prompt / 后处理改动是变好还是变坏，替代肉眼看日志对账。
        List<List<String>> actualRows = toGrid(finalResult.rows());
        List<List<String>> expectedRows = loadGoldenExpectedRows();
        TableScore score = evaluate(expectedRows, actualRows);
        log.info("\n{}", score.report());
        // 【门槛说明】cropped_table_1 期望网格取自模型收敛输出（bootstrap 基线，非人工真值），
        // 因此同一管线跑同一张图时分数偏高。此处不做断言、仅打日志对照参考门槛，
        // 由人工比对实际分数与期望网格判定质量（当前属单元测试，不以硬断言卡死回归）。
        log.info("评分对比（实际分数 vs 参考门槛）:");
        log.info("  textRecall={} （参考门槛 >= 0.90）{}", score.textRecall(),
                score.textRecall() >= 0.90 ? "PASS" : "WARN 偏低");
        log.info("  structuralSimilarity={} （参考门槛 >= 0.75）{}", score.structuralSimilarity(),
                score.structuralSimilarity() >= 0.75 ? "PASS" : "WARN 偏低");
        log.info("  levenshteinSimilarity={} （参考门槛 >= 0.70）{}", score.levenshteinSimilarity(),
                score.levenshteinSimilarity() >= 0.70 ? "PASS" : "WARN 偏低");

        log.info("==== 解析状态：{} ====\n",
                finalResult.converged() ? "完全收敛（无遗漏）"
                        : "存在遗漏/错误（converged=false）：遗漏见 missingOcr，错误见 errorMessage，可通过 getLastParseResult() 获取");
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
            log.error("解析模型输出为 JSON 失败: ", e);
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

    /** 从模型输出中解析 ocr 字段（兼容数组或换行分隔的纯文本），无 ocr 时返回空列表 */
    private List<String> parseOcrList(String rawContent) {
        List<String> result = new ArrayList<>();
        String content = stripCodeFences(rawContent);
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(content);
        } catch (Exception e) {
            log.error("解析模型输出中的 ocr 字段 JSON 失败: ", e);
            return result;
        }
        if (root == null || !root.has("ocr")) {
            return result;
        }
        JsonNode ocr = root.path("ocr");
        if (ocr.isArray()) {
            for (JsonNode node : ocr) {
                if (node.isTextual() && !node.asText().isBlank()) {
                    result.add(node.asText());
                }
            }
        } else if (ocr.isTextual() && !ocr.asText().isBlank()) {
            for (String line : ocr.asText().split("[\\r\\n]+")) {
                if (!line.isBlank()) {
                    result.add(line.trim());
                }
            }
        }
        return result;
    }

    /**
     * 校验 rows 单元格文本是否完整覆盖 ocr 全量可见文字。
     * 判定策略：
     * 1) 一级判定：将 ocr 片段与 rows 拼接文本统一去除空白后做子串匹配；
     * 2) 二级兜底：整体未命中的片段，若其每个语义 token 都已在 rows 中出现，判定为「分段差异」（拆分/合并方式不同，非真实缺失）；
     *    否则判定为「确定遗漏」——rows 整体缺字，通常是整行/整块内容被丢弃。
     * 3) ignore 豁免：片段被修复计划判为 ignore（模型核对后认为不在表格内）时，豁免覆盖要求，不计入遗漏。
     */
    private OcrCoverageReport checkOcrCoverage(JsonNode rows, List<String> ocrList, Set<String> exemptFragments) {
        List<String> missing = new ArrayList<>();
        List<String> segmentedDiff = new ArrayList<>();
        if (ocrList == null || ocrList.isEmpty()) {
            return new OcrCoverageReport(0, 0, 0, missing, segmentedDiff);
        }

        Set<String> exemptNorm = new HashSet<>();
        if (exemptFragments != null) {
            for (String f : exemptFragments) {
                if (f != null) {
                    exemptNorm.add(normalizeWhitespace(f));
                }
            }
        }

        String joinedRows = normalizeWhitespace(joinRowsText(rows));
        Set<String> rowsTokens = tokenizeToSet(joinedRows);

        int total = 0;
        int covered = 0;
        int ignored = 0;
        for (String fragment : ocrList) {
            if (fragment == null) continue;
            String norm = normalizeWhitespace(fragment);
            if (norm.isEmpty()) continue;
            total++;
            // 一级判定：片段整体（去空白）是否出现在 rows 拼接文本中
            if (joinedRows.contains(norm)) {
                covered++;
                continue;
            }
            // 二级兜底：整体未命中但绝大多数 token 已在 rows 中出现 → 大概率是 ocr 分词/合并方式不同，非真实缺失
            List<String> fragTokens = tokenize(norm);
            if (!fragTokens.isEmpty() && tokenCoverageRatio(fragTokens, rowsTokens) >= 0.7) {
                segmentedDiff.add(fragment);
                continue;
            }
            // ignore 豁免：模型核对后判定不在表格内
            if (exemptNorm.contains(norm)) {
                ignored++;
                continue;
            }
            // 存在 rows 中完全缺失的 token → 确定遗漏（可能是整行/整块被丢弃）
            missing.add(fragment.trim());
        }
        return new OcrCoverageReport(total, covered, ignored, missing, segmentedDiff);
    }

    /** 拼接 rows 全部单元格文本，作为覆盖判定的检索底文本 */
    private String joinRowsText(JsonNode rows) {
        StringBuilder sb = new StringBuilder();
        if (rows != null) {
            for (JsonNode row : rows) {
                sb.append(joinRowText(row));
            }
        }
        return sb.toString();
    }

    /** 拼接单行全部单元格文本 */
    private String joinRowText(JsonNode row) {
        StringBuilder sb = new StringBuilder();
        if (row == null || !row.isArray()) {
            return "";
        }
        for (JsonNode cell : row) {
            if (cell != null && cell.isValueNode()) {
                sb.append(cell.isTextual() ? cell.asText() : cell.toString());
            }
        }
        return sb.toString();
    }

    /** 从模型输出中解析 repair 修复计划（fragment + action + 可选 row/col），无 repair 字段时返回空列表 */
    private List<RepairPlanItem> parseRepairPlan(String rawContent) {
        List<RepairPlanItem> result = new ArrayList<>();
        String content = stripCodeFences(rawContent);
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(content);
        } catch (Exception e) {
            log.error("解析模型输出中的 repair 计划 JSON 失败: ", e);
            return result;
        }
        if (root == null || !root.has("repair") || !root.path("repair").isArray()) {
            return result;
        }
        for (JsonNode node : root.path("repair")) {
            String fragment = node.path("fragment").isTextual() ? node.path("fragment").asText() : "";
            String action = node.path("action").isTextual() ? node.path("action").asText() : "";
            int row = node.path("row").isIntegralNumber() ? node.path("row").asInt() : -1;
            int col = node.path("col").isIntegralNumber() ? node.path("col").asInt() : -1;
            if (!fragment.isBlank() && !action.isBlank()) {
                result.add(new RepairPlanItem(fragment, action, row, col));
            }
        }
        return result;
    }

    /** repair 计划是否合法：action 只能是 merge / new_row / ignore 三者之一 */
    private boolean isValidAction(String action) {
        return "merge".equals(action) || "new_row".equals(action) || "ignore".equals(action);
    }

    /** repair 修复计划单条记录（merge 必须声明 row/col，0 起始；否则为 -1） */
    private record RepairPlanItem(String fragment, String action, int row, int col) {}

    /** 去除字符串所有空白，便于宽松的片段匹配 */
    private static String normalizeWhitespace(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    /** 提取语义 token：连续的中文/字母/数字（含上/下标数字，如 ²³₁），用于覆盖度兜底判定 */
    private static List<String> tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(s);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static Set<String> tokenizeToSet(String s) {
        return new HashSet<>(tokenize(s));
    }

    /** 片段 token 在 rows token 集合中的覆盖率（0.0~1.0），用于容忍 ocr 分词噪声 */
    private static double tokenCoverageRatio(List<String> fragTokens, Set<String> rowsTokens) {
        if (fragTokens.isEmpty()) {
            return 0;
        }
        int present = 0;
        for (String token : fragTokens) {
            if (rowsTokens.contains(token)) {
                present++;
            }
        }
        return (double) present / fragTokens.size();
    }

    /** 将 ocr 片段列表格式化为一目了然的文本，便于日志核验 */
    private String formatOcrList(List<String> ocrList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ocrList.size(); i++) {
            sb.append("  ").append(i).append(": ").append(ocrList.get(i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 将 rows 渲染为带行号/列号的文本（单元格内容完整展示，禁止截断——截断的「…」会被模型原样抄进输出，破坏原文），
     * 供 repair 提示词定位 merge 目标。
     */
    private String formatRowsWithIndices(JsonNode rows) {
        StringBuilder sb = new StringBuilder();
        if (rows == null) {
            return "(无 rows)";
        }
        for (int r = 0; r < rows.size(); r++) {
            JsonNode row = rows.get(r);
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < row.size(); c++) {
                String t = "";
                if (row.get(c).isTextual()) {
                    t = row.get(c).asText();
                } else if (row.get(c).isValueNode()) {
                    t = row.get(c).toString();
                }
                cells.add("[" + c + "]" + t);
            }
            sb.append("  行 ").append(r).append(": ").append(String.join(" | ", cells)).append("\n");
        }
        return sb.toString();
    }

    /** OCR 覆盖度校验报告（ignored = 被 repair 计划判为 ignore 而豁免覆盖的片段数） */
    private record OcrCoverageReport(int total, int covered, int ignored, List<String> missing, List<String> segmentedDiff) {
        @Override
        public String toString() {
            return String.format("{total=%d, covered=%d, ignored=%d, missing=%s, segmentedDiff=%s}",
                    total, covered, ignored, missing, segmentedDiff);
        }
    }

    /**
     * 表格解析结果记录：保留相对最好的一次结果。
     * converged=true 表示全部校验通过；否则 converged=false，missingOcr 为遗漏清单，errorMessage 为错误信息。
     */
    public record TableParseResult(JsonNode rows, List<String> baselineOcr, int rowCount, int colCount,
                                   boolean converged, List<String> missingOcr, String errorMessage, int attemptsUsed) {
        @Override
        public String toString() {
            return String.format("{converged=%s, rows=%d行x%d列, 缺字=%s, error=%s, attemptsUsed=%d}",
                    converged, rowCount, colCount, missingOcr, errorMessage, attemptsUsed);
        }
    }

    /**
     * 将解析器输出的 JsonNode rows 转换为 {@code List<List<String>>} 网格，供黄金样本对比评分。
     */
    private List<List<String>> toGrid(JsonNode rows) {
        List<List<String>> grid = new ArrayList<>();
        if (rows == null || !rows.isArray()) {
            return grid;
        }
        for (JsonNode row : rows) {
            List<String> cells = new ArrayList<>();
            for (JsonNode cell : row) {
                cells.add(cell.isTextual() ? cell.asText() : cell.toString());
            }
            grid.add(cells);
        }
        return grid;
    }

    /**
     * 从与图片同名的期望网格 JSON（{@code src/test/resources/cropped_table_1.json}）读取期望归一化网格。
     * 期望网格与图片 {@code cropped_table_1.jpg} 同名存放，便于对应理解。
     */
    private List<List<String>> loadGoldenExpectedRows() {
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("cropped_table_1.json");
            try (java.io.InputStream is = resource.getInputStream()) {
                JsonNode root = new ObjectMapper().readTree(is);
                return new ObjectMapper().convertValue(root.path("expectedRows"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<List<String>>>() {});
            }
        } catch (java.io.IOException e) {
            log.error("读取黄金样本期望网格失败: ", e);
            throw new RuntimeException("读取黄金样本期望网格失败", e);
        }
    }

    // =====================================================================
    // 黄金样本评分逻辑（纯确定性逻辑，无 LLM 依赖）
    // =====================================================================

    /** 三层指标评分结果 */
    private record TableScore(
            double structuralSimilarity,
            double textRecall,
            double textPrecision,
            double textF1,
            double levenshteinSimilarity) {

        /** 渲染为一眼可读的评测报告文本 */
        public String report() {
            return """
                    ================= 表格解析质量评分 =================
                     结构相似度 (Structural)     : %.4f
                     内容召回率 (Text Recall)    : %.4f
                     内容精确率 (Text Precision) : %.4f
                     内容 F1 (Text F1)           : %.4f
                     编辑距离相似度 (Levenshtein) : %.4f
                    ==================================================
                    """.formatted(structuralSimilarity, textRecall, textPrecision, textF1, levenshteinSimilarity);
        }
    }

    /**
     * 计算期望网格与预测网格的三层指标：
     * 结构相似度（行列）、内容召回率/精确率（丢字与幻觉）、归一化编辑距离相似度（整体还原度）。
     */
    private static TableScore evaluate(List<List<String>> expectedRows, List<List<String>> actualRows) {
        double structural = structuralSimilarity(expectedRows, actualRows);
        double[] pr = textPrecisionRecall(expectedRows, actualRows);
        double precision = pr[0];
        double recall = pr[1];
        double f1 = f1(precision, recall);
        double lev = levenshteinSimilarity(expectedRows, actualRows);
        return new TableScore(structural, recall, precision, f1, lev);
    }

    /** 结构相似度：1 - (|Δ行| + |Δ列|) / (期望行数 + 期望列数)，预测与期望完全同形时为 1.0 */
    private static double structuralSimilarity(List<List<String>> expectedRows, List<List<String>> actualRows) {
        if (expectedRows == null || expectedRows.isEmpty()) {
            return 0.0;
        }
        int expRows = expectedRows.size();
        int expCols = expectedRows.get(0).size();
        int actRows = (actualRows == null) ? 0 : actualRows.size();
        int actCols = (actualRows == null || actualRows.isEmpty() || actualRows.get(0) == null)
                ? 0 : actualRows.get(0).size();
        int rowDiff = Math.abs(expRows - actRows);
        int colDiff = Math.abs(expCols - actCols);
        double sim = 1.0 - ((double) (rowDiff + colDiff) / (expRows + expCols));
        return clamp01(sim);
    }

    /**
     * 内容精确率 / 召回率（基于语义 token 的多重集合匹配，复用类级 {@link #TOKEN_PATTERN}）：
     * recall = 期望单元格 token 中出现在预测网格里的比例（对准「丢字」）；
     * precision = 预测单元格 token 中出现在期望网格里的比例（对准「幻觉/多写」）。
     *
     * @return double[0] = precision, double[1] = recall
     */
    private static double[] textPrecisionRecall(List<List<String>> expectedRows, List<List<String>> actualRows) {
        Map<String, Integer> expectedTokens = tokenCounts(expectedRows);
        Map<String, Integer> actualTokens = tokenCounts(actualRows);

        int expectedTotal = sum(expectedTokens);
        int actualTotal = sum(actualTokens);

        int expectedMatched = countMatched(expectedTokens, actualTokens);
        int actualMatched = countMatched(actualTokens, expectedTokens);

        double precision = actualTotal == 0 ? 0.0 : (double) actualMatched / actualTotal;
        double recall = expectedTotal == 0 ? 0.0 : (double) expectedMatched / expectedTotal;
        return new double[] {clamp01(precision), clamp01(recall)};
    }

    /** 归一化编辑距离相似度：1 - Levenshtein(期望平铺, 预测平铺) / 最大长度 */
    private static double levenshteinSimilarity(List<List<String>> expectedRows, List<List<String>> actualRows) {
        String expectedFlat = flatten(expectedRows);
        String actualFlat = flatten(actualRows);
        int maxLen = Math.max(expectedFlat.length(), actualFlat.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int dist = levenshteinDistance(expectedFlat, actualFlat);
        return clamp01(1.0 - ((double) dist / maxLen));
    }

    /** 将网格按阅读顺序平铺为单一文本：单元格 " | " 分隔，行用换行分隔，内部空白压缩为单空格 */
    private static String flatten(List<List<String>> grid) {
        if (grid == null || grid.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < grid.size(); r++) {
            if (r > 0) {
                sb.append('\n');
            }
            List<String> row = grid.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    sb.append(" | ");
                }
                sb.append(collapseWhitespace(row.get(c)));
            }
        }
        return sb.toString();
    }

    /** 去除首尾空白并把连续空白折叠为单个空格（保留单元格内部语义词分隔） */
    private static String collapseWhitespace(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    /** 统计网格内所有单元格的语义 token 频次（多重集合） */
    private static Map<String, Integer> tokenCounts(List<List<String>> grid) {
        Map<String, Integer> counts = new HashMap<>();
        if (grid == null) {
            return counts;
        }
        for (List<String> row : grid) {
            if (row == null) {
                continue;
            }
            for (String cell : row) {
                if (cell == null) {
                    continue;
                }
                Matcher matcher = TOKEN_PATTERN.matcher(cell);
                while (matcher.find()) {
                    counts.merge(matcher.group(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /** 源 token 在目标 token 多重集合中的命中数（尊重重复出现次数） */
    private static int countMatched(Map<String, Integer> source, Map<String, Integer> target) {
        int matched = 0;
        Map<String, Integer> remaining = new HashMap<>(target);
        for (Map.Entry<String, Integer> e : source.entrySet()) {
            int available = remaining.getOrDefault(e.getKey(), 0);
            int want = e.getValue();
            int hit = Math.min(available, want);
            matched += hit;
            if (hit > 0) {
                remaining.put(e.getKey(), available - hit);
            }
        }
        return matched;
    }

    private static int sum(Map<String, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static double f1(double precision, double recall) {
        double denom = precision + recall;
        return denom == 0 ? 0.0 : 2.0 * precision * recall / denom;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 1.0);
    }

    /** 经典 Levenshtein 编辑距离（DP，O(n*m)） */
    private static int levenshteinDistance(String a, String b) {
        if (a.isEmpty()) {
            return b.length();
        }
        if (b.isEmpty()) {
            return a.length();
        }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
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
            log.error("TCP 探活失败 ({})，将跳过本组多模态解析单元测试。", baseUrl, e);
            return false;
        }
    }
}
