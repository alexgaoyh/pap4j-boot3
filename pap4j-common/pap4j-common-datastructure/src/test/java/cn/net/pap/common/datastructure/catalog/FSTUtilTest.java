package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.catalog.dto.FileSegmentDTO;
import cn.net.pap.common.datastructure.fst.FST;
import cn.net.pap.common.datastructure.fst.FSTUtil;
import cn.net.pap.common.datastructure.fst.ValueLocationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FSTUtilTest {

    @Test
    public void extbTest() throws IOException {
        FST dict = new FST();
        dict.addWord("\uD840\uDC00\uD840\uDC01\uD840\uDC02");
        dict.addWord("\uD840\uDC00\uD840\uDC01");
        dict.addWord("\uD840\uDC03\uD840\uDC04");

        String text = "叫\uD840\uDC00\uD840\uDC01\uD840\uDC02，曾用名是\uD840\uDC00\uD840\uDC01,别名是\uD840\uDC03\uD840\uDC04";
        List<ValueLocationDTO> result = FSTUtil.maxMatchLocation(text, dict);

        dict.removeWord("\uD840\uDC00\uD840\uDC01\uD840\uDC02");
        dict.removeWord("\uD840\uDC00\uD840\uDC01");

        List<ValueLocationDTO> result2 = FSTUtil.maxMatchLocation(text, dict);

        System.out.println();
    }


    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_TESTS", matches = "true")
    public void hanziTest() {
        FST dict = new FST();

        long start = System.currentTimeMillis();
        int idx = 0;
        int count = 0;
        for (char b1 = '\u4E00'; b1 <= '\u9FA5'; b1++) {
            for (char b2 = '\u4E00'; b2 <= '\u9FA5'; b2++) {
                if(!String.valueOf(b1).equals(String.valueOf(b2))) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(String.valueOf(b1).intern());
                    builder.append(String.valueOf(b2).intern());
                    dict.addWord(builder.toString());
                    count++;
                }
            }
            idx++;
            if(idx == 1000) {
                break;
            }
        }
        long end = System.currentTimeMillis();
        System.out.println("word count : " + count + " ; timeMillis " + (end - start));

        dict.addWord("分词");
        dict.addWord("彭胜");
        dict.addWord("彭胜文");
        dict.addWord("18");
        String text = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result = FSTUtil.maxMatchLocation(text, dict);
        System.out.println(result);

        dict.removeWord("18");
        dict.removeWord("彭胜文");
        String text2 = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result2 = FSTUtil.maxMatchLocation(text2, dict);
        System.out.println(result2);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_TESTS", matches = "true")
    public void test() throws Exception {
        FST dict = new FST();
        for(int i = 0; i < 10000000; i++) {
            dict.addWord(i + "");
        }
        dict.addWord("分词");
        dict.addWord("彭胜");
        dict.addWord("彭胜文");

        String text = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result = FSTUtil.maxMatchLocation(text, dict);
        System.out.println(result);

        dict.removeWord("18");
        dict.removeWord("彭胜文");
        String text2 = "试一试分词效果，我得名字叫彭胜文，曾用名是彭胜,我18岁";
        List<ValueLocationDTO> result2 = FSTUtil.maxMatchLocation(text2, dict);
        System.out.println(result2);

        List<String> maxMatchList = FSTUtil.maxMatch(text2, dict);
        System.out.println(maxMatchList);
    }

    /**
     * Optimized implementation took: 175623 ms
     * atomicInteger size: 1000000000
     *
     * [ValueLocationDTO{text='Kano;27.2', start=3, end=12}]
     * segment took: 13 ms
     * @throws Exception
     */
    // @Test
    public void billionSegment() throws Exception {
        String filePath = "d:\\measurements.txt";

        AtomicInteger atomicInteger = new AtomicInteger(0);
        FST dict = new FST();

        long startTime = System.currentTimeMillis();

        try {
            FileInputStream fileInputStream = new FileInputStream(new File(filePath));
            FileChannel fileChannel = fileInputStream.getChannel();
            List<FileSegmentDTO> fileSegmentDTOS = getFileSegments(new File(filePath), fileChannel);
            fileSegmentDTOS.parallelStream().forEach( fileSegmentDTO -> {
                try {
                    MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fileSegmentDTO.getStart(), fileSegmentDTO.getEnd() - fileSegmentDTO.getStart());
                    processBuffer(buffer, atomicInteger, dict);
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

        long startTimeSeg = System.currentTimeMillis();
        List<ValueLocationDTO> result2 = FSTUtil.maxMatchLocation("气象站Kano;27.2是某温度", dict);
        System.out.println(result2);
        long endTimeSeg = System.currentTimeMillis();
        System.out.println("segment took: " + (endTimeSeg - startTimeSeg) + " ms");

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

    private static void processBuffer(MappedByteBuffer buffer, AtomicInteger atomicInteger, FST dict) {
        StringBuilder line = new StringBuilder();

        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();
            if (c == '\n' || c == '\r') {
                if (line.length() > 0) {
                    dict.addWord(line.toString());
                    atomicInteger.incrementAndGet();
                    line.setLength(0);
                }
            } else {
                line.append(c);
            }
        }

        if (line.length() > 0) {
            dict.addWord(line.toString());
            atomicInteger.incrementAndGet();
        }
    }
}
