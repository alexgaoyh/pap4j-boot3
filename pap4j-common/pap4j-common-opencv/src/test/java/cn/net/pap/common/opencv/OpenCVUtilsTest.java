package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

public class OpenCVUtilsTest {

    @Test
    public void templateTest() {
        String sourceImg = "origin.jpg";
        String templateImg = "template.jpg";
        String targetPath = "target.jpg";
        OpenCVUtils.templateMatching(sourceImg, templateImg, targetPath);
    }
}
