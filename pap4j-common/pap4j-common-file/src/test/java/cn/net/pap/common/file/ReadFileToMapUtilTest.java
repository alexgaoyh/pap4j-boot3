package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadFileToMapUtilTest {

    public static final Integer MAX_LINE_NUMBER = 10000000;

    @Test
    public void createBigFile() throws Exception {
        FileWriter fw = new FileWriter(new File("big-file.txt"));
        BufferedWriter bw = new BufferedWriter(fw);

        for (int idx = 0; idx < MAX_LINE_NUMBER; idx++) {
            bw.write(idx + "," + idx + "\n");
        }
        bw.close();
        fw.close();
    }

    @Test
    public void toMapTest() {
        String filePath = "big-file.txt";

        long startTime = System.nanoTime();
        ConcurrentHashMap<String, String> fileMap = ReadFileToMapUtil.toMap(filePath, (byte) ',');
        long endTime = System.nanoTime();

        System.out.println("Optimized implementation took: " + (endTime - startTime) / 1e6 + " ms");
        System.out.println("file Map size: " + fileMap.size());
        assertTrue(fileMap.size() == MAX_LINE_NUMBER);

    }

}
