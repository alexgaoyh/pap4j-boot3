package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.file.dto.FileSegmentDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadFileToMapUtilTest {
    private static final Logger log = LoggerFactory.getLogger(ReadFileToMapUtilTest.class);

    public static final Integer MAX_LINE_NUMBER = 10000000;

    public static final Integer MAX_LINE_NUMBER2 = 10000000;

    // 提取统一的文件路径，使用相对路径以便于统一生成和清理
    private static String FILE_PATH_1;
    private static String FILE_PATH_2;
    private static String FILE_PATH_3;

    @BeforeAll
    public static void setup() throws Exception {
        FILE_PATH_1 = Files.createTempFile("big-file", ".txt").toAbsolutePath().toString();
        FILE_PATH_2 = Files.createTempFile("big-file", ".txt").toAbsolutePath().toString();
        FILE_PATH_3 = Files.createTempFile("big-file", ".txt").toAbsolutePath().toString();
        log.info("====== [BeforeAll] 开始初始化测试大文件 ======");
        createBigFile();
        createBigFile2();
        createBigFile3(); // 初始化 13g 测试文件
        log.info("====== [BeforeAll] 测试文件初始化完成 ======\n");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        log.info("\n====== [AfterAll] 开始清理测试文件 ======");
        // 强制提示 JVM 执行垃圾回收，释放 MappedByteBuffer 占用的文件系统锁
        System.gc();
        System.runFinalization();

        // 给 GC 一点时间来完成对象的回收和句柄的释放
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (FILE_PATH_1 != null) {
            Files.deleteIfExists(Paths.get(FILE_PATH_1));
        }
        if (FILE_PATH_2 != null) {
            Files.deleteIfExists(Paths.get(FILE_PATH_2));
        }
        if (FILE_PATH_3 != null) {
            Files.deleteIfExists(Paths.get(FILE_PATH_3));
        }
        log.info("====== [AfterAll] 测试文件清理完成 ======");
    }

    public static void createBigFile() throws Exception {
        FileWriter fw = new FileWriter(new File(FILE_PATH_1));
        BufferedWriter bw = new BufferedWriter(fw);

        for (int idx = 0; idx < MAX_LINE_NUMBER; idx++) {
            bw.write(idx + "," + idx + "\n");
        }
        bw.close();
        fw.close();
    }

    public static void createBigFile2() throws Exception {
        FileWriter fw = new FileWriter(new File(FILE_PATH_2));
        BufferedWriter bw = new BufferedWriter(fw);

        for (int idx = 0; idx < MAX_LINE_NUMBER2; idx++) {
            bw.write(idx + "\n");
        }
        bw.close();
        fw.close();
    }

    public static void createBigFile3() throws Exception {
        // 提供一个占位文件供 test4 和 test5 消费，避免 FileNotFoundException
        FileWriter fw = new FileWriter(new File(FILE_PATH_3));
        BufferedWriter bw = new BufferedWriter(fw);
        for (int idx = 0; idx < 10000; idx++) { // 象征性写入部分数据
            bw.write("test_data_" + idx + "\n");
        }
        bw.close();
        fw.close();
    }

    @Test
    public void toMapTest() {
        long startTime = System.nanoTime();
        ConcurrentHashMap<String, String> fileMap = ReadFileToMapUtil.toMap(FILE_PATH_1, (byte) ',');
        long endTime = System.nanoTime();

        log.info("{}", "Optimized implementation took: " + (endTime - startTime) / 1e6 + " ms");
        log.info("{}", "file Map size: " + fileMap.size());
        assertTrue(fileMap.size() == MAX_LINE_NUMBER);
    }

    @Test
    public void toMapTest2() {
        long startTime = System.currentTimeMillis();
        ConcurrentHashMap<String, String> fileMap = ReadFileToMapUtil.toMap(FILE_PATH_2, (byte) ';');
        long endTime = System.currentTimeMillis();

        log.info("{}", "Optimized implementation took: " + (endTime - startTime) + " ms");
        log.info("{}", "file Map size: " + fileMap.size());
        log.info("");
    }

    @Test
    public void toMapTest3() throws Exception {
        long startTime = System.currentTimeMillis();

        BufferedReader reader = new BufferedReader(new FileReader(FILE_PATH_2));
        String line;
        List<String> words = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
            words.add(line);
        }
        reader.close();

        long endTime = System.currentTimeMillis();

        log.info("{}", "Optimized implementation took: " + (endTime - startTime) + " ms");
        log.info("{}", "file Map size: " + words.size());
        log.info("");
    }

    @Test
    public void toMapTest4() throws Exception {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        long bufferSize = 1024 * 1024 * 100;

        try (RandomAccessFile file = new RandomAccessFile(FILE_PATH_3, "r");
             FileChannel fileChannel = file.getChannel()) {

            long fileSize = fileChannel.size();
            long position = 0;

            while (position < fileSize) {
                long remaining = fileSize - position;
                long mappedSize = Math.min(bufferSize, remaining);

                MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, position, mappedSize);
                position += mappedSize;

                processBuffer(buffer, atomicInteger);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();

        log.info("{}", "Optimized implementation took: " + (endTime - startTime) + " ms");
        log.info("");
    }

    @Test
    public void toMapTest5() throws Exception {
        AtomicInteger atomicInteger = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        try {
            try (FileInputStream fileInputStream = new FileInputStream(new File(FILE_PATH_3));
                 FileChannel fileChannel = fileInputStream.getChannel()) {
                List<FileSegmentDTO> fileSegmentDTOS = getFileSegments(new File(FILE_PATH_3), fileChannel);
                fileSegmentDTOS.parallelStream().forEach( fileSegmentDTO -> {
                    try {
                        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fileSegmentDTO.getStart(), fileSegmentDTO.getEnd() - fileSegmentDTO.getStart());
                        processBuffer(buffer, atomicInteger);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        long endTime = System.currentTimeMillis();

        log.info("{}", "Optimized implementation took: " + (endTime - startTime) + " ms");
        log.info("{}", "atomicInteger size: " + (atomicInteger));
        log.info("");
    }

    private static List<FileSegmentDTO> getFileSegments(final File file, final FileChannel fileChannel) throws IOException {
        final int numberOfSegments = Runtime.getRuntime().availableProcessors();
        final long fileSize = file.length();
        final long segmentSize = fileSize / numberOfSegments;
        final List<FileSegmentDTO> segments = new ArrayList<>();
        if (segmentSize < 1000) {
            segments.add(new FileSegmentDTO(0, fileSize, fileChannel));
            return segments;
        }
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            long segStart = 0;
            long segEnd = segmentSize;
            while (segStart < fileSize) {
                segEnd = findSegment(randomAccessFile, segEnd, fileSize);
                segments.add(new FileSegmentDTO(segStart, segEnd, fileChannel));
                segStart = segEnd;
                segEnd = Math.min(fileSize, segEnd + segmentSize);
            }
        }
        return segments;
    }

    private static long findSegment(RandomAccessFile raf, long location, final long fileSize) throws IOException {
        raf.seek(location);
        while (location < fileSize) {
            location++;
            if (raf.read() == '\n')
                return location;
        }
        return location;
    }

    private static void processBuffer(MappedByteBuffer buffer, AtomicInteger atomicInteger) {
        StringBuilder line = new StringBuilder();

        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();
            if (c == '\n' || c == '\r') {
                if (line.length() > 0) {
                    // todo Process the line
                    // can check size using atomicInteger.incrementAndGet();
                    line.setLength(0);
                }
            } else {
                line.append(c);
            }
        }

        if (line.length() > 0) {
            // todo log.info(line.toString());
            // can check size using atomicInteger.incrementAndGet();
        }
    }
}
