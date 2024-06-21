package cn.net.pap.common.boofcv;

import boofcv.io.image.UtilImageIO;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

public class BoofcvUtilTest {

    @Test
    public void brightnessAndContrastedImageTest() {
        BufferedImage inputImage = UtilImageIO.loadImage("origin.jpg");

        // Adjust brightness
        BufferedImage brightenedImage = BoofcvUtil.adjustBrightness(inputImage, 0.01f);
        UtilImageIO.saveImage(brightenedImage, "brightened_origin.jpg");

        // Adjust contrast
        BufferedImage contrastedImage = BoofcvUtil.adjustContrast(inputImage, 15f);
        UtilImageIO.saveImage(contrastedImage, "contrasted_origin.jpg");
    }

}
