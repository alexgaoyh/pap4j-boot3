package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Memory Test
 */
public class FileMemoryTest {

    @Test
    public void memoryPrintTest() throws Exception {
        String outputPath = Files.createTempFile("testBigText", ".txt").toAbsolutePath().toString();
        generateBigFile(Paths.get(outputPath), 1000000);

        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        System.out.println("Total Memory: " + totalMemory / (1024 * 1024) + " Mb");
        System.out.println("Free Memory: " + freeMemory / (1024 * 1024) + " Mb");

        List<String> lines = Files.readAllLines(Paths.get(outputPath), StandardCharsets.UTF_8);

        long totalMemory2 = Runtime.getRuntime().totalMemory();
        long freeMemory2 = Runtime.getRuntime().freeMemory();

        System.out.println("Total Memory2: " + totalMemory2 / (1024 * 1024) + " Mb");
        System.out.println("Free Memory2: " + freeMemory2 / (1024 * 1024) + " Mb");

        new File(outputPath).deleteOnExit();
    }

    /**
     * 生成测试用的文本大文件
     */
    private void generateBigFile(Path filePath, int lineCount) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < lineCount; i++) {
                writer.write("这是一行用于测试内存占用的长文本数据，行号为: " + i + "。使用流式读取与一次性读取会产生显著差异。");
                writer.newLine();
            }
        }
    }

}
