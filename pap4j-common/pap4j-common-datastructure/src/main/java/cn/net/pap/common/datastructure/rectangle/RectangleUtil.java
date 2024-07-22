package cn.net.pap.common.datastructure.rectangle;

import java.util.List;

/**
 * 矩形操作 工具类
 */
public class RectangleUtil {


    /**
     * 两个矩形区域集合是否有重叠
     * @param rectangleList1    矩形区域集合1 [x, x', y, y']
     * @param rectangleList2    矩形区域集合2 [x, x', y, y']
     * @return
     */
    public static boolean isOverlap(List<List<Double>> rectangleList1, List<List<Double>> rectangleList2) {
        if(rectangleList1 == null || rectangleList1.size() == 0 ||
            rectangleList2 == null || rectangleList2.size() == 0) {
            return false;
        }
        for(List<Double> box1 : rectangleList1) {
            for(List<Double> box2 : rectangleList2) {
                boolean b = doRectanglesOverlap(box1, box2);
                if(b) {
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

}
