package cn.net.pap.common.datastructure.meanShift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p><strong>MeanShiftUtil</strong> 实现了 Mean Shift（均值漂移）聚类算法。</p>
 *
 * <p>均值漂移是一种非参数特征空间分析技术，用于定位密度函数的最大值。
 * 这个工具类提供了用于查找由 {@link PointX} 表示的 1D 点簇的工具。</p>
 *
 * <ul>
 *     <li>处理向质心（centroids）的迭代收敛。</li>
 *     <li>将点分配到最接近的已发现的簇中。</li>
 * </ul>
 */
public class MeanShiftUtil {

    /**
     * <p>确定算法收敛的阈值。</p>
     */
    private static final double THRESHOLD = 0.001;

    /**
     * <p>允许的最大迭代次数。</p>
     */
    private static final int MAX_ITERATIONS = 300;

    /**
     * <p>执行均值漂移算法并移除任何生成的空簇。</p>
     *
     * @param pointXES 要进行聚类的点 {@link List}。
     * @param BANDWIDTH 定义邻域的带宽（半径）。
     * @return 包含有点的簇的列表。
     */
    public static List<List<PointX>> meanShiftRemoveZero(List<PointX> pointXES, double BANDWIDTH) {
        List<List<PointX>> lists = meanShift(pointXES, BANDWIDTH);
        if(lists != null && lists.size() > 0) {
            return lists.stream().filter(e -> e.size() > 0).collect(Collectors.toList());
        }
        return lists;
    }

    /**
     * <p>执行核心的均值漂移聚类算法。</p>
     *
     * @param pointXES 要进行聚类的点 {@link List}。
     * @param BANDWIDTH 定义邻域的带宽（半径）。
     * @return 表示找到的簇及其分配的点的列表的列表。
     */
    public static List<List<PointX>> meanShift(List<PointX> pointXES, double BANDWIDTH) {
        List<PointX> centroids = new ArrayList<>(pointXES);
        boolean hasConverged;
        int iteration = 0;

        do {
            hasConverged = true;
            List<PointX> newCentroids = new ArrayList<>();

            for (PointX centroid : centroids) {
                PointX newCentroid = calculateMean(centroid, pointXES, BANDWIDTH);
                if (Math.abs(centroid.getX() - newCentroid.getX()) > THRESHOLD) {
                    hasConverged = false;
                }
                newCentroids.add(newCentroid);
            }

            centroids = newCentroids;
            iteration++;
        } while (!hasConverged && iteration < MAX_ITERATIONS);

        return assignPointsToClusters(centroids, pointXES);
    }

    /**
     * <p>计算定义的带宽内的平均值（质心）。</p>
     *
     * @param centroid 当前的质心点。
     * @param pointXES 数据点的完整列表。
     * @param BANDWIDTH 寻找相邻点的搜索半径。
     * @return 新计算的质心 {@link PointX}。
     */
    private static PointX calculateMean(PointX centroid, List<PointX> pointXES, double BANDWIDTH) {
        double sumX = 0.0;
        int count = 0;

        for (PointX pointX : pointXES) {
            if (Math.abs(centroid.getX() - pointX.getX()) <= BANDWIDTH) {
                sumX += pointX.getX();
                count++;
            }
        }

        return new PointX(sumX / count, new HashMap<>());
    }

    /**
     * <p>将每个原始点分配给最近的最终质心。</p>
     *
     * @param centroids 最终收敛的质心列表。
     * @param pointXES 原始点列表。
     * @return 结构化为聚类点列表的数据。
     */
    private static List<List<PointX>> assignPointsToClusters(List<PointX> centroids, List<PointX> pointXES) {
        List<List<PointX>> clusters = new ArrayList<>();

        for (PointX centroid : centroids) {
            clusters.add(new ArrayList<>());
        }

        for (PointX pointX : pointXES) {
            int closestCentroidIndex = 0;
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < centroids.size(); i++) {
                double dist = Math.abs(pointX.getX() - centroids.get(i).getX());
                if (dist < minDistance) {
                    minDistance = dist;
                    closestCentroidIndex = i;
                }
            }

            clusters.get(closestCentroidIndex).add(pointX);
        }

        return clusters;
    }

}