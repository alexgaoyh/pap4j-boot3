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

public class MmapSegmentReaderTest {

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
        assertEquals("aa\nbb", MmapSegmentReader.readSegment(file, 0, 5));
        // 越界自动截断到 EOF
        assertEquals("cc\ndd\n", MmapSegmentReader.readSegment(file, 6, 100));
        // offset 超出文件长度 → 空串
        assertEquals("", MmapSegmentReader.readSegment(file, 100, 5));
        // length=0 → 空
        assertEquals("", MmapSegmentReader.readSegment(file, 0, 0));
        assertArrayEquals("bb\n".getBytes(StandardCharsets.UTF_8), MmapSegmentReader.readSegmentRaw(file, 3, 3));
    }

    @Test
    void readSegment_stripsUtf8Bom() throws IOException {
        Path file = tempDir.resolve("bom.txt");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        Files.write(file, concat(bom, "hello\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals("hello\n", MmapSegmentReader.readSegment(file, 0, 9));
        assertEquals("", MmapSegmentReader.readSegment(file, 0, 3));
        assertEquals("hello\n", MmapSegmentReader.readSegment(file, 3, 6));
    }

    @Test
    void readLineRange_smallFile() throws IOException {
        Path file = writeFile("lines.txt", "aa\nbb\ncc\ndd\n");
        assertEquals("aa\n", MmapSegmentReader.readLineRange(file, 1, 1));
        assertEquals("bb\ncc\n", MmapSegmentReader.readLineRange(file, 2, 2));
        assertEquals("dd\n", MmapSegmentReader.readLineRange(file, 4, 1));
        // 不足则截断
        assertEquals("cc\ndd\n", MmapSegmentReader.readLineRange(file, 3, 5));
        // 越界 → 空
        assertEquals("", MmapSegmentReader.readLineRange(file, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> MmapSegmentReader.readLineRange(file, 0, 1));
    }

    @Test
    void readLineRange_noTrailingNewline() throws IOException {
        Path file = writeFile("notrail.txt", "a\nb\nc");
        assertEquals("c", MmapSegmentReader.readLineRange(file, 3, 1));
        assertEquals("b\nc", MmapSegmentReader.readLineRange(file, 2, 2));
        assertEquals("a\nb\nc", MmapSegmentReader.readLineRange(file, 1, 3));
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
                MmapSegmentReader.readLineRange(file, 1, 3));
        assertEquals("line-049999\nline-050000\nline-050001\n",
                MmapSegmentReader.readLineRange(file, 50_000, 3));
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
                MmapSegmentReader.readSegment(file, 0, pattern.length));
        // 大偏移量（3600 万字节处）且跨模式边界对齐读取
        long offset = 36L * 1_000_000;
        String expected = new String(pattern, StandardCharsets.UTF_8)
                + new String(pattern, StandardCharsets.UTF_8);
        assertEquals(expected, MmapSegmentReader.readSegment(file, offset, pattern.length * 2));
        // 未对齐偏移
        assertEquals(new String(pattern, StandardCharsets.UTF_8).substring(10, 30),
                MmapSegmentReader.readSegment(file, offset + 10, 20));
    }

    @Test
    void close_isIdempotent() throws IOException {
        Path file = writeFile("close.txt", "hello\n");
        MmapSegmentReader reader = new MmapSegmentReader(file);
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
