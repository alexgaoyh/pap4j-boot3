package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.rectangle.RectangleUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class RectangleUtilTest {

    @Test
    public void isOverlapTest() {
        // x x' y y'
        List<Double> box11 = Arrays.asList(new Double[]{10.0, 20.0, 10.0, 20.0});
        List<Double> box12 = Arrays.asList(new Double[]{19.0, 39.0, 19.0, 39.0});

        List<Double> box21 = Arrays.asList(new Double[]{10.0, 20.0, 20.0, 30.0});
        List<Double> box22 = Arrays.asList(new Double[]{15.0, 25.0, 15.0, 25.0});

        List<List<Double>> box1 = new ArrayList<>();
        box1.add(box11);
        box1.add(box12);

        List<List<Double>> box2 = new ArrayList<>();
        box2.add(box21);
        box2.add(box22);

        boolean overlap = RectangleUtil.isOverlap(box1, box2);
        System.out.println(overlap);
    }

    @Test
    public void verticalPartTest() {
        Double[] rect = new Double[]{1702.08,1125.63,1806.04,1565.68};
        List<Double[]> partRects = RectangleUtil.verticalPart(rect, 4);
        System.out.println(partRects);
    }

    @Test
    public void reSortTest() {
        Double[] rect1 = new Double[]{1702.08,2920.65,1806.04,3472.88};
        Double[] rect2 = new Double[]{1702.08,1125.63,1806.04,1565.68};
        Double[] rect3 = new Double[]{1346.68,2023.14,1450.64,3248.51};
        Double[] rect4 = new Double[]{1524.38,2023.14,1628.34,3248.51};
        List<Double[]> rectList = new ArrayList<>();
        rectList.add(rect1);
        rectList.add(rect2);
        rectList.add(rect3);
        rectList.add(rect4);

        RectangleUtil.reSort(rectList);
        System.out.println(rectList);
    }

    @Test
    public void mergeRectanglesTest() {
        List<Double> box1 = List.of(10.0, 30.0, 20.0, 40.0);
        List<Double> box2 = List.of(15.0, 50.0, 25.0, 45.0);
        List<Double> box3 = List.of(5.0, 20.0, 10.0, 35.0);
        List<Double> box4 = List.of(12.0, 32.0, 18.0, 42.0);

        List<Double> merged = RectangleUtil.mergeRectangles(box1, box2, box3, box4);

        System.out.println(merged);
    }

    @Test
    public void mergeBoxesTest() {
        List<List<Double>> boxes = new ArrayList<>();
        boxes.add(List.of(10.0, 40.0, 0.0, 20.0));
        boxes.add(List.of(52.0, 80.0, 0.0, 20.0));
        boxes.add(List.of(15.0, 50.0, 25.0, 45.0));
        boxes.add(List.of(60.0, 90.0, 25.0, 45.0));
        boxes.add(List.of(10.0, 50.0, 50.0, 70.0));

        List<List<Double>> lines = RectangleUtil.mergeBoxesToLines(boxes, 1.5, 0.6);
        System.out.println("Merged lines:");
        for (List<Double> l : lines) {
            System.out.println(l);
        }

        List<List<Double>> columns = RectangleUtil.mergeBoxesToColumns(boxes, 1.5, 0.6);
        System.out.println("Merged columns:");
        for (List<Double> c : columns) {
            System.out.println(c);
        }
    }

    @Test
    public void getSkylineTest() {
        int[][] buildings = {{2, 9, 10}, {3, 7, 15}, {5, 12, 12}, {15, 20, 10}, {19, 24, 8}};
        List<List<Integer>> skyline = RectangleUtil.getSkyline(buildings);
        System.out.println(skyline);

    }

    @Test
    public void getSkylineFromRectanglesTest() {
        int[][] buildings = {
                {2, 9, 7, 0},
                {3, 12, 8, 0},
                {5, 8, 9, 0}
        };
        List<List<Integer>> skyline = RectangleUtil.getSkylineFromRectangles(buildings);
        System.out.println(skyline);

    }

}
