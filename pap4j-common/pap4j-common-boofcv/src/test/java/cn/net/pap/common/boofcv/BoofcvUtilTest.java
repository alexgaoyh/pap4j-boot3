package cn.net.pap.common.boofcv;

import boofcv.alg.misc.ImageMiscOps;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.*;
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

    // @Test
    public void adjustTwoTest() {
        BufferedImage inputImage = UtilImageIO.loadImage("C:\\Users\\86181\\Desktop\\origin.jpg");

        BufferedImage brightenedImage = BoofcvUtil.adjustTwo(inputImage, 0f, 100f);
        UtilImageIO.saveImage(brightenedImage, "C:\\Users\\86181\\Desktop\\out.jpg");

    }

    @Test
    public void rotateTest() {
        BufferedImage input = UtilImageIO.loadImage(UtilIO.pathExample("input.jpg"));
        InterleavedF32 imageInterleaved = new InterleavedF32();
        ConvertBufferedImage.convertFromInterleaved(input, imageInterleaved, true);
        // 旋转
        InterleavedF32 interleavedF32 = ImageMiscOps.rotateCW(imageInterleaved, null);
        UtilImageIO.saveImage(interleavedF32, "output.jpg");

    }

    // @Test
    public void cropTest() {
        BufferedImage input = UtilImageIO.loadImage(UtilIO.pathExample("input.jpg"));
        BufferedImage crop = BoofcvUtil.crop(input, 100, 100, 1000, 1000);
        UtilImageIO.saveImage(crop, "output.jpg");
    }

    // @Test
    public void base64Test() {
        BufferedImage input = UtilImageIO.loadImage(UtilIO.pathExample("input.jpg"));
        String jpg = BoofcvUtil.getBase64(input, "jpg");
    }

}
