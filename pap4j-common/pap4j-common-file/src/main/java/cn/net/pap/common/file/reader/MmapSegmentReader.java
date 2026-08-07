package cn.net.pap.common.file.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 mmap 的分段读取器：只 map 需要的字节区间，避免整文件读入（对标 AI 编程工具精准读取文件片段的能力）。
 * <p>
 * 读取后通过 {@code sun.misc.Unsafe#invokeCleaner} 显式 unmap（JDK 9+），避免 {@link MappedByteBuffer}
 * 在 GC 前堆积映射段；unmap 失败时静默降级，交由 GC 回收。
 * <p>
 * 行区间读取 {@link #readLineRange(long, int)} 需从头扫描行边界（无行索引），读取后即 map 目标字节区间。
 */
public final class MmapSegmentReader implements AutoCloseable {

    private static final int SCAN_BUFFER = 8192;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Logger log = LoggerFactory.getLogger(MmapSegmentReader.class);
    private static final AtomicBoolean UNMAP_WARNED = new AtomicBoolean();

    private final FileChannel channel;
    private final long fileSize;

    public MmapSegmentReader(Path path) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        this.fileSize = channel.size();
    }

    /** 一次性读取指定字节区间的文本（UTF-8 解码，自动剥离 BOM）。 */
    public static String readSegment(Path path, long offset, int length) throws IOException {
        try (MmapSegmentReader reader = new MmapSegmentReader(path)) {
            return reader.readSegment(offset, length);
        }
    }

    /** 一次性读取指定字节区间的原始字节。 */
    public static byte[] readSegmentRaw(Path path, long offset, int length) throws IOException {
        try (MmapSegmentReader reader = new MmapSegmentReader(path)) {
            return reader.readSegmentRaw(offset, length);
        }
    }

    /** 一次性读取从 startLine 起的连续 lineCount 行（不足则截断到 EOF）。 */
    public static String readLineRange(Path path, long startLine, int lineCount) throws IOException {
        try (MmapSegmentReader reader = new MmapSegmentReader(path)) {
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
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, offset, actual);
        byte[] bytes = new byte[actual];
        buffer.get(bytes);
        unmap(buffer);
        return bytes;
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
        long[] range = findLineByteRange(startLine, lineCount);
        if (range[0] < 0 || range[0] >= range[1]) {
            return "";
        }
        return decode(readSegmentRaw(range[0], (int) (range[1] - range[0])), range[0] == 0);
    }

    /**
     * 扫描行边界，返回 [startByte, endByte)：startLine 起连续 lineCount 行的字节区间。
     */
    private long[] findLineByteRange(long startLine, int lineCount) throws IOException {
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

    private static void unmap(MappedByteBuffer buffer) {
        if (INVOKE_CLEANER != null && THE_UNSAFE != null) {
            try {
                INVOKE_CLEANER.invoke(THE_UNSAFE, buffer);
                return;
            } catch (Throwable t) {
                warnOnce("显式 unmap 失败，映射段交由 GC 回收（Windows 下可能短暂占用文件）", t);
                return;
            }
        }
        warnOnce("当前 JVM 不支持显式 unmap，映射段交由 GC 回收（Windows 下可能短暂占用文件）", null);
    }

    /** 首次失败打一条警告，避免每个片段读取都刷日志。 */
    private static void warnOnce(String message, Throwable t) {
        if (UNMAP_WARNED.compareAndSet(false, true)) {
            if (t != null) {
                log.warn(message, t);
            } else {
                log.warn(message);
            }
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static final Method INVOKE_CLEANER = loadInvokeCleaner();
    private static final Object THE_UNSAFE = loadUnsafe();

    private static Method loadInvokeCleaner() {
        try {
            return Class.forName("sun.misc.Unsafe").getMethod("invokeCleaner", ByteBuffer.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object loadUnsafe() {
        try {
            Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
