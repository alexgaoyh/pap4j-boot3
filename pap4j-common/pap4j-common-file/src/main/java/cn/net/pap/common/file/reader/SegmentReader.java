package cn.net.pap.common.file.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * 基于位置读 + 懒构建行索引的分段读取器：只读需要的字节区间，避免整文件读入
 * （对标 AI 编程工具精准读取文件片段的能力，与 {@link MmapSegmentReader} 同 API、同语义）。
 * <p>
 * 与 {@link MmapSegmentReader} 的差异：
 * <ul>
 *   <li><b>无 mmap</b>：通过 {@link FileChannel#read(ByteBuffer, long)} 位置读，把目标区间
 *       直接读进堆数组——没有映射段、不需要显式 unmap、不依赖 {@code sun.misc.Unsafe}，
 *       Windows 下也不会锁文件；</li>
 *   <li><b>O(1) 行区间读取</b>：{@link #readLineRange(long, int)} 首次调用时全文件扫描一遍，
 *       构建"每行起始字节偏移"索引（一次性 O(fileSize)），之后任意行区间读取都是两次数组
 *       查表 + 一次位置读，不再像 {@code MmapSegmentReader} 那样每次从头扫描行边界；</li>
 *   <li><b>索引按文件大小选存储类型</b>：文件 &lt; 2GB 时偏移装得下 int，用 {@code int[]} 存储
 *       （驻留内存减半），否则用 {@code long[]}；行数超过 {@link #MAX_LINE_INDEX_ENTRIES} 时
 *       放弃索引，回退为逐次扫描（与 {@code MmapSegmentReader} 相同的 O(fileSize)/次），
 *       避免超大文件上索引占掉过多堆内存。</li>
 * </ul>
 * <p>
 * 静态一次性 {@link #readLineRange(Path, long, int)} 直接走逐次扫描、不建索引（为一次读取建整个
 * 索引是浪费）；需反复取同一文件的行区间时，请持有实例多次调用实例方法，首次建索引后每次 O(1)。
 * <p>
 * 索引基于打开时刻的文件快照，文件在持有期间被修改后索引可能失效（{@link #readSegmentRaw(long, int)}
 * 与 {@link #readSegment(long, int)} 始终按最新文件内容读，不受索引影响）。请通过
 * try-with-resources 使用本类；实例非线程安全，多线程并发读请使用静态一次性方法。
 */
public final class SegmentReader implements AutoCloseable {

    private static final int SCAN_BUFFER = 64 * 1024;

    /** 行索引允许的最大条目数；超过则放弃索引、回退逐次扫描（约 int[] 40MB / long[] 80MB）。 */
    private static final int MAX_LINE_INDEX_ENTRIES = 10_000_000;

    /** 行索引条目上限（默认取 {@link #MAX_LINE_INDEX_ENTRIES}）。包内可见，便于测试触发"超限回退"分支。 */
    static volatile int lineIndexEntryLimit = MAX_LINE_INDEX_ENTRIES;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Logger log = LoggerFactory.getLogger(SegmentReader.class);

    private final FileChannel channel;
    private final long fileSize;

    /** 是否为实例构建行索引。静态一次性 readLineRange 为 false，避免为一次读取建整个索引。 */
    private final boolean useLineIndex;

    /** 懒构建的行索引；null 表示尚未构建（或已放弃构建，见 {@link #indexUnavailable}）。 */
    private volatile LineStarts index;

    /** 行索引构建被放弃（超限）时为 true，此后 readLineRange 走逐次扫描。 */
    private volatile boolean indexUnavailable;

    public SegmentReader(Path path) throws IOException {
        this(path, true);
    }

    private SegmentReader(Path path, boolean useLineIndex) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        this.fileSize = channel.size();
        this.useLineIndex = useLineIndex;
    }

    /** 一次性读取指定字节区间的文本（UTF-8 解码，自动剥离 BOM）。 */
    public static String readSegment(Path path, long offset, int length) throws IOException {
        try (SegmentReader reader = new SegmentReader(path)) {
            return reader.readSegment(offset, length);
        }
    }

    /** 一次性读取指定字节区间的原始字节。 */
    public static byte[] readSegmentRaw(Path path, long offset, int length) throws IOException {
        try (SegmentReader reader = new SegmentReader(path)) {
            return reader.readSegmentRaw(offset, length);
        }
    }

    /**
     * 一次性读取从 startLine 起的连续 lineCount 行（不足则截断到 EOF）。
     * <p>
     * 一次性调用不建行索引，直接逐次扫描（每次 O(fileSize)）——为读一次而构建整个索引是浪费；
     * 若需反复取同一文件的行区间，请持有实例并多次调用实例方法（首次建索引后 O(1)）。
     */
    public static String readLineRange(Path path, long startLine, int lineCount) throws IOException {
        try (SegmentReader reader = new SegmentReader(path, false)) {
            return reader.readLineRange(startLine, lineCount);
        }
    }

    /** 读取指定字节区间的文本（UTF-8 解码，自动剥离 BOM）。 */
    public String readSegment(long offset, int length) throws IOException {
        return decode(readSegmentRaw(offset, length), offset == 0);
    }

    /**
     * 读取指定字节区间的原始字节。区间越界时自动截断；offset 超出文件长度返回空数组。
     */
    public byte[] readSegmentRaw(long offset, int length) throws IOException {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("offset/length 必须非负: offset=" + offset + ", length=" + length);
        }
        if (length == 0 || offset >= fileSize) {
            return new byte[0];
        }
        int actual = (int) Math.min((long) length, fileSize - offset);
        return readFully(offset, actual);
    }

    /**
     * 读取从 startLine（1-based）起的连续 lineCount 行；不足则返回实际行数，越界返回空串。
     */
    public String readLineRange(long startLine, int lineCount) throws IOException {
        if (startLine <= 0) {
            throw new IllegalArgumentException("startLine 必须 >= 1: " + startLine);
        }
        if (lineCount <= 0) {
            return "";
        }
        LineStarts starts = lineIndex();
        if (starts == null) {
            return readLineRangeByScan(startLine, lineCount);
        }
        if (startLine - 1 >= starts.length()) {
            return "";
        }
        int from = (int) (startLine - 1);
        long start = starts.get(from);
        // lastLine 是区间内最后一行的下标，区间终点取其下一行起点（天然包含该行行尾换行符）
        int lastLine = from + lineCount;
        long end = lastLine < starts.length() ? starts.get(lastLine) : fileSize;
        if (start >= end) {
            return "";
        }
        return decode(readFully(start, (int) (end - start)), start == 0);
    }

    /** 位置读：[offset, offset+length) 完整读入堆数组；文件实际不足则截断到 EOF。 */
    private byte[] readFully(long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(bytes); // 零拷贝视图，无 unwrap；数组即存储
        long pos = offset;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, pos);
            if (n <= 0) {
                break; // EOF
            }
            pos += n;
        }
        // 正常情况（区间已按 fileSize 截断）position == length，直接返回；
        // 仅当文件在持有期间被改短时 position < length，此时裁一次再返回
        return buffer.position() == length ? bytes : Arrays.copyOf(bytes, buffer.position());
    }

    /** 懒构建行索引：首次调用 O(fileSize) 扫描一次，之后 O(1) 查表；一次性读取或超限放弃则返回 null。 */
    private LineStarts lineIndex() throws IOException {
        if (!useLineIndex) {
            return null; // 一次性读取：不建索引，readLineRange 走逐次扫描
        }
        LineStarts idx = index;
        if (idx != null) {
            return idx;
        }
        if (indexUnavailable) {
            return null;
        }
        synchronized (this) {
            idx = index;
            if (idx != null) {
                return idx;
            }
            if (indexUnavailable) {
                return null;
            }
            try {
                idx = buildLineStarts();
                index = idx;
                return idx;
            } catch (IndexLimitExceeded e) {
                indexUnavailable = true;
                log.error("文件行数超过索引上限({} 条)，readLineRange 回退为逐次扫描（每次 O(fileSize)）",
                        lineIndexEntryLimit, e);
                return null;
            }
        }
    }

    /**
     * 全文件扫描一遍构建行索引。文件 &lt; 2GB 时用 int[] 存储偏移（驻留内存减半），否则 long[]。
     * 首元素恒为 0；文件尾有换行符时会包含一个等于 fileSize 的末尾空行起点。
     * 条目数达到 {@link #lineIndexEntryLimit} 时抛 {@link IndexLimitExceeded}。
     */
    private LineStarts buildLineStarts() throws IOException {
        int limit = lineIndexEntryLimit;
        if (fileSize <= Integer.MAX_VALUE) {
            // 常见路径：偏移装得下 int，直接建 int[]，避免先用 long[] 再转的翻倍驻留
            IntList starts = new IntList();
            starts.add(0);
            byte[] buf = new byte[SCAN_BUFFER];
            long pos = 0;
            while (pos < fileSize) {
                int n = channel.read(ByteBuffer.wrap(buf), pos);
                if (n <= 0) {
                    break;
                }
                for (int i = 0; i < n; i++) {
                    if (buf[i] == '\n') {
                        if (starts.size() >= limit) {
                            throw new IndexLimitExceeded();
                        }
                        starts.add((int) (pos + i + 1));
                    }
                }
                pos += n;
            }
            LineStarts idx = LineStarts.ofInts(starts.toArray());
            log.debug("构建行索引: fileSize={}, lines={}, storage=int[]", fileSize, idx.length());
            return idx;
        }
        LongList starts = new LongList();
        starts.add(0);
        byte[] buf = new byte[SCAN_BUFFER];
        long pos = 0;
        while (pos < fileSize) {
            int n = channel.read(ByteBuffer.wrap(buf), pos);
            if (n <= 0) {
                break;
            }
            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    if (starts.size() >= limit) {
                        throw new IndexLimitExceeded();
                    }
                    starts.add(pos + i + 1);
                }
            }
            pos += n;
        }
        LineStarts idx = LineStarts.ofLongs(starts.toArray());
        log.debug("构建行索引: fileSize={}, lines={}, storage=long[]", fileSize, idx.length());
        return idx;
    }

    /** 无索引回退：从头扫描行边界，返回 [startByte, endByte)。O(fileSize)/次。 */
    private String readLineRangeByScan(long startLine, int lineCount) throws IOException {
        long[] range = scanLineByteRange(startLine, lineCount);
        if (range[0] < 0 || range[0] >= range[1]) {
            return "";
        }
        return decode(readFully(range[0], (int) (range[1] - range[0])), range[0] == 0);
    }

    /** 从头逐 '\n' 扫描，定位 [startLine, startLine+lineCount) 的字节区间（含行尾换行符）。 */
    private long[] scanLineByteRange(long startLine, int lineCount) throws IOException {
        long endAfterNewline = startLine + lineCount - 1;
        long startByte = startLine == 1 ? 0 : -1;
        long endByte = -1;
        long newlinesSeen = 0;
        byte[] buf = new byte[SCAN_BUFFER];
        long scanPos = 0;
        outer:
        while (true) {
            int n = channel.read(ByteBuffer.wrap(buf), scanPos);
            if (n <= 0) {
                if (startByte < 0) {
                    startByte = fileSize;
                }
                endByte = fileSize;
                break;
            }
            for (int i = 0; i < n; i++) {
                if (buf[i] == '\n') {
                    newlinesSeen++;
                    if (startByte < 0 && newlinesSeen == startLine - 1) {
                        startByte = scanPos + i + 1;
                    }
                    if (newlinesSeen == endAfterNewline) {
                        endByte = scanPos + i + 1;
                        break outer;
                    }
                }
            }
            scanPos += n;
        }
        if (endByte < 0) {
            endByte = fileSize;
        }
        return new long[]{startByte, endByte};
    }

    private static String decode(byte[] bytes, boolean stripBom) {
        if (bytes.length == 0) {
            return "";
        }
        int start = 0;
        if (stripBom && bytes.length >= 3
                && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            start = 3;
        }
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /** 行索引：按文件大小用 int[]（< 2GB）或 long[]（>= 2GB）存储每行起始字节偏移。 */
    private static final class LineStarts {
        private final int[] ints;
        private final long[] longs;

        private LineStarts(int[] ints, long[] longs) {
            this.ints = ints;
            this.longs = longs;
        }

        static LineStarts ofInts(int[] arr) {
            return new LineStarts(arr, null);
        }

        static LineStarts ofLongs(long[] arr) {
            return new LineStarts(null, arr);
        }

        long get(int i) {
            return ints != null ? ints[i] : longs[i];
        }

        int length() {
            return ints != null ? ints.length : longs.length;
        }
    }

    /** 行索引构建超过条目上限时抛出，由 {@link #lineIndex()} 捕获并触发回退。 */
    private static final class IndexLimitExceeded extends RuntimeException {
    }

    /** 避免装箱的可增长 int 数组（构建 int 行索引时使用，减轻构建期临时内存）。 */
    private static final class IntList {
        private int[] data = new int[16];
        private int size;

        void add(int value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length << 1);
            }
            data[size++] = value;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }

    /** 避免装箱的可增长 long 数组（构建 long 行索引时使用）。 */
    private static final class LongList {
        private long[] data = new long[16];
        private int size;

        void add(long value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length << 1);
            }
            data[size++] = value;
        }

        int size() {
            return size;
        }

        long[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
