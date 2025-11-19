package cn.net.pap.common.datastructure.rectangle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 矩形操作 工具类
 */
public class RectangleUtil {

    /**
     * 天际线问题
     *
     * @param buildings 输入的所有建筑物的位置和高度  x1 x2 y
     * @return 显示由这些建筑物形成的天际线。  轮廓
     */
    public static List<List<Integer>> getSkyline(int[][] buildings) {
        List<int[]> all = new ArrayList<>();
        for (int[] e : buildings) {
            all.add(new int[]{e[0], -e[2]}); //left top corner
            all.add(new int[]{e[1], e[2]}); //right top corner
        }
        // sort by x asc, if x is equal , by y desc
        all.sort((o1, o2) -> o1[0] - o2[0] == 0 ? o1[1] - o2[1] : o1[0] - o2[0]);

        List<List<Integer>> res = new ArrayList<>();
        // queue to store heights
        PriorityQueue<Integer> heights = new PriorityQueue<>(Comparator.reverseOrder());
        // when the last build gone, the max height is 0
        heights.add(0);

        // last point's height
        int maxHeight = 0;
        for (int[] p : all) {
            // p[1] < 0 left corner, add height to stack
            // else right corner, remove height from stack
            if (p[1] < 0) heights.add(-p[1]);
            else heights.remove(p[1]);

            // meet change point, add it to the result list
            if (maxHeight != heights.peek()) {
                maxHeight = heights.peek();
                res.add(Arrays.asList(p[0], maxHeight));
            }
        }

        return res;
    }


    /**
     * 处理多个矩形区域的天际线问题
     *
     * @param rectangles 矩形数组，每个矩形为[leftTopX, leftTopY, rightBottomX, rightBottomY]， 注意右下角点的y在水平线上(rightBottomY==0)。
     * @return 显示由这些矩形形成的天际线轮廓
     */
    public static List<List<Integer>> getSkylineFromRectangles(int[][] rectangles) {
        List<int[]> all = new ArrayList<>();
        for (int[] rect : rectangles) {
            int leftTopX = rect[0];
            int leftTopY = rect[1]; // 高度
            int rightBottomX = rect[2];
            int rightBottomY = rect[3]; // 高度

            all.add(new int[]{leftTopX, -leftTopY}); // left top corner
            all.add(new int[]{rightBottomX, leftTopY}); // right top corner
        }

        // sort by x asc, if x is equal , by y desc
        all.sort((o1, o2) -> o1[0] - o2[0] == 0 ? o1[1] - o2[1] : o1[0] - o2[0]);

        List<List<Integer>> res = new ArrayList<>();
        PriorityQueue<Integer> heights = new PriorityQueue<>(Comparator.reverseOrder());
        heights.add(0);

        int maxHeight = 0;
        for (int[] p : all) {
            if (p[1] < 0) heights.add(-p[1]);
            else heights.remove(p[1]);

            if (maxHeight != heights.peek()) {
                maxHeight = heights.peek();
                res.add(Arrays.asList(p[0], maxHeight));
            }
        }

        return res;
    }

