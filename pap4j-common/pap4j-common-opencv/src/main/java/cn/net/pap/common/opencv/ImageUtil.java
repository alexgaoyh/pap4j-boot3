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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

public class ImageUtil {

    /**
     * generateEmptyJpeg() 的返回值
     * Base64.getEncoder().encodeToString(bytes);
     * Base64.getDecoder().decode(s)
     *
     * response.getOutputStream().write(java.util.Base64.getDecoder().decode(oneXOneJpg));
     */
    public static final String oneXOneJpg = "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigD//2Q==";

    /**
     * 创建1x1像素的最小JPEG图像 背景色白色 的 jpg 图像字符串， 可以直接 response.getOutputStream().write(generateEmptyJpeg()); 写入响应流
     * @return
     */
    public static byte[] generateEmptyJpeg() {
        try {
            // 创建1x1像素的最小JPEG图像
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            // 设置背景色（白色）
            image.setRGB(0, 0, 0xFFFFFF);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

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

    /**
     * 水平镜像
     * @return
     * @throws Exception
     */
    public static boolean horizontalMirror(String inputFilePath, String outputFilePath) throws Exception {
        try {
            // 加载原始图像
            File originalImageFile = new File(inputFilePath);
            BufferedImage originalImage = ImageIO.read(originalImageFile);

            // 创建一个新的BufferedImage来存放翻转后的图像
            BufferedImage flippedImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    originalImage.getType()
            );

            // 创建一个用于绘制的Graphics2D对象
            Graphics2D g2d = flippedImage.createGraphics();

            // 设置仿射变换以实现水平翻转
            AffineTransform at = AffineTransform.getScaleInstance(-1, 1);
            at.translate(-originalImage.getWidth(null), 0);
            g2d.setTransform(at);

            // 将原始图像绘制到带有仿射变换的Graphics2D对象上，实现水平翻转
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose(); // 释放资源

            // 将翻转后的图像保存到文件
            File flippedImageFile = new File(outputFilePath);
            ImageIO.write(flippedImage, "jpg", flippedImageFile);

            return true;
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常堆栈跟踪信息
            return false;
        }
    }

    /**
     * 垂直镜像
     * @param inputFilePath
     * @param outputFilePath
     * @return
     */
    public static boolean verticalMirror(String inputFilePath, String outputFilePath) {
        try {
            // 加载原始图像
            File originalImageFile = new File(inputFilePath);
            BufferedImage originalImage = ImageIO.read(originalImageFile);

            // 创建一个新的BufferedImage来存放翻转后的图像
            BufferedImage flippedImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    originalImage.getType()
            );

            // 创建一个用于绘制的Graphics2D对象
            Graphics2D g2d = flippedImage.createGraphics();

            // 设置仿射变换以实现垂直翻转
            AffineTransform at = AffineTransform.getScaleInstance(1, -1);
            at.translate(0, -originalImage.getHeight(null));
            g2d.setTransform(at);

            // 将原始图像绘制到带有仿射变换的Graphics2D对象上，实现垂直翻转
            g2d.drawImage(originalImage, 0, 0, null);
            g2d.dispose(); // 释放资源

            // 将翻转后的图像保存到文件
            File flippedImageFile = new File(outputFilePath);
            ImageIO.write(flippedImage, "jpg", flippedImageFile);

            return true;
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常堆栈跟踪信息
            return false;
        }
    }

    /**
     * 从一个区域获得信息覆盖到另一个区域
     * @param originalImage
     * @param sourceX
     * @param sourceY
     * @param sourceWidth
     * @param sourceHeight
     * @param targetX
     * @param targetY
     * @return
     */
    public static BufferedImage cover(BufferedImage originalImage, int sourceX, int sourceY, int sourceWidth, int sourceHeight, int targetX, int targetY) {
        BufferedImage subImage = originalImage.getSubimage(sourceX, sourceY, sourceWidth, sourceHeight);

        Graphics2D g = originalImage.createGraphics();

        g.drawImage(subImage, targetX, targetY, null);

        g.dispose();

        return originalImage;
    }

