package cn.net.pap.common.datastructure.rectangle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 矩形操作 工具类
 */
public class RectangleUtil {

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

}