    /**
     * 两个矩形区域集合是否有重叠
     *
     * @param rectangleList1 矩形区域集合1 [x, x', y, y']
     * @param rectangleList2 矩形区域集合2 [x, x', y, y']
     * @return
     */
    public static boolean isOverlap(List<List<Double>> rectangleList1, List<List<Double>> rectangleList2) {
        if (rectangleList1 == null || rectangleList1.size() == 0 || rectangleList2 == null || rectangleList2.size() == 0) {
            return false;
        }
        for (List<Double> box1 : rectangleList1) {
            for (List<Double> box2 : rectangleList2) {
                boolean b = doRectanglesOverlap(box1, box2);
                if (b) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean doRectanglesOverlap(List<Double> box1, List<Double> box2) {
        // 提取矩形的左上角和右下角的坐标
        double x1 = box1.get(0);
        double y1 = box1.get(2);
        double x1Prime = box1.get(1);
        double y1Prime = box1.get(3);

        double x2 = box2.get(0);
        double y2 = box2.get(2);
        double x2Prime = box2.get(1);
        double y2Prime = box2.get(3);

        // 检查矩形是否重叠
        boolean overlapX = x1 < x2Prime && x1Prime > x2;
        boolean overlapY = y1 < y2Prime && y1Prime > y2;

        return overlapX && overlapY;
    }

    /**
     * 垂直切分矩形区域
     *
     * @param rect     [x y x' y']  (x,y)左上角  (x',y')右下角
     * @param partSize 切分的份数
     * @return
     */
    public static List<Double[]> verticalPart(Double[] rect, Integer partSize) {
        List<Double[]> subRects = new ArrayList<>();
        double height = rect[3] - rect[1];
        double quarterHeight = height / partSize; // 计算 partSize 分之一的宽度

        // 创建等分的矩形
        for (int i = 0; i < partSize; i++) {
            double newY = rect[1] + i * quarterHeight;
            double newYPrime = newY + quarterHeight;
            BigDecimal newYBigDecimal = new BigDecimal(newY).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal newYPrimeBigDecimal = new BigDecimal(newYPrime).setScale(2, BigDecimal.ROUND_HALF_UP);
            subRects.add(new Double[]{rect[0], newYBigDecimal.doubleValue(), rect[2], newYPrimeBigDecimal.doubleValue()});
        }

        return subRects;
    }

    /**
     * 重排序
     *
     * @param rectList [x, y, x', y'] 坐标集合
     * @return
     */
    public static void reSort(List<Double[]> rectList) {
        Collections.sort(rectList, new Comparator<Double[]>() {
            @Override
            public int compare(Double[] c1, Double[] c2) {
                // x 坐标是越大越在前面
                int xCompare = Double.compare(c2[0], c1[0]);
                if (xCompare != 0) {
                    return xCompare;
                } else {
                    // y 坐标是越小越在前面
                    return Double.compare(c1[1], c2[1]);
                }
            }
        });
    }


    /**
     * 合并 4 个矩形，最终生成一个最小外接矩形
     * <p>
     * 每个 box = [leftTopX, rightBottomX, leftTopY, rightBottomY]
     *
     * @return 合并后的矩形结构：[minX, maxX, minY, maxY]
     */
    public static List<Double> mergeRectangles(List<Double> box1, List<Double> box2, List<Double> box3, List<Double> box4) {
        validateBox(box1);
        validateBox(box2);
        validateBox(box3);
        validateBox(box4);

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        List<List<Double>> boxes = List.of(box1, box2, box3, box4);

        for (List<Double> box : boxes) {
            double x1 = box.get(0); // left top x
            double x2 = box.get(1); // right bottom x
            double y1 = box.get(2); // left top y
            double y2 = box.get(3); // right bottom y

            minX = Math.min(minX, x1);
            maxX = Math.max(maxX, x2);
            minY = Math.min(minY, y1);
            maxY = Math.max(maxY, y2);
        }

        return List.of(minX, maxX, minY, maxY);
    }

    private static void validateBox(List<Double> box) {
        if (box == null || box.size() != 4) {
            throw new IllegalArgumentException("Each box must be a List<Double> of length 4");
        }
    }

    // ------------------- 行合并 -------------------
    /**
     * 按行合并字框
     * @param boxes 字框列表
     * @param gapMultiplier 字间隔阈值系数（推荐1.5）
     * @param verticalOverlapThreshold Y方向重叠比例阈值（推荐0.6）
     * @return List<List<Double>> 每个元素表示一行矩形 [ltX, rbX, ltY, rbY]
     */
    public static List<List<Double>> mergeBoxesToLines(List<List<Double>> boxes,
                                                       double gapMultiplier,
                                                       double verticalOverlapThreshold) {
        List<List<Double>> lines = new ArrayList<>();
        // 按 top 排序
        boxes.sort(Comparator.comparingDouble(b -> b.get(2))); // ltY

        for (List<Double> b : boxes) {
            boolean merged = false;
            for (List<Double> line : lines) {
                if (isSameLine(line, b, verticalOverlapThreshold)) {
                    double avgWidth = line.get(1) - line.get(0);
                    if (isAdjacent(line, b, avgWidth, gapMultiplier)) {
                        // 合并
                        line.set(0, Math.min(line.get(0), b.get(0)));
                        line.set(1, Math.max(line.get(1), b.get(1)));
                        line.set(2, Math.min(line.get(2), b.get(2)));
                        line.set(3, Math.max(line.get(3), b.get(3)));
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) {
                lines.add(new ArrayList<>(b));
            }
        }
        return lines;
    }

    // ------------------- 列合并 -------------------
    /**
     * 按列合并字框
     * @param boxes 字框列表
     * @param gapMultiplier 行间隔阈值系数（推荐1.5）
     * @param horizontalOverlapThreshold X方向重叠比例阈值（推荐0.6）
     * @return List<List<Double>> 每个元素表示一列矩形 [ltX, rbX, ltY, rbY]
     */
    public static List<List<Double>> mergeBoxesToColumns(List<List<Double>> boxes,
                                                         double gapMultiplier,
                                                         double horizontalOverlapThreshold) {
        List<List<Double>> columns = new ArrayList<>();
        // 按 left 排序
        boxes.sort(Comparator.comparingDouble(b -> b.get(0))); // ltX

        for (List<Double> b : boxes) {
            boolean merged = false;
            for (List<Double> col : columns) {
                if (isSameColumn(col, b, horizontalOverlapThreshold)) {
                    double avgHeight = col.get(3) - col.get(2);
                    double gap = Math.max(0, b.get(2) - col.get(3));
                    if (gap <= avgHeight * gapMultiplier) {
                        // 合并列
                        col.set(0, Math.min(col.get(0), b.get(0)));
                        col.set(1, Math.max(col.get(1), b.get(1)));
                        col.set(2, Math.min(col.get(2), b.get(2)));
                        col.set(3, Math.max(col.get(3), b.get(3)));
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged) {
                columns.add(new ArrayList<>(b));
            }
        }
        return columns;
    }

    // ------------------- 内部辅助 -------------------
    /** 判断两个字框是否属于同一行（Y方向重叠比例） */
    private static boolean isSameLine(List<Double> a, List<Double> b, double verticalOverlapThreshold) {
        double interHeight = Math.min(a.get(3), b.get(3)) - Math.max(a.get(2), b.get(2));
        if (interHeight <= 0) return false;
        double minHeight = Math.min(a.get(3) - a.get(2), b.get(3) - b.get(2));
        return interHeight / minHeight >= verticalOverlapThreshold;
    }

    /** 判断两个字框是否属于同一列（X方向重叠比例） */
    private static boolean isSameColumn(List<Double> a, List<Double> b, double horizontalOverlapThreshold) {
        double interWidth = Math.min(a.get(1), b.get(1)) - Math.max(a.get(0), b.get(0));
        if (interWidth <= 0) return false;
        double minWidth = Math.min(a.get(1) - a.get(0), b.get(1) - b.get(0));
        return interWidth / minWidth >= horizontalOverlapThreshold;
    }

    /** 判断水平间隔是否可以合并 */
    private static boolean isAdjacent(List<Double> a, List<Double> b, double avgWidth, double multiplier) {
        if (b.get(0) <= a.get(1)) return true; // 有交叠
        double gap = b.get(0) - a.get(1);
        return gap <= avgWidth * multiplier;
    }

}
