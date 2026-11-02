package cn.net.pap.example.spring.ai;

import cn.net.pap.example.spring.ai.tool.FileSearchTool;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>RAG 向量检索 vs 智能体文件检索横向对比单元测试</h1>
 * <p>
 * 本测试类基于相同的 9 个标准知识库问题，对以下两种检索范式进行纯量化横向对比：
 * </p>
 * <ol>
 *   <li><b>传统向量检索 (Vector RAG)</b>：基于 {@link VectorStore} 余弦相似度搜索 (TopK=3)，依赖 Embedding 模型。</li>
 *   <li><b>智能体工具检索 (Agentic RAG)</b>：基于 {@link FileSearchTool} 进程内关键词搜索，零模型开销与即改即查。</li>
 * </ol>
 */
@SpringBootTest
public class RagVsAgentRetrievalTest {

    private static final Logger log = LoggerFactory.getLogger(RagVsAgentRetrievalTest.class);

    private final VectorStore vectorStore;
    private final FileSearchTool fileSearchTool;

    /**
     * 构造横向对比单元测试类，注入向量存储与文件检索智能体工具。
     *
     * @param vectorStore    知识库向量存储
     * @param fileSearchTool 本地知识库文件检索工具
     */
    @Autowired
    public RagVsAgentRetrievalTest(@Qualifier("knowledgeVectorStore") VectorStore vectorStore,
                                   FileSearchTool fileSearchTool) {
        this.vectorStore = vectorStore;
        this.fileSearchTool = fileSearchTool;
    }

    /**
     * 测试用例数据定义。
     *
     * @param question     用户提问
     * @param expectedFile 期望召回的目标文档文件名
     * @param desc         用例业务说明
     * @param agentKeyword 智能体提炼的核心实体/专有名词关键词（模拟两阶段检索中，前置通过改写提炼模型 queryRewriteChatClient
     *                     或向量语义分析将用户白话提纯扩充后的专业检索词，而非直接使用用户原始口语长句）
     */
    private record TestCase(String question, String expectedFile, String desc, String agentKeyword) {
    }

    /**
     * 单个用例的横向评测结果。
     *
     * @param index        用例序号
     * @param testCase     用例定义
     * @param vectorHit    向量检索是否命中 Top 3
     * @param vectorRank   向量检索命中名次（若未命中为 -1）
     * @param vectorScore  向量相似度得分
     * @param vectorCostMs 向量检索耗时（毫秒）
     * @param agentHit     Agent 工具检索是否命中
     * @param agentCostMs  Agent 检索耗时（毫秒）
     */
    private record ComparisonResult(int index,
                                    TestCase testCase,
                                    boolean vectorHit,
                                    int vectorRank,
                                    Double vectorScore,
                                    long vectorCostMs,
                                    boolean agentHit,
                                    long agentCostMs
    ) {
    }

    private static final List<TestCase> TEST_CASES = List.of(
            new TestCase("如何进行高精度除法计算以计算点击率或费率？", "function_div2.md", "高精度除法 (DIV2)", "除法"),
            new TestCase("系统根据什么规则把表达式分发路由给 JsonPath 或 QLExpress 引擎？", "engine_routing_strategy.md", "引擎路由策略", "路由"),
            new TestCase("怎样判断一个变量是空值或者仅仅由空格组成？", "function_isblank.md", "空值校验 (ISBLANK)", "空值"),
            new TestCase("在 QLExpress 脚本中如何去提取或者解析复杂的 JSON 节点数据？", "function_json_path.md", "JSONPath 数据提取", "JSON_PATH"),
            new TestCase("有没有可以将扁平的树状结构数据转换成平铺列表的函数？", "function_tree_flatten.md", "树结构扁平化 (TREE_FLATTEN)", "树"),
            new TestCase("我想去除文本前后的所有空白和换行字符，有专门的函数吗？", "function_trim.md", "去除首尾空格 (TRIM)", "首尾空白"),
            new TestCase("在表达式里，如何将一个 list 里面的所有元素用指定的连接符拼接成一个字符串？", "function_list_join.md", "列表元素拼接 (LIST_JOIN)", "拼接"),
            new TestCase("QLExpress 中怎么做三元逻辑判断？有对应的函数或者语法吗？", "function_ternary.md", "三元运算符 (TERNARY)", "三元"),
            new TestCase("QLExpress 引擎能否处理树形 JSON 数据", "function_tree_flatten.md", "树形JSON处理 (TREE_FLATTEN)", "树形")
    );

