package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class BigTextProcessorTest {
    private static final Logger log = LoggerFactory.getLogger(BigTextProcessorTest.class);

    @Test
    public void testBigText() throws IOException {
        String outputPath = Files.createTempFile("testBigText", ".txt").toAbsolutePath().toString();
        try {
            generateBigFile(Paths.get(outputPath), 1000000);

            log.info("====== 测试一：流式读取 (BufferedReader) ======");
            runWithMetrics(() -> {
                try {
                    readFile(outputPath.toString(), line -> {
                        List<String> words = new ArrayList<>();
                        words.add(line);
                        return words;
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // ================= 阶段三：方式二对比 =================
            log.info("\n====== 测试二：一次性全部读取 (Files.readAllLines) ======");
            runWithMetrics(() -> {
                try {
                    List<String> allLines = Files.readAllLines(Paths.get(outputPath), StandardCharsets.UTF_8);
                    // 模拟一下读取后的操作，防止被JVM优化掉
                    if (!allLines.isEmpty()) {
                        String first = allLines.get(0);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            File file = new File(outputPath);
            if (file.exists()) {
                file.delete();
            }
        }
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

    /**
     * 执行任务并监控耗时与内存增长
     */
    private void runWithMetrics(Runnable task) {
        // 先建议JVM执行一次垃圾回收，尽量保证测试基线干净
        System.gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Runtime runtime = Runtime.getRuntime();
        // 计算执行前的已用内存
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        // 执行具体的读取方法
        task.run();

        long endTime = System.currentTimeMillis();
        // 计算执行后的已用内存
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsed = afterMemory - beforeMemory;

        log.info("{}", "-> 耗时: " + (endTime - startTime) + " ms");
        // 如果触发了隐式的GC可能导致差值为负数，这里简单取 max(0, val) 处理
        log.info("{}", "-> 内存消耗(估值): " + Math.max(0, memoryUsed / (1024 * 1024)) + " MB");
    }

    /**
     * 以流式方式读取大型txt文件，确保不会出现中文乱码。
     *
     * @param filePath      文件路径
     * @param lineProcessor 每一行的处理逻辑
     * @throws IOException 如果读取文件时发生错误
     */
    public static void readFile(String filePath, LineProcessor lineProcessor) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> words = lineProcessor.process(line);
                // log.info(words);
            }
        }
    }

    /**
     * 每一行的处理逻辑接口
     */
    public interface LineProcessor {
        List<String> process(String line);
    }

}
