package cn.net.pap.common.file;

import cn.net.pap.common.file.dto.FileSegmentDTO;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadFileToMapUtilTest {

    public static final Integer MAX_LINE_NUMBER = 10000000;

    public static final Integer MAX_LINE_NUMBER2 = 50000000;

    //@Test
    public void createBigFile() throws Exception {
        FileWriter fw = new FileWriter(new File("big-file.txt"));
        BufferedWriter bw = new BufferedWriter(fw);

        for (int idx = 0; idx < MAX_LINE_NUMBER; idx++) {
            bw.write(idx + "," + idx + "\n");
        }
        bw.close();
        fw.close();
    }

    // @Test
    public void createBigFile2() throws Exception {
        FileWriter fw = new FileWriter(new File("C:\\Users\\86181\\Desktop\\big-file2.txt"));
        BufferedWriter bw = new BufferedWriter(fw);

        for (int idx = 0; idx < MAX_LINE_NUMBER2; idx++) {
            bw.write(idx + "\n");
        }
        bw.close();
        fw.close();
    }

    //@Test
    public void toMapTest() {
        String filePath = "big-file.txt";

        long startTime = System.nanoTime();
        ConcurrentHashMap<String, String> fileMap = ReadFileToMapUtil.toMap(filePath, (byte) ',');
        long endTime = System.nanoTime();

        System.out.println("Optimized implementation took: " + (endTime - startTime) / 1e6 + " ms");
        System.out.println("file Map size: " + fileMap.size());
        assertTrue(fileMap.size() == MAX_LINE_NUMBER);

    }

    //@Test
    public void toMapTest2() {
        String filePath = "C:\\Users\\86181\\Desktop\\big-file2.txt";

        long startTime = System.currentTimeMillis();
        ConcurrentHashMap<String, String> fileMap = ReadFileToMapUtil.toMap(filePath, (byte) ';');
        long endTime = System.currentTimeMillis();

        System.out.println("Optimized implementation took: " + (endTime - startTime) + " ms");
        System.out.println("file Map size: " + fileMap.size());
        System.out.println();

        // todo compare with  Files.readAllLines(Paths.get(filePath)); method
    }

    //@Test
    public void toMapTest3() throws Exception {
        String filePath = "C:\\Users\\86181\\Desktop\\big-file2.txt";

        long startTime = System.currentTimeMillis();

        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;
        List<String> words = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {
            words.add(line);
        }
        reader.close();

        long endTime = System.currentTimeMillis();

        System.out.println("Optimized implementation took: " + (endTime - startTime) + " ms");
        System.out.println("file Map size: " + words.size());
        System.out.println();
    }

    // @Test
    public void toMapTest4() throws Exception {
        String filePath = "13g-file.txt";

        AtomicInteger atomicInteger = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        long bufferSize = 1024 * 1024 * 100;

        try (RandomAccessFile file = new RandomAccessFile(filePath, "r");
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

        System.out.println("Optimized implementation took: " + (endTime - startTime) + " ms");
        System.out.println();
    }

    // @Test
    public void toMapTest5() throws Exception {
        String filePath = "13g-file.txt";

        AtomicInteger atomicInteger = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        try {
            FileInputStream fileInputStream = new FileInputStream(new File(filePath));
            FileChannel fileChannel = fileInputStream.getChannel();
            List<FileSegmentDTO> fileSegmentDTOS = getFileSegments(new File(filePath), fileChannel);
            fileSegmentDTOS.parallelStream().forEach( fileSegmentDTO -> {
                try {
                    MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fileSegmentDTO.getStart(), fileSegmentDTO.getEnd() - fileSegmentDTO.getStart());
                    processBuffer(buffer, atomicInteger);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        long endTime = System.currentTimeMillis();

        System.out.println("Optimized implementation took: " + (endTime - startTime) + " ms");
        System.out.println("atomicInteger size: " + (atomicInteger));
        System.out.println();
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
            // todo System.out.println(line.toString());
            // can check size using atomicInteger.incrementAndGet();
        }
    }
}
