package cn.net.pap.common.datastructure.chroniclemap.util;

import cn.net.pap.common.datastructure.chroniclemap.dto.TermStatDTO;
import net.openhft.chronicle.map.ChronicleMap;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChronicleMapTFIDFAnalyzer implements AutoCloseable {

    private final ChronicleMap<String, TermStatDTO> termMap;

    private final ChronicleMap<String, Long> metaMap;

    private static final String TOTAL_DOCS_KEY = "TOTAL_DOCS";

    private static final String TOTAL_TERMS_KEY = "TOTAL_TERMS";

    public ChronicleMapTFIDFAnalyzer(String baseDir) throws IOException {

        File dir = new File(baseDir);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 1. 存储词项统计信息的 Map
        termMap = ChronicleMap
                .of(String.class, TermStatDTO.class)
                .name("term-statistics")
                .entries(2_500_000)      // 预估词库大小
                .averageKeySize(16)      // 词平均长度
                .averageValue(new TermStatDTO())
                .createPersistedTo(new File(dir, "terms.dat"));

        // 2. 存储全局统计信息的 Map (总文档数、总词数)
        metaMap = ChronicleMap
                .of(String.class, Long.class)
                .name("meta-data")
                .entries(10)
                .averageKeySize(16)
                .createPersistedTo(new File(dir, "meta.dat"));

        metaMap.putIfAbsent(TOTAL_DOCS_KEY, 0L);
        metaMap.putIfAbsent(TOTAL_TERMS_KEY, 0L);
    }

    /**
     * 处理新文档：更新全局 TF 和 DF
     */
    public void processDocument(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return;

        // 更新总文档数
        metaMap.compute(TOTAL_DOCS_KEY, (k, v) -> v + 1);
        // 更新总词数
        metaMap.compute(TOTAL_TERMS_KEY, (k, v) -> v + tokens.size());

        // 计算当前文档内每个词的出现次数 (TF计数)
        Map<String, Long> docTermCounts = tokens.stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        // 将统计信息同步到持久化 Map
        docTermCounts.forEach((term, count) -> {
            TermStatDTO stat = termMap.get(term);
            if (stat == null) stat = new TermStatDTO();

            stat.globalTotalCount += count; // 累加全局词频
            stat.docCount += 1;             // 包含该词的文档数 +1

            termMap.put(term, stat);
        });
    }

    /**
     * 打印 TF-IDF 结果
     *
     * @param n 取前 N 个
     */
    public void printTopResults(int n) {
        long totalDocs = metaMap.getOrDefault(TOTAL_DOCS_KEY, 0L);
        long totalTerms = metaMap.getOrDefault(TOTAL_TERMS_KEY, 0L);

        if (totalDocs == 0) {
            System.out.println("没有可处理的数据。");
            return;
        }

        System.out.println("=== 统计报告 ===");
        System.out.println("总文档数: " + totalDocs);
        System.out.println("总词数: " + totalTerms);
        System.out.println("---------------------------------");

        // 计算并排序
        List<Map.Entry<String, Double>> tfIdfList = new ArrayList<>();

        termMap.forEach((term, stat) -> {
            // 这里我们使用“全局 TF” = (词在全局出现的总次数 / 全局总词数)
            // 或者你可以根据需求改为某个特定文档的 TF
            double tf = (double) stat.globalTotalCount / totalTerms;
            double idf = Math.log((double) totalDocs / (stat.docCount));
            double tfIdf = tf * idf;

            tfIdfList.add(new AbstractMap.SimpleEntry<>(term, tfIdf));
        });

        // 按 TF-IDF 降序排列
        tfIdfList.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

        System.out.printf("%-15s | %-10s | %-10s | %-10s%n", "词项", "TF(全局)", "IDF", "TF-IDF");
        tfIdfList.stream().limit(n).forEach(e -> {
            String term = e.getKey();
            TermStatDTO stat = termMap.get(term);
            double tf = (double) stat.globalTotalCount / totalTerms;
            double idf = Math.log((double) totalDocs / (stat.docCount));
            System.out.printf("%-15s | %-10.6f | %-10.6f | %-10.6f%n",
                    term, tf, idf, e.getValue());
        });
    }

    @Override
    public void close() {
        if (termMap != null) termMap.close();
        if (metaMap != null) metaMap.close();
    }

}
