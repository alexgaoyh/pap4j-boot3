package cn.net.pap.common.datasketches.util;

import org.apache.datasketches.frequencies.ErrorType;
import org.apache.datasketches.frequencies.ItemsSketch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class TfIdfDataSketchesUtil {

    // 使用 ItemsSketch 来估计文档频率 (DF)
    private ItemsSketch<String> dfSketch;

    // 存储每个文档的词频 (TF)
    private final List<Map<String, Integer>> documentTfMaps;

    // 总文档数
    private final AtomicLong totalDocuments;

    // 配置参数
    private final int mapSize;

    public TfIdfDataSketchesUtil(int mapSize) {
        this.mapSize = mapSize;
        this.dfSketch = new ItemsSketch<String>(mapSize);
        this.documentTfMaps = new ArrayList<>();
        this.totalDocuments = new AtomicLong(0);
    }

    /**
     * 文本预处理和分词
     */
    private String[] preprocessAndTokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }

        return text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim().split("\\s+");
    }

    /**
     * 处理文档流，构建 TF 和 DF 统计
     */
    public void processDocuments(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8), 2 * 1024 * 1024)) {
            String line;
            int lineCount = 0;

            while ((line = reader.readLine()) != null) {
                processDocument(line);
                lineCount++;

                if (lineCount % 10000 == 0) {
                    System.out.printf("Processed %d documents, active items: %d%n", lineCount, dfSketch.getNumActiveItems());
                }
            }
        }
    }

    /**
     * 处理单个文档（一行）
     */
    public void processDocument(String documentText) {
        String[] words = preprocessAndTokenize(documentText);

        if (words.length == 0) {
            return;
        }

        Map<String, Integer> tfMap = new HashMap<>();

        // 统计当前文档的词频 (TF)
        for (String word : words) {
            if (word.length() < 2) continue;
            tfMap.put(word, tfMap.getOrDefault(word, 0) + 1);
        }

        // 更新文档频率 (DF) - 每个词在文档中出现就记一次
        for (String uniqueWord : tfMap.keySet()) {
            dfSketch.update(uniqueWord);
        }

        documentTfMaps.add(tfMap);
        totalDocuments.incrementAndGet();
    }

    /**
     * 计算单个词的 TF-IDF 值
     */
    public double calculateTfIdf(String word, int documentIndex) {
        if (documentIndex < 0 || documentIndex >= documentTfMaps.size()) {
            throw new IllegalArgumentException("Document index out of bounds: " + documentIndex);
        }

        Map<String, Integer> tfMap = documentTfMaps.get(documentIndex);

        // 获取词频 (TF)
        Integer termFrequency = tfMap.get(word);
        if (termFrequency == null || termFrequency == 0) {
            return 0.0;
        }

        // 计算 TF (词频比例)
        int totalWordsInDoc = tfMap.values().stream().mapToInt(Integer::intValue).sum();
        double tf = (double) termFrequency / totalWordsInDoc;

        // 获取文档频率 (DF) 的估计值
        long documentFrequency = dfSketch.getEstimate(word);

        // 计算 IDF (使用平滑避免除零)
        double idf = Math.log((totalDocuments.get() + 1.0) / (documentFrequency + 1.0)) + 1.0;

        return tf * idf;
    }

    /**
     * 获取文档中所有词的 TF-IDF 分数
     */
    public Map<String, Double> getDocumentTfIdfScores(int documentIndex) {
        if (documentIndex < 0 || documentIndex >= documentTfMaps.size()) {
            throw new IllegalArgumentException("Document index out of bounds: " + documentIndex);
        }

        Map<String, Double> scores = new HashMap<>();
        Map<String, Integer> tfMap = documentTfMaps.get(documentIndex);

        for (String word : tfMap.keySet()) {
            scores.put(word, calculateTfIdf(word, documentIndex));
        }

        return scores;
    }

    /**
     * 获取所有文档中某个词的 TF-IDF 向量
     */
    public double[] getTfIdfVector(String word) {
        double[] vector = new double[documentTfMaps.size()];
        for (int i = 0; i < documentTfMaps.size(); i++) {
            vector[i] = calculateTfIdf(word, i);
        }
        return vector;
    }

    /**
     * 获取高频词列表
     */
    public List<String> getFrequentWords(int topN) {
        // 返回满足默认阈值（max error）的频繁项
        ItemsSketch.Row<String>[] rows = dfSketch.getFrequentItems(ErrorType.NO_FALSE_POSITIVES);

        // 按估计频次降序
        Arrays.sort(rows, Comparator.comparingLong(ItemsSketch.Row<String>::getEstimate).reversed());

        List<String> result = new ArrayList<>(Math.min(topN, rows.length));
        for (int i = 0; i < Math.min(topN, rows.length); i++) {
            result.add(rows[i].getItem());
        }
        return result;
    }


    /**
     * 合并多个 Sketch
     */
    public void merge(TfIdfDataSketchesUtil other) {
        this.dfSketch.merge(other.dfSketch);
        this.documentTfMaps.addAll(other.documentTfMaps);
        this.totalDocuments.addAndGet(other.totalDocuments.get());
    }

    // 获取统计信息
    public long getTotalDocuments() {
        return totalDocuments.get();
    }

    public int getVocabularySize() {
        return dfSketch.getNumActiveItems();
    }


    public double getMaximumError() {
        return dfSketch.getMaximumError();
    }

    public void printStatistics() {
        System.out.println("=== TF-IDF Statistics ===");
        System.out.println("Total documents: " + getTotalDocuments());
        System.out.println("Vocabulary size: " + getVocabularySize());
        System.out.println("Maximum error: " + getMaximumError());

        // 显示前10个高频词
        List<String> topWords = getFrequentWords(10);
        System.out.println("\nTop 10 frequent words:");
        for (String word : topWords) {
            long freq = dfSketch.getEstimate(word);
            System.out.printf("  %s: %d documents%n", word, freq);
        }
    }
}

