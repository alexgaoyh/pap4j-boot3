package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public class ImageSteganographyUtilsTest {

    // @Test
    public void hiddenAndReveal() {
        try {
            // 读取图像
            BufferedImage originalImage = ImageIO.read(new File("pap.jpg"));
            // 隐写信息 已'.'结尾
            String message = "alexgaoyh" + ".";
            // 在图像中隐藏信息
            BufferedImage stegoImage = ImageSteganographyUtils.embedMessage(originalImage, message);
            // 提取隐藏的信息
            String extractedMessage = ImageSteganographyUtils.extractMessage(stegoImage);
            // 输出提取的信息
            System.out.println("Extracted Message: " + extractedMessage);
            // 保存含有隐藏信息的图像
            File output = new File("pap-out.jpg");
            ImageIO.write(stegoImage, "jpg", output);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // @Test
    public void hiddenAndRevealOpenCV() {
        try {
            // 读取图像
            Mat originalImage = OpenCVUtils.imread("pap.jpg");
            // 隐写信息 已'.'结尾
            String message = "alexgaoyh" + ".";
            // 在图像中隐藏信息
            Mat stegoImage = OpenCVUtils.embedMessage(originalImage.clone(), message);
            // 提取隐藏的信息
            String extractedMessage = OpenCVUtils.extractMessage(stegoImage);
            // 输出提取的信息
            System.out.println("Extracted Message: " + extractedMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
