package cn.net.pap.common.file.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSearchTest {

    @TempDir
    Path tempDir;

    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        root = tempDir;
        buildFixture(root);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void buildFixture(Path root) throws IOException {
        write(root.resolve("src/main/Hello.java"),
                "package a;\npublic class Hello { public void run() { System.out.println(\"hello world\"); } }\n");
        write(root.resolve("src/main/Utils.java"),
                "package a;\npublic class Utils { }\n");
        write(root.resolve("src/test/LoginTest.java"),
                "// test login flow\nassert login(user, pass);\n");
        write(root.resolve("docs/guide.md"),
                "# login guide\nhow to login quickly\nhello world\nend\n");
        write(root.resolve("docs/notes.md"),
                "nothing relevant\n");
        write(root.resolve("docs/context.txt"),
                "alpha\nbeta login only here\ngamma\ndelta\n");
        write(root.resolve("gen/output.txt"),
                "hello generated\n");
        write(root.resolve("target/built.txt"),
                "compiled artifact\n");
        write(root.resolve("app.log"),
                "hello log\n");
        write(root.resolve(".gitignore"),
                "*.log\ntarget/\n");
        // 二进制文件（含 NUL 字节），应被 skipBinary 默认跳过
        Files.createDirectories(root);
        Files.write(root.resolve("data.bin"), "hello\0world\n".getBytes(StandardCharsets.UTF_8));
    }

    private static Set<String> fileNames(List<Path> paths) {
        return paths.stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
    }

    private static Set<String> relativeNames(List<FileSearch.FileMatch> matches) {
        return matches.stream().map(FileSearch.FileMatch::relativePath).collect(Collectors.toSet());
    }

    @Test
    void findFiles_allDefaults_skipsBinaryAndFindsAllTextFiles() throws IOException {
        List<Path> files = FileSearch.findFiles(root, FileSearch.SearchOptions.ofKeywords("hello"));
        Set<String> names = fileNames(files);
        assertEquals(10, names.size());
        assertFalse(names.contains("data.bin"));
        assertTrue(names.contains("Hello.java"));
        assertTrue(names.contains(".gitignore"));
    }

    @Test
    void findFiles_includeGlobs_filtersByPath() throws IOException {
        List<Path> files = FileSearch.findFiles(root,
                FileSearch.SearchOptions.ofKeywords("hello").withIncludeGlobs(Set.of("**/*.java")));
        assertEquals(Set.of("Hello.java", "Utils.java", "LoginTest.java"), fileNames(files));
    }

    @Test
    void findFiles_extensions_filtersByExtension() throws IOException {
        List<Path> files = FileSearch.findFiles(root,
                FileSearch.SearchOptions.ofKeywords("hello").withExtensions(Set.of("md")));
        assertEquals(Set.of("guide.md", "notes.md"), fileNames(files));
    }

    @Test
    void findFiles_excludeGlobs_excludesTargetSubtree() throws IOException {
        List<Path> files = FileSearch.findFiles(root,
                FileSearch.SearchOptions.ofKeywords("hello").withExcludeGlobs(Set.of("**/target/**")));
        Set<String> names = fileNames(files);
        assertFalse(names.contains("built.txt"));
        assertTrue(names.contains("Hello.java"));
    }

    @Test
    void findFiles_honorGitignore_excludesIgnoredEntries() throws IOException {
        List<Path> files = FileSearch.findFiles(root,
                FileSearch.SearchOptions.ofKeywords("hello").withHonorGitignore(true));
        Set<String> names = fileNames(files);
        assertFalse(names.contains("log.txt"));
        assertFalse(names.contains("built.txt"));
        assertTrue(names.contains("guide.md"));
        assertTrue(names.contains("Hello.java"));
    }

    @Test
    void grep_singleKeyword_findsContainingFilesAndSkipsBinary() throws IOException {
        List<FileSearch.FileMatch> matches = FileSearch.grep(root, FileSearch.SearchOptions.ofKeywords("hello"));
        assertEquals(Set.of("src/main/Hello.java", "docs/guide.md", "gen/output.txt", "app.log"),
                relativeNames(matches));
    }

    @Test
    void grep_multiKeyword_usesAhoCorasick() throws IOException {
        List<FileSearch.FileMatch> matches = FileSearch.grep(root, FileSearch.SearchOptions.ofKeywords("hello", "login"));
        assertEquals(Set.of("src/main/Hello.java", "docs/guide.md", "docs/context.txt",
                        "src/test/LoginTest.java", "gen/output.txt", "app.log"),
                relativeNames(matches));
    }

    @Test
    void grep_regex_usesJavaUtilRegex() throws IOException {
        List<FileSearch.FileMatch> matches = FileSearch.grep(root, FileSearch.SearchOptions.ofRegex("login|world"));
        assertEquals(Set.of("src/main/Hello.java", "docs/guide.md", "docs/context.txt", "src/test/LoginTest.java"),
                relativeNames(matches));
    }

    @Test
    void grep_contextLines_returnsSurroundingLines() throws IOException {
        List<FileSearch.FileMatch> matches = FileSearch.grep(root,
                FileSearch.SearchOptions.ofKeywords("login").withContextLines(1));
        FileSearch.FileMatch context = matches.stream()
                .filter(m -> m.relativePath().equals("docs/context.txt"))
                .findFirst().orElseThrow();
        assertEquals(3, context.lines().size());
        assertEquals(1L, context.lines().get(0).lineNumber());
        assertEquals("alpha", context.lines().get(0).text());
        assertEquals("beta login only here", context.lines().get(1).text());
        assertEquals(3L, context.lines().get(2).lineNumber());
        assertEquals("gamma", context.lines().get(2).text());
    }

    @Test
    void grep_caseInsensitive_lowercasesKeyword() throws IOException {
        List<FileSearch.FileMatch> matches = FileSearch.grep(root,
                FileSearch.SearchOptions.ofKeywords("HELLO").withCaseSensitive(false));
        assertEquals(Set.of("src/main/Hello.java", "docs/guide.md", "gen/output.txt", "app.log"),
                relativeNames(matches));
    }

    @Test
    void grep_parallelMatchesSequential() throws IOException {
        FileSearch.SearchOptions opts = FileSearch.SearchOptions.ofKeywords("hello");
        List<FileSearch.FileMatch> parallel = FileSearch.grep(root, opts.withParallel(true));
        List<FileSearch.FileMatch> sequential = FileSearch.grep(root, opts.withParallel(false));
        assertEquals(relativeNames(sequential), relativeNames(parallel));
        // 行号与文本也一致
        assertEquals(sequential.stream().map(FileSearch.FileMatch::lines).collect(Collectors.toList()),
                parallel.stream().map(FileSearch.FileMatch::lines).collect(Collectors.toList()));
    }

    @Test
    void stream_returnsSameFilesAsFindFiles() throws IOException {
        try (Stream<Path> s = FileSearch.stream(root, FileSearch.SearchOptions.ofKeywords("hello"))) {
            List<Path> files = s.collect(Collectors.toList());
            assertEquals(10, files.size());
            assertFalse(fileNames(files).contains("data.bin"));
        }
    }

    @Test
    void grep_streamingLargeFile_matchesInlineResult(@TempDir Path dir) throws IOException {
        Path big = dir.resolve("big.txt");
        try (BufferedWriter w = Files.newBufferedWriter(big, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 200_000; i++) {
                w.write("line " + i + (i % 1000 == 0 ? " target" : " plain") + "\n");
            }
        }
        // 强制走流式（inlineReadLimit=1KB），与整读路径（Long.MAX_VALUE）结果一致
        FileSearch.SearchOptions opts = FileSearch.SearchOptions.ofKeywords("target").withInlineReadLimit(1024);
        List<FileSearch.FileMatch> streaming = FileSearch.grep(dir, opts);
        List<FileSearch.FileMatch> inline = FileSearch.grep(dir, opts.withInlineReadLimit(Long.MAX_VALUE));
        assertEquals(1, streaming.size());
        assertEquals(200, streaming.get(0).lines().size());
        assertEquals(inline, streaming);
    }

    @Test
    void grep_streaming_contextLines_works(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("f.txt");
        Files.writeString(f, "alpha\nbeta target here\ngamma\ndelta\n", StandardCharsets.UTF_8);
        FileSearch.SearchOptions opts = FileSearch.SearchOptions.ofKeywords("target")
                .withInlineReadLimit(1).withContextLines(1);
        List<FileSearch.FileMatch> res = FileSearch.grep(dir, opts);
        assertEquals(1, res.size());
        assertEquals(List.of("alpha", "beta target here", "gamma"),
                res.get(0).lines().stream().map(FileSearch.LineMatch::text).collect(Collectors.toList()));
    }

    @Test
    void findFiles_nestedGitignore_appliesOnlyToSubtree(@TempDir Path dir) throws IOException {
        // 根 .gitignore 忽略 *.tmp；sub/.gitignore 只忽略 sub 下的 secret.log
        Files.writeString(dir.resolve(".gitignore"), "*.tmp\n", StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/.gitignore"), "secret.log\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("sub/secret.log"), "x\n");
        Files.writeString(dir.resolve("sub/keep.log"), "x\n");
        Files.writeString(dir.resolve("root.log"), "x\n");
        Files.writeString(dir.resolve("a.tmp"), "x\n");
        Files.writeString(dir.resolve("sub/b.tmp"), "x\n");

        List<Path> files = FileSearch.findFiles(dir, FileSearch.SearchOptions.ofKeywords("x").withHonorGitignore(true));
        Set<String> names = fileNames(files);
        assertFalse(names.contains("a.tmp"));
        assertFalse(names.contains("b.tmp"));
        assertFalse(names.contains("secret.log"));
        assertTrue(names.contains("root.log"));
        assertTrue(names.contains("keep.log"));
    }

    @Test
    void grep_maxLines_truncatesTotalLinesAndIsOrderIndependent(@TempDir Path dir) throws IOException {
        Path f1 = dir.resolve("f1.txt");
        Path f2 = dir.resolve("f2.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("hit line ").append(i).append('\n');
        }
        Files.writeString(f1, sb.toString(), StandardCharsets.UTF_8);
        Files.writeString(f2, sb.toString(), StandardCharsets.UTF_8);

        FileSearch.SearchOptions opts = FileSearch.SearchOptions.ofKeywords("hit").withMaxLines(5);
        List<FileSearch.FileMatch> par = FileSearch.grep(dir, opts);
        List<FileSearch.FileMatch> seq = FileSearch.grep(dir, opts.withParallel(false));
        // 总行数 ≤ 5，且 parallel / sequential 截断结果一致（与处理顺序无关）
        long totalLines = par.stream().mapToLong(m -> m.lines().size()).sum();
        assertEquals(5, totalLines);
        assertEquals(par, seq);
        // 只可能返回 1 个文件（每个文件 10 行，第 1 个文件即耗尽 5 行预算）
        assertEquals(1, par.size());
        assertEquals(5, par.get(0).lines().size());
    }

    private static void writeUtf16LeWithBom(Path file, String content) throws IOException {
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        byte[] body = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        Files.write(file, all);
    }

    @Test
    void findFiles_utf16WithBom_notFilteredAsBinary(@TempDir Path dir) throws IOException {
        writeUtf16LeWithBom(dir.resolve("utf16.txt"), "hello utf16\n");
        List<Path> files = FileSearch.findFiles(dir, FileSearch.SearchOptions.ofKeywords("hello"));
        assertTrue(fileNames(files).contains("utf16.txt"));
    }

    @Test
    void grep_utf16LeWithBom_searchedAsText(@TempDir Path dir) throws IOException {
        writeUtf16LeWithBom(dir.resolve("utf16.txt"), "hello utf16 world\nsecond line\n");
        List<FileSearch.FileMatch> res = FileSearch.grep(dir, FileSearch.SearchOptions.ofKeywords("utf16"));
        assertEquals(1, res.size());
        assertEquals(1L, res.get(0).lines().get(0).lineNumber());
        assertEquals("hello utf16 world", res.get(0).lines().get(0).text());
    }

    @Test
    void grep_utf16LeWithBom_streamingMatchesInline(@TempDir Path dir) throws IOException {
        writeUtf16LeWithBom(dir.resolve("utf16.txt"), "alpha\nbeta utf16 here\ngamma\n");
        FileSearch.SearchOptions opts = FileSearch.SearchOptions.ofKeywords("utf16").withInlineReadLimit(1);
        List<FileSearch.FileMatch> res = FileSearch.grep(dir, opts);
        assertEquals(1, res.size());
        assertEquals(2L, res.get(0).lines().get(0).lineNumber());
        assertEquals("beta utf16 here", res.get(0).lines().get(0).text());
    }

    @Test
    void grep_binaryNulBeyondProbe_skippedInInlineAndStreaming(@TempDir Path dir) throws IOException {
        // NUL 出现在第 9000 字节（超出旧实现的 8KB 探测范围）；新实现应全文判定为二进制并跳过
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 9000) {
            sb.append("hello plain text line\n");
        }
        sb.setLength(9000);
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = "end".getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 1 + tail.length];
        System.arraycopy(body, 0, out, 0, body.length);
        System.arraycopy(tail, 0, out, body.length + 1, tail.length); // body.length 处留默认 0 → NUL
        Files.write(dir.resolve("late.bin"), out);

        assertTrue(FileSearch.grep(dir, FileSearch.SearchOptions.ofKeywords("hello")).isEmpty());
        assertTrue(FileSearch.grep(dir, FileSearch.SearchOptions.ofKeywords("hello").withInlineReadLimit(1)).isEmpty());
    }

    @Test
    void grep_skipBinaryOff_searchesBinaryContent(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("data.bin");
        Files.writeString(f, "hello binary" + (char) 0 + "world\n", StandardCharsets.UTF_8);
        List<FileSearch.FileMatch> res = FileSearch.grep(dir,
                FileSearch.SearchOptions.ofKeywords("hello").withSkipBinary(false));
        assertEquals(1, res.size());
        assertTrue(res.get(0).lines().get(0).text().startsWith("hello binary"));
    }

    @Test
    void shutdownSearchExecutor_idempotentAndRecreatable() throws IOException {
        // 触发线程池懒创建
        FileSearch.grep(root, FileSearch.SearchOptions.ofKeywords("hello"));
        // 显式关闭（幂等：重复调用不抛异常、不报错）
        FileSearch.shutdownSearchExecutor();
        FileSearch.shutdownSearchExecutor();
        // 关闭后再次检索应仍可用（searchExecutor 按需懒重建）
        List<FileSearch.FileMatch> res = FileSearch.grep(root, FileSearch.SearchOptions.ofKeywords("hello"));
        assertEquals(4, res.size());
    }
}
