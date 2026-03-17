package cn.net.pap.common.datastructure.myersdiff;

import java.util.ArrayList;
import java.util.List;

/**
 * <p><strong>MyersDiffUtil</strong> 实现了 Myers 差异（Diff）算法。</p>
 *
 * <p>该工具类计算将一个序列转换为另一个序列所需的最短编辑脚本（SES）。
 * 这通常被认为是 Git diff 等工具底层所使用的算法。</p>
 *
 * <ul>
 *     <li>识别 INSERT（插入）、DELETE（删除）和 KEEP（保留）操作。</li>
 *     <li>返回编辑转换的线性历史记录。</li>
 * </ul>
 */
public class MyersDiffUtil {

    /**
     * <p>表示编辑操作的类型。</p>
     */
    public enum EditType {
        /** <p>表示未做任何更改（保留）。</p> */
        KEEP, 
        /** <p>表示插入了内容。</p> */
        INSERT, 
        /** <p>表示删除了内容。</p> */
        DELETE
    }

    /**
     * <p>一个 <strong>Edit</strong> 对象持有一个编辑操作以及其所应用的文本。</p>
     */
    public static class Edit {
        public EditType type;
        public String text;

        /**
         * <p>创建一个新的编辑序列元素。</p>
         *
         * @param type 操作的类型。
         * @param text 受影响的文本。
         */
        public Edit(EditType type, String text) {
            this.type = type;
            this.text = text;
        }

        /**
         * <p>提供编辑操作的格式化字符串表示。</p>
         *
         * @return 采用 diff 样式的文本表示。
         */
        @Override
        public String toString() {
            switch (type) {
                case KEEP:
                    return "  " + text;
                case INSERT:
                    return "+ " + text;
                case DELETE:
                    return "- " + text;
            }
            return text;
        }
    }

    /**
     * <p>计算两个字符串数组之间的差异。</p>
     *
     * @param a 原始序列。
     * @param b 目标序列。
     * @return 一个 {@link Edit} 对象列表，表示将 <code>a</code> 转换为 <code>b</code> 的操作。
     */
    public static List<Edit> diff(String[] a, String[] b) {
        int N = a.length;
        int M = b.length;
        int max = N + M;
        int offset = max; // 用偏移量避免负索引
        int[] V = new int[2 * max + 1];
        for (int i = 0; i < V.length; i++) V[i] = 0;

        List<int[]> trace = new ArrayList<>();

        for (int D = 0; D <= max; D++) {
            int[] Vcopy = V.clone();
            for (int k = -D; k <= D; k += 2) {
                int idx = k + offset;
                int x;
                if (k == -D || (k != D && V[idx - 1] < V[idx + 1])) {
                    x = V[idx + 1]; // down
                } else {
                    x = V[idx - 1] + 1; // right
                }
                int y = x - k;

                // 沿对角线匹配
                while (x < N && y < M && a[x].equals(b[y])) {
                    x++;
                    y++;
                }
                V[idx] = x;

                if (x >= N && y >= M) {
                    trace.add(V.clone());
                    return buildEdits(trace, a, b, offset);
                }
            }
            trace.add(V.clone());
        }
        return new ArrayList<>();
    }

    /**
     * <p>通过记录的轨迹回溯以构建最终的编辑列表。</p>
     *
     * @param trace 收集到的算法状态轨迹。
     * @param a 原始序列。
     * @param b 目标序列。
     * @param offset 计算期间使用的索引偏移量。
     * @return 组装后的 {@link Edit} 元素列表。
     */
    private static List<Edit> buildEdits(List<int[]> trace, String[] a, String[] b, int offset) {
        List<Edit> edits = new ArrayList<>();
        int x = a.length;
        int y = b.length;

        for (int D = trace.size() - 1; D >= 0; D--) {
            int[] V = trace.get(D);
            int k = x - y;
            int idx = k + offset;

            int prevK;
            if (k == -D || (k != D && V[idx - 1] < V[idx + 1])) {
                prevK = k + 1; // down
            } else {
                prevK = k - 1; // right
            }
            int prevX = V[prevK + offset];
            int prevY = prevX - prevK;

            // 对角线匹配部分
            while (x > prevX && y > prevY) {
                edits.add(0, new Edit(EditType.KEEP, a[x - 1]));
                x--;
                y--;
            }

            if (D == 0) break;

            if (x == prevX) {
                edits.add(0, new Edit(EditType.INSERT, b[y - 1]));
                y--;
            } else {
                edits.add(0, new Edit(EditType.DELETE, a[x - 1]));
                x--;
            }
        }
        return edits;
    }

}