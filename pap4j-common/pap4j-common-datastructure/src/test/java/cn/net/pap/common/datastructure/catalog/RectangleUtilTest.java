package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.rectangle.RectangleUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
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

}
