package cn.net.pap.common.datastructure.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 数据血缘 TraceId 工具类（多分支派生 + Base62 + 内嵌父节点信息）
 * - 每个 traceId 包含自身信息 + 父节点 path
 * - 支持多分支派生
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
     * 生成根 traceId（根节点父节点设为 00 00）
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
     * 多分支派生（父节点 traceId -> numBranches 子节点 traceId）
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
     * 在当前节点新增一步（单条派生，branch=1）
     */
    public static String nextStep(String currentTraceId) {
        TraceMeta meta = decode(currentTraceId);
        int nextLevel = meta.level + 1;
        int nextBranch = 1; // 单步新增 branch = 1
        return encode(meta.biz, meta.source, meta.time, nextLevel, nextBranch, meta.level, meta.branch);
    }

    /**
     * 解析 traceId
     */
    public static TraceMeta parse(String traceId) {
        return decode(traceId);
    }

    /**
     * 编码 traceId
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
     * 解码 traceId
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
     * Base62 编码固定长度
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
     * Base62 解码
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
     * 计算 2 位校验码
     */
    private static String calculateChecksum(String raw) {
        int hash = Math.abs(raw.hashCode());
        String hex = Integer.toHexString(hash % 256).toUpperCase();
        return hex.length() == 1 ? "0" + hex : hex;
    }

    /**
     * TraceId 元信息
     */
    public static class TraceMeta {

        /**
         * 业务域编码，例如 "ORD1"
         */
        public final String biz;
        /**
         * 数据源编码，例如 "S001"
         */
        public final String source;
        /**
         * 根 traceId 创建的 Unix 时间戳（秒），Base62 编码在 traceId 中
         */
        public final long time;
        /**
         * 当前 traceId 所在层级（level），根节点为 1，派生后依次递增
         */
        public final int level;
        /**
         * 当前 traceId 在该层级的分支号（branch），从 1 开始，多分支派生用于区分兄弟节点
         */
        public final int branch;
        /**
         * 父节点所在层级（parentLevel），用于追溯父节点，根节点为 0
         */
        public final int parentLevel;
        /**
         * 父节点在该层级的分支号（parentBranch），根节点为 0
         */
        public final int parentBranch;
        /**
         * 校验码（checksum），用于验证 traceId 的完整性和防篡改
         */
        public final String checksum;

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
