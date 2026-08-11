package cn.net.pap.common.file.search;

import cn.net.pap.common.file.ReadTxtToStringUtil;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
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
 * 整体流程：{@code collectCandidates} 先做路径过滤遍历（包含/排除 glob、扩展名、大小、可选 gitignore），
 * {@code buildMatcher} 按选项构造匹配器，随后逐文件扫描（大文件自动降级为流式行匹配），
 * 最终对结果按候选顺序重排，并按总行数预算截断。
 * <p>
 * 所有过滤项均为可选：默认无深度/大小/类型限制，{@code .gitignore} 默认关闭（全量搜索）。
 */
public final class FileSearch {

    private static final Logger log = LoggerFactory.getLogger(FileSearch.class);

    private static final int BINARY_PROBE_BYTES = 8192;

    private FileSearch() {
    }

    /**
     * 检索选项（不可变 record，用 {@code withXxx} 链式派生新实例）。
     * <p>
     * 内容检索条件：{@link #keywords()} 与 {@link #regex()} 二选一（regex 优先），提供内容检索时至少需设置其一；
     * 仅做路径过滤（findFiles / stream / forEach）时两者均可为空。
     * <p>
     * 默认值：{@code skipBinary} / {@code caseSensitive} / {@code parallel} 开启，
     * {@code honorGitignore} / {@code detectCharset} 关闭，
     * {@code contextLines} / {@code maxDepth} / {@code maxFileSize} / {@code maxLines} 为 0（不限制），
     * {@code inlineReadLimit} 为 16MB（超出则改用流式行匹配）。
     *
     * @param keywords        关键词集合：单关键词走 indexOf 快路径，多关键词走 Aho-Corasick
     * @param regex           正则，优先于 keywords；两者都为空时构造匹配器会抛异常
     * @param includeGlobs    仅包含「匹配这些通配符模式之一」的文件（空=不限）。
     *                        不含 / 的模式按文件名匹配任意层级，如 "*.java"；
     *                        含 / 的模式按相对路径匹配，如 "src/**&#47;*.kt"
     * @param excludeGlobs    排除「匹配这些通配符模式之一」的文件/目录（空=不限），
     *                        语法同 includeGlobs，如 "**&#47;target/**"、"*.log"
     * @param extensions      仅包含的扩展名：不含点、忽略大小写，如 "java"、"md"（空=不限）
     * @param honorGitignore  遵循 .gitignore（默认 false=全量搜索）
     * @param detectCharset   检测非 UTF-8 编码（开启后大文件不流式、需整读）
     * @param skipBinary      跳过二进制文件（带 UTF-8/UTF-16 BOM 视为文本；否则以是否含 NUL 字节判定）
     * @param caseSensitive   匹配是否区分大小写
     * @param contextLines    命中行前后各带几行上下文（0=不带）
     * @param maxDepth        目录最大下潜深度（0=不限）
     * @param maxFileSize     仅处理不超过该字节数的文件（0=不限）
     * @param inlineReadLimit 超过该字节数改走流式行匹配（0=始终整读）
     * @param maxLines        grep 返回总行数上限，超出截断（0=不限）
     * @param parallel        跨文件并行扫描（自持有界线程池）
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

        /**
         * 仅按关键词检索（多关键词走 Aho-Corasick，单关键词走 indexOf）。
         *
         * @param keywords 检索关键词（多个可组合；null/空元素会被忽略）
         * @return 携带默认配置的关键词检索选项
         */
        public static SearchOptions ofKeywords(String... keywords) {
            return new SearchOptions(toSet(keywords), null, null, null, null,
                    false, false, true, true, 0, 0, 0, DEFAULT_INLINE_READ_LIMIT, DEFAULT_MAX_LINES, true);
        }

        /**
         * 按正则检索（完整 {@code java.util.regex} 语义）。
         *
         * @param regex 正则表达式字符串
         * @return 携带默认配置的正则检索选项
         */
        public static SearchOptions ofRegex(String regex) {
            return ofRegex(Pattern.compile(regex));
        }

        /**
         * 按预编译正则检索。
         *
         * @param pattern 预编译的 {@link Pattern}
         * @return 携带默认配置的正则检索选项
         */
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

