package cn.net.pap.common.datastructure.sequence;

/**
 * <p><strong>SequenceAlignmentUtil</strong> 提供序列比对算法。</p>
 *
 * <p>目前实现了用于全局序列比对的 Needleman-Wunsch 算法，
 * 该算法常用于生物信息学中比对蛋白质或核苷酸序列。</p>
 *
 * <ul>
 *     <li>计算最佳全局比对。</li>
 *     <li>使用可配置的罚分。</li>
 * </ul>
 */
public class SequenceAlignmentUtil {

    // 初始化的代价矩阵（可以根据需要进行调整）
    private static final int MATCH_SCORE = 1;
    private static final int MISMATCH_SCORE = -1;
    private static final int GAP_SCORE = -2;

    /**
     * <p>对两个序列执行 Needleman-Wunsch 全局比对算法。</p>
     *
     * <p>它构建一个评分矩阵并通过回溯以找到最佳比对，在必要时引入间隙（<strong>'-'</strong>）以使总分最大化。</p>
     *
     * @param seq1 第一个序列字符串。
     * @param seq2 第二个序列字符串。
     * @return 包含分别比对后的两个字符串数组。
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
