package cn.net.pap.example.spring.ai.tool;

import cn.net.pap.common.core.search.FileSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 {@link FileSearch} 的进程内文件检索智能体工具。
 * <p>
 * 提供毫秒级零初始化的文件名查找（findFiles）与内容精确搜索（grepContent），
 * 专用于补充大模型在生僻函数名、代码符号检索上的语义漂移短板。
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
     * 工具 1：按通配符或扩展名搜索文件路径。
     *
     * @param pattern 文件通配符模式，如 {@code *.md}、{@code *syntax*}
     * @return 匹配的文件相对路径列表字符串
     */
    @Tool(name = "FileSearchTool-findFiles", description = "根据通配符模式在本地知识库中查找匹配的文件相对路径列表（例如查找 *.md 或包含特定名称的文档）")
    public String findFiles(
            @ToolParam(description = "文件名匹配通配符，如 *.md、*operator* 等") String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return "错误：通配符参数不能为空";
        }
        try {
            FileSearch.SearchOptions options = FileSearch.SearchOptions.ofKeywords("")
                    .withIncludeGlobs(Set.of(pattern))
                    .withMaxLines(MAX_FIND_FILES_LINES);

            List<Path> matchedPaths = FileSearch.findFiles(sandboxRoot, options);
            if (matchedPaths.isEmpty()) {
                return "未找到匹配的文件: " + pattern;
            }

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
        if (keyword == null || keyword.isBlank()) {
            return "错误：检索关键词不能为空";
        }
        try {
            FileSearch.SearchOptions options = FileSearch.SearchOptions.ofKeywords(keyword)
                    .withCaseSensitive(false)
                    .withContextLines(CONTEXT_LINES)
                    .withMaxLines(MAX_GREP_LINES);

            List<FileSearch.FileMatch> matches = FileSearch.grep(sandboxRoot, options);
            if (matches.isEmpty()) {
                return "未在文档中检索到关键词: " + keyword;
            }

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
        if (path == null || path.isBlank()) {
            return "错误：文档路径不能为空";
        }
        try {
            Path target = sandboxRoot.resolve(path).normalize();
            // 防止 ../../ 目录穿越读取沙箱外的文件
            if (!target.startsWith(sandboxRoot)) {
                return "错误：路径越界，仅允许访问知识库目录内的文档";
            }
            if (!Files.isRegularFile(target)) {
                return "未找到文档: " + path + "，请先调用 FileSearchTool-findFiles 确认路径";
            }

            String text = Files.readString(target, StandardCharsets.UTF_8);
            if (text.length() > MAX_READ_CHARS) {
                text = text.substring(0, MAX_READ_CHARS) + "\n...(文档过长已截断，剩余内容请用 FileSearchTool-grepContent 按关键词检索)";
            }
            return "### 文件: " + sandboxRoot.relativize(target).toString().replace('\\', '/') + "\n" + text;
        } catch (IOException e) {
            log.error("[FileSearchTool] 文档读取失败, path: {}", path, e);
            return "读取异常: " + e.getMessage();
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
