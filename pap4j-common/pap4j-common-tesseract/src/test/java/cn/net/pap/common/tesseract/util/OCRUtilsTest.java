package cn.net.pap.common.tesseract.util;

import org.junit.jupiter.api.Test;

import java.util.List;

public class OCRUtilsTest {

    @Test
    public void test1() throws Exception {
        List<OCRUtils.OCRResult> chi = OCRUtils.recognizeWithCoordinates("d:\\tessdata", "C:\\Users\\alexg\\Desktop\\ocr.png", "chi_sim");
        System.out.println(chi);
    }
}
