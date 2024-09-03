package cn.net.pap.common.opencv;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

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

    /**
     * 在一张图像中同时截取多个矩形区域并按顺序返回 base64.
     * @param inputFilePath
     * @param regions
     * @return
     */
    public static List<String> cropImageCutList(String inputFilePath, List<Rectangle> regions) {
        List<String> base64Images = new ArrayList<>();

        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
            if (!readers.hasNext()) {
                return base64Images;
            }
            ImageReader reader = readers.next();

            try (ImageInputStream iis = ImageIO.createImageInputStream(new File(inputFilePath))) {
                reader.setInput(iis);

                for (Rectangle rect : regions) {
                    ImageReadParam param = reader.getDefaultReadParam();
                    param.setSourceRegion(rect);
                    BufferedImage image = reader.read(0, param);

                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageIO.write(image, "JPEG", baos);
                        byte[] imageBytes = baos.toByteArray();
                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                        base64Images.add(base64Image);
                    }
                }
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return base64Images;
    }

    /**
     * 图像等比缩放并灰度化
     * @param inputPath
     * @param outputPath
     * @param widthHeight
     * @return
     */
    public static boolean scaleAndGray(String inputPath, String outputPath, Integer widthHeight) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(inputPath));

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            double scale = Math.min(Double.valueOf(widthHeight) / originalWidth, Double.valueOf(widthHeight) / originalHeight);
            scale = Math.max(scale, 1.0 / Math.max(originalWidth, originalHeight));

            int newWidth = (int) Math.ceil(originalWidth * scale);
            int newHeight = (int) Math.ceil(originalHeight * scale);

            BufferedImage scaledImage = new BufferedImage(widthHeight, widthHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, widthHeight, widthHeight);

            int x = (widthHeight - newWidth) / 2;
            int y = (widthHeight - newHeight) / 2;
            g2d.drawImage(originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), x, y, null);
            g2d.dispose();

            BufferedImage grayImage = new BufferedImage(widthHeight, widthHeight, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g2dGray = grayImage.createGraphics();
            g2dGray.drawImage(scaledImage, 0, 0, null);
            g2dGray.dispose();

            ImageIO.write(grayImage, "jpg", new File(outputPath));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将两张图片根据给定的坐标点进行拼接
     * @param img1Path 第一张图片的路径
     * @param img2Path 第二张图片的路径
     * @param x1 第一张图片放置的x坐标
     * @param y1 第一张图片放置的y坐标
     * @param x2 第二张图片放置的x坐标
     * @param y2 第二张图片放置的y坐标
     * @return 拼接后的图片
     * @throws IOException
     */
    public static BufferedImage mergeImages(String img1Path, String img2Path, int x1, int y1, int x2, int y2) throws IOException {
        BufferedImage img1 = ImageIO.read(new File(img1Path));
        BufferedImage img2 = ImageIO.read(new File(img2Path));

        int width = Math.max(img1.getWidth() + x1, img2.getWidth() + x2);
        int height = Math.max(img1.getHeight() + y1, img2.getHeight() + y2);
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics g = combined.getGraphics();
        g.drawImage(img1, x1, y1, null);
        g.drawImage(img2, x2, y2, null);
        g.dispose();

        return combined;
    }

    /**
     * 图像旋转
     * @param inputFilePath
     * @param outputFilePath
     * @param angle
     * @return
     */
    public static boolean rotateImage(String inputFilePath, String outputFilePath, double angle) {
        try {
            // 读取原始图像
            BufferedImage originalImage = ImageIO.read(new File(inputFilePath));

            // 计算旋转后的图像的尺寸
            double radians = Math.toRadians(angle);
            double sin = Math.abs(Math.sin(radians));
            double cos = Math.abs(Math.cos(radians));
            int width = (int) Math.floor(originalImage.getWidth() * cos + originalImage.getHeight() * sin);
            int height = (int) Math.floor(originalImage.getHeight() * cos + originalImage.getWidth() * sin);

            // 创建一个新的图像缓冲区，用于存储旋转后的图像
            BufferedImage rotatedImage = new BufferedImage(width, height, originalImage.getType());
            Graphics2D g2d = rotatedImage.createGraphics();
            // 填充背景色为白色
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // 进行旋转变换
            AffineTransform transform = new AffineTransform();
            transform.rotate(radians, width / 2.0, height / 2.0);
            transform.translate((width - originalImage.getWidth()) / 2.0, (height - originalImage.getHeight()) / 2.0);

            // 应用变换到图像上
            g2d.setTransform(transform);
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose();

            // 将旋转后的图像写入到输出文件中
            boolean result = ImageIO.write(rotatedImage, "jpg", new File(outputFilePath));
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


}