        /**
         * 设置仅包含的 glob 过滤（其余字段保持不变）。
         *
         * @param globs 包含的 glob 模式集合，命中其一即包含，如 "*.java"、"src/**&#47;*.kt"（空=不限）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withIncludeGlobs(Set<String> globs) {
            return new SearchOptions(keywords, regex, globs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置排除的 glob 过滤（其余字段保持不变）。
         *
         * @param globs 排除的 glob 模式集合，命中其一即排除，如 "**&#47;target/**"、"*.log"（空=不限）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withExcludeGlobs(Set<String> globs) {
            return new SearchOptions(keywords, regex, includeGlobs, globs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置仅包含的扩展名过滤（其余字段保持不变）。
         *
         * @param exts 扩展名集合（不含点、忽略大小写，如 "java"、"md"；空=不限）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withExtensions(Set<String> exts) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, exts,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置是否遵循 {@code .gitignore}（其余字段保持不变）。
         *
         * @param honor true=遵循，false=全量搜索
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withHonorGitignore(boolean honor) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honor, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置是否检测非 UTF-8 编码（其余字段保持不变）。
         *
         * @param detect true=开启编码检测（开启后大文件不流式、需整读）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withDetectCharset(boolean detect) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detect, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置是否跳过二进制文件（其余字段保持不变）。
         *
         * @param skip true=跳过（带 UTF-8/UTF-16 BOM 视为文本；否则按是否含 NUL 字节判定）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withSkipBinary(boolean skip) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skip, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置匹配是否区分大小写（其余字段保持不变）。
         *
         * @param cs true=区分大小写
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withCaseSensitive(boolean cs) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, cs, contextLines, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置命中行前后各带几行上下文（其余字段保持不变）。
         *
         * @param n 上下文行数（0=不带上下文）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withContextLines(int n) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, n, maxDepth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置目录最大下潜深度（其余字段保持不变）。
         *
         * @param depth 最大深度（0=不限）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withMaxDepth(int depth) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, depth, maxFileSize, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置文件大小上限（其余字段保持不变）。
         *
         * @param size 最大字节数，仅处理不超过该值的文件（0=不限）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withMaxFileSize(long size) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, size, inlineReadLimit, maxLines, parallel);
        }

        /**
         * 设置流式行匹配的阈值（其余字段保持不变）。
         *
         * @param limit 超过该字节数改走流式行匹配（0=始终整读）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withInlineReadLimit(long limit) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, limit, maxLines, parallel);
        }

        /**
         * 设置 grep 返回总行数上限（其余字段保持不变）。
         *
         * @param max 总行数上限，超出截断（0=不限）
         * @return 派生出的新 SearchOptions 实例
         */
        public SearchOptions withMaxLines(long max) {
            return new SearchOptions(keywords, regex, includeGlobs, excludeGlobs, extensions,
                    honorGitignore, detectCharset, skipBinary, caseSensitive, contextLines, maxDepth, maxFileSize, inlineReadLimit, max, parallel);
        }