    /**
     * 根据指定坐标合并两张图像
     *
     * @param leftImage    左图像
     * @param leftPoint1X  左图像上边点 X 坐标
     * @param leftPoint1Y  左图像上边点 Y 坐标
     * @param leftPoint2X  左图像下边点 X 坐标
     * @param leftPoint2Y  左图像下边点 Y 坐标
     * @param rightImage   右图像
     * @param rightPoint1X 右图像上边点 X 坐标
     * @param rightPoint1Y 右图像上边点 Y 坐标
     * @param rightPoint2X 右图像下边点 X 坐标
     * @param rightPoint2Y 右图像下边点 Y 坐标
     * @return
     */
    public static BufferedImage mergeByPointInTwoPic(BufferedImage leftImage, int leftPoint1X, int leftPoint1Y, int leftPoint2X, int leftPoint2Y,
                                                     BufferedImage rightImage, int rightPoint1X, int rightPoint1Y, int rightPoint2X, int rightPoint2Y) {
        Point AP1 = new Point(leftPoint1X, leftPoint1Y);
        Point AP2 = new Point(leftPoint2X, leftPoint2Y);
        Point BP1 = new Point(rightPoint1X, rightPoint1Y);
        Point BP2 = new Point(rightPoint2X, rightPoint2Y);

        // 计算缩放比例
        double distanceA = AP1.distance(AP2);
        double distanceB = BP1.distance(BP2);
        double scale = distanceA / distanceB;

        // 计算旋转角度（夹角）
        double angleA = Math.atan2(AP2.y - AP1.y, AP2.x - AP1.x);
        double angleB = Math.atan2(BP2.y - BP1.y, BP2.x - BP1.x);
        double rotationAngle = angleA - angleB;

        // 对图像B进行缩放和旋转
        BufferedImage transformedImageB = transformImage(rightImage, scale, rotationAngle);

        // 计算图像B的偏移位置，将其基于BP1绘制在图像A上
        int offsetX = AP1.x - BP1.x; // 将图像B移动到与AP1对齐的位置
        int offsetY = AP1.y - BP1.y;

        // 计算合并后的图像的宽度和高度
        int minX = Math.min(0, offsetX); // 考虑图像B偏移后可能超出左边界
        int minY = Math.min(0, offsetY); // 考虑图像B偏移后可能超出上边界
        int maxX = Math.max(leftImage.getWidth(), offsetX + transformedImageB.getWidth());
        int maxY = Math.max(leftImage.getHeight(), offsetY + transformedImageB.getHeight());

        int mergedWidth = maxX - minX;
        int mergedHeight = maxY - minY;

        // 创建合并后的图像
        BufferedImage mergedImage = new BufferedImage(mergedWidth, mergedHeight, BufferedImage.TYPE_INT_RGB);

        // 将图像A绘制到新图像上
        Graphics2D g2d = mergedImage.createGraphics();

        // 首先绘制图像A
        g2d.drawImage(leftImage, -minX, -minY, null);

        // 然后绘制变换后的图像B，考虑偏移量
        g2d.drawImage(transformedImageB, offsetX - minX, offsetY - minY, null);

        g2d.dispose();

        return mergedImage;
    }

    /**
     * 创建一个指定宽高的图像，并在指定区域内添加不同的颜色
     *
     * @param width 图像的宽度
     * @param height 图像的高度
     * @param regions 一个二维数组，每个子数组包含四个元素：x, y, width, height（区域的左上角坐标和宽高）
     *                以及第五个元素：颜色值（例如：Color.RED.getRGB()）
     * @return 创建的BufferedImage对象
     */
    public static BufferedImage createImageWithRegions(int width, int height, Color backColor, int[][] regions) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(backColor);
        g2d.fillRect(0, 0, width, height);

        // 填充指定区域的颜色
        for (int[] region : regions) {
            int x = region[0];
            int y = region[1];
            int regionWidth = region[2];
            int regionHeight = region[3];
            int color = region[4];

            g2d.setColor(new Color(color, true));
            g2d.fillRect(x, y, regionWidth, regionHeight);
        }

        g2d.dispose();
        return image;
    }

    /**
     * 缩放和旋转图像
     *
     * @param image
     * @param scale
     * @param rotationAngle
     * @return
     */
    private static BufferedImage transformImage(BufferedImage image, double scale, double rotationAngle) {
        int width = image.getWidth();
        int height = image.getHeight();

        AffineTransform transform = new AffineTransform();
        transform.scale(scale, scale);
        transform.rotate(rotationAngle, width / 2.0, height / 2.0);

        BufferedImage transformedImage = new BufferedImage((int) (width * scale), (int) (height * scale), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = transformedImage.createGraphics();
        g2d.setTransform(transform);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        return transformedImage;
    }

    /**
     * base64ToImage
     * @param base64String
     * @param outputFilePath
     * @throws IOException
     */
    public static void base64ToImage(String base64String, String outputFilePath) throws IOException {
        String base64Data = base64String;
        // 移除Base64字符串的前缀（如果有的话）
        if(base64String.contains(",")) {
            base64Data = base64String.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        Files.write(Paths.get(outputFilePath), imageBytes);
    }

}
