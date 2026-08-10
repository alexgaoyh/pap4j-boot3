package cn.net.pap.common.file.reader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SegmentReaderTest {

    @TempDir
    Path tempDir;

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readSegment_basicAndClamp() throws IOException {
        Path file = writeFile("basic.txt", "aa\nbb\ncc\ndd\n");
        assertEquals("aa\nbb", SegmentReader.readSegment(file, 0, 5));
        // 越界自动截断到 EOF
        assertEquals("cc\ndd\n", SegmentReader.readSegment(file, 6, 100));
        // offset 超出文件长度 → 空串
        assertEquals("", SegmentReader.readSegment(file, 100, 5));
        // length=0 → 空
        assertEquals("", SegmentReader.readSegment(file, 0, 0));
        assertArrayEquals("bb\n".getBytes(StandardCharsets.UTF_8), SegmentReader.readSegmentRaw(file, 3, 3));
    }

    @Test
    void readSegment_stripsUtf8Bom() throws IOException {
        Path file = tempDir.resolve("bom.txt");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        Files.write(file, concat(bom, "hello\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("hello\n", SegmentReader.readSegment(file, 0, 9));
        assertEquals("", SegmentReader.readSegment(file, 0, 3));
        assertEquals("hello\n", SegmentReader.readSegment(file, 3, 6));
    }

    @Test
    void readLineRange_smallFile() throws IOException {
        Path file = writeFile("lines.txt", "aa\nbb\ncc\ndd\n");
        assertEquals("aa\n", SegmentReader.readLineRange(file, 1, 1));
        assertEquals("bb\ncc\n", SegmentReader.readLineRange(file, 2, 2));
        assertEquals("dd\n", SegmentReader.readLineRange(file, 4, 1));
        // 不足则截断
        assertEquals("cc\ndd\n", SegmentReader.readLineRange(file, 3, 5));
        // 越界 → 空
        assertEquals("", SegmentReader.readLineRange(file, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> SegmentReader.readLineRange(file, 0, 1));
    }

    @Test
    void readLineRange_noTrailingNewline() throws IOException {
        Path file = writeFile("notrail.txt", "a\nb\nc");
        assertEquals("c", SegmentReader.readLineRange(file, 3, 1));
        assertEquals("b\nc", SegmentReader.readLineRange(file, 2, 2));
        assertEquals("a\nb\nc", SegmentReader.readLineRange(file, 1, 3));
    }

    @Test
    void readLineRange_endsWithNewline_trailingEmptyLineIsOutOfRange() throws IOException {
        // 文件尾有换行符时索引会带一个等于 fileSize 的"末尾空行"起点；按语义它不构成真实行
        Path file = writeFile("trail.txt", "aa\nbb\n");
        assertEquals("bb\n", SegmentReader.readLineRange(file, 2, 1));
        assertEquals("", SegmentReader.readLineRange(file, 3, 1));
        assertEquals("aa\nbb\n", SegmentReader.readLineRange(file, 1, 2));
    }

    @Test
    void readLineRange_emptyFile() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");
        assertEquals("", SegmentReader.readLineRange(file, 1, 1));
    }

    @Test
    void readLineRange_fallsBackToScanWhenIndexLimitExceeded() throws IOException {
        int saved = SegmentReader.lineIndexEntryLimit;
        try {
            SegmentReader.lineIndexEntryLimit = 3; // 极小上限，强制实例放弃索引、走逐次扫描
            Path file = writeFile("scanfallback.txt", "aa\nbb\ncc\ndd\n");
            try (SegmentReader reader = new SegmentReader(file)) {
                // 实例路径仍会尝试建索引；撞上限后回退逐次扫描，语义与索引路径一致
                assertEquals("aa\n", reader.readLineRange(1, 1));
                assertEquals("bb\ncc\n", reader.readLineRange(2, 2));
                assertEquals("cc\ndd\n", reader.readLineRange(3, 5));
                assertEquals("", reader.readLineRange(5, 1));
            }
            assertThrows(IllegalArgumentException.class, () -> SegmentReader.readLineRange(file, 0, 1));
        } finally {
            SegmentReader.lineIndexEntryLimit = saved;
        }
    }

    @Test
    void readLineRange_largeFile() throws IOException {
        Path file = tempDir.resolve("large.txt");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 100_000; i++) {
                w.write(String.format("line-%06d\n", i));
            }
        }
        assertEquals("line-000000\nline-000001\nline-000002\n",
                SegmentReader.readLineRange(file, 1, 3));
        assertEquals("line-049999\nline-050000\nline-050001\n",
                SegmentReader.readLineRange(file, 50_000, 3));
        // 同一实例上反复随机取行区间：索引只构建一次，各次结果一致
        try (SegmentReader reader = new SegmentReader(file)) {
            String a = reader.readLineRange(20_000, 2);
            String b = reader.readLineRange(80_000, 2);
            String a2 = reader.readLineRange(20_000, 2);
            assertEquals(a, a2);
            assertEquals("line-019999\nline-020000\n", a);
            assertEquals("line-079999\nline-080000\n", b);
        }
    }

    @Test
    void readLineRange_instanceUsesIndexPath() throws IOException {
        Path file = writeFile("instidx.txt", "aa\nbb\ncc\ndd\n");
        // 实例（useLineIndex=true）走索引路径：首次建 int[] 索引，之后 O(1) 查表。
        // 静态一次性 readLineRange 已改为直接逐次扫描，故用实例单独验证索引路径本身。
        try (SegmentReader reader = new SegmentReader(file)) {
            assertEquals("aa\n", reader.readLineRange(1, 1));
            assertEquals("bb\ncc\n", reader.readLineRange(2, 2));
            assertEquals("dd\n", reader.readLineRange(4, 1));
            assertEquals("", reader.readLineRange(5, 1));
        }
    }

    @Test
    void readSegment_largeFileOffset() throws IOException {
        byte[] pattern = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("big.bin");
        try (OutputStream out = Files.newOutputStream(file)) {
            // chunk 长度取 pattern 长度的整数倍，保证跨块时相位对齐（否则偏移处内容与预期不符）
            byte[] chunk = new byte[pattern.length * 30_000];
            for (int i = 0; i < chunk.length; i++) {
                chunk[i] = pattern[i % pattern.length];
            }
            for (int i = 0; i < 60; i++) {
                out.write(chunk);
            }
        }
        assertEquals(new String(pattern, StandardCharsets.UTF_8),
                SegmentReader.readSegment(file, 0, pattern.length));
        // 大偏移量（3600 万字节处）且跨模式边界对齐读取
        long offset = 36L * 1_000_000;
        String expected = new String(pattern, StandardCharsets.UTF_8)
                + new String(pattern, StandardCharsets.UTF_8);
        assertEquals(expected, SegmentReader.readSegment(file, offset, pattern.length * 2));
        // 未对齐偏移
        assertEquals(new String(pattern, StandardCharsets.UTF_8).substring(10, 30),
                SegmentReader.readSegment(file, offset + 10, 20));
    }

    @Test
    void close_isIdempotent() throws IOException {
        Path file = writeFile("close.txt", "hello\n");
        SegmentReader reader = new SegmentReader(file);
        assertEquals("hello\n", reader.readSegment(0, 6));
        reader.close();
        assertDoesNotThrow(reader::close);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
