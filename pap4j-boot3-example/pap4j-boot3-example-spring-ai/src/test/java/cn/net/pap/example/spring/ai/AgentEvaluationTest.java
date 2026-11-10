package cn.net.pap.example.spring.ai;

import cn.net.pap.example.spring.ai.config.AiProperties;
import cn.net.pap.example.spring.ai.tool.AgentTool;
import cn.net.pap.example.spring.ai.tool.FileSearchTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * <h1>AGENT_EVAL 20 题响应记录与依据文档匹配</h1>
 * <p>
 * 题目在 {@link #QUESTIONS} 中
 * 预置「题面 + 期望依赖的 md 文档」，测试按顺序把题面喂给生产环境同一个 {@code agentChatClient}
 * （带 FileSearchTool 工具链），然后：
 * </p>
 * <ol>
 *   <li>从 {@link FileSearchTool.EvidenceLedger} 取本次回答通过原文硬校验的文档（去重）；</li>
 *   <li>与预置的期望文档集合做<b>覆盖校验</b>：只要预置的 md 全部被实际引用命中即算成功
 *       （允许多引、不要求数量一致；漏引则判失败）；</li>
 *   <li>逐题打印题面、预期文档、实际文档、匹配结果与回答全文，最后打印命中汇总。</li>
 * </ol>
 * <p>
 * 说明：
 * </p>
 * <ol>
 *   <li>每题独立 chatId，避免记忆串味；仅第 20 题复用第 2 题的 chatId（题集要求的多轮指代）。</li>
 *   <li>temperature 取 {@code ai.main-llm.temperature}；若要对比跑批，请先按题集铁律把它压到 0.1。</li>
 *   <li>端点不可达时自动跳过，不阻塞构建；只跑指定题号可用 {@code -Dagent.eval.only=16}。</li>
 * </ol>
 * <pre>
 * mvn -Pagent -pl pap4j-boot3-example/pap4j-boot3-example-spring-ai test -Dtest=AgentEvaluationTest
 * </pre>
 */
@SpringBootTest
public class AgentEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluationTest.class);

    /**
     * 单题超时秒数
     */
    private static final int TIMEOUT_SECONDS = 300;

    /**
     * 端点探活超时毫秒
     */
    private static final int PROBE_TIMEOUT_MS = 1500;

    /**
     * 可选：只跑指定题号（逗号分隔），如 -Dagent.eval.only=16
     */
    private static final String PROP_ONLY = "agent.eval.only";

    /**
     * Map 中题面的 key
     */
    private static final String KEY_QUESTION = "question";

    /**
     * Map 中期望依赖文档的 key
     */
    private static final String KEY_DOCS = "docs";

    /**
     * 20 道题：每项含 {@code question}（题面）与 {@code docs}（期望依赖的 md 文档）。
     * <p>题面与 AGENT_EVAL.md 主表一致；docs 取主表「依据文档」列，#10 / #11 为知识库外的题，期望为空。</p>
     */
    private static final List<Map<String, Object>> QUESTIONS = List.of(
            Map.of(KEY_QUESTION, "ISBLANK 会不会把纯空格字符串判为空？", KEY_DOCS, List.of("function_isblank.md")),
            Map.of(KEY_QUESTION, "TREE_FLATTEN 省略 sep 时默认分隔符？", KEY_DOCS, List.of("function_tree_flatten.md")),
            Map.of(KEY_QUESTION, "LIST_JOIN 传空列表返回什么？", KEY_DOCS, List.of("function_list_join.md")),
            Map.of(KEY_QUESTION, "DIV2(1, 3) 结果是多少？", KEY_DOCS, List.of("function_div2.md")),
            Map.of(KEY_QUESTION, "TREE_FLATTEN 与 TREE_LEAF_FLATTEN 区别？", KEY_DOCS, List.of("function_tree_flatten.md", "function_tree_leaf_flatten.md")),
            Map.of(KEY_QUESTION, "TRIM 与 ISBLANK 在空值上如何分工？", KEY_DOCS, List.of("function_trim.md", "function_isblank.md")),
            Map.of(KEY_QUESTION, "LIST_SIZE 与 LIST_JOIN 在 null / 空集合下行为差异？", KEY_DOCS, List.of("function_list_size.md", "function_list_join.md")),
            Map.of(KEY_QUESTION, "DIV2 分子或分母为 0 时返回什么？", KEY_DOCS, List.of("function_div2.md")),
            Map.of(KEY_QUESTION, "ExpressRunner.execute 第 4、5 个布尔参数含义？", KEY_DOCS, List.of("devutil_trace_execution.md")),
            Map.of(KEY_QUESTION, "怎么配置 Redis 集群主从复制？", KEY_DOCS, List.of()),
            Map.of(KEY_QUESTION, "用 QLExpress 做订单计算，MySQL 分库分表怎么设计？", KEY_DOCS, List.of()),
            Map.of(KEY_QUESTION, "$.id + DIV2(1,2) 为何报错？怎么写对？", KEY_DOCS, List.of("engine_routing_strategy.md", "function_json_path.md", "function_div2.md")),
            Map.of(KEY_QUESTION, "脚本 return json 字段有什么坑？", KEY_DOCS, List.of("context_variables.md", "syntax_map_construction.md")),
            Map.of(KEY_QUESTION, "如何从外部注入「最小分数阈值」？为何不能用脚本内变量？", KEY_DOCS, List.of("syntax_interpolation.md")),
            Map.of(KEY_QUESTION, "树形分类路径脱敏（保前 3 位）再统一转大写，怎么组合？", KEY_DOCS, List.of("function_tree_flatten.md", "function_substring.md", "function_upper.md")),
            Map.of(KEY_QUESTION, "手机号脱敏怎么写？13812345678 处理后是什么？", KEY_DOCS, List.of("function_substring.md")),
            Map.of(KEY_QUESTION, "调用 UPPER 前必须先做什么防护？为什么？", KEY_DOCS, List.of("function_upper.md", "function_isblank.md")),
            Map.of(KEY_QUESTION, "语法校验方法名是 check() 还是 checkInstruction()？", KEY_DOCS, List.of("devutil_syntax_check.md")),
            Map.of(KEY_QUESTION, "traceExpression 开启后具体怎么打印计算链路？", KEY_DOCS, List.of("devutil_trace_execution.md")),
            Map.of(KEY_QUESTION, "那它支持自定义分隔符吗？", KEY_DOCS, List.of("function_tree_flatten.md"))
    );

    private final ChatClient agentChatClient;
    private final AiProperties aiProperties;

    /**
     * 构造测试，注入生产环境同一个 Agent 对话客户端。
     *
     * @param agentChatClient 生产 Agent 客户端（已挂载 FileSearchTool 工具集）
     * @param aiProperties    AI 配置（用于端点探活）
     */
    @Autowired
    public AgentEvaluationTest(@Qualifier("agentChatClient") ChatClient agentChatClient, AiProperties aiProperties) {
        this.agentChatClient = agentChatClient;
        this.aiProperties = aiProperties;
    }

    /**
     * 逐题调用 Agent，比对依据文档是否与预置完全一致，并打印回答。
     */
    @Test
    @DisplayName("AGENT_EVAL 20 题：依据文档覆盖校验 + 回答记录")
    public void recordAgentAnswers() {
        Assumptions.assumeTrue(isEndpointReachable(aiProperties.mainLlm()), "大模型端点不可达或未配置有效 api-key，跳过 AGENT_EVAL 记录。");

        List<Integer> only = parseOnly();
        List<String> summary = new ArrayList<>(QUESTIONS.size());
        int matched = 0;
        int executed = 0;
        for (int i = 0; i < QUESTIONS.size(); i++) {
            int no = i + 1;
            if (!only.isEmpty() && !only.contains(no)) {
                continue;
            }
            Map<String, Object> item = QUESTIONS.get(i);
            String question = String.valueOf(item.get(KEY_QUESTION));
            Set<String> expectedDocs = toDocSet(item.get(KEY_DOCS));
            // 第 20 题复用第 2 题的 chatId，用于检验多轮指代消解
            String chatId = (no == 20) ? "agent-eval-q2" : "agent-eval-q" + no;
            FileSearchTool.EvidenceLedger ledger = new FileSearchTool.EvidenceLedger();
            StringBuilder answer = new StringBuilder();
            long start = System.currentTimeMillis();
            try {
                agentChatClient.prompt().user(question).advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId)).stream().content().doOnNext(answer::append).contextWrite(context -> context.put(AgentTool.EVIDENCE_LEDGER_KEY, ledger)).blockLast(Duration.ofSeconds(TIMEOUT_SECONDS));
            } catch (Exception e) {
                log.error("[AgentEval] #{} 调用失败", no, e);
            }
            long costMs = System.currentTimeMillis() - start;
            executed++;

            Set<String> actualDocs = new TreeSet<>(ledger.getVerifiedCitations().stream().map(FileSearchTool.VerifiedCitation::relativePath).toList());
            // 覆盖校验：预置文档全部被实际引用命中即算成功，多引不扣分
            boolean docMatched = actualDocs.containsAll(expectedDocs);
            if (docMatched) {
                matched++;
            }
            if (answer.length() == 0) {
                log.warn("[AgentEval] #{} 空回答（模型未返回正文，多为端点偶发空响应，建议重跑该题）", no);
            }

            log.info("[AgentEval] #{} 问题     : {}", no, question);
            log.info("[AgentEval] #{} 预期文档 : {}", no, describe(expectedDocs));
            log.info("[AgentEval] #{} 实际文档 : {}", no, describe(actualDocs));
            log.info("[AgentEval] #{} 匹配结果 : {} {}", no, docMatched ? "✅ 已覆盖预设文档" : "❌ 未覆盖预设文档", diff(expectedDocs, actualDocs));
            log.info("[AgentEval] #{} 耗时     : {} ms / 回答 {} 字符", no, costMs, answer.length());
            log.info("[AgentEval] #{} 回答     :\n{}", no, answer);

            summary.add("#" + no + " | " + (docMatched ? "✅" : "❌") + " | 预期[" + describe(expectedDocs) + "] | 实际[" + describe(actualDocs) + "] | " + question);
        }

        log.info("================ AGENT_EVAL 依据文档匹配汇总（覆盖预设 {}/{}） ================", matched, executed);
        summary.forEach(line -> log.info("[AgentEval][汇总] {}", line));
        log.info("=========================================================================");
    }

    /**
     * 解析 {@code -Dagent.eval.only} 指定的题号，未指定则返回空列表表示全跑。
     *
     * @return 题号列表
     */
    private List<Integer> parseOnly() {
        String only = System.getProperty(PROP_ONLY, "").trim();
        if (only.isEmpty()) {
            return List.of();
        }
        List<Integer> nos = new ArrayList<>();
        for (String part : only.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                nos.add(Integer.parseInt(trimmed));
            }
        }
        log.info("[AgentEval] 限定题号: {}", nos);
        return nos;
    }

    /**
     * 把 Map 中的期望文档值转换为去重集合（空列表表示期望“无核验引用”）。
     *
     * @param docs 期望文档值，类型应为 {@code List<String>}
     * @return 文档名有序集合
     */
    private Set<String> toDocSet(Object docs) {
        Set<String> docSet = new TreeSet<>();
        if (docs instanceof List<?> list) {
            for (Object doc : list) {
                docSet.add(String.valueOf(doc));
            }
        }
        return docSet;
    }

    /**
     * 集合转打印文本。
     *
     * @param docs 文档集合
     * @return 空集合返回 {@code (无)}，否则逗号分隔
     */
    private String describe(Set<String> docs) {
        return docs.isEmpty() ? "(无)" : String.join(", ", docs);
    }

    /**
     * 生成差异描述，便于直接看出漏引（多引仅提示，不影响判定）。
     *
     * @param expected 期望文档
     * @param actual   实际文档
     * @return 差异文本，预置已全覆盖时只提示多引或返回空串
     */
    private String diff(Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        List<String> parts = new ArrayList<>(2);
        if (!missing.isEmpty()) {
            parts.add("漏引[" + String.join(", ", missing) + "]");
        }
        if (!extra.isEmpty()) {
            parts.add("（多引[" + String.join(", ", extra) + "]，不影响判定）");
        }
        return String.join(" ", parts);
    }

    /**
     * 轻量 TCP 探活，端点不可达时优雅跳过而不是挂起或报错。
     *
     * @param config 主模型配置
     * @return true 表示端点可达
     */
    private boolean isEndpointReachable(AiProperties.ModelConfig config) {
        if (config == null || config.baseUrl() == null || config.baseUrl().isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(config.baseUrl());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            if ("10.0.0.0".equals(host) || "0.0.0.0".equals(host) || host.contains("dummy") || host.contains("example.com")) {
                log.warn("大模型配置为占位符地址 ({})，跳过连通性测试。", config.baseUrl());
                return false;
            }
            int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            }
            log.info("[AgentEval] 大模型端点探活成功: {}:{}", host, port);
            return true;
        } catch (Exception e) {
            log.warn("[AgentEval] 大模型端点探活失败 ({})，跳过记录。", config.baseUrl(), e);
            return false;
        }
    }
}
