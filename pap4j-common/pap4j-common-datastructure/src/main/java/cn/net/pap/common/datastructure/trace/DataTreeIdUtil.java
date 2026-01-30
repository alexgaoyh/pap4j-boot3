package cn.net.pap.common.datastructure.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * 从根节点开始 → 一直到当前节点的“完整路径集合”, 而且是严格有序的路径前缀序列。
 */
public class DataTreeIdUtil {

    // ================== 协议常量 ==================

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * level 字段长度
     */
    private static final int LEVEL_LEN = 2;

    /**
     * branch 字段长度
     */
    private static final int BRANCH_LEN = 3;

    /**
     * 单个节点总长度
     */
    private static final int NODE_LEN = LEVEL_LEN + BRANCH_LEN;

    // ================== 对外 API ==================

    /**
     * 生成根节点 traceId
     */
    public static String generateRoot() {
        return encodeNode(1, 1);
    }

    /**
     * 单步派生（branch = 1）
     */
    public static String nextStep(String currentTraceId) {
        TraceMeta meta = decode(currentTraceId);
        String nextNode = encodeNode(meta.level + 1, 1);
        return currentTraceId + nextNode;
    }

    /**
     * 多分支派生
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
     * 解析 traceId，返回最后一个节点的元信息
     */
    public static TraceMeta parse(String traceId) {
        return decode(traceId);
    }

    /**
     * 从任意节点 traceId 向上追溯到根节点
     *
     * @return 从根 → 当前节点的 traceId 列表
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

    public static int getNodeLength() {
        return NODE_LEN;
    }

    // ================== 编码 / 解码 ==================

    /**
     * 编码单个节点（定长）
     */
    private static String encodeNode(int level, int branch) {
        return toFixedBase62(level, LEVEL_LEN) + toFixedBase62(branch, BRANCH_LEN);
    }

    /**
     * 解析 traceId 的最后一个节点
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
     * 获取父节点 traceId（不解析内容）
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

    public static class TraceMeta {
        public final int level;
        public final int branch;
        public final String parentTraceId;

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
