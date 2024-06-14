package cn.net.pap.common.boofcv;

import cn.net.pap.common.boofcv.dto.MarginDTO;
import org.junit.jupiter.api.Test;

public class ExampleCannyEdge {

    @Test
    public void blackMarginTest() {
        MarginDTO blackMargin = CannyEdgeUtilss.getBlackMargin("C:\\Users\\86181\\Desktop\\test.jpg");
        System.out.println(blackMargin);
    }

}
