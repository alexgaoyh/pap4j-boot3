package cn.net.pap.common.datastructure.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><strong>DataTreeIdUtil</strong> 提供了用于生成和管理分层树状追踪 ID 的功能。</p>
 *
 * <p>生成的追踪 ID 包含从根节点一直到当前节点的完整、严格且有序的路径前缀序列。</p>
 *
 * <ul>
 *     <li>利用 Base62 编码实现紧凑的表示形式。</li>
 *     <li>支持确定性的树形派生和多分支追踪。</li>
 *     <li>支持将路径元数据向后追溯至根节点。</li>
 * </ul>
 */
public class DataTreeIdUtil {

    // ================== 协议常量 ==================

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * <p>分配给层级字段的固定字符串长度。</p>
     */
    private static final int LEVEL_LEN = 2;

    /**
     * <p>分配给分支字段的固定字符串长度。</p>
     */
    private static final int BRANCH_LEN = 3;

    /**
     * <p>单个树节点的总固定字符串长度。</p>
     */
    private static final int NODE_LEN = LEVEL_LEN + BRANCH_LEN;

    // ================== 对外 API ==================

    /**
     * <p>生成根节点追踪 ID。</p>
     *
     * @return 表示根节点的生成追踪 ID。
     */
    public static String generateRoot() {
        return encodeNode(1, 1);
    }

    /**
     * <p>从当前追踪 ID 派生出单步子节点（始终分配 branch = 1）。</p>
     *
     * @param currentTraceId 父级追踪 ID 字符串。
     * @return 派生的子级追踪 ID 字符串。
     */
    public static String nextStep(String currentTraceId) {
        TraceMeta meta = decode(currentTraceId);
        String nextNode = encodeNode(meta.level + 1, 1);
        return currentTraceId + nextNode;
    }

    /**
     * <p>从当前父级追踪 ID 派生多个子分支节点。</p>
     *
     * @param parentTraceId 父级追踪 ID 字符串。
     * @param numBranches   要生成的子分支数量。
     * @return 生成的子级追踪 ID 的 {@link List}。
     * @throws IllegalArgumentException 如果 <strong>numBranches</strong> 小于或等于 0。
     */
    public static List<String> deriveBranches(String parentTraceId, int numBranches) {
        if (numBranches <= 0) {
            throw new IllegalArgumentException("numBranches must > 0");
        }

        TraceMeta meta = decode(parentTraceId);
        int nextLevel = meta.level + 1;

        List<String> result = new ArrayList<>(numBranches);
        for (int i = 1; i <= numBranches; i++) {
            result.add(parentTraceId + encodeNode(nextLevel, i));
        }
        return result;
    }

    /**
     * <p>解析完整的追踪 ID 序列并提取最终节点的元数据。</p>
     *
     * @param traceId 要解析的追踪 ID。
     * @return 包含层级和分支信息的 {@link TraceMeta} 对象。
     */
    public static TraceMeta parse(String traceId) {
        return decode(traceId);
    }

    /**
     * <p>重构从根节点到当前节点的层级路径。</p>
     *
     * @param traceId 最终追踪 ID。
     * @return 表示从根到给定节点的有序追踪 ID 路径的 {@link List}。
     */
    public static List<String> traceToRoot(String traceId) {
        List<String> path = new ArrayList<>();
        String current = traceId;

        while (current != null && !current.isEmpty()) {
            path.add(0, current);
            current = parentTraceId(current);
        }
        return path;
    }

    /**
     * <p>获取定义的单一节点段统一字符串长度。</p>
     *
     * @return 段长度。
     */
    public static int getNodeLength() {
        return NODE_LEN;
    }

    // ================== 编码 / 解码 ==================

    /**
     * <p>将单一节点的层级和分支编码为固定长度字符串。</p>
     *
     * @param level  节点层级。
     * @param branch 分支编号。
     * @return 拼接的节点字符串。
     */
    private static String encodeNode(int level, int branch) {
        return toFixedBase62(level, LEVEL_LEN) + toFixedBase62(branch, BRANCH_LEN);
    }

    /**
     * <p>解码提供的追踪 ID 的尾部节点部分。</p>
     *
     * @param traceId 完整的追踪 ID 字符串。
     * @return 提取出的该节点的 {@link TraceMeta}。
     * @throws IllegalArgumentException 如果追踪 ID 长度格式不正确。
     */
    private static TraceMeta decode(String traceId) {
        if (traceId == null || traceId.length() < NODE_LEN || traceId.length() % NODE_LEN != 0) {
            throw new IllegalArgumentException("Invalid traceId length: " + traceId);
        }

        int len = traceId.length();
        String nodePart = traceId.substring(len - NODE_LEN);
        String parentTraceId = (len == NODE_LEN) ? null : traceId.substring(0, len - NODE_LEN);

        int level = (int) fromBase62(nodePart.substring(0, LEVEL_LEN));
        int branch = (int) fromBase62(nodePart.substring(LEVEL_LEN));

        return new TraceMeta(level, branch, parentTraceId);
    }

    /**
     * <p>无需解析直接从追踪 ID 中提取父段部分。</p>
     *
     * @param traceId 子追踪 ID 字符串。
     * @return 父追踪 ID 字符串，如果是根节点则为 null。
     */
    private static String parentTraceId(String traceId) {
        if (traceId.length() <= NODE_LEN) {
            return null;
        }
        return traceId.substring(0, traceId.length() - NODE_LEN);
    }

    // ================== Base62 编解码 ==================

    private static String toBase62(long value) {
        StringBuilder sb = new StringBuilder();
        do {
            int idx = (int) (value % 62);
            sb.insert(0, BASE62.charAt(idx));
            value /= 62;
        } while (value > 0);
        return sb.toString();
    }

    private static String toFixedBase62(long value, int length) {
        String raw = toBase62(value);
        if (raw.length() > length) {
            throw new IllegalArgumentException("Base62 overflow, value=" + value + ", maxLength=" + length);
        }
        return "0".repeat(length - raw.length()) + raw;
    }

    private static long fromBase62(String str) {
        long value = 0;
        for (char c : str.toCharArray()) {
            int idx = BASE62.indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid Base62 char: " + c);
            }
            value = value * 62 + idx;
        }
        return value;
    }

    // ================== TraceMeta ==================

    /**
     * <p><strong>TraceMeta</strong> 用作包装从节点追踪块提取的参数的结构体。</p>
     */
    public static class TraceMeta {
        /**
         * <p>节点的树深度层级。</p>
         */
        public final int level;
        
        /**
         * <p>当前深度下的分支序列指示器。</p>
         */
        public final int branch;
        
        /**
         * <p>表示父级路径追踪的字符串前缀。</p>
         */
        public final String parentTraceId;

        /**
         * <p>创建一个包含节点评估数据的新实例。</p>
         *
         * @param level         节点深度层级。
         * @param branch        分支标识符。
         * @param parentTraceId 父级追踪段字符串。
         */
        public TraceMeta(int level, int branch, String parentTraceId) {
            this.level = level;
            this.branch = branch;
            this.parentTraceId = parentTraceId;
        }

        @Override
        public String toString() {
            return "TraceMeta{" + "level=" + level + ", branch=" + branch + ", parentTraceId='" + parentTraceId + '\'' + '}';
        }
    }

}
