package cn.net.pap.common.datastructure.sequence;

/**
 * 序列 对齐算法
 */
public class SequenceAlignmentUtil {

    // 初始化的代价矩阵（可以根据需要进行调整）
    private static final int MATCH_SCORE = 1;
    private static final int MISMATCH_SCORE = -1;
    private static final int GAP_SCORE = -2;

    /**
     * Needleman-Wunsch 对齐算法
     *
     * @param seq1
     * @param seq2
     * @return
     */
    public static String[] needlemanWunsch(String seq1, String seq2) {
        int m = seq1.length();
        int n = seq2.length();

        // 创建矩阵来保存计算的分数
        int[][] scoreMatrix = new int[m + 1][n + 1];

        // 初始化矩阵第一行和第一列
        for (int i = 0; i <= m; i++) {
            scoreMatrix[i][0] = i * GAP_SCORE;
        }
        for (int j = 0; j <= n; j++) {
            scoreMatrix[0][j] = j * GAP_SCORE;
        }

        // 填充分数矩阵
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int match = scoreMatrix[i - 1][j - 1] + (seq1.charAt(i - 1) == seq2.charAt(j - 1) ? MATCH_SCORE : MISMATCH_SCORE);
                int delete = scoreMatrix[i - 1][j] + GAP_SCORE;
                int insert = scoreMatrix[i][j - 1] + GAP_SCORE;
                scoreMatrix[i][j] = Math.max(match, Math.max(delete, insert));
            }
        }

        // 回溯得到最优对齐
        StringBuilder alignedSeq1 = new StringBuilder();
        StringBuilder alignedSeq2 = new StringBuilder();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && scoreMatrix[i][j] == scoreMatrix[i - 1][j - 1] + (seq1.charAt(i - 1) == seq2.charAt(j - 1) ? MATCH_SCORE : MISMATCH_SCORE)) {
                alignedSeq1.append(seq1.charAt(i - 1));
                alignedSeq2.append(seq2.charAt(j - 1));
                i--;
                j--;
            } else if (i > 0 && scoreMatrix[i][j] == scoreMatrix[i - 1][j] + GAP_SCORE) {
                alignedSeq1.append(seq1.charAt(i - 1));
                alignedSeq2.append('-');
                i--;
            } else {
                alignedSeq1.append('-');
                alignedSeq2.append(seq2.charAt(j - 1));
                j--;
            }
        }

        // 结果反转
        return new String[]{alignedSeq1.reverse().toString(), alignedSeq2.reverse().toString()};
    }

}
