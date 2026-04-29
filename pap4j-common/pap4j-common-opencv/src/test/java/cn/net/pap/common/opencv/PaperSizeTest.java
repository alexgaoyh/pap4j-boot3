package cn.net.pap.common.opencv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.opencv.enums.PaperSize;
import org.junit.jupiter.api.Test;

import java.awt.*;

public class PaperSizeTest {
    private static final Logger log = LoggerFactory.getLogger(PaperSizeTest.class);

    @Test
    public void test1() {
        int dpi = 400; // 设置DPI值
        PaperSize paperSize = PaperSize.A3; // 直接使用枚举值
        boolean landscape = true; // 是否横向
        Dimension dimension = PaperSize.calculatePixelSize(dpi, paperSize, landscape);
        String info = String.format("%s %s - %d DPI - %dx%d px",
                paperSize.getName(),
                landscape ? "Landscape" : "Portrait",
                dpi,
                dimension.width, dimension.height);
        log.info("{}", info);

    }

}
