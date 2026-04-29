package cn.net.pap.common.opencv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class ImageSteganographyUtilsTest {
    private static final Logger log = LoggerFactory.getLogger(ImageSteganographyUtilsTest.class);

    @Test
    public void hiddenAndReveal() {
        try {
            // 读取图像
            BufferedImage originalImage = ImageIO.read(TestResourceUtil.getFile("0.jpg"));
            // 隐写信息 已'.'结尾
            String message = "alexgaoyh" + ".";
            // 在图像中隐藏信息
            BufferedImage stegoImage = ImageSteganographyUtils.embedMessage(originalImage, message);
            // 提取隐藏的信息
            String extractedMessage = ImageSteganographyUtils.extractMessage(stegoImage);
            // 输出提取的信息
            log.info("{}", "Extracted Message: " + extractedMessage);
            // 保存含有隐藏信息的图像
            File output = TestResourceUtil.createTempFile("pap-out", ".jpg");
            ImageIO.write(stegoImage, "jpg", output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void hiddenAndRevealOpenCV() {
        try {
            // 读取图像
            Mat originalImage = OpenCVUtils.imread(TestResourceUtil.getFile("0.jpg").getAbsolutePath().toString());
            // 隐写信息 已'.'结尾
            String message = "alexgaoyh" + ".";
            // 在图像中隐藏信息
            Mat stegoImage = OpenCVUtils.embedMessage(originalImage.clone(), message);
            // 提取隐藏的信息
            String extractedMessage = OpenCVUtils.extractMessage(stegoImage);
            // 输出提取的信息
            log.info("{}", "Extracted Message: " + extractedMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
