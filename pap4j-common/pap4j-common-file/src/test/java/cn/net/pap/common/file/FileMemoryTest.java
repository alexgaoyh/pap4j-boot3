package cn.net.pap.common.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Memory Test
 */
public class FileMemoryTest {

    // @Test
    public void memoryPrintTest() throws Exception {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        System.out.println("Total Memory: " + totalMemory / (1024 * 1024) + " Mb");
        System.out.println("Free Memory: " + freeMemory / (1024 * 1024) + " Mb");

        List<String> lines = Files.readAllLines(Paths.get("C:\\Users\\86181\\Desktop\\char_base.json"), StandardCharsets.UTF_8);

        long totalMemory2 = Runtime.getRuntime().totalMemory();
        long freeMemory2 = Runtime.getRuntime().freeMemory();

        System.out.println("Total Memory2: " + totalMemory2 / (1024 * 1024) + " Mb");
        System.out.println("Free Memory2: " + freeMemory2 / (1024 * 1024) + " Mb");
    }

}
