package cn.net.pap.example.spring.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class RagRetrievalTest {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalTest.class);

    @Autowired
    @Qualifier("knowledgeVectorStore")
    private VectorStore vectorStore;

    private record TestCase(String question, String expectedFile, String desc) {
    }

    private record EvalResult(
            int index,
            TestCase testCase,
            boolean isRecalled,
            int hitRank,
            Double hitScore,
            long costMs,
            List<Document> candidates
    ) {
    }

    private static final List<TestCase> TEST_CASES = List.of(
            new TestCase("如何进行高精度除法计算以计算点击率或费率？", "function_div2.md", "高精度除法 (DIV2)"),
            new TestCase("系统根据什么规则把表达式分发路由给 JsonPath 或 QLExpress 引擎？", "engine_routing_strategy.md", "引擎路由策略"),
            new TestCase("怎样判断一个变量是空值或者仅仅由空格组成？", "function_isblank.md", "空值校验 (ISBLANK)"),
            new TestCase("在 QLExpress 脚本中如何去提取或者解析复杂的 JSON 节点数据？", "function_json_path.md", "JSONPath 数据提取"),
            new TestCase("有没有可以将扁平的树状结构数据转换成平铺列表的函数？", "function_tree_flatten.md", "树结构扁平化 (TREE_FLATTEN)"),
            new TestCase("我想去除文本前后的所有空白和换行字符，有专门的函数吗？", "function_trim.md", "去除首尾空格 (TRIM)"),
            new TestCase("在表达式里，如何将一个 list 里面的所有元素用指定的连接符拼接成一个字符串？", "function_list_join.md", "列表元素拼接 (LIST_JOIN)"),
            new TestCase("QLExpress 中怎么做三元逻辑判断？有对应的函数或者语法吗？", "function_ternary.md", "三元运算符 (TERNARY)"),
            // 注意：此处的查询词 "QLExpress 引擎能否处理树形 JSON 数据" 属于典型的"注意力稀释与检索漂移"现象。
            // 原始短查询 "你能处理树形json吗" 能 100% 命中，但因改写模型引入了高频通用词（"QLExpress"、"引擎"、"数据"），
            // 导致向量表示向宏观架构文档漂移而未能在纯向量检索（Top 3）下命中。在实际应用中需要通过 Hybrid 混合检索（向量 + BM25）解决此类漂移。
            // 当前对比 输入是“改写提炼模型”，改为后是“QLExpress 引擎能否处理树形 JSON 数据”，检索关键词抽取是“QLExpress 树形 JSON 处理”
            new TestCase("QLExpress 引擎能否处理树形 JSON 数据", "function_tree_flatten.md", "树形JSON处理 (TREE_FLATTEN)")
    );

    @Test
    @DisplayName("Evaluate RAG Retrieval Quality")
    public void testRetrievalQuality() {
        List<EvalResult> results = new ArrayList<>();
        int recalledCount = 0;

        for (int i = 0; i < TEST_CASES.size(); i++) {
            TestCase tc = TEST_CASES.get(i);
            long start = System.currentTimeMillis();

            List<Document> candidates = vectorStore.similaritySearch(
                    SearchRequest.builder().query(tc.question()).topK(3).build()
            );

            long cost = System.currentTimeMillis() - start;

            boolean recalled = false;
            int rank = -1;
            Double score = null;

            for (int r = 0; r < candidates.size(); r++) {
                Document doc = candidates.get(r);
                String source = (String) doc.getMetadata().get("source");
                if (tc.expectedFile().equalsIgnoreCase(source)) {
                    recalled = true;
                    rank = r + 1;
                    score = doc.getScore();
                    break;
                }
            }

            if (recalled) {
                recalledCount++;
            }
            results.add(new EvalResult(i + 1, tc, recalled, rank, score, cost, candidates));
        }

        printReport(results, recalledCount);

        double rate = (double) recalledCount / TEST_CASES.size() * 100;
        assertTrue(rate >= 80.0, "Recall rate is below 80%");
    }

    private void printReport(List<EvalResult> results, int recalledCount) {
        log.info(" ");
        log.info("==================================================");
        log.info("RAG Retrieval Audit Report");
        log.info("==================================================");

        for (EvalResult res : results) {
            TestCase tc = res.testCase();
            log.info("Case {}: [{}]", res.index(), tc.desc());
            log.info("  - Question: \"{}\"", tc.question());
            log.info("  - Expected: '{}'", tc.expectedFile());

            if (res.isRecalled()) {
                log.info("  - Result  : SUCCESS (Rank {}, Score: {}, Cost: {}ms)",
                        res.hitRank(), String.format("%.4f", res.hitScore()), res.costMs());
            } else {
                log.warn("  - Result  : FAILED (Not in Top 3, Cost: {}ms)", res.costMs());
            }

            log.info("  - Details :");
            for (int r = 0; r < res.candidates().size(); r++) {
                Document doc = res.candidates().get(r);
                String src = (String) doc.getMetadata().get("source");
                String hitTag = tc.expectedFile().equalsIgnoreCase(src) ? " [HIT]" : "";
                String snippet = doc.getText().substring(0, Math.min(50, doc.getText().length()))
                                         .replace("\n", " ") + "...";

                log.info("    [{}] Score: {}, Source: '{}'{} | Snippet: {}",
                        r + 1,
                        String.format("%.4f", doc.getScore() != null ? doc.getScore() : 0.0),
                        src,
                        hitTag,
                        snippet
                );
            }
            log.info("--------------------------------------------------");
        }

        double rate = (double) recalledCount / results.size() * 100;
        log.info("==================================================");
        log.info("RAG Metrics Summary:");
        log.info("  - Total Cases: {}", results.size());
        log.info("  - Recalled   : {}", recalledCount);
        log.info("  - Recall Rate: {}%", String.format("%.2f", rate));
        log.info("==================================================");
        log.info(" ");
    }
}