        /**
         * 设置是否跨文件并行扫描（其余字段保持不变）。
         *
         * @param parallel true=并行（自持有界线程池）
         * @return 派生出的新 SearchOptions 实例
         */
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
     * 只发现符合条件的文件路径，不做关键词/正则内容匹配（仅按路径与属性过滤，
     * skipBinary 开启时会探测文件头以判定二进制）。
     * 对标 {@code rg --files}；默认行为差异：本方法默认不遵循 .gitignore、默认跳过二进制文件。
     *
     * @param root 检索根目录
     * @param opts 检索选项
     * @return 符合条件的文件路径列表，按遍历顺序排列（受 {@code skipBinary} 过滤）
     * @throws IOException 目录遍历失败时抛出
     */
    public static List<Path> findFiles(Path root, SearchOptions opts) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(opts, "opts");
        // 只收集路径时无需解码文件内容，唯一可能淘汰的是 skipBinary 命中的二进制文件
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
     *
     * @param root 检索根目录
     * @param opts 检索选项（内容检索必须提供 {@link SearchOptions#keywords()} 或 {@link SearchOptions#regex()}）
     * @return 命中文件及其行信息列表，按遍历顺序排列、按 maxLines 截断
     * @throws IOException 目录遍历失败时抛出
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
     * <p>
     * 并发模型：
     * <ul>
     *   <li><b>任务分发</b>：每个候选文件提交一个任务；线程池队列有界，配合 {@code CallerRunsPolicy}
     *       在队列满时由提交线程直接兜底执行，既不无限堆积任务、也不丢弃任务，形成天然背压；</li>
     *   <li><b>结果收集</b>：写入 {@code synchronizedList}（线程安全），仅收纳非 null 结果
     *       （null 表示该文件被过滤/跳过）；</li>
     *   <li><b>同步等待</b>：{@link CountDownLatch} 以候选数初始化、每任务完成递减，方法阻塞至全部结束，
     *       保证返回时结果完备；被中断时恢复中断位并上抛。</li>
     * </ul>
     * 返回集合顺序与任务完成顺序无关（并发完成顺序不确定），调用方如需确定性顺序需自行排序。
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

    /** 文件处理回调：{@link #apply(Path)} 返回 null 表示该文件被跳过（过滤语义）。 */
    @FunctionalInterface
    private interface FileProcessor<T> {
        T apply(Path path) throws IOException;
    }

    /**
     * 把并发结果按候选文件的遍历顺序重排，保证 parallel 与 sequential 输出一致。
     * 预建 Path → 序号 映射后按序号排序；候选列表之外的文件（正常不会出现）排到最后。
     */
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

    /**
     * 按总行数预算截断：按候选顺序逐文件累加行数，达到 maxLines 即停止；
     * 单个文件超出剩余额度时只保留前几行，保证输出顺序仍是候选顺序。
     */
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
     * 优雅关闭检索线程池（幂等，可重复调用）。
     * <p>
     * <b>⚠️ 必须由外部生命周期显式调用</b>：本类持有静态单例线程池，若从不调用，
     * 线程将常驻至 JVM 退出——在 Web 容器 redeploy 场景会持续钉住 webapp 类加载器，造成内存泄露。
     * <p>
     * 挂载示例（Spring 应用，在任意单例 Bean 上，由容器销毁时自动触发）：
     * <pre>
     * &#64;PreDestroy
     * public void shutdown() {
     *     FileSearch.shutdownSearchExecutor();
     * }
     * </pre>
     * 非 Spring 场景则交由主程序在结束前直接调用。
     * <p>
     * 行为：先 {@code shutdown()} 停止接收新任务，等待存量任务 60 秒内完成；
     * 超时或被中断则 {@code shutdownNow()} 强制关闭并恢复中断状态；关闭后如需再次检索，
     * {@code searchExecutor()} 会按需重新懒创建。
     */
    public static void shutdownSearchExecutor() {
        ThreadPoolExecutor executor = searchExecutorInstance;
        if (executor == null) {
            return;
        }
        log.info("[FileSearch-Shutdown] 准备关闭文件检索线程池");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("[FileSearch-Shutdown] 部分任务未在 60 秒内完成，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("[FileSearch-Shutdown] 等待关闭时被中断，强制关闭", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        searchExecutorInstance = null;   // 置空，允许后续按需重新懒创建
    }

    /**
     * 返回候选文件路径流（对标 {@code rg --files}）。
     * <p>
     * 基于 {@code collectCandidates} 物化候选列表后转为流：返回的流不持有任何底层资源，
     * 调用方无需（也不必）用 try-with-resources 关闭；skipBinary 过滤在消费时惰性执行。
     *
     * @param root 检索根目录
     * @param opts 检索选项
     * @return 候选文件路径的流（无资源句柄，无需关闭）
     * @throws IOException 目录遍历失败时抛出
     */
    public static Stream<Path> stream(Path root, SearchOptions opts) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(opts, "opts");
        Stream<Path> base = collectCandidates(root, opts).stream();
        return opts.skipBinary() ? base.filter(p -> !isBinaryFile(p)) : base;
    }

    /**
     * 遍历符合条件的所有文件；回调抛出的 {@link IOException} 原样上抛。
     *
     * @param root     检索根目录
     * @param opts     检索选项
     * @param consumer 每个候选文件的消费回调
     * @throws IOException 目录遍历或回调抛出的 {@link IOException} 原样上抛
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
        /**
         * 消费单个候选文件。
         *
         * @param file 候选文件路径
         * @throws IOException 处理该文件失败时抛出
         */
        void accept(Path file) throws IOException;
    }

    private interface ContentMatcher {
        List<Integer> findOffsets(String content);

        /** 仅判断是否存在命中（流式匹配逐行使用，避免逐行构建 offset 列表）。 */
        default boolean matches(String content) {
            return !findOffsets(content).isEmpty();
        }
    }

