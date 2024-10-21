package cn.net.pap.common.datastructure.queen;

import org.junit.jupiter.api.Test;

/**
 * N-Queens 回溯 非递归
 */
public class NQueens {

    @Test
    public void nqueensTest() {
        queen();
    }

    private static final int N = 4; // 棋盘的大小
    private static int[] queens = new int[N + 1]; // 存储皇后的列号，下标从1到N
    private static int answer = 0; // 方案数

    // 检查第 j 个皇后的位置是否合法
    private static boolean check(int j) {
        for (int i = 1; i < j; i++) {
            if (queens[i] == queens[j] || Math.abs(i - j) == Math.abs(queens[i] - queens[j])) {
                return false;
            }
        }
        return true;
    }

    // 求解 N 皇后 方案
    private static void queen() {
        for (int i = 1; i <= N; i++) {
            queens[i] = 0;
        }

        int j = 1; // 表示正在摆放第 j 个皇后
        while (j >= 1) {
            queens[j]++; // 让第 j 个皇后向后一列摆放

            while (queens[j] <= N && !check(j)) { // 判断第 j 个皇后的位置是否合法
                queens[j]++; // 不合法就往后一个位置摆放
            }

            if (queens[j] <= N) { // 表示第 j 个皇后找到一个合法的摆放位置
                if (j == N) { // 找到了 N 皇后的一组解
                    answer++;
                    printSolution();
                } else {
                    j++; // 继续摆放下一个皇后
                }
            } else { // 表示第 j 个皇后找不到一个合法的摆放位置
                queens[j] = 0; // 还原第 j 个皇后的位置
                j--; // 回溯
            }
        }
    }

    // 打印当前解决方案
    private static void printSolution() {
        System.out.println("方案" + answer + "：");
        for (int i = 1; i <= N; i++) {
            for (int j = 1; j <= N; j++) {
                if (queens[i] == j) {
                    System.out.print("Q ");
                } else {
                    System.out.print(". ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }

}
