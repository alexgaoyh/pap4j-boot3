package cn.net.pap.common.datastructure.polygon;

import org.junit.jupiter.api.Test;

public class ConvexPolygonTest {

    //六边形
    static int N = 7;
    //给定六边形的权值
    static int[][] w = {{0, 2, 2, 3, 1, 4}, {2, 0, 1, 5, 2, 3}, {2, 1, 0, 2, 1, 4}, {3, 5, 2, 0, 6, 2}, {1, 2, 1, 6, 0, 1}, {4, 3, 4, 2, 1, 0}};

    @Test
    public void test() {
        MinWeightTriangulation(N, w);
    }

    public static void MinWeightTriangulation(int N, int[][] w) {
        int[][] s = new int[N][N];//存放最优剖分点k
        int[][] t = new int[N][N];//存放解
        //初始化
        for (int i = 0; i < N; i++) {
            t[i][i] = 0;
        }
        //r为间隔边数，先从两条边开始，即三个点组成的凸多边形（三角形）
        for (int r = 2; r < N; r++) {
            for (int i = 1; i < N - r; i++) {//i的范围 (N-1-1)-(i-1)>=r → i<N-r 或 i<=N-r+1，N两次-1分别是：1、初始N为边数加一 2、顶点编号从0开始
                int j = i - 1 + r;//j为i加上间隔边数
                t[i][j] = t[i][i] + t[i + 1][j] + Weight(i - 1, i, j);
                s[i][j] = i;//记录k值
                //开始考虑不同的k值
                for (int k = i + 1; k < j; k++) {
                    int min = t[i][k] + t[k + 1][j] + Weight(i - 1, k, j);
                    if (min < t[i][j]) {//有更优解
                        t[i][j] = min;
                        s[i][j] = k;
                    }
                }
            }
        }
        System.out.println("最优解：" + t[1][N - 2]);
        Traceback(1, N - 2, s);
    }

    static void Traceback(int i, int j, int[][] s) {
        if (i == j) return;
        //子问题的k值
        Traceback(i, s[i][j], s);
        Traceback(s[i][j] + 1, j, s);
        System.out.println("剖分点：" + (i - 1) + "  " + j + "  " + s[i][j]);
    }

    public static int Weight(int a, int b, int c) {//求三角形的权值
        return w[a][b] + w[b][c] + w[a][c];
    }

}
