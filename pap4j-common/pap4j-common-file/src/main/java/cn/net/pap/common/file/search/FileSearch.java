package cn.net.pap.common.file.search;

import cn.net.pap.common.file.ReadTxtToStringUtil;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 对标 ripgrep 的进程内文件检索工具：过滤遍历（Glob） + 内容检索（Grep） + 片段读取（Read）。
 * <p>
 * 设计要点：
 * <ul>
 *   <li><b>零初始化</b>：不建索引，每次调用实时读取当前文件系统状态，文件增删改立刻反映；</li>
 *   <li><b>进程内</b>：不 spawn 外部进程、不依赖原生库，仅引入 {@code org.ahocorasick} 做多关键词匹配；</li>
 *   <li><b>零逐行分配</b>：整文件一次解码后按行区间匹配，只为命中的行构建结果；</li>
 *   <li><b>跨文件并行</b>：默认通过 {@code parallelStream}（ForkJoin 公共池）处理候选文件；</li>
 *   <li><b>匹配策略自动选择</b>：单字面量走 {@link String#indexOf(String)}，多关键词走 Aho-Corasick，正则走 {@link Pattern}（完整语义，无限制）。</li>
 * </ul>
 * <p>
 * 所有过滤项均为可选：默认无深度/大小/类型限制，{@code .gitignore} 默认关闭（全量搜索）。
 */
public final class FileSearch {

    private static final Logger log = LoggerFactory.getLogger(FileSearch.class);

    private static final int BINARY_PROBE_BYTES = 8192;

    private FileSearch() {
    }

    /**
     * 检索选项。{@link #keywords()} 与 {@link #regex()} 二选一（regex 优先），提供内容检索时至少需设置其一。
     */
    public record SearchOptions(
            Set<String> keywords,
            Pattern regex,
            Set<String> includeGlobs,
            Set<String> excludeGlobs,
            Set<String> extensions,
            boolean honorGitignore,
            boolean detectCharset,
            boolean skipBinary,
            boolean caseSensitive,
            int contextLines,
            int maxDepth,
            long maxFileSize,
            long inlineReadLimit,
            long maxLines,
            boolean parallel) {

        /** 文件超过该字节数时，grep 改用流式行匹配以控制堆占用；0 表示始终整读（不流式）。 */
        public static final long DEFAULT_INLINE_READ_LIMIT = 16L * 1024 * 1024;

        /** grep 返回的总行数上限（避免命中过多塞爆下游上下文）；0 表示不限。 */
        public static final long DEFAULT_MAX_LINES = 1000;

        /** 仅按关键词检索（多关键词走 Aho-Corasick，单关键词走 indexOf）。 */
        public static SearchOptions ofKeywords(String... keywords) {
            return new SearchOptions(toSet(keywords), null, null, null, null,
                    false, false, true, true, 0, 0, 0, DEFAULT_INLINE_READ_LIMIT, DEFAULT_MAX_LINES, true);
        }

        /** 按正则检索（完整 {@code java.util.regex} 语义）。 */
        public static SearchOptions ofRegex(String regex) {
            return ofRegex(Pattern.compile(regex));
        }

        /** 按预编译正则检索。 */
        public static SearchOptions ofRegex(Pattern pattern) {
            return new SearchOptions(null, pattern, null, null, null,
                    false, false, true, true, 0, 0, 0, DEFAULT_INLINE_READ_LIMIT, DEFAULT_MAX_LINES, true);
        }

        private static Set<String> toSet(String... values) {
            if (values == null || values.length == 0) {
                return null;
            }
            Set<String> set = new HashSet<>();
            for (String v : values) {
                if (v != null && !v.isEmpty()) {
                    set.add(v);
                }
            }
            return set.isEmpty() ? null : Collections.unmodifiableSet(set);
        }

        public SearchOptions withIncludeGlobs(Set<String> globs) {
            return new SearchOptions(keywords, regex, globs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withExcludeGlobs(Set<String> globs) {
            return new SearchOptions(keywords, regex, includeGlobs, globs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withExtensions(Set<String> exts) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, exts,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withHonorGitignore(boolean honor) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honor, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withDetectCharset(boolean detect) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detect, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withSkipBinary(boolean skip) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skip, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withCaseSensitive(boolean cs) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, cs, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withContextLines(int n) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, n, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withMaxDepth(int depth) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, depth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withMaxFileSize(long size) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, size, inlineReadLimit, maxLines, parallel);
        }

        public SearchOptions withInlineReadLimit(long limit) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, limit, maxLines, parallel);
        }

        public SearchOptions withMaxLines(long max) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, max, parallel);
        }

        public SearchOptions withParallel(boolean parallel) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }
    }

    /** 单行命中：1-based 行号 + 该行文本（不含行尾换行符）。 */
    public record LineMatch(long lineNumber, String text) {
    }

    /** 单文件命中：文件路径、相对路径、字节大小、命中的行（含上下文行）。 */
    public record FileMatch(Path file, String relativePath, long size, List<LineMatch> lines) {
    }

    /**
     * 只发现符合条件的文件路径（对标 {@code rg -l}）。
     */
    public static List<Path> findFiles(Path root, SearchOptions opts) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(opts, "opts");
        List<Path> candidates = collectCandidates(root, opts);
        if (!opts.parallel()) {
            return candidates.stream()
                    .filter(p -> !(opts.skipBinary() && isBinaryFile(p)))
                    .collect(Collectors.toList());
        }
        return parallelProcess(candidates, p -> (opts.skipBinary() && isBinaryFile(p)) ? null : p);
    }

    /**
     * 内容检索（对标 {@code rg}），返回所有命中文件及其行号/文本/上下文。
     * <p>
     * 结果按候选文件遍历顺序排列，并按 {@link SearchOptions#maxLines()} 截断总行数
     * （parallel 与 sequential 输出一致）。
     */
    public static List<FileMatch> grep(Path root, SearchOptions opts) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(opts, "opts");
        ContentMatcher matcher = buildMatcher(opts);
        List<Path> candidates = collectCandidates(root, opts);
        List<FileMatch> matches;
        if (!opts.parallel()) {
            matches = candidates.stream()
                    .map(p -> scanFile(p, root, opts, matcher))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            matches = parallelProcess(candidates, p -> scanFile(p, root, opts, matcher));
        }
        return truncateToBudget(sortByCandidateOrder(matches, candidates), opts.maxLines());
    }

    /**
     * 在自有的有界线程池上并行处理候选文件（不占用公共 ForkJoin 池）。
     * 返回集合顺序与处理顺序无关；调用方如需确定性顺序需自行排序。
     */
    private static <T> List<T> parallelProcess(List<Path> candidates, FileProcessor<T> processor) {
        ThreadPoolExecutor pool = searchExecutor();
        List<T> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(candidates.size());
        for (Path p : candidates) {
            pool.execute(() -> {
                try {
                    T r = processor.apply(p);
                    if (r != null) {
                        results.add(r);
                    }
                } catch (IOException e) {
                    log.warn("处理文件失败: {}", p, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("文件检索被中断", e);
        }
        return results;
    }

    private interface FileProcessor<T> {
        T apply(Path path) throws IOException;
    }

    private static List<FileMatch> sortByCandidateOrder(List<FileMatch> matches, List<Path> candidates) {
        if (matches.size() <= 1) {
            return matches;
        }
        Map<Path, Integer> order = new HashMap<>(candidates.size() * 2);
        for (int i = 0; i < candidates.size(); i++) {
            order.put(candidates.get(i), i);
        }
        List<FileMatch> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparingInt(m -> order.getOrDefault(m.file(), Integer.MAX_VALUE)));
        return sorted;
    }

    private static List<FileMatch> truncateToBudget(List<FileMatch> matches, long maxLines) {
        if (maxLines <= 0 || matches.isEmpty()) {
            return matches;
        }
        List<FileMatch> out = new ArrayList<>(matches.size());
        long used = 0;
        for (FileMatch m : matches) {
            if (used >= maxLines) {
                break;
            }
            long remaining = maxLines - used;
            List<LineMatch> lines = m.lines();
            if (lines.size() > remaining) {
                lines = new ArrayList<>(lines.subList(0, (int) remaining));
            }
            used += lines.size();
            out.add(new FileMatch(m.file(), m.relativePath(), m.size(), lines));
        }
        return out;
    }

    private static final int SEARCH_PARALLELISM =
            Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 8));

    private static final ThreadFactory SEARCH_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "pap-file-search");
        thread.setDaemon(true);
        return thread;
    };

    private static volatile ThreadPoolExecutor searchExecutorInstance;

    /** 懒加载有界线程池：core=max=SEARCH_PARALLELISM、有界队列、CallerRuns 回压、daemon 线程。 */
    private static ThreadPoolExecutor searchExecutor() {
        ThreadPoolExecutor executor = searchExecutorInstance;
        if (executor == null) {
            synchronized (FileSearch.class) {
                if (searchExecutorInstance == null) {
                    searchExecutorInstance = new ThreadPoolExecutor(
                            SEARCH_PARALLELISM, SEARCH_PARALLELISM, 60L, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(SEARCH_PARALLELISM * 4),
                            SEARCH_THREAD_FACTORY, new ThreadPoolExecutor.CallerRunsPolicy());
                }
                executor = searchExecutorInstance;
            }
        }
        return executor;
    }

    /**
     * 内存友好的候选文件流（对标 {@code rg --files}）。返回的流需由调用方关闭（try-with-resources）。
     */
    public static Stream<Path> stream(Path root, SearchOptions opts) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(opts, "opts");
        int depth = opts.maxDepth() > 0 ? opts.maxDepth() : Integer.MAX_VALUE;
        boolean honorGitignore = opts.honorGitignore();
        List<GlobFilter> includes = compileGlobs(opts.includeGlobs());
        List<GlobFilter> excludes = compileGlobs(opts.excludeGlobs());
        Set<String> exts = lowerExtensions(opts.extensions());
        Stream<Path> walk = Files.walk(root, depth);
        return walk
                .filter(p -> !p.equals(root))
                .filter(Files::isRegularFile)
                .filter(p -> !honorGitignore || !GitIgnoreMatcher.isIgnored(root, root.relativize(p), false))
                .filter(p -> !matchesAny(excludes, p, root))
                .filter(p -> includes.isEmpty() || matchesAny(includes, p, root))
                .filter(p -> exts.isEmpty() || exts.contains(extensionOf(p)))
                .filter(p -> opts.maxFileSize() <= 0 || safeSize(p) <= opts.maxFileSize())
                .filter(p -> !(opts.skipBinary() && isBinaryFile(p)));
    }

    /**
     * 遍历符合条件的所有文件，内部自动以 try-with-resources 关闭底层资源流
     * （避免 {@link #stream(Path, SearchOptions)} 返回的流被调用方忘记关闭导致句柄泄漏）。
     * 回调抛出的 {@link IOException} 原样上抛。
     */
    public static void forEach(Path root, SearchOptions opts, FileConsumer consumer) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(opts, "opts");
        Objects.requireNonNull(consumer, "consumer");
        try (Stream<Path> walk = stream(root, opts)) {
            Iterator<Path> iterator = walk.iterator();
            while (iterator.hasNext()) {
                consumer.accept(iterator.next());
            }
        }
    }

    /** 可抛出 {@link IOException} 的文件消费回调。 */
    @FunctionalInterface
    public interface FileConsumer {
        void accept(Path file) throws IOException;
    }

    private interface ContentMatcher {
        List<Integer> findOffsets(String content);

        /** 仅判断是否存在命中（流式匹配逐行使用，避免逐行构建 offset 列表）。 */
        default boolean matches(String content) {
            return !findOffsets(content).isEmpty();
        }
    }

    private static ContentMatcher buildMatcher(SearchOptions opts) {
        Pattern regex = opts.regex();
        if (regex != null) {
            return new ContentMatcher() {
                @Override
                public List<Integer> findOffsets(String content) {
                    return regexFindAll(content, regex);
                }

                @Override
                public boolean matches(String content) {
                    return regex.matcher(content).find();
                }
            };
        }
        Set<String> keywords = opts.keywords();
        if (keywords != null && !keywords.isEmpty()) {
            if (keywords.size() == 1) {
                String kw = keywords.iterator().next();
                if (opts.caseSensitive()) {
                    return new ContentMatcher() {
                        @Override
                        public List<Integer> findOffsets(String content) {
                            return indexOfAll(content, kw);
                        }

                        @Override
                        public boolean matches(String content) {
                            return content.indexOf(kw) >= 0;
                        }
                    };
                }
                Pattern p = Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE);
                return new ContentMatcher() {
                    @Override
                    public List<Integer> findOffsets(String content) {
                        return regexFindAll(content, p);
                    }

                    @Override
                    public boolean matches(String content) {
                        return p.matcher(content).find();
                    }
                };
            }
            Trie.TrieBuilder builder = Trie.builder();
            if (!opts.caseSensitive()) {
                builder.ignoreCase();
            }
            for (String kw : keywords) {
                builder.addKeyword(kw);
            }
            Trie trie = builder.build();
            return new ContentMatcher() {
                @Override
                public List<Integer> findOffsets(String content) {
                    List<Integer> offsets = new ArrayList<>();
                    for (Emit emit : trie.parseText(content)) {
                        offsets.add(emit.getStart());
                    }
                    return offsets;
                }

                @Override
                public boolean matches(String content) {
                    boolean[] hit = {false};
                    trie.parseText(content, emit -> hit[0] = true);
                    return hit[0];
                }
            };
        }
        throw new IllegalArgumentException("SearchOptions 必须提供 keywords 或 regex 之一");
    }

    private static List<Integer> indexOfAll(String content, String keyword) {
        List<Integer> offsets = new ArrayList<>();
        int idx = content.indexOf(keyword);
        while (idx >= 0) {
            offsets.add(idx);
            idx = content.indexOf(keyword, idx + keyword.length());
        }
        return offsets;
    }

    private static List<Integer> regexFindAll(String content, Pattern pattern) {
        Matcher m = pattern.matcher(content);
        List<Integer> offsets = new ArrayList<>();
        while (m.find()) {
            offsets.add(m.start());
        }
        return offsets;
    }

    private static FileMatch scanFile(Path file, Path root, SearchOptions opts, ContentMatcher matcher) {
        try {
            long size = Files.size(file);
            // 大文件走流式行匹配，避免整文件读入堆（detectCharset 路径本身要整读，不流式）
            if (!opts.detectCharset() && opts.inlineReadLimit() > 0 && size > opts.inlineReadLimit()) {
                return scanFileStreaming(file, root, opts, matcher);
            }
            byte[] bytes = Files.readAllBytes(file);
            if (opts.skipBinary() && isBinary(bytes)) {
                return null;
            }
            String content = decode(bytes, file, opts.detectCharset());
            List<Integer> offsets = matcher.findOffsets(content);
            if (offsets.isEmpty()) {
                return null;
            }
            List<LineMatch> lines = toLineMatches(content, offsets, opts.contextLines());
            String rel = root.relativize(file).toString().replace('\\', '/');
            return new FileMatch(file, rel, bytes.length, lines);
        } catch (IOException e) {
            log.warn("跳过无法读取的文件: {}", file, e);
            return null;
        }
    }

    /**
     * 大文件流式行匹配：逐行读取、逐行匹配，堆占用 O(最长行)。
     * <p>
     * 行语义与 rg 一致（按行匹配）；与整读路径的唯一语义差异：跨行的多行正则（如包含 {@code \n}）
     * 无法在流式模式下命中。为提取上下文行，会二次流式读取文件。
     */
    private static FileMatch scanFileStreaming(Path file, Path root, SearchOptions opts, ContentMatcher matcher) {
        try {
            if (opts.skipBinary() && isBinaryFile(file)) {
                return null;
            }
            // 第一趟：流式扫描，收集命中行号（1-based）
            List<Integer> matchedLines = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int no = 0;
                while ((line = reader.readLine()) != null) {
                    no++;
                    if (no == 1 && !line.isEmpty() && line.charAt(0) == '﻿') {
                        line = line.substring(1);
                    }
                    if (matcher.matches(line)) {
                        matchedLines.add(no);
                    }
                }
            }
            if (matchedLines.isEmpty()) {
                return null;
            }
            // 扩展上下文行
            TreeSet<Integer> wanted = new TreeSet<>();
            int context = opts.contextLines();
            for (int ln : matchedLines) {
                for (int k = ln - context; k <= ln + context; k++) {
                    if (k >= 1) {
                        wanted.add(k);
                    }
                }
            }
            // 第二趟：流式抽取所需行
            List<LineMatch> lines = new ArrayList<>(wanted.size());
            Integer[] wantedArr = wanted.toArray(new Integer[0]);
            int next = 0;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int no = 0;
                while (next < wantedArr.length && (line = reader.readLine()) != null) {
                    no++;
                    if (no == 1 && !line.isEmpty() && line.charAt(0) == '﻿') {
                        line = line.substring(1);
                    }
                    if (no == wantedArr[next]) {
                        lines.add(new LineMatch((long) no, line));
                        next++;
                    }
                }
            }
            String rel = root.relativize(file).toString().replace('\\', '/');
            return new FileMatch(file, rel, Files.size(file), lines);
        } catch (IOException e) {
            log.warn("跳过无法读取的文件: {}", file, e);
            return null;
        }
    }

    private static List<LineMatch> toLineMatches(String content, List<Integer> offsets, int contextLines) {
        if (offsets.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(offsets);
        // 一趟扫描：把每个命中偏移归入所在行号，去重
        TreeSet<Integer> matchedLines = new TreeSet<>();
        int p = 0;
        int lineStart = 0;
        int lineNo = 0;
        int len = content.length();
        while (p < offsets.size()) {
            int off = offsets.get(p);
            int nl = content.indexOf('\n', lineStart);
            int lineEnd = nl < 0 ? len : nl;
            if (off < lineEnd) {
                matchedLines.add(lineNo);
                while (p < offsets.size() && offsets.get(p) < lineEnd) {
                    p++;
                }
            } else {
                if (nl < 0) {
                    break;
                }
                lineStart = nl + 1;
                lineNo++;
            }
        }
        if (matchedLines.isEmpty()) {
            return Collections.emptyList();
        }
        // 扩展上下文行
        TreeSet<Integer> resultLines = new TreeSet<>();
        for (int l : matchedLines) {
            for (int k = l - contextLines; k <= l + contextLines; k++) {
                if (k >= 0) {
                    resultLines.add(k);
                }
            }
        }
        // 第二趟：按需抽取结果行的起止字节区间
        List<LineMatch> out = new ArrayList<>(resultLines.size());
        Integer[] wanted = resultLines.toArray(new Integer[0]);
        int emitted = 0;
        int start = 0;
        int no = 0;
        while (emitted < wanted.length) {
            int nl = content.indexOf('\n', start);
            boolean hasNl = nl >= 0;
            int end = hasNl ? nl : len;
            if (!hasNl && start >= len && len > 0 && content.charAt(len - 1) == '\n') {
                // 结尾换行符产生的空"行"不计入行集合（与 rg / 流式 readLine 语义一致）
                break;
            }
            if (no == wanted[emitted]) {
                int e = end;
                if (e > start && content.charAt(e - 1) == '\n') {
                    e--;
                }
                if (e > start && content.charAt(e - 1) == '\r') {
                    e--;
                }
                out.add(new LineMatch((long) no + 1, content.substring(start, e)));
                emitted++;
            }
            if (!hasNl) {
                break;
            }
            start = nl + 1;
            no++;
        }
        return out;
    }

    private static List<Path> collectCandidates(Path root, SearchOptions opts) throws IOException {
        List<GlobFilter> includes = compileGlobs(opts.includeGlobs());
        List<GlobFilter> excludes = compileGlobs(opts.excludeGlobs());
        Set<String> exts = lowerExtensions(opts.extensions());
        int depth = opts.maxDepth() > 0 ? opts.maxDepth() : Integer.MAX_VALUE;
        GitIgnoreMatcher ignore = opts.honorGitignore() ? GitIgnoreMatcher.load(root) : null;
        CandidateVisitor visitor = new CandidateVisitor(root, ignore, includes, excludes, exts, opts.maxFileSize());
        Files.walkFileTree(root, Collections.<FileVisitOption>emptySet(), depth, visitor);
        return visitor.out();
    }

    /**
     * 遍历候选文件的有状态 visitor：维护嵌套 {@code .gitignore} 的 push/pop 栈，
     * 使各目录的 {@code .gitignore} 只作用于其子树。
     */
    private static final class CandidateVisitor extends SimpleFileVisitor<Path> {

        private final Path root;
        private final List<GlobFilter> includes;
        private final List<GlobFilter> excludes;
        private final Set<String> exts;
        private final long maxFileSize;
        private final List<Path> out = new ArrayList<>();
        private final ArrayDeque<GitIgnoreMatcher> ignoreStack = new ArrayDeque<>();
        private GitIgnoreMatcher ignore;

        CandidateVisitor(Path root, GitIgnoreMatcher ignore, List<GlobFilter> includes,
                         List<GlobFilter> excludes, Set<String> exts, long maxFileSize) {
            this.root = root;
            this.ignore = ignore;
            this.includes = includes;
            this.excludes = excludes;
            this.exts = exts;
            this.maxFileSize = maxFileSize;
        }

        List<Path> out() {
            return out;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root)) {
                if (ignore != null && ignore.ignores(root.relativize(dir), true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (matchesAny(excludes, dir, root)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ignore != null) {
                    // 下潜前保存当前 ignore，再追加本目录的 .gitignore
                    ignoreStack.push(ignore);
                    ignore = ignore.extend(dir);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            if (!dir.equals(root) && ignore != null) {
                ignore = ignoreStack.pop();
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (matchesAny(excludes, file, root)) {
                return FileVisitResult.CONTINUE;
            }
            if (!includes.isEmpty() && !matchesAny(includes, file, root)) {
                return FileVisitResult.CONTINUE;
            }
            if (maxFileSize > 0 && attrs.size() > maxFileSize) {
                return FileVisitResult.CONTINUE;
            }
            if (!exts.isEmpty() && !exts.contains(extensionOf(file))) {
                return FileVisitResult.CONTINUE;
            }
            if (ignore != null && ignore.ignores(root.relativize(file), false)) {
                return FileVisitResult.CONTINUE;
            }
            out.add(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            // 最后一个参数传异常对象，SLF4J 会输出完整堆栈，便于生产排查
            log.warn("访问文件失败: {}", file, exc);
            return FileVisitResult.CONTINUE;
        }
    }

    private static String decode(byte[] bytes, Path file, boolean detectCharset) throws IOException {
        if (detectCharset) {
            String s = ReadTxtToStringUtil.readFileContent(file.toFile());
            return s != null ? s : new String(bytes, StandardCharsets.UTF_8);
        }
        int start = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    private static boolean isBinary(byte[] bytes) {
        int probe = Math.min(BINARY_PROBE_BYTES, bytes.length);
        for (int i = 0; i < probe; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBinaryFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(BINARY_PROBE_BYTES);
            for (byte b : head) {
                if (b == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static long safeSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    private record GlobFilter(PathMatcher matcher, boolean nameOnly) {
    }

    private static List<GlobFilter> compileGlobs(Set<String> globs) {
        if (globs == null || globs.isEmpty()) {
            return Collections.emptyList();
        }
        FileSystem fs = FileSystems.getDefault();
        List<GlobFilter> list = new ArrayList<>(globs.size() * 2);
        for (String g : globs) {
            list.add(new GlobFilter(fs.getPathMatcher("glob:" + g), isNameOnlyGlob(g)));
            // Java 的 glob 中 '**/' 前缀不匹配根级元素（例如 **/target/** 不匹配根目录下的 target/built.txt），
            // 这里补一个去掉 '**/' 的变体，使其与 rg 语义一致：'**/target/**' 同时匹配根级与任意层级。
            if (g.startsWith("**/")) {
                String alt = g.substring(3);
                list.add(new GlobFilter(fs.getPathMatcher("glob:" + alt), isNameOnlyGlob(alt)));
            }
        }
        return list;
    }

    private static boolean isNameOnlyGlob(String glob) {
        return !glob.contains("/") && !glob.contains("\\");
    }

    private static boolean matchesAny(List<GlobFilter> filters, Path p, Path root) {
        if (filters.isEmpty()) {
            return false;
        }
        Path rel = root.relativize(p);
        Path name = rel.getFileName();
        for (GlobFilter f : filters) {
            if (f.nameOnly()) {
                if (name != null && f.matcher().matches(name)) {
                    return true;
                }
            } else if (f.matcher().matches(rel)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> lowerExtensions(Set<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> s = new HashSet<>(extensions.size());
        for (String e : extensions) {
            s.add(e.toLowerCase(Locale.ROOT));
        }
        return s;
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
