package cn.net.pap.example.spring.ai.tool;

import cn.net.pap.common.core.search.FileSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;
import reactor.util.context.ContextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于 {@link FileSearch} 的进程内文件检索智能体工具。
 * <p>
 * 提供毫秒级零初始化的文件名查找（findFiles）与内容精确搜索（grepContent），
 * 配合目录树探索（listDir）、大纲提取（getOutline）、区间切片读取（readLines/readDoc）
 * 以及原文引用硬校验（quoteEvidence），构成带 Guardrail 原文依赖核验的 Agent 检索工具链。
 */
@Component
public class FileSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FileSearchTool.class);

    /** 单次路径查找最大放行文件数，防止塞爆提示词上下文 */
    private static final long MAX_FIND_FILES_LINES = 20;

    /** 单次内容检索最大放行匹配行数 */
    private static final long MAX_GREP_LINES = 30;

    /** 命中行前后上下文行数 */
    private static final int CONTEXT_LINES = 1;

    /** 单次整文档读取最大字符数，防止把一篇长文塞爆上下文 */
    private static final int MAX_READ_CHARS = 12_000;

    /** 单次切片读取最大放行行数，防止大范围读取塞爆提示词上下文 */
    private static final int MAX_READ_LINES = 200;

    /** 单次目录树遍历最大放行条目数 */
    private static final int MAX_LIST_DIR_ENTRIES = 60;

    /** 单次大纲最大标题条数 */
    private static final int MAX_OUTLINE_ITEMS = 60;

    /** 原文引用核验最小归一化有效字符数，防止单字/单词糊弄过关 */
    private static final int MIN_QUOTE_NORMALIZED_LENGTH = 8;

    /** 单次会话原文引用核验最大连续打回重试次数（超限触发熔断降级，防死循环） */
    private static final int MAX_QUOTE_FAIL_ATTEMPTS = 2;

    /** 尾帧展示单条原文摘录的最大预览字符数 */
    private static final int MAX_QUOTE_PREVIEW_CHARS = 80;

    private final Path sandboxRoot;

    /**
     * 构造文件检索工具，自适应探测沙箱根目录并进行规范化保护。
     *
     * @param configuredRoot 用户自定义配置的根路径，若为空则自适应定位默认知识库路径
     */
    public FileSearchTool(@Value("${ai.search.sandbox-root:}") String configuredRoot) {
        this.sandboxRoot = resolveSandboxRoot(configuredRoot);
        log.info("[FileSearchTool] 知识库检索沙箱已就绪，根目录: {}", this.sandboxRoot);
    }

    /**
     * 获取沙箱根路径。
     *
     * @return 绝对规范化路径
     */
    public Path getSandboxRoot() {
        return sandboxRoot;
    }

    /**
     * 将当前工具的执行动作与结果摘要推送到响应式上下文关联的流式通知通道中，
     * 用于实时同步至前端作为 Agent 思考（Thinking）的一部分展现。
     *
     * @param text 待推送的活动摘要文本
     */
    private void emitActivity(String text) {
        try {
            ContextView ctx = ToolCallReactiveContextHolder.getContext();
            if (ctx == null || !ctx.hasKey(AgentTool.ACTIVITY_SINK_KEY)) {
                return;
            }
            Sinks.Many<String> sink = ctx.get(AgentTool.ACTIVITY_SINK_KEY);
            if (sink == null) {
                return;
            }
            // 累计序号：本节会话第几次工具动作，包裹后插入轨迹行首
            int callNo = ctx.hasKey(AgentTool.TOOL_CALL_COUNTER_KEY)
                    && ctx.get(AgentTool.TOOL_CALL_COUNTER_KEY) instanceof AtomicInteger counter
                    ? counter.incrementAndGet() : 0;
            sink.tryEmitNext(callNo > 0
                    ? text.replaceFirst("^(\\s*>\\s*)", "$1`#" + callNo + "` ")
                    : text);
        } catch (Exception e) {
            log.error("[FileSearchTool] 反应式通知发送异常", e);
        }
    }

    /**
     * 工具 1：按通配符或扩展名搜索文件路径。
     *
     * @param pattern 文件通配符模式，如 {@code *.md}、{@code *syntax*}
     * @return 匹配的文件相对路径列表字符串
     */
    @Tool(name = "FileSearchTool-findFiles", description = "根据通配符模式在本地知识库中查找匹配的文件相对路径列表（例如查找 *.md 或包含特定名称的文档）")
    public String findFiles(
            @ToolParam(description = "文件名匹配通配符，如 *.md、*operator* 等") String pattern) {
        emitActivity("\n> 🔍 **[智能体文件检索]** 检索模式: `" + pattern + "`\n");
        if (pattern == null || pattern.isBlank()) {
            emitActivity("> ❌ **[检索失败]** 通配符参数为空\n\n");
            return "错误：通配符参数不能为空";
        }
        try {
            FileSearch.SearchOptions options = FileSearch.SearchOptions.ofKeywords("")
                    .withIncludeGlobs(Set.of(pattern))
                    .withMaxLines(MAX_FIND_FILES_LINES);

            List<Path> matchedPaths = FileSearch.findFiles(sandboxRoot, options);
            if (matchedPaths.isEmpty()) {
                emitActivity("> ⚠️ **[检索反馈]** 未找到匹配的文件: `" + pattern + "`\n\n");
                return "未找到匹配的文件: " + pattern;
            }

            emitActivity("> 📄 **[检索反馈]** 匹配到 " + matchedPaths.size() + " 个相关文档\n\n");
            return matchedPaths.stream()
                    .map(p -> sandboxRoot.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.error("[FileSearchTool] 文件路径查找失败, pattern: {}", pattern, e);
            return "检索异常: " + e.getMessage();
        }
    }

    /**
     * 工具 2：按关键词或专有名词在文档内容中进行精确检索（Grep）。
     *
     * @param keyword 待检索的关键词或代码符号
     * @return 包含文件相对路径、行号及命中内容的代码文本片段
     */
    @Tool(name = "FileSearchTool-grepContent", description = "在知识库文件中按关键词或专有名词精确搜索内容，返回命中的文件、行号以及对应代码片段")
    public String grepContent(
            @ToolParam(description = "要搜索的专有名词、函数名或关键词，例如：TREE_FLATTEN、ISBLANK") String keyword) {
        emitActivity("\n> 🔎 **[智能体内容精确检索]** 检索关键词: `" + keyword + "`\n");
        if (keyword == null || keyword.isBlank()) {
            emitActivity("> ❌ **[检索失败]** 关键词为空\n\n");
            return "错误：检索关键词不能为空";
        }
        try {
            FileSearch.SearchOptions options = FileSearch.SearchOptions.ofKeywords(keyword)
                    .withCaseSensitive(false)
                    .withContextLines(CONTEXT_LINES)
                    .withMaxLines(MAX_GREP_LINES);

            List<FileSearch.FileMatch> matches = FileSearch.grep(sandboxRoot, options);
            if (matches.isEmpty()) {
                emitActivity("> ⚠️ **[检索反馈]** 未在文档中检索到关键词: `" + keyword + "`\n\n");
                return "未在文档中检索到关键词: " + keyword;
            }

            emitActivity("> 📄 **[检索反馈]** 命中 " + matches.size() + " 篇文档，准备提取匹配行\n\n");
            StringBuilder sb = new StringBuilder();
            for (FileSearch.FileMatch fileMatch : matches) {
                String relative = sandboxRoot.relativize(fileMatch.file()).toString().replace('\\', '/');
                sb.append("### 文件: ").append(relative).append("\n");
                for (FileSearch.LineMatch line : fileMatch.lines()) {
                    sb.append("L").append(line.lineNumber()).append(": ").append(line.text()).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("[FileSearchTool] 内容检索失败, keyword: {}", keyword, e);
            return "检索异常: " + e.getMessage();
        }
    }

    /**
     * 工具 3：读取单篇文档的完整内容。
     * <p>为什么需要它：findFiles 只返回路径，grepContent 必须带关键词且只回命中行，
     * 两者都无法把一篇文档完整喂给模型，会导致模型说“我没有读取文件的工具”。
     *
     * @param path 相对于知识库根目录的文档路径，例如 {@code _index.md}
     * @return 文档正文；越界、不存在或过大时返回可预期的错误文本
     */
    @Tool(name = "FileSearchTool-readDoc", description = "读取知识库中指定文档的完整正文，path 使用 findFiles 返回的相对路径（例如 _index.md）")
    public String readDoc(
            @ToolParam(description = "文档相对路径，如 _index.md、function_isblank.md") String path) {
        emitActivity("\n> 📖 **[智能体文档阅读]** 阅读文档: `" + path + "`\n");
        if (path == null || path.isBlank()) {
            emitActivity("> ❌ **[读取失败]** 路径为空\n\n");
            return "错误：文档路径不能为空";
        }
        try {
            Path target = sandboxRoot.resolve(path).normalize();
            // 防止 ../../ 目录穿越读取沙箱外的文件
            if (!target.startsWith(sandboxRoot)) {
                emitActivity("> ❌ **[安全拦截]** 路径越界沙箱\n\n");
                return "错误：路径越界，仅允许访问知识库目录内的文档";
            }
            if (!Files.isRegularFile(target)) {
                emitActivity("> ⚠️ **[读取反馈]** 文档不存在: `" + path + "`\n\n");
                return "未找到文档: " + path + "，请先调用 FileSearchTool-findFiles 确认路径";
            }

            String text = Files.readString(target, StandardCharsets.UTF_8);
            emitActivity("> 📄 **[读取反馈]** 成功载入正文 (" + text.length() + " 字符)\n\n");
            if (text.length() > MAX_READ_CHARS) {
                text = text.substring(0, MAX_READ_CHARS) + "\n...(文档过长已截断，剩余内容请用 FileSearchTool-readLines 或 FileSearchTool-grepContent 按需检索)";
            }
            return "### 文件: " + sandboxRoot.relativize(target).toString().replace('\\', '/') + "\n" + text;
        } catch (IOException e) {
            log.error("[FileSearchTool] 文档读取失败, path: {}", path, e);
            emitActivity("> ❌ **[读取异常]** " + e.getMessage() + "\n\n");
            return "读取异常: " + e.getMessage();
        }
    }

    /**
     * 工具 4：指定行号区间读取文档正文（视窗切片读取）。
     * <p>配合 grepContent 的行号返回，按需精准切片上下文，避免大篇幅读取塞爆提示词。
     *
     * @param path 文档相对路径
     * @param startLine 起始行号（从 1 开始，包含）
     * @param endLine 结束行号（包含）
     * @return 指定行号区间的文本片段
     */
    @Tool(name = "FileSearchTool-readLines", description = "读取知识库文档中指定行号区间的正文（如第 100 行至 150 行）。当通过 grepContent 获得目标行号后，优先使用此工具按需切片读取上下文，避免整篇长文塞爆上下文。")
    public String readLines(
            @ToolParam(description = "文档相对路径，如 docs/functions/tree.md 或 _index.md") String path,
            @ToolParam(description = "起始行号（从 1 开始，包含）") int startLine,
            @ToolParam(description = "结束行号（包含）") int endLine) {
        emitActivity("\n> 📑 **[智能体视窗切片读取]** `" + path + "` (L" + startLine + "-L" + endLine + ")\n");
        if (path == null || path.isBlank()) {
            emitActivity("> ❌ **[读取失败]** 路径为空\n\n");
            return "错误：文档路径不能为空";
        }
        try {
            Path target = sandboxRoot.resolve(path).normalize();
            if (!target.startsWith(sandboxRoot)) {
                return "错误：路径越界，仅允许访问知识库目录内的文档";
            }
            if (!Files.isRegularFile(target)) {
                return "未找到文档: " + path + "，请先调用 FileSearchTool-findFiles 或 FileSearchTool-listDir 确认路径";
            }

            int start = Math.max(1, startLine);
            int end = Math.max(start, endLine);
            if (end - start + 1 > MAX_READ_LINES) {
                end = start + MAX_READ_LINES - 1;
            }

            FileSearch.LineSlice slice = FileSearch.readLines(target, start, end, MAX_READ_CHARS);
            if (slice.totalLines() == 0) {
                emitActivity("> ⚠️ **[切片反馈]** 内容为空: `" + path + "`\n\n");
                return "文档内容为空: " + path;
            }
            if (start > slice.totalLines()) {
                emitActivity("> ❌ **[切片反馈]** 起始行超限\n\n");
                return "错误：起始行号 " + start + " 超过文档总行数 (" + slice.totalLines() + " 行)";
            }

            emitActivity("> 📄 **[切片反馈]** 成功切片读取 " + slice.lines().size() + " 行内容\n\n");

            StringBuilder sb = new StringBuilder();
            String relative = sandboxRoot.relativize(target).toString().replace('\\', '/');
            sb.append("### 文件: ").append(relative).append(" (L").append(slice.startLine()).append("-L").append(slice.endLine()).append(" / 共 ").append(slice.totalLines()).append(" 行)\n");

            for (int i = 0; i < slice.lines().size(); i++) {
                sb.append("L").append(slice.startLine() + i).append(": ").append(slice.lines().get(i)).append("\n");
            }
            if (slice.truncatedByChars()) {
                sb.append("...(内容已达单次最大字符上限，后续行已截断)\n");
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("[FileSearchTool] 行区间读取失败, path: {}, range: [{}, {}]", path, startLine, endLine, e);
            return "读取异常: " + e.getMessage();
        }
    }

    /**
     * 工具 5：列出知识库指定目录下的文件与子目录树。
     * <p>用于面对宏观问题或不清楚知识库模块结构时，先看目录结构树建立全局视野。
     *
     * @param path 目录相对路径，根目录传入空字符串或 "."
     * @param maxDepth 递归深度，默认为 2，最大不超过 3
     * @return 格式化后的树状层级列表
     */
    @Tool(name = "FileSearchTool-listDir", description = "列出知识库指定目录下的文件与子目录树。当你面对宏观问题、不清楚模块分类或寻找特定文档路径时优先调用。")
    public String listDir(
            @ToolParam(description = "目录相对路径，根目录可传入空字符串、'.' 或特定子目录如 'functions'") String path,
            @ToolParam(description = "目录递归深度，默认为 2，最大不超过 3") Integer maxDepth) {
        emitActivity("\n> 📁 **[智能体目录探测]** 探测路径: `" + (path == null || path.isBlank() ? "/" : path) + "` (深度: " + maxDepth + ")\n");
        try {
            Path target = (path == null || path.isBlank() || ".".equals(path.trim()))
                    ? sandboxRoot
                    : sandboxRoot.resolve(path.trim()).normalize();
            if (!target.startsWith(sandboxRoot)) {
                emitActivity("> ❌ **[安全拦截]** 路径越界沙箱\n\n");
                return "错误：路径越界，仅允许访问知识库沙箱目录";
            }
            if (!Files.exists(target)) {
                emitActivity("> ⚠️ **[探测反馈]** 目录不存在\n\n");
                return "目录不存在: " + path;
            }
            if (!Files.isDirectory(target)) {
                emitActivity("> ⚠️ **[探测反馈]** 目标不是目录\n\n");
                return "指定路径不是目录: " + path + "，如需读取文件请调用 FileSearchTool-readDoc 或 FileSearchTool-readLines";
            }

            int depth = (maxDepth == null || maxDepth <= 0) ? 2 : Math.min(maxDepth, 3);
            List<FileSearch.DirEntry> entries = FileSearch.listDirTree(target, depth, MAX_LIST_DIR_ENTRIES);

            if (entries.isEmpty()) {
                emitActivity("> 📄 **[探测反馈]** 目录为空\n\n");
                return "目录为空: " + (target.equals(sandboxRoot) ? "/" : sandboxRoot.relativize(target).toString().replace('\\', '/'));
            }

            emitActivity("> 📄 **[探测反馈]** 发现 " + entries.size() + " 个文件/子目录项\n\n");

            StringBuilder sb = new StringBuilder();
            String rootLabel = target.equals(sandboxRoot) ? "/" : sandboxRoot.relativize(target).toString().replace('\\', '/');
            sb.append("### 目录树: ").append(rootLabel).append("\n");

            for (FileSearch.DirEntry entry : entries) {
                String indent = "  ".repeat(Math.max(0, entry.depth() - 1));
                if (entry.isDirectory()) {
                    sb.append(indent).append("📁 ").append(entry.name()).append("/\n");
                } else {
                    sb.append(indent).append("📄 ").append(entry.name()).append("\n");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("[FileSearchTool] 目录列表读取失败, path: {}", path, e);
            return "遍历异常: " + e.getMessage();
        }
    }

    /**
     * 工具 6：提取指定 Markdown 文档的大纲标题层级（H1~H4）及其对应起始行号。
     * <p>极低 Token 代价提取 TOC 目录，定位需要阅读的章节行号范围。
     *
     * @param path 文档相对路径
     * @return 包含行号的标题大纲清单
     */
    @Tool(name = "FileSearchTool-getOutline", description = "提取指定 Markdown 文档的标题大纲（H1~H4）及其对应起始行号。用于快速了解长文档结构、定位目标章节行号范围，避免全篇盲读。")
    public String getOutline(
            @ToolParam(description = "文档相对路径，如 docs/functions/tree_flatten.md") String path) {
        emitActivity("\n> 📑 **[智能体提取文档大纲]** 大纲提取: `" + path + "`\n");
        if (path == null || path.isBlank()) {
            emitActivity("> ❌ **[提取失败]** 路径为空\n\n");
            return "错误：文档路径不能为空";
        }
        try {
            Path target = sandboxRoot.resolve(path).normalize();
            if (!target.startsWith(sandboxRoot)) {
                emitActivity("> ❌ **[安全拦截]** 路径越界沙箱\n\n");
                return "错误：路径越界，仅允许访问知识库沙箱目录内的文档";
            }
            if (!Files.isRegularFile(target)) {
                emitActivity("> ⚠️ **[提取反馈]** 文档不存在: `" + path + "`\n\n");
                return "未找到文档: " + path + "，请先调用 FileSearchTool-findFiles 或 FileSearchTool-listDir 确认路径";
            }

            List<FileSearch.MarkdownHeading> headings = FileSearch.extractMarkdownOutline(target, MAX_OUTLINE_ITEMS);
            emitActivity("> 📄 **[提取反馈]** 成功提取 " + headings.size() + " 个层级标题\n\n");
            String relative = sandboxRoot.relativize(target).toString().replace('\\', '/');
            if (headings.isEmpty()) {
                return "文档未包含标准 Markdown 标题大纲: " + relative + " (建议直接使用 FileSearchTool-readLines 或 FileSearchTool-readDoc 查看内容)";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("### 文档大纲: ").append(relative).append("\n");

            for (FileSearch.MarkdownHeading h : headings) {
                if (h.level() <= 4) {
                    String indent = "  ".repeat(Math.max(0, h.level() - 1));
                    sb.append("L").append(h.lineNumber()).append(": ")
                            .append(indent).append("#".repeat(h.level())).append(" ")
                            .append(h.title()).append("\n");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            log.error("[FileSearchTool] 文档大纲提取失败, path: {}", path, e);
            return "大纲提取异常: " + e.getMessage();
        }
    }

    /**
     * 工具 7：原文引用硬校验工具（Citation Verification）。
     * <p>
     * 模型在读取文档后、输出最终回答前调用此工具，提交所依据的文档相对路径及逐字摘录的原文关键句。
     * 工具内部直接读取目标文件执行归一化子串硬匹配，若篡改或不存在则当场打回重核。
     *
     * @param path 文档相对路径，例如 {@code function_div2.md}
     * @param verbatimQuote 从文档中逐字原样复制的原文关键片段
     * @return 核验通过凭证或打回重核指令文本
     */
    @Tool(name = "FileSearchTool-quoteEvidence", description = "【原文引用硬校验工具】在调用 readDoc/readLines/grepContent 获取文档内容后、输出最终回答前，必须调用此工具提交你所依据的文档相对路径及逐字摘录的原文关键片段。系统将对原文执行硬校验。")
    public String quoteEvidence(
            @ToolParam(description = "文档相对路径，如 function_div2.md") String path,
            @ToolParam(description = "从文档中逐字原样复制的原文关键句或代码片段（不少于 8 个有效字符，严禁改写或用省略号跨段拼接）") String verbatimQuote) {
        emitActivity("\n> 🛡️ **[智能体原文引用核验]** 核验文档: `" + path + "`\n");
        if (path == null || path.isBlank() || verbatimQuote == null || verbatimQuote.isBlank()) {
            emitActivity("> ❌ **[核验失败]** 文档路径或原文摘录为空\n\n");
            return "【校验失败】文档路径与原文摘录均不能为空，请传入真实路径与原文关键句。";
        }

        Path target = sandboxRoot.resolve(path.trim()).normalize();
        if (!target.startsWith(sandboxRoot)) {
            emitActivity("> ❌ **[安全拦截]** 引用路径越界沙箱\n\n");
            return "【校验失败】路径越界，仅允许引用知识库目录内的文档。";
        }

        EvidenceLedger ledger = getCurrentLedger();
        String relativePath = sandboxRoot.relativize(target).toString().replace('\\', '/');
        if (!Files.isRegularFile(target)) {
            return handleQuoteFailure(ledger,
                    "未找到文档: " + relativePath + "，请先调用 FileSearchTool-findFiles 确认真实文档路径！");
        }

        String normalizedQuote = normalizeForMatch(verbatimQuote);
        if (normalizedQuote.length() < MIN_QUOTE_NORMALIZED_LENGTH) {
            return handleQuoteFailure(ledger,
                    "提交的原文片段有效字符过少（不足 " + MIN_QUOTE_NORMALIZED_LENGTH + " 个字符），请摘录包含完整业务结论或语法定义的原文整句！");
        }

        return verifyQuoteInFile(target, relativePath, verbatimQuote.trim(), normalizedQuote, ledger);
    }

    private String verifyQuoteInFile(Path target, String relativePath, String rawQuote,
                                     String normalizedQuote, EvidenceLedger ledger) {
        try {
            List<String> lines = Files.readAllLines(target, StandardCharsets.UTF_8);
            VerifiedCitation citation = matchCitationInLines(ledger, relativePath, lines, rawQuote, normalizedQuote);
            if (citation == null) {
                return handleQuoteFailure(ledger,
                        "你提交的原文片段在 " + relativePath + " 中不存在（可能被改写或跨段拼接），请重新调用 FileSearchTool-readLines 核对原文并逐字复制，严禁编造！");
            }
            if (ledger != null) {
                ledger.resetFailCount();
                ledger.add(citation);
            }
            emitActivity("> ✅ **[原文硬校验通过]** `[" + citation.refId() + "]` `" + relativePath
                    + "` (L" + citation.startLine() + "-L" + citation.endLine() + ")\n\n");
            return "【引用核验通过】编号 [" + citation.refId() + "]，来源文档: " + relativePath
                    + " (L" + citation.startLine() + "-L" + citation.endLine() + ")。现在可以基于核验通过的原文回答用户问题。";
        } catch (IOException e) {
            log.error("[FileSearchTool] 原文引用核验读盘异常, path: {}", relativePath, e);
            return "【校验异常】读取文档失败: " + e.getMessage();
        }
    }

    private VerifiedCitation matchCitationInLines(EvidenceLedger ledger, String relativePath, List<String> lines,
                                                  String rawQuote, String normalizedQuote) {
        if (lines == null || lines.isEmpty() || normalizedQuote.isEmpty()) {
            return null;
        }
        StringBuilder fullNormalized = new StringBuilder();
        int[] lineEndOffsets = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            fullNormalized.append(normalizeForMatch(lines.get(i)));
            lineEndOffsets[i] = fullNormalized.length();
        }

        int matchStart = fullNormalized.indexOf(normalizedQuote);
        if (matchStart < 0) {
            return null;
        }
        int matchEnd = matchStart + normalizedQuote.length() - 1;

        int startLine = 1;
        int endLine = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lineEndOffsets[i] > matchStart) {
                startLine = i + 1;
                break;
            }
        }
        for (int i = startLine - 1; i < lines.size(); i++) {
            if (lineEndOffsets[i] > matchEnd) {
                endLine = i + 1;
                break;
            }
        }
        String refId = ledger != null ? ledger.nextRefId() : "Ref-1";
        return new VerifiedCitation(refId, relativePath, startLine, endLine, rawQuote);
    }

    private EvidenceLedger getCurrentLedger() {
        try {
            ContextView ctx = ToolCallReactiveContextHolder.getContext();
            if (ctx != null && ctx.hasKey(AgentTool.EVIDENCE_LEDGER_KEY)) {
                return ctx.get(AgentTool.EVIDENCE_LEDGER_KEY);
            }
        } catch (Exception e) {
            log.error("[FileSearchTool] 获取证据账本异常", e);
        }
        return null;
    }

    private String handleQuoteFailure(EvidenceLedger ledger, String reason) {
        int fails = ledger != null ? ledger.incrementAndGetFailCount() : 1;
        if (fails > MAX_QUOTE_FAIL_ATTEMPTS) {
            emitActivity("> ⚠️ **[原文校验熔断]** 连续核验未通过达上限 (" + fails + " 次)，终止重试并要求如实拒答\n\n");
            return "【核验终止】原文连续核验未通过，请停止调用工具并直接回复用户：【知识库中未找到可核验的原文依据】，绝不可凭空作答。";
        }
        emitActivity("> ❌ **[原文校验打回]** " + reason + "\n\n");
        return "【校验失败】" + reason;
    }

    /**
     * 对文本进行去噪归一化：剥离空白符、换行符与 Markdown 排版修饰符，统一中英文标点与大小写，
     * 使得子串比对既能 100% 拦截字词篡改，又不会因换行或空格排版差异误杀真实引用。
     *
     * @param input 原始文本
     * @return 归一化后的紧凑文本
     */
    public static String normalizeForMatch(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return input.replaceAll("[\\s*#`>\\-|~]+", "")
                .replace('（', '(')
                .replace('）', ')')
                .replace('：', ':')
                .replace('，', ',')
                .replace('；', ';')
                .replace('“', '"')
                .replace('”', '"')
                .replace('‘', '\'')
                .replace('’', '\'')
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 核验通过的单条原文引用凭证。
     *
     * @param refId 引用编号（如 {@code Ref-1}）
     * @param relativePath 文档相对路径（如 {@code function_div2.md}）
     * @param startLine 命中的起始行号（从 1 开始）
     * @param endLine 命中的结束行号（包含）
     * @param verbatimQuote 模型提交并核验通过的原文摘录
     */
    public record VerifiedCitation(String refId, String relativePath, int startLine, int endLine, String verbatimQuote) {
    }

    /**
     * 请求级已核验引用收集器（维护单次对话中通过逐字子串硬校验的引用明细与连续失败计数）。
     */
    public static final class EvidenceLedger {

        private final List<VerifiedCitation> verifiedCitations = new CopyOnWriteArrayList<>();
        private final AtomicInteger refSeq = new AtomicInteger(0);
        private final AtomicInteger quoteFailCount = new AtomicInteger(0);

        void add(VerifiedCitation citation) {
            if (citation != null) {
                verifiedCitations.add(citation);
            }
        }

        String nextRefId() {
            return "Ref-" + refSeq.incrementAndGet();
        }

        /**
         * 获取所有通过原文子串硬校验的引用列表。
         *
         * @return 核验通过的引用列表
         */
        public List<VerifiedCitation> getVerifiedCitations() {
            return Collections.unmodifiableList(verifiedCitations);
        }

        /**
         * 递增并获取当前连续引用核验失败次数。
         *
         * @return 递增后的连续失败次数
         */
        public int incrementAndGetFailCount() {
            return quoteFailCount.incrementAndGet();
        }

        /**
         * 核验通过时清零连续失败计数器。
         */
        public void resetFailCount() {
            quoteFailCount.set(0);
        }

        /**
         * 生成直接拼接在流式输出末尾的 Guardrail 出口核验与文档相对路径溯源 Markdown 字符串。
         * <p>若无核验通过的引用（例如日常问候或拒答场景），返回空串 {@code ""}。
         *
         * @return 格式化后的 Markdown 尾部区块字符串；无引用时返回 {@code ""}
         */
        public String formatGuardrailFooter() {
            if (verifiedCitations.isEmpty()) {
                return "";
            }
            String pathList = verifiedCitations.stream()
                    .map(VerifiedCitation::relativePath)
                    .distinct()
                    .map(p -> "`" + p + "`")
                    .collect(Collectors.joining(", "));
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n---\n> 🛡️ **[Guardrail 原文依赖硬校验通过]**\n");
            sb.append("> - 📂 **使用文档相对路径**: ").append(pathList).append("\n");
            sb.append("> - 📌 **原文引用核验明细**:\n");
            for (VerifiedCitation c : verifiedCitations) {
                String preview = c.verbatimQuote().replaceAll("\\s+", " ").trim();
                if (preview.length() > MAX_QUOTE_PREVIEW_CHARS) {
                    preview = preview.substring(0, MAX_QUOTE_PREVIEW_CHARS) + "...";
                }
                sb.append(">   - `[").append(c.refId()).append("]` `").append(c.relativePath())
                        .append("` (L").append(c.startLine()).append("-L").append(c.endLine())
                        .append("): *\"").append(preview).append("\"*\n");
            }
            return sb.toString();
        }
    }

    private static Path resolveSandboxRoot(String configuredRoot) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            return Paths.get(configuredRoot).toAbsolutePath().normalize();
        }

        // 自适应探测：多模块根目录模式或子模块目录模式
        Path p1 = Paths.get("pap4j-boot3-example/pap4j-boot3-example-spring-ai/src/main/resources/knowledge/docs");
        if (Files.exists(p1)) {
            return p1.toAbsolutePath().normalize();
        }

        Path p2 = Paths.get("src/main/resources/knowledge/docs");
        if (Files.exists(p2)) {
            return p2.toAbsolutePath().normalize();
        }

        // 默认保底当前目录
        return Paths.get(".").toAbsolutePath().normalize();
    }
}
