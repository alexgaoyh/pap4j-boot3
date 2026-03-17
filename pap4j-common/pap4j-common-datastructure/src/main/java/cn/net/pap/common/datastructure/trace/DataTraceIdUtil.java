package cn.net.pap.common.datastructure.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p><strong>DataTraceIdUtil</strong> 用于管理用于数据血缘追踪的结构化追踪 ID。</p>
 *
 * <p>它使用 Base62 编码生成包含严格父节点路径的自定义追踪 ID，支持广泛的分支流并以紧凑的方式保留关系链。
 * 每个 ID 直接嵌入关键元数据，无需依赖二级存储。</p>
 *
 * <ul>
 *     <li>自包含父节点历史记录。</li>
 *     <li>Base62 时间/层级/分支编码。</li>
 *     <li>内置确定性完整性校验码验证。</li>
 * </ul>
 */
public class DataTraceIdUtil {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int BIZ_LEN = 4;        // 业务域长度

    private static final int SOURCE_LEN = 4;     // 数据源长度

    private static final int TIME_LEN = 6;       // Base62 时间戳长度（秒级）

    private static final int LEVEL_LEN = 3;      // 层级长度

    private static final int BRANCH_LEN = 3;     // 分支号长度

    private static final int PARENT_LEN = LEVEL_LEN + BRANCH_LEN; // 父节点 path 长度

    private static final int CHECKSUM_LEN = 2;   // 校验码长度

