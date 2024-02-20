package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class OpenCVUtilsTest {

    // @Test
    public void templateTest() {
        String sourceImg = "origin.jpg";
        String templateImg = "template.jpg";
        String targetPath = "target.jpg";
        OpenCVUtils.templateMatching(sourceImg, templateImg, targetPath);
    }

    // @Test
    public void searchingTest() {
        String image1Path = "image1.jpg";
        String image2Path = "image2.jpg";
        List<String> typeList = Arrays.asList(new String[]{"Histogram"});
        for(String type : typeList) {
            double v = OpenCVUtils.similarityImage(image1Path, image2Path, type);
            System.out.println(v);
        }
    }
}
