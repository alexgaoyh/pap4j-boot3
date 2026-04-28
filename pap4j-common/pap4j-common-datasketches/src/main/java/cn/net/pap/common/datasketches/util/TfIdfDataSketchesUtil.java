package cn.net.pap.common.datasketches.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.datasketches.frequencies.ErrorType;
import org.apache.datasketches.frequencies.ItemsSketch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用 Apache DataSketches + Count-Min Sketch 实现近似 TF-IDF
 */
public class TfIdfDataSketchesUtil {
    private static final Logger log = LoggerFactory.getLogger(TfIdfDataSketchesUtil.class);

    // 用 ItemsSketch 来估计文档频率 (DF)
    private ItemsSketch<String> dfSketch;

    // 存储每个文档的词频 (近似 TF)
    private final List<DocumentTfSketch> documentTfSketches;

    // 总文档数
    private final AtomicLong totalDocuments;

    // CMS 配置参数
    private final int cmsWidth;
    private final int cmsDepth;
    private final int seed;

    // ItemsSketch 配置
    private final int mapSize;

    public TfIdfDataSketchesUtil(int mapSize, int cmsWidth, int cmsDepth, int seed) {
        this.mapSize = mapSize;
        this.dfSketch = new ItemsSketch<>(mapSize);
        this.documentTfSketches = new ArrayList<>();
        this.totalDocuments = new AtomicLong(0);

        this.cmsWidth = cmsWidth;
        this.cmsDepth = cmsDepth;
        this.seed = seed;
    }

    /**
     * 文本预处理和分词
     */
    private String[] preprocessAndTokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new String[0];
        }

        return text.split("");
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
                    log.info(String.format("Processed %d documents, active items: %d%n", lineCount, dfSketch.getNumActiveItems()));
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

        DocumentTfSketch tfSketch = new DocumentTfSketch(cmsWidth, cmsDepth, seed);

        // 统计当前文档的词频 (近似 TF)
        Set<String> uniqueWords = new HashSet<>();
        for (String word : words) {
            tfSketch.add(word);
            uniqueWords.add(word);
        }

        // 更新文档频率 (DF) - 每个词在文档中出现就记一次
        for (String uniqueWord : uniqueWords) {
            dfSketch.update(uniqueWord);
        }

        documentTfSketches.add(tfSketch);
        totalDocuments.incrementAndGet();
    }

    /**
     * 计算单个词的 TF-IDF 值
     */
    public double calculateTfIdf(String word, int documentIndex) {
        if (documentIndex < 0 || documentIndex >= documentTfSketches.size()) {
            throw new IllegalArgumentException("Document index out of bounds: " + documentIndex);
        }

        DocumentTfSketch tfSketch = documentTfSketches.get(documentIndex);

        // 获取词频 (TF)
        long termFrequency = tfSketch.getCount(word);
        if (termFrequency == 0) {
            return 0.0;
        }

        double tf = (double) termFrequency / tfSketch.getTotalWords();

        // 获取文档频率 (DF) 的估计值
        long documentFrequency = dfSketch.getEstimate(word);

        // 计算 IDF (使用平滑避免除零)
        double idf = Math.log((totalDocuments.get() + 1.0) / (documentFrequency + 1.0)) + 1.0;

        return tf * idf;
    }

    /**
     * 获取文档中所有词的 TF-IDF 分数（注意：CMS 无法直接枚举所有词）
     * 这里建议结合 dfSketch 的 frequent items 作为候选
     */
    public Map<String, Double> getDocumentTfIdfScores(int documentIndex, int topCandidateWords) {
        if (documentIndex < 0 || documentIndex >= documentTfSketches.size()) {
            throw new IllegalArgumentException("Document index out of bounds: " + documentIndex);
        }

        Map<String, Double> scores = new HashMap<>();

        // 候选词来自全局高频词（避免扫描整个 CMS）
        ItemsSketch.Row<String>[] rows = dfSketch.getFrequentItems(ErrorType.NO_FALSE_NEGATIVES);
        Arrays.sort(rows, Comparator.comparingLong(ItemsSketch.Row<String>::getEstimate).reversed());

        int limit = Math.min(topCandidateWords, rows.length);
        for (int i = 0; i < limit; i++) {
            String word = rows[i].getItem();
            double score = calculateTfIdf(word, documentIndex);
            if (score > 0) {
                scores.put(word, score);
            }
        }

        return scores;
    }

    /**
     * 获取所有文档中某个词的 TF-IDF 向量
     */
    public double[] getTfIdfVector(String word) {
        double[] vector = new double[documentTfSketches.size()];
        for (int i = 0; i < documentTfSketches.size(); i++) {
            vector[i] = calculateTfIdf(word, i);
        }
        return vector;
    }

    /**
     * 获取高频词列表
     */
    public List<String> getFrequentWords(int topN) {
        // 返回满足默认阈值（max error）的频繁项
        ItemsSketch.Row<String>[] rows = dfSketch.getFrequentItems(ErrorType.NO_FALSE_NEGATIVES);

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
        this.documentTfSketches.addAll(other.documentTfSketches);
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
        log.info("=== TF-IDF Statistics ===");
        log.info("{}", "Total documents: " + getTotalDocuments());
        log.info("{}", "Vocabulary size: " + getVocabularySize());
        log.info("{}", "Maximum error: " + getMaximumError());

        // 显示前10个高频词
        List<String> topWords = getFrequentWords(10);
        log.info("\nTop 10 frequent words:");
        for (String word : topWords) {
            long freq = dfSketch.getEstimate(word);
            log.info(String.format("  %s: %d documents%n", word, freq));
        }
    }

    /**
     * 单个文档的 TF 存储结构 (基于 Count-Min Sketch)
     */
    static class DocumentTfSketch {
        private final long[][] table;
        private final long[] hashA;
        private final int width;
        private final int depth;
        private final int prime = 2_147_483_647; // 大素数
        private int totalWords = 0;

        DocumentTfSketch(int width, int depth, int seed) {
            this.width = width;
            this.depth = depth;
            this.table = new long[depth][width];
            this.hashA = new long[depth];
            Random r = new Random(seed);
            for (int i = 0; i < depth; i++) {
                hashA[i] = r.nextInt(prime - 1) + 1;
            }
        }

        private int hash(String key, int i) {
            long h = hashA[i] * key.hashCode();
            h = (h % prime + prime) % prime;
            return (int) (h % width);
        }

        public void add(String key) {
            for (int i = 0; i < depth; i++) {
                int idx = hash(key, i);
                table[i][idx]++;
            }
            totalWords++;
        }

        public long getCount(String key) {
            long min = Long.MAX_VALUE;
            for (int i = 0; i < depth; i++) {
                int idx = hash(key, i);
                min = Math.min(min, table[i][idx]);
            }
            return min;
        }

        public int getTotalWords() {
            return totalWords;
        }
    }
}
