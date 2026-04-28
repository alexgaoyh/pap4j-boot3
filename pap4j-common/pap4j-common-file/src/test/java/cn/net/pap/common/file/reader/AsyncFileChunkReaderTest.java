package cn.net.pap.common.file.reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Random;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class AsyncFileChunkReaderTest {

    private static final Logger log = LoggerFactory.getLogger(AsyncFileChunkReaderTest.class);

    private static Path testFile;

    @BeforeAll
    static void setup() throws IOException {
        testFile = Files.createTempFile("large-test-", ".bin");
        log.info("{}", "Generating test file: " + testFile);

        byte[] data = new byte[1024 * 1024 * 1024]; // 1024MB
        new Random().nextBytes(data);
        Files.write(testFile, data, StandardOpenOption.WRITE);
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(testFile);
    }

    @Test
    void testReadSingleChunk() throws Exception {
        try (AsyncFileChunkReader reader = new AsyncFileChunkReader(testFile)) {
            byte[] chunk = reader.readChunkBlocking(1024, 4096);
            assertEquals(4096, chunk.length);
            log.info("Read single 4KB chunk success.");
        }
    }

    @Test
    void testConcurrentReads() throws Exception {
        try (AsyncFileChunkReader reader = new AsyncFileChunkReader(testFile)) {
            Future<byte[]> f1 = reader.readChunk(0, 1024 * 1024);
            Future<byte[]> f2 = reader.readChunk(1 * 1024 * 1024, 1024 * 1024);
            Future<byte[]> f3 = reader.readChunk(2 * 1024 * 1024, 1024 * 1024);

            byte[] c1 = f1.get();
            byte[] c2 = f2.get();
            byte[] c3 = f3.get();

            assertEquals(1024 * 1024, c1.length);
            assertEquals(1024 * 1024, c2.length);
            assertEquals(1024 * 1024, c3.length);
            log.info(String.format("Concurrent reads done: %d MB total%n", (c1.length + c2.length + c3.length) / (1024 * 1024)));
        }
    }

    @Test
    void testRepeatedOpenAndRead() throws Exception {
        final int iterations = 100; // 循环100次
        final int chunkSize = 1024 * 1024; // 每次读取1MB

        long totalBytes = 0;
        long start = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            // 每次循环都新建并关闭 reader
            try (AsyncFileChunkReader reader = new AsyncFileChunkReader(testFile)) {
                long offset = (long) (Math.random() * (4 * 1024 * 1024)); // 随机偏移（前4MB）
                byte[] chunk = reader.readChunkBlocking(offset, chunkSize);

                assertNotNull(chunk);
                assertTrue(chunk.length > 0, "Chunk must not be empty");
                totalBytes += chunk.length;
            }

            if (i % 10 == 0) {
                log.info(String.format("Loop %d / %d done%n", i + 1, iterations));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info(String.format("Repeated open/read test done: %d MB read in %d ms%n",
                totalBytes / (1024 * 1024), elapsed));

        // 断言总读取量大致正确
        assertTrue(totalBytes > 50 * 1024 * 1024L / 2, "Should read at least 25MB total");
    }

    @Test
    void testReadAtEndOfFile() throws Exception {
        long fileSize = Files.size(testFile);
        int readSize = 4096;
        try (AsyncFileChunkReader reader = new AsyncFileChunkReader(testFile)) {
            byte[] chunk = reader.readChunkBlocking(fileSize - readSize / 2, readSize);
            assertTrue(chunk.length > 0);
            log.info("{}", "EOF partial read, bytes read: " + chunk.length);
        }
    }
}

