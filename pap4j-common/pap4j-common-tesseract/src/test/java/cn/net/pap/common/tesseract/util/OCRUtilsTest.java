package cn.net.pap.common.tesseract.util;

import org.junit.jupiter.api.Test;

import java.util.List;

public class OCRUtilsTest {

    @Test
    public void test1() throws Exception {
        List<OCRUtils.OCRResult> chi = OCRUtils.recognizeWithCoordinates("d:\\tessdata", "C:\\Users\\alexg\\Desktop\\ocr.png", "chi_sim");
        System.out.println(chi);
    }

    @Test
    public void test2() throws Exception {
        List<OCRUtils.OCRResult> chi = OCRUtils.recognizeWithWordCoordinates("d:\\tessdata", "C:\\Users\\alexg\\Desktop\\ocr.png", "chi_sim");
        System.out.println(chi);
    }

    /**
     * 文字有正向，逆向等，一个不太友好的方案，把这个图像朝着各个方向翻转，看最后OCR的结果，哪个置信度高，就把哪个当做正确的方向
     * @throws Exception
     */
    // @Test
    public void test3() throws Exception {
        List<OCRUtils.OCRResult> list0 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", "C:\\Users\\86181\\Desktop\\0.jpg", "chi_sim");
        List<OCRUtils.OCRResult> list90 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", "C:\\Users\\86181\\Desktop\\90.jpg", "chi_sim");
        List<OCRUtils.OCRResult> list180 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", "C:\\Users\\86181\\Desktop\\180.jpg", "chi_sim");
        List<OCRUtils.OCRResult> list270 = OCRUtils.recognizeWithCoordinates("d:\\tessdata", "C:\\Users\\86181\\Desktop\\270.jpg", "chi_sim");
        float confidence0 = list0.stream().filter(e -> e.getLevel().equals("PAGE")).findFirst().get().getConfidence();
        float confidence90 = list90.stream().filter(e -> e.getLevel().equals("PAGE")).findFirst().get().getConfidence();
        float confidence180 = list180.stream().filter(e -> e.getLevel().equals("PAGE")).findFirst().get().getConfidence();
        float confidence270 = list270.stream().filter(e -> e.getLevel().equals("PAGE")).findFirst().get().getConfidence();
        System.out.println(confidence0 + " " + confidence90 + " " + confidence180 + " " + confidence270);
    }

}
