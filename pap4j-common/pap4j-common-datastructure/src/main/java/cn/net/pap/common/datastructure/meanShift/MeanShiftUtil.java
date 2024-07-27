package cn.net.pap.common.datastructure.meanShift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 聚类算法
 */
public class MeanShiftUtil {

    /**
     * 阈值，用于判断算法是否收敛。具体来说，当质心的移动距离小于阈值时，算法认为已经收敛，不再继续迭代。
     */
    private static final double THRESHOLD = 0.001;

    /**
     * 最大迭代次数
     */
    private static final int MAX_ITERATIONS = 300;

    public static List<List<PointX>> meanShiftRemoveZero(List<PointX> pointXES, double BANDWIDTH) {
        List<List<PointX>> lists = meanShift(pointXES, BANDWIDTH);
        if(lists != null && lists.size() > 0) {
            return lists.stream().filter(e -> e.size() > 0).collect(Collectors.toList());
        }
        return lists;
    }

    /**
     *
     * @param pointXES
     * @param BANDWIDTH 带宽（或半径），表示一个点在计算质心时考虑的邻域范围。换句话说，带宽决定了一个点周围多少距离内的点会被视为邻居。
     * @return
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
