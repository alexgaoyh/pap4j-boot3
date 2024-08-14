package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

}
