package cn.net.pap.common.datastructure.myersdiff;

import java.util.ArrayList;
import java.util.List;

/**
 * like Git diff
 */
public class MyersDiffUtil {

    public enum EditType {
        KEEP, INSERT, DELETE
    }

    public static class Edit {
        public EditType type;
        public String text;

        public Edit(EditType type, String text) {
            this.type = type;
            this.text = text;
        }

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
