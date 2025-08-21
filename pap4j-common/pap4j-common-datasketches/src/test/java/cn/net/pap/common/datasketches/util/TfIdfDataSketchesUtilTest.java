package cn.net.pap.common.datasketches.util;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Map;

public class TfIdfDataSketchesUtilTest {

    @Test
    public void test1() {
        try {
            // 初始化 TF-IDF 计算器
            TfIdfDataSketchesUtil calculator = new TfIdfDataSketchesUtil(4096); // 4096 items

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
            Map<String, Double> docScores = calculator.getDocumentTfIdfScores(0);
            docScores.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(5)
                    .forEach(entry ->
                            System.out.printf("  %s: %.4f%n", entry.getKey(), entry.getValue()));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
