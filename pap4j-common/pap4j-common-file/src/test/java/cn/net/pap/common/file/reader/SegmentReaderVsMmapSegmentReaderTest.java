package cn.net.pap.common.file.reader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SegmentReader} 与 {@link MmapSegmentReader} 的手动性能对比（同维度）。
 * <p>
 * 只保留"同维度"的对比：
 * <ul>
 *   <li>行区间读取：两边都不建索引、每次调用 O(fileSize) 从头重扫
 *       （{@link #lineRangeRepeatedReads_noIndex_sameDimension}）；</li>
 *   <li>文本检索：用 SegmentReader 分块整扫 / 行索引逐行两种方式实测
 *       （{@link #segmentSearch_findText_measured}）；</li>
 *   <li>UTF-8 跨块正确性：扩展区字符的字节被块边界劈开也能完整还原
 *       （{@link #searchByChunkScan_utf8CharsAtChunkBoundary}）。</li>
 * </ul>
 * 注意：不做"SegmentReader 建索引后 O(1)"与"Mmap 每次重扫"的直接对照——两者不在同一维度
 * （建索引有一次一次性成本），故不放在这里比较。
 * <p>
 * 性能对比部分阈值故意放得很宽（墙钟时间会抖动，别写死），重点看打印的实测数字；
 * 功能正确性（检索结果、UTF-8 跨块）仍用严格断言。
 */
class SegmentReaderVsMmapSegmentReaderTest {

    @TempDir
    Path tempDir;

    /** 行数：约 20MB 文件。 */
    private static final int LINE_COUNT = 500_000;

    /** 每个路径重复读取的行区间次数。 */
    private static final int RANGE_READS = 60;

    @Test
    void lineRangeRepeatedReads_noIndex_sameDimension() throws IOException {
        // 同一维度对比：两边都不建索引，每次调用都 O(fileSize) 从头扫行边界。
        // Mmap 静态方法每次重扫；SegmentReader 静态方法 useLineIndex=false 同样每次重扫。
        Path file = writeFixedWidthFile(LINE_COUNT);
        Files.readAllBytes(file); // 预热页缓存

        Random random = new Random(42);
        long[] startLines = new long[RANGE_READS];
        int[] lineCounts = new int[RANGE_READS];
        for (int i = 0; i < RANGE_READS; i++) {
            startLines[i] = 1 + random.nextInt(LINE_COUNT - 6);
            lineCounts[i] = 1 + random.nextInt(5);
        }

        // 热身（两边都没有索引可建）
        runRanges(s -> MmapSegmentReader.readLineRange(file, s[0], (int) s[1]), startLines, lineCounts, 2);
        runRanges(s -> SegmentReader.readLineRange(file, s[0], (int) s[1]), startLines, lineCounts, 2);

        long mmap = runRanges(
                s -> MmapSegmentReader.readLineRange(file, s[0], (int) s[1]), startLines, lineCounts, RANGE_READS);
        long seg = runRanges(
                s -> SegmentReader.readLineRange(file, s[0], (int) s[1]), startLines, lineCounts, RANGE_READS);

        // 参考：索引构建的一次性成本（不同维度，摊薄后才是 O(1) 读取）
        long indexBuild;
        try (SegmentReader indexed = new SegmentReader(file)) {
            long t0 = System.nanoTime();
            indexed.readLineRange(LINE_COUNT / 2, 3); // 首次调用触发构建
            indexBuild = (System.nanoTime() - t0) / 1_000_000;
        }

        System.out.printf("文件: %d 行, %.1f MB, 每次重扫 %d 次%n",
                LINE_COUNT, Files.size(file) / 1024.0 / 1024.0, RANGE_READS);
        System.out.printf("%-52s %8d ms%n", "MmapSegmentReader.readLineRange (static, 每次重扫)", mmap);
        System.out.printf("%-52s %8d ms%n", "SegmentReader.readLineRange   (static, 不建索引重扫)", seg);
        System.out.printf("%-52s %8d ms%n", "（参考）SegmentReader 实例首次调用含建索引", indexBuild);

        // 正确性：同一区间两条路径内容一致
        assertEquals(MmapSegmentReader.readLineRange(file, LINE_COUNT / 2, 3),
                SegmentReader.readLineRange(file, LINE_COUNT / 2, 3));

        // 同维度应接近：允许 SegmentReader 最多慢 3 倍（实际通常略快，靠 64KB buffer 省 syscall）。
        // 这里不断言方向，只防"位置读反而慢很多"的回归。
        assertTrue(seg < mmap * 3,
                "同维度重扫不应差太多: seg=" + seg + "ms, mmap=" + mmap + "ms");
    }

    @Test
    void segmentSearch_findText_measured() throws IOException {
        // 用 SegmentReader 自己做"查找文本"检索，给出匹配结果与实测耗时。
        // 参照（本机实测，记录在注释）：rg（Claude Code 内置，同一 20MB 文件）单次定位 79ms。
        Path file = writeFixedWidthFile(LINE_COUNT);
        Files.readAllBytes(file); // 预热页缓存
        String needle = "line-2500"; // 匹配 line-250000~250099 → 行号 250001~250100，共 100 处
        System.out.printf("检索 needle=%s (应 100 处), 文件 %d 行 %.1f MB%n",
                needle, LINE_COUNT, Files.size(file) / 1024.0 / 1024.0);

        // 方式一：利用行索引逐行迭代（每次 readLineRange 一行）
        long t0 = System.nanoTime();
        List<Integer> byIndex;
        try (SegmentReader reader = new SegmentReader(file)) {
            byIndex = searchByLineIndex(reader, needle, LINE_COUNT);
        }
        long indexMs = (System.nanoTime() - t0) / 1_000_000;

        // 方式二：readSegmentRaw 分块整扫（UTF-8 安全，64KB/块 + 状态化解码器）
        long t1 = System.nanoTime();
        List<Integer> byScan;
        try (SegmentReader reader = new SegmentReader(file)) {
            byScan = searchByChunkScan(reader, needle);
        }
        long scanMs = (System.nanoTime() - t1) / 1_000_000;

        // 方式二 fast：同样的解码器，行扫描改用向量化的 indexOf('\n')/indexOf(needle)
        long t2 = System.nanoTime();
        List<Integer> byScanFast;
        try (SegmentReader reader = new SegmentReader(file)) {
            byScanFast = searchByChunkScanFast(reader, needle);
        }
        long scanFastMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.printf("[行索引逐行]        匹配 %d 处: %d ms, 示例 %s%n",
                byIndex.size(), indexMs, sample(byIndex));
        System.out.printf("[分块整扫-逐字符]   匹配 %d 处: %d ms, 示例 %s%n",
                byScan.size(), scanMs, sample(byScan));
        System.out.printf("[分块整扫-indexOf]  匹配 %d 处: %d ms, 示例 %s%n",
                byScanFast.size(), scanFastMs, sample(byScanFast));
        System.out.printf("参照: 本机 rg(Claude Code 内置) 同文件单次定位 ≈ 79 ms（含进程启动税）%n");

        // 三种方式结果一致，且确实是 100 处
        assertEquals(byIndex, byScan);
        assertEquals(byIndex, byScanFast);
        assertEquals(100, byIndex.size());
        assertEquals(250_001, (int) byIndex.get(0));
        assertEquals(250_100, (int) byIndex.get(byIndex.size() - 1));
    }

    @Test
    void searchByChunkScan_utf8CharsAtChunkBoundary() throws IOException {
        // 扩展区（补充平面）字符 𝄞 (U+1D11E)，UTF-8 编码为 4 字节 F0 9D 84 9E。
        // 让它从字节偏移 65535 开始：F0 落在 64KB 块的最后一个字节，9D 84 9E 落在下一块——
        // 旧版"每块各自 readSegment 解码"在这里必乱码；新版靠解码器跨块补全能正确还原。
        String supplementary = "𝄞";
        String line1 = "a".repeat(65535) + supplementary + "b";
        Path file = tempDir.resolve("utf8-boundary.txt");
        Files.writeString(file, line1 + "\nsecond\n", StandardCharsets.UTF_8);

        try (SegmentReader reader = new SegmentReader(file)) {
            // 正确解码：能找到含 𝄞 的那一行（第 1 行），逐字符版 / indexOf 版都行
            assertEquals(List.of(1), searchByChunkScan(reader, supplementary));
            assertEquals(List.of(1), searchByChunkScanFast(reader, supplementary));
            // 整行读回来内容完整，没有乱码
            assertTrue(reader.readLineRange(1, 1).contains(supplementary));
            assertTrue(reader.readLineRange(2, 1).contains("second"));
        }

        // 原始两个类的 readLineRange：行对齐的整段一次性解码，天然 UTF-8 安全——
        // '\n'(0x0A) 是独立字节、绝不可能被多字节序列占用，所以行边界即字符边界，
        // 不会像"分块各自解码"那样劈开字符。用整行全等验证两类的行读取都不丢字。
        String expectLine1 = "a".repeat(65535) + supplementary + "b\n";
        assertEquals(expectLine1, SegmentReader.readLineRange(file, 1, 1));
        assertEquals(expectLine1, MmapSegmentReader.readLineRange(file, 1, 1));

        // 常规位置：BMP 中文（3 字节）与扩展区字符都要能检索到
        Path file2 = tempDir.resolve("utf8-normal.txt");
        Files.writeString(file2, "before\n中文\n" + supplementary + " tail\n", StandardCharsets.UTF_8);
        try (SegmentReader reader = new SegmentReader(file2)) {
            assertEquals(List.of(3), searchByChunkScan(reader, supplementary));
            assertEquals(List.of(2), searchByChunkScan(reader, "中"));
            assertEquals(List.of(3), searchByChunkScanFast(reader, supplementary));
            assertEquals(List.of(2), searchByChunkScanFast(reader, "中"));
        }
    }

    // ---- helpers ----

    private interface SegRead {
        String read(long[] range) throws IOException;
    }

    private static long runRanges(SegRead fn, long[] startLines, int[] lineCounts, int iterations) throws IOException {
        long t0 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            fn.read(new long[]{startLines[i % startLines.length], lineCounts[i % lineCounts.length]});
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    /** 方式一：利用行索引逐行迭代检索——每行一次 readLineRange(ln,1)，O(行数) 次调用。 */
    private static List<Integer> searchByLineIndex(SegmentReader reader, String needle, int lineCount)
            throws IOException {
        List<Integer> out = new ArrayList<>();
        for (int ln = 1; ln <= lineCount; ln++) {
            if (reader.readLineRange(ln, 1).contains(needle)) {
                out.add(ln);
            }
        }
        return out;
    }

    /**
     * 方式二（UTF-8 安全）：readSegmentRaw 按原始字节分块 + 状态化 CharsetDecoder。
     * <p>
     * 关键：解码器跨块保留未完成的 UTF-8 序列——多字节字符（含扩展区 4 字节字符）即使字节
     * 恰好被 64KB 块边界劈开，也会在下一块补齐后再输出，不会乱码。逐字符扫 + carry 拼行，
     * '\n' 结算，逻辑与旧版一致。
     */
    private static List<Integer> searchByChunkScan(SegmentReader reader, String needle) throws IOException {
        List<Integer> out = new ArrayList<>();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        byte[] pending = new byte[0]; // 上一块没吃完的不完整 UTF-8 序列尾部
        CharBuffer outBuf = CharBuffer.allocate(64 * 1024);
        long pos = 0;
        int line = 1;
        StringBuilder carry = new StringBuilder(80);
        while (true) {
            byte[] chunk = reader.readSegmentRaw(pos, 64 * 1024);
            if (chunk.length == 0) {
                break;
            }
            pos += chunk.length;
            byte[] combined = concat(pending, chunk);
            ByteBuffer in = ByteBuffer.wrap(combined);
            CoderResult result;
            do {
                outBuf.clear();
                result = decoder.decode(in, outBuf, false);
                outBuf.flip(); // limit=已解码字符数, position=0 —— CharBuffer.charAt 是相对 position 的
                line = scanChars(outBuf, 0, outBuf.limit(), carry, line, needle, out);
            } while (result.isOverflow());
            // UNDERFLOW 时 in 里剩的字节 = 不完整的 UTF-8 序列，留到下一块补全
            pending = Arrays.copyOfRange(combined, in.position(), combined.length);
        }
        // EOF：把最后残留的序列按"文件结束"解码（截断的尾字节 → 替换符 U+FFFD）
        outBuf.clear();
        decoder.decode(ByteBuffer.wrap(pending), outBuf, true);
        outBuf.flip();
        line = scanChars(outBuf, 0, outBuf.limit(), carry, line, needle, out);
        outBuf.clear();
        decoder.flush(outBuf);
        outBuf.flip();
        line = scanChars(outBuf, 0, outBuf.limit(), carry, line, needle, out);
        if (carry.length() > 0 && carry.indexOf(needle) >= 0) {
            out.add(line); // 末行无换行
        }
        return out;
    }

    /**
     * 方式二（indexOf 向量化版）：同样的字节分块 + 状态化解码器，但行扫描改用
     * {@link String#indexOf(String, int)} / {@link String#indexOf(int, int)}。
     * JDK 对这些内建是 SIMD 向量化的，替代逐字符循环里的"每字符分支 + StringBuilder.append"。
     * 整行在块内时直接用 indexOf 判断命中，不拼行字符串；只有跨块的行才进 carry。
     */
    private static List<Integer> searchByChunkScanFast(SegmentReader reader, String needle) throws IOException {
        List<Integer> out = new ArrayList<>();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        byte[] pending = new byte[0];
        CharBuffer outBuf = CharBuffer.allocate(64 * 1024);
        long pos = 0;
        int line = 1;
        StringBuilder carry = new StringBuilder(80);
        while (true) {
            byte[] chunk = reader.readSegmentRaw(pos, 64 * 1024);
            if (chunk.length == 0) {
                break;
            }
            pos += chunk.length;
            byte[] combined = concat(pending, chunk);
            ByteBuffer in = ByteBuffer.wrap(combined);
            CoderResult result;
            do {
                outBuf.clear();
                result = decoder.decode(in, outBuf, false);
                outBuf.flip();
                line = scanLinesFast(outBuf.toString(), carry, line, needle, out);
            } while (result.isOverflow());
            pending = Arrays.copyOfRange(combined, in.position(), combined.length);
        }
        // EOF：残留的不完整 UTF-8 序列按"文件结束"解码
        outBuf.clear();
        decoder.decode(ByteBuffer.wrap(pending), outBuf, true);
        outBuf.flip();
        line = scanLinesFast(outBuf.toString(), carry, line, needle, out);
        outBuf.clear();
        decoder.flush(outBuf);
        outBuf.flip();
        line = scanLinesFast(outBuf.toString(), carry, line, needle, out);
        if (carry.length() > 0 && carry.indexOf(needle) >= 0) {
            out.add(line); // 末行无换行
        }
        return out;
    }

    /** 用向量化的 indexOf 扫一段已解码文本：'\n' 定位行边界，行内 bounded 判命中，返回最新行号。 */
    private static int scanLinesFast(String text, StringBuilder carry, int line, String needle, List<Integer> out) {
        int from = 0;
        int nl;
        while ((nl = text.indexOf('\n', from)) >= 0) {
            if (carry.length() > 0) {
                // 上一块没结束的行，续到这里第一个 '\n'
                carry.append(text, from, nl);
                if (carry.indexOf(needle) >= 0) {
                    out.add(line);
                }
                carry.setLength(0);
            } else if (containsNeedle(text, from, nl, needle)) {
                out.add(line);
            }
            line++;
            from = nl + 1;
        }
        carry.append(text, from, text.length()); // 本块末尾未结束的行，续给下一块
        return line;
    }

    /**
     * 判断 text[from, to) 行区间内是否包含 needle。
     * <p>
     * 关键：不能用 {@code text.indexOf(needle, from)}——它会一路搜到字符串末尾（到块尾），
     * 每行都白扫大半个块。这里用"首字符 indexOf + regionMatches"把搜索限定在行内 [from, to)：
     * 首字符 indexOf 是向量化的，regionMatches 只比较 needle 长度。
     */
    private static boolean containsNeedle(String text, int from, int to, String needle) {
        char first = needle.charAt(0);
        int i = text.indexOf(first, from);
        while (i >= 0 && i < to) {
            if (text.regionMatches(i, needle, 0, needle.length())) {
                return true;
            }
            i = text.indexOf(first, i + 1);
        }
        return false;
    }

    /** 逐字符扫一段解码输出：普通字符进 carry，'\n' 结算当前行（命中记行号），返回最新行号。 */
    private static int scanChars(CharSequence chars, int start, int end, StringBuilder carry,
            int line, String needle, List<Integer> out) {
        for (int i = start; i < end; i++) {
            char c = chars.charAt(i);
            if (c == '\n') {
                if (carry.indexOf(needle) >= 0) {
                    out.add(line);
                }
                line++;
                carry.setLength(0);
            } else {
                carry.append(c);
            }
        }
        return line;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** 打印匹配行号的前 5 个 + 省略号（避免刷屏）。 */
    private static String sample(List<Integer> lines) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(lines.get(i));
        }
        if (lines.size() > 5) {
            sb.append(", ...");
        }
        return sb.append(']').toString();
    }

    /** 生成固定行宽的文件：`line-%06d` + 空格补齐到 40 字符 + 换行，约 41B/行。 */
    private Path writeFixedWidthFile(int lines) throws IOException {
        Path file = tempDir.resolve("perf-" + lines + ".txt");
        StringBuilder sb = new StringBuilder(48);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines; i++) {
                sb.setLength(0);
                sb.append("line-").append(String.format("%06d", i));
                while (sb.length() < 40) {
                    sb.append(' ');
                }
                sb.append('\n');
                w.write(sb.toString());
            }
        }
        return file;
    }
}
