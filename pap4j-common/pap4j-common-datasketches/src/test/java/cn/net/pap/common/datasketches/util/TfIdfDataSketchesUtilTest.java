package cn.net.pap.common.datasketches.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Map;

public class TfIdfDataSketchesUtilTest {

    @Test
    public void test1() {
        try {
            // 初始化 TF-IDF 计算器
            TfIdfDataSketchesUtil calculator = new TfIdfDataSketchesUtil(4096, 2000, 5, 12345); // 4096 items

            System.out.println("Processing documents...");

            // 处理示例文档
            String[] sampleDocuments = {
                    "apache datasketches is a library for probabilistic data structures",
                    "datasketches provides efficient algorithms for big data processing",
                    "java implementation of datasketches is available on github",
                    "probabilistic data structures help with memory efficiency",
                    "apache software foundation supports many open source projects",
                    "big data processing requires efficient algorithms and data structures",
                    "apache spark and hadoop are popular big data frameworks",
                    "datasketches can be used with spark for approximate analytics",
                    "memory efficiency is important for large scale data processing",
                    "probabilistic algorithms provide approximate answers with guarantees"
            };

            for (String doc : sampleDocuments) {
                calculator.processDocument(doc);
            }

            // 打印统计信息
            calculator.printStatistics();

            // 计算特定词的 TF-IDF
            String targetWord = "datasketches";
            System.out.println("\nTF-IDF for '" + targetWord + "':");
            double[] tfidfScores = calculator.getTfIdfVector(targetWord);
            for (int i = 0; i < tfidfScores.length; i++) {
                System.out.printf("Document %d: %.4f%n", i, tfidfScores[i]);
            }

            // 获取第一个文档中最重要的词
            System.out.println("\nTop words in document 0:");
            Map<String, Double> docScores = calculator.getDocumentTfIdfScores(0, 5);
            docScores.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(5)
                    .forEach(entry ->
                            System.out.printf("  %s: %.4f%n", entry.getKey(), entry.getValue()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 常见编码列表
    private static final Charset[] SUPPORTED_CHARSETS = {
            StandardCharsets.UTF_8,
            StandardCharsets.ISO_8859_1,
            StandardCharsets.US_ASCII,
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            StandardCharsets.UTF_16,
            StandardCharsets.UTF_16BE,
            StandardCharsets.UTF_16LE
    };

    public static String readFileWithAutoEncoding(Path filePath) throws IOException {
        for (Charset charset : SUPPORTED_CHARSETS) {
            try {
                return new String(Files.readAllBytes(filePath), charset);
            } catch (MalformedInputException e) {
                // 尝试下一个编码
                continue;
            }
        }
        throw new IOException("无法使用支持的编码读取文件: " + filePath);
    }

    @Test
    public void test2() {
        try {
            // 初始化 TF-IDF 计算器
            TfIdfDataSketchesUtil calculator = new TfIdfDataSketchesUtil(65536, 2000, 5, 12345); // 4096 items

            System.out.println("Processing documents...");

            String folderPath = "D:\\小说0101";
            Path start = Paths.get(folderPath);

            Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    System.out.println("文件: " + file.toAbsolutePath());
                    try {
                        String strings = readFileWithAutoEncoding(file.toAbsolutePath());
                        calculator.processDocument(strings);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    //System.out.println("进入目录: " + dir.toAbsolutePath());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    //System.err.println("访问文件失败: " + file.toAbsolutePath() + " - " + exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });

            // 打印统计信息
            calculator.printStatistics();

            // 计算特定词的 TF-IDF
            String targetWord = "的";
            System.out.println("\nTF-IDF for '" + targetWord + "':");
            double[] tfidfScores = calculator.getTfIdfVector(targetWord);
            for (int i = 0; i < tfidfScores.length; i++) {
                System.out.printf("Document %d: %.4f%n", i, tfidfScores[i]);
            }

            // 获取第一个文档中最重要的词
            System.out.println("\nTop words in document 1:");
            Map<String, Double> docScores = calculator.getDocumentTfIdfScores(1, Integer.MAX_VALUE);
            docScores.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    // .limit(100)
                    .forEach(entry ->
                            System.out.printf("  %s: %.4f%n", entry.getKey(), entry.getValue()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