    /**
     * 构造内容匹配器，优先级：正则 &gt; 关键词。
     * <p>
     * 关键词路径再细分（核心性能策略）：
     * <ul>
     *   <li><b>单个关键词</b>：直接走 {@link String#indexOf(String)}，匹配最快、零额外内存；</li>
     *   <li><b>多个关键词</b>：构建 Aho-Corasick 自动机，对文本单次扫描即可找出所有关键词的全部出现位置，
     *       避免逐关键词 {@code indexOf} 的 O(关键词数 × 文本长) 退化；</li>
     *   <li><b>不区分大小写</b>：单关键词降级为 {@code Pattern.quote + CASE_INSENSITIVE} 正则，
     *       多关键词降级为 {@code ignoreCase} 的 Trie。</li>
     * </ul>
     * {@link ContentMatcher} 提供两套入口：{@code findOffsets} 供整读路径使用（需要命中偏移以定位行号），
     * {@code matches} 只做存在性判断，供流式逐行路径使用（省去逐行构建偏移列表的开销）。
     */
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
     * 大文件流式行匹配：逐行读取、逐行匹配，堆占用 O(最长行)，避免大文件整读进堆。
     * <p>
     * 分两趟流式读取（每趟堆占用仍为 O(最长行)）：
     * <ul>
     *   <li>第一趟：逐行判断是否存在命中，仅收集命中行号（1-based）；</li>
     *   <li>第二趟：只抽取「命中行 ± 上下文」所需行号的文本。</li>
     * </ul>
     * 行语义与 rg 一致（按行匹配）；与整读路径的唯一语义差异：跨行的多行正则（如包含 {@code \n}）
     * 无法在流式模式下命中。两趟均按 BOM 探测到的编码（UTF-8 / UTF-16LE / UTF-16BE）解码，
     * 并会剥掉首行行首的 BOM 字符（U+FEFF）。
     * <p>
     * 前置条件：{@code detectCharset=false}（编码检测路径必须整读，走 {@code scanFile}）。
     */
    private static FileMatch scanFileStreaming(Path file, Path root, SearchOptions opts, ContentMatcher matcher) {
        try {
            if (opts.skipBinary() && isBinaryFile(file)) {
                return null;
            }
            // 第一趟：流式扫描，收集命中行号（1-based）
            List<Integer> matchedLines = new ArrayList<>();
            try (BufferedReader reader = openReader(file)) {
                String line;
                int no = 0;
                while ((line = reader.readLine()) != null) {
                    no++;
                    // 首行若以 BOM 字符（U+FEFF）开头则先剥掉再匹配/抽取（Reader 不会自动去除 BOM）
                    if (no == 1 && !line.isEmpty() && line.charAt(0) == '﻿') {
                        line = line.substring(1);
                    }
                    // skipBinary 开启时：行内出现 NUL 字符即判为二进制并放弃该文件
                    //（逐行检查覆盖全文，不限于文件头 8KB）
                    if (opts.skipBinary() && line.indexOf('\u0000') >= 0) {
                        return null;
                    }
                    if (matcher.matches(line)) {
                        matchedLines.add(no);
                    }
                }
            }
            if (matchedLines.isEmpty()) {
                return null;
            }
            // 扩展上下文行：命中行前后各 contextLines 行也纳入结果（越界行号由下方范围判断剔除）
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
            try (BufferedReader reader = openReader(file)) {
                String line;
                int no = 0;
                while (next < wantedArr.length && (line = reader.readLine()) != null) {
                    no++;
                    // 首行若以 BOM 字符（U+FEFF）开头则先剥掉再匹配/抽取（Reader 不会自动去除 BOM）
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
        Collections.sort(offsets); // 后续算法依赖偏移升序
        // 第一趟：把每个命中偏移归入其所在行号（0-based），去重。
        // 算法：维护「当前行」的起点 lineStart 与行号 lineNo，沿行边界线性推进——
        //   - 偏移落在当前行区间 [lineStart, lineEnd) 内：该行命中，同行内的后续偏移一并跳过；
        //   - 否则当前行不含命中，推进到下一行（lineStart 越过换行符、lineNo+1）后重复判断。
        // 整体只线性扫描一遍行边界，复杂度 O(行数 + 命中数)，不随偏移数嵌套增长。
        TreeSet<Integer> matchedLines = new TreeSet<>();
        int p = 0;
        int lineStart = 0;
        int lineNo = 0;
        int len = content.length();
        while (p < offsets.size()) {
            int off = offsets.get(p);
            // 当前行的行尾：换行符位置（无换行则为文末）
            int nl = content.indexOf('\n', lineStart);
            int lineEnd = nl < 0 ? len : nl;
            if (off < lineEnd) {
                // 命中落在当前行内 → 记该行命中，并跳过本行内剩余偏移（重复命中只记一行）
                matchedLines.add(lineNo);
                while (p < offsets.size() && offsets.get(p) < lineEnd) {
                    p++;
                }
            } else {
                // 当前行不含命中（偏移在行尾之后）→ 推进到下一行
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
        // 第二趟：按需抽取结果行的文本。与第一趟同构：沿行边界线性推进，每行只比较一次
        // 即知是否落在 wanted 中，O(行数) 完成。
        List<LineMatch> out = new ArrayList<>(resultLines.size());
        Integer[] wanted = resultLines.toArray(new Integer[0]);
        int emitted = 0; // wanted 中下一个待输出行的下标
        int start = 0;   // 当前行起点（0-based 字符偏移）
        int no = 0;      // 当前行号（0-based）
        while (emitted < wanted.length) {
            int nl = content.indexOf('\n', start);
            boolean hasNl = nl >= 0;
            int end = hasNl ? nl : len; // 行尾：换行符位置或文末
            if (!hasNl && start >= len && len > 0 && content.charAt(len - 1) == '\n') {
                // 文本以换行符结尾时，start 越过文末会多出一个空"行"（如 "a\n" 的第二个空行）；
                // 该虚拟行不被 rg / readLine 视为一行，直接终止，避免误输出空行。
                break;
            }
            if (no == wanted[emitted]) {
                // 本行是被需要的行：去掉行尾 \n 与 \r 后输出，行号转为 1-based
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
                // 目录自身被 gitignore 忽略或命中排除 glob：整棵子树不再进入
                if (ignore != null && ignore.ignores(root.relativize(dir), true)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (matchesAny(excludes, dir, root)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (ignore != null) {
                    // 下潜前把当前 gitignore 上下文入栈，再追加本目录的 .gitignore，
                    // 使嵌套规则只作用于本子树；离开目录时由 postVisitDirectory 弹栈还原
                    ignoreStack.push(ignore);
                    ignore = ignore.extend(dir);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            // 离开目录：恢复下潜前的 gitignore 上下文（与 preVisitDirectory 的入栈成对）
            if (!dir.equals(root) && ignore != null) {
                ignore = ignoreStack.pop();
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            // 依次套用 排除 glob / 包含 glob / 大小上限 / 扩展名 / gitignore，全部通过才收录
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
        // 按文件头 BOM 选择编码（UTF-8 / UTF-16LE / UTF-16BE），无 BOM 一律按 UTF-8
        String s = new String(bytes, sniffCharset(bytes));
        // 剥离解码后残留的 BOM 字符（U+FEFF）——UTF-8 的 EF BB BF 与 UTF-16 的 BOM 都解码成它
        if (!s.isEmpty() && s.charAt(0) == 0xFEFF) {
            s = s.substring(1);
        }
        return s;
    }

    private static boolean isBinary(byte[] bytes) {
        // 以 UTF-8 / UTF-16 BOM 开头的文件带编码标记，视为文本，不做 NUL 判定
        if (hasTextBom(bytes)) {
            return false;
        }
        // 整文件扫描 NUL：整读路径下字节已全部在内存，直接全量判定（不限于文件头）
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    /** 文件头是否为 UTF-8（EF BB BF）或 UTF-16（FF FE / FE FF）BOM。 */
    private static boolean hasTextBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return true;
        }
        if (bytes.length >= 2) {
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return true;
            }
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return true;
            }
        }
        return false;
    }

    /** 依据文件头 BOM 判定编码：UTF-8 / UTF-16LE / UTF-16BE；无 BOM 回退 UTF-8。 */
    private static Charset sniffCharset(byte[] head) {
        if (hasTextBom(head)) {
            if (head.length >= 3 && (head[0] & 0xFF) == 0xEF) {
                return StandardCharsets.UTF_8;
            }
            return (head[0] & 0xFF) == 0xFF ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE;
        }
        return StandardCharsets.UTF_8;
    }

    /** 打开按 BOM 探测到编码的逐行 reader（供大文件流式路径使用，避免 UTF-16 文件被当二进制跳过）。 */
    private static BufferedReader openReader(Path file) throws IOException {
        Charset cs;
        try (InputStream in = Files.newInputStream(file)) {
            cs = sniffCharset(in.readNBytes(4)); // 4 字节足以覆盖 UTF-8（3 字节）与 UTF-16（2 字节）BOM
        }
        return Files.newBufferedReader(file, cs);
    }

    private static boolean isBinaryFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            // 路径级预检只读文件头，保持"不整读"的取舍；BOM 开头的文件视为文本
            byte[] head = in.readNBytes(BINARY_PROBE_BYTES);
            return isBinary(head);
        } catch (IOException e) {
            // 读失败不再静默当二进制：记日志后视为文本，交由下游读取路径统一报错/跳过
            log.warn("探测二进制失败，视为文本交由后续处理: {}", file, e);
            return false;
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