    /**
     * <p>生成基础的根追踪 ID。</p>
     * 
     * <p>原始根节点的父节点默认层级为 0，分支为 0。</p>
     *
     * @param biz    业务域标识符。
     * @param source 特定的数据源标识符。
     * @return 格式化的根追踪 ID 字符串。
     * @throws NullPointerException 如果 biz 或 source 为 null。
     */
    public static String generateRoot(String biz, String source) {
        Objects.requireNonNull(biz, "biz cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        int level = 1;
        int branch = 1;
        long time = System.currentTimeMillis() / 1000; // 秒级时间戳
        int parentLevel = 0;
        int parentBranch = 0;
        return encode(biz, source, time, level, branch, parentLevel, parentBranch);
    }

    /**
     * <p>从父追踪 ID 将血缘树扩展为多个分支。</p>
     *
     * @param parentTraceId 源父追踪 ID 字符串。
     * @param numBranches   要派生的并行分支总数。
     * @return 包含唯一派生的子级追踪 ID 序列的 {@link List}。
     * @throws IllegalArgumentException 如果 numBranches 小于或等于 0。
     * @throws NullPointerException     如果 parentTraceId 为 null。
     */
    public static List<String> deriveBranches(String parentTraceId, int numBranches) {
        Objects.requireNonNull(parentTraceId, "parentTraceId cannot be null");
        if (numBranches <= 0) throw new IllegalArgumentException("numBranches must > 0");

        TraceMeta parentMeta = decode(parentTraceId);
        int nextLevel = parentMeta.level + 1;

        List<String> branches = new ArrayList<>(numBranches);
        for (int i = 1; i <= numBranches; i++) {
            branches.add(encode(parentMeta.biz, parentMeta.source, parentMeta.time, nextLevel, i, parentMeta.level, parentMeta.branch));
        }
        return branches;
    }

    /**
     * <p>将血缘追踪向前推进一步，并保持单一分支。</p>
     *
     * @param currentTraceId 当前的追踪 ID 字符串。
     * @return 新的后续序列追踪 ID 字符串。
     */
    public static String nextStep(String currentTraceId) {
        TraceMeta meta = decode(currentTraceId);
        int nextLevel = meta.level + 1;
        int nextBranch = 1; // 单步新增 branch = 1
        return encode(meta.biz, meta.source, meta.time, nextLevel, nextBranch, meta.level, meta.branch);
    }

    /**
     * <p>将活跃的已编码追踪 ID 字符串解析为其内部元数据属性。</p>
     *
     * @param traceId 已编码的追踪 ID 字符串。
     * @return 表示底层层级定义的 {@link TraceMeta} 映射。
     */
    public static TraceMeta parse(String traceId) {
        return decode(traceId);
    }

    /**
     * <p>将离散参数编码为复合固定长度的 Base62 字符串表示形式。</p>
     */
    private static String encode(String biz, String source, long time, int level, int branch, int parentLevel, int parentBranch) {
        String timeStr = toBase62(time, TIME_LEN);
        String levelStr = toBase62(level, LEVEL_LEN);
        String branchStr = toBase62(branch, BRANCH_LEN);
        String parentStr = toBase62(parentLevel, LEVEL_LEN) + toBase62(parentBranch, BRANCH_LEN);
        String raw = biz + source + timeStr + levelStr + branchStr + parentStr;
        String checksum = calculateChecksum(raw);
        return raw + checksum;
    }

    /**
     * <p>从已编码的追踪字符串中重构内部参数并执行校验码验证。</p>
     */
    private static TraceMeta decode(String traceId) {
        int expectedLength = BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN + BRANCH_LEN + PARENT_LEN + CHECKSUM_LEN;
        if (traceId.length() != expectedLength) {
            throw new IllegalArgumentException("Invalid traceId length: " + traceId);
        }

        String biz = traceId.substring(0, BIZ_LEN);
        String source = traceId.substring(BIZ_LEN, BIZ_LEN + SOURCE_LEN);
        String timeStr = traceId.substring(BIZ_LEN + SOURCE_LEN, BIZ_LEN + SOURCE_LEN + TIME_LEN);
        String levelStr = traceId.substring(BIZ_LEN + SOURCE_LEN + TIME_LEN, BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN);
        String branchStr = traceId.substring(BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN, BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN + BRANCH_LEN);
        String parentLevelStr = traceId.substring(BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN + BRANCH_LEN, BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN + BRANCH_LEN + LEVEL_LEN);
        String parentBranchStr = traceId.substring(BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN + BRANCH_LEN + LEVEL_LEN, BIZ_LEN + SOURCE_LEN + TIME_LEN + LEVEL_LEN + BRANCH_LEN + LEVEL_LEN + BRANCH_LEN);
        String checksum = traceId.substring(traceId.length() - CHECKSUM_LEN);

        long time = fromBase62(timeStr);
        int level = (int) fromBase62(levelStr);
        int branch = (int) fromBase62(branchStr);
        int parentLevel = (int) fromBase62(parentLevelStr);
        int parentBranch = (int) fromBase62(parentBranchStr);

        // 校验码验证
        String raw = biz + source + timeStr + levelStr + branchStr + parentLevelStr + parentBranchStr;
        String expected = calculateChecksum(raw);
        if (!checksum.equalsIgnoreCase(expected)) {
            throw new IllegalArgumentException("traceId checksum mismatch: " + traceId);
        }

        return new TraceMeta(biz, source, time, level, branch, parentLevel, parentBranch, checksum);
    }

    /**
     * <p>将变量参数标准化为按比例补零的固定 Base62 字符串。</p>
     */
    private static String toBase62(long value, int length) {
        StringBuilder sb = new StringBuilder();
        do {
            int idx = (int) (value % 62);
            sb.insert(0, BASE62.charAt(idx));
            value /= 62;
        } while (value > 0);
        while (sb.length() < length) sb.insert(0, '0');
        return sb.toString();
    }

    /**
     * <p>处理 Base62 表示并重构内部十进制数。</p>
     */
    private static long fromBase62(String str) {
        long value = 0;
        for (char c : str.toCharArray()) {
            int idx = BASE62.indexOf(c);
            if (idx < 0) throw new IllegalArgumentException("Invalid Base62 char: " + c);
            value = value * 62 + idx;
        }
        return value;
    }

    /**
     * <p>生成支持序列验证的确定性十六进制校验和签名字节。</p>
     */
    private static String calculateChecksum(String raw) {
        int hash = Math.abs(raw.hashCode());
        String hex = Integer.toHexString(hash % 256).toUpperCase();
        return hex.length() == 1 ? "0" + hex : hex;
    }

    /**
     * <p><strong>TraceMeta</strong> 充当表示层级细节的内部有效载荷。</p>
     */
    public static class TraceMeta {

        /**
         * <p>域代码，例如 "ORD1"。</p>
         */
        public final String biz;
        /**
         * <p>原始数据源配置，例如 "S001"。</p>
         */
        public final String source;
        /**
         * <p>初始根分配 UNIX 时间戳，以秒为单位。</p>
         */
        public final long time;
        /**
         * <p>从级别 1 开始的树深度大小。</p>
         */
        public final int level;
        /**
         * <p>指定子派生的序列序数分支值。</p>
         */
        public final int branch;
        /**
         * <p>从 0 开始的父级层次深度索引。</p>
         */
        public final int parentLevel;
        /**
         * <p>父级特定派生标识符分支节点约束。</p>
         */
        public final int parentBranch;
        /**
         * <p>基于哈希的验证序列。</p>
         */
        public final String checksum;

        /**
         * <p>构造一个映射基本结构的完整元信封容器表示。</p>
         */
        public TraceMeta(String biz, String source, long time, int level, int branch, int parentLevel, int parentBranch, String checksum) {
            this.biz = biz;
            this.source = source;
            this.time = time;
            this.level = level;
            this.branch = branch;
            this.parentLevel = parentLevel;
            this.parentBranch = parentBranch;
            this.checksum = checksum;
        }

        @Override
        public String toString() {
            return "TraceMeta{" + "biz='" + biz + '\'' + ", source='" + source + '\'' + ", time=" + time + ", level=" + level + ", branch=" + branch + ", parentLevel=" + parentLevel + ", parentBranch=" + parentBranch + ", checksum='" + checksum + '\'' + '}';
        }
    }

}
