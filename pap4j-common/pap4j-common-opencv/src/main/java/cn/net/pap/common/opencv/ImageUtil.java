package cn.net.pap.common.opencv;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;

public class ImageUtil {

    /**
     * 高效的图像裁剪功能， 指定原始图像路径和裁剪后的图像路径， 指定左上角的x/y 和 宽高 进行高效的裁剪
     * 相比于 ImageIO.read("").getSubimage(x, y, width, height)， 在极端情况下能够提示执行效率10倍以上。
     * 实验过程中一张7.35M大小的JPG图像(分辨率 9007 x 6221)，当前方法执行时间小于1s， ImageIO.read("").getSubimage 执行时间24s.
     * @param inputFilePath
     * @param outputFilePath
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public static Boolean cropImageCut(String inputFilePath, String outputFilePath, int x, int y, int width, int height) {
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();

            ImageInputStream iis = ImageIO.createImageInputStream(new File(inputFilePath));
            reader.setInput(iis);

            ImageReadParam param = reader.getDefaultReadParam();
            Rectangle rect = new Rectangle(x, y, width, height);
            param.setSourceRegion(rect);
            BufferedImage image = reader.read(0, param);

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
            if (!writers.hasNext()) {
                return false;
            }
            ImageWriter writer = writers.next();

            ImageOutputStream ios = ImageIO.createImageOutputStream(new File(outputFilePath));
            writer.setOutput(ios);

            writer.write(image);

            reader.dispose();
            writer.dispose();
            iis.close();
            ios.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