    /**
     * 执行 RAG 向量检索与 Agent 文件检索的双路对照基准测试。
     */
    @Test
    @DisplayName("Compare Vector RAG vs Agent FileSearch Retrieval Quality")
    public void testCompareRetrievalQuality() {
        List<ComparisonResult> results = new ArrayList<>();
        int vectorRecalledCount = 0;
        int agentRecalledCount = 0;

        for (int i = 0; i < TEST_CASES.size(); i++) {
            TestCase tc = TEST_CASES.get(i);

            // 1. 向量数据库检索 (TopK = 3)
            long vStart = System.currentTimeMillis();
            List<Document> candidates = vectorStore.similaritySearch(
                    SearchRequest.builder().query(tc.question()).topK(3).build()
            );
            long vCost = System.currentTimeMillis() - vStart;

            boolean vHit = false;
            int vRank = -1;
            Double vScore = null;
            for (int r = 0; r < candidates.size(); r++) {
                Document doc = candidates.get(r);
                String source = (String) doc.getMetadata().get("source");
                if (tc.expectedFile().equalsIgnoreCase(source)) {
                    vHit = true;
                    vRank = r + 1;
                    vScore = doc.getScore();
                    break;
                }
            }
            if (vHit) {
                vectorRecalledCount++;
            }

            // 2. 智能体工具检索 (按核心专有名词精确 Grep)
            // 【两阶段检索设计说明】：此处不能直接将用户的口语长句（tc.question）直接丢入 grepContent 进行精确匹配，因为技术文档记录的是严谨的语法签名与规范，不可能包含一模一样的口语整句。
            // 这里的 tc.agentKeyword() 模拟了“前置语义提炼与提示词扩充”后的理想输出：生产环境下通常由轻量改写模型（如 queryRewriteChatClient 提炼 bm25Keywords）、
            // 或 Agent 思考阶段（Thinking SOP）自主提纯、或由向量库粗筛圈定大致范围后提取得到，随后交由 FileSearchTool 执行精准的行级匹配与按需视窗读取。
            long aStart = System.currentTimeMillis();
            String grepOutput = fileSearchTool.grepContent(tc.agentKeyword());
            long aCost = System.currentTimeMillis() - aStart;

            boolean aHit = grepOutput != null && grepOutput.contains(tc.expectedFile());
            if (aHit) {
                agentRecalledCount++;
            }

            results.add(new ComparisonResult(i + 1, tc, vHit, vRank, vScore, vCost, aHit, aCost));
        }

        printComparisonReport(results, vectorRecalledCount, agentRecalledCount);

        // 断言验证：Agent 检索对技术实体应当具备 100% 确定性召回，攻克向量语义漂移
        assertEquals(TEST_CASES.size(), agentRecalledCount, "Agent 检索召回率未达到 100%");
        assertTrue(agentRecalledCount >= vectorRecalledCount, "Agent 检索召回率应优于或等于纯向量检索");
    }

    /**
     * 打印双路对照审计报告。
     *
     * @param results 对比评测详情列表
     * @param vCount  向量检索召回命中数
     * @param aCount  Agent 检索召回命中数
     */
    private void printComparisonReport(List<ComparisonResult> results, int vCount, int aCount) {
        log.info(" ");
        log.info("==========================================================================================");
        log.info("                     RAG vs AGENT RETRIEVAL BENCHMARK AUDIT REPORT                        ");
        log.info("==========================================================================================");
        log.info(String.format("%-4s | %-24s | %-26s | %-18s | %s",
                "Case", "Expected File", "Vector RAG (Top 3)", "Agent Tool (Grep)", "Winner"));
        log.info("------------------------------------------------------------------------------------------");

        for (ComparisonResult res : results) {
            String vDesc = res.vectorHit()
                    ? String.format("HIT (Rank %d, %.4f, %dms)", res.vectorRank(), res.vectorScore(), res.vectorCostMs())
                    : String.format("MISS (Drifted, %dms)", res.vectorCostMs());

            String aDesc = res.agentHit()
                    ? String.format("HIT (Cost %dms)", res.agentCostMs())
                    : String.format("MISS (Cost %dms)", res.agentCostMs());

            String winner;
            if (res.agentHit() && !res.vectorHit()) {
                winner = "AGENT [Antidrift] 🏆";
            } else if (!res.agentHit() && res.vectorHit()) {
                winner = "VECTOR 🏆";
            } else {
                winner = "DRAW";
            }

            log.info(String.format("%-4d | %-24s | %-26s | %-18s | %s",
                    res.index(), res.testCase().expectedFile(), vDesc, aDesc, winner));
        }

        double vRate = (double) vCount / results.size() * 100;
        double aRate = (double) aCount / results.size() * 100;

        log.info("==========================================================================================");
        log.info("BENCHMARK METRICS SUMMARY:");
        log.info("  - Total Test Cases       : {}", results.size());
        log.info("  - Vector RAG Recall Rate : {}% ({}/{})  [Requires Embedding Model & VectorDB Index]",
                String.format("%.2f", vRate), vCount, results.size());
        log.info("  - Agent Tool Recall Rate : {}% ({}/{})  [Zero Model Overhead, Zero Cold Start]",
                String.format("%.2f", aRate), aCount, results.size());
        log.info("==========================================================================================");
        log.info(" ");
    }
}
