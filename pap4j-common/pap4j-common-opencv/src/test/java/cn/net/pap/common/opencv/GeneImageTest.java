package cn.net.pap.common.opencv;

import cn.net.pap.common.opencv.jpeg.JpegDPIProcessor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneImageTest {

    @Test
    void testGenerateImageProcess() throws IOException {
        // 在方法前面指定参数
        int width = 8290;
        int height = 7138;
        int dpi = 600;
        int number = 1;
        String fileNamePrefix = "Sample";

        // 创建临时文件路径（生成在系统临时目录，指定前缀和后缀）
        Path tempFilePath = Files.createTempFile(fileNamePrefix + "-" + String.format("%04d", number) + "-", ".jpg");

        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // 设置背景颜色
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, width, height);

            // 设置文本颜色
            g2d.setColor(Color.WHITE);
            // 字体大小根据高度动态调整（或保持硬编码 3000）
            Font font = new Font("Arial", Font.PLAIN, 3000);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int x = (width - fm.stringWidth(String.valueOf(number))) / 2;
            int y = (height + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(String.valueOf(number), x, y);

            g2d.dispose();

            // 使用 JpegDPIProcessor 处理并写入临时文件
            JpegDPIProcessor processor = new JpegDPIProcessor();
            byte[] imgData = processor.setDPI(image, dpi);

            Files.write(tempFilePath, imgData);

            // 打印生成信息
            System.out.println("临时图片已生成: " + tempFilePath.toAbsolutePath());

            // 验证文件是否存在
            assertTrue(Files.exists(tempFilePath), "文件应该生成成功");

        } finally {
            // 执行完删除
            Files.deleteIfExists(tempFilePath);
            System.out.println("临时文件已清理。");
        }
    }

    /**
     * 在指定文件夹按照传入数字生成图像，可循环生成多张图像
     * @param fileDirPath
     * @param number
     */
    private static void generateImage(String fileDirPath, int number, String fileNamePrefix) {
        int width = 8000; // 图片宽度
        int height = 8000; // 图片高度
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 设置背景颜色
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);

        // 设置文本颜色
        g2d.setColor(Color.WHITE);
        Font font = new Font("Arial", Font.PLAIN, 3000); // 设置字体和大小
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(String.valueOf(number))) / 2; // 计算文本的水平位置
        int y = (height + fm.getAscent() - fm.getDescent()) / 2; // 计算文本的垂直位置
        g2d.drawString(String.valueOf(number), x, y); // 绘制文本

        g2d.dispose(); // 释放资源

        try {
            // 将图片写入文件
            String newFileName = fileNamePrefix + "-" + String.format("%04d", number) + "-00.jpg";

            JpegDPIProcessor processor = new JpegDPIProcessor();
            byte[] img = processor.setDPI(image, 300);
            try (FileOutputStream outputStream = new FileOutputStream(fileDirPath + newFileName);){
                outputStream.write(img);
            }
            System.out.println("Image " + number + " has been generated.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void generateImageTest() {
        String basePath = "C:\\Users\\86181\\Desktop\\";
        for(int dirIdx = 0; dirIdx < 1; dirIdx++) {
            String detailPath = "tmp\\" + String.format("%04d", dirIdx);
            String basePath2 = basePath + detailPath;
            new File(basePath2).mkdirs();
            for(int idx = 0; idx < 60000; idx++) {
                generateImage(basePath2 + "\\", idx, String.format("%04d", dirIdx));
            }
        }

    }

    // @Test
    public void generateImageTest2() throws IOException {
        String basePath = "D:\\knowledge\\";
        List<String> paths = Files.readAllLines(Paths.get("C:\\Users\\86181\\Desktop\\filename.txt"));
        for(String path : paths) {
            generateEmptyJpeg(basePath + path);
        }
    }

    public static void generateEmptyJpeg(String outputPath) {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xFFFFFF);
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            ImageIO.write(image, "jpg", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void test() {
        Path sourceFilePath = Paths.get("");
        Path destinationFolderPath = Paths.get("");
        try {
            // 确保目标文件夹存在
            Files.createDirectories(destinationFolderPath);

            // 遍历并创建1000张图片
            for (int i = 1; i <= 1000; i++) {
                // 创建新的文件名
                String newFileName = String.format("%06d.jpg", i);
                Path newFilePath = destinationFolderPath.resolve(newFileName);

                // 拷贝文件
                Files.copy(sourceFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("Copied " + newFileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void geneAndCheckImageTest() throws Exception {
        createImageWithDPI("C:\\Users\\86181\\Desktop\\geneAndCheckImageTest.jpg", 1, 300);
        Integer dpi = getDPI("C:\\Users\\86181\\Desktop\\geneAndCheckImageTest.jpg");
        System.out.println(dpi);
    }

    /**
     * 引入 org.apache.sanselan.sanselan 后，获得原始图像的 DPI
     * @param imageAbsPath
     * @return
     * @throws Exception
     */
    private static Integer getDPI(String imageAbsPath) throws Exception {
        org.apache.sanselan.ImageInfo imageInfo = org.apache.sanselan.Sanselan.getImageInfo(new File(imageAbsPath));
        int physicalWidthDpi = imageInfo.getPhysicalWidthDpi();
        int physicalHeightDpi = imageInfo.getPhysicalHeightDpi();
        return Math.max(physicalWidthDpi, physicalHeightDpi);
    }

    private static void createImageWithDPI(String imagePath, int number, int dpi) throws Exception {
        int width = 800;
        int height = 800;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = image.createGraphics();

        // 设置背景颜色
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // 设置文本颜色
        g2d.setColor(Color.BLACK);
        Font font = new Font("Arial", Font.PLAIN, 500); // 设置字体和大小
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(String.valueOf(number))) / 2; // 计算文本的水平位置
        int y = (height + fm.getAscent() - fm.getDescent()) / 2; // 计算文本的垂直位置
        g2d.drawString(String.valueOf(number), x, y); // 绘制文本

        g2d.dispose(); // 释放资源

        JpegDPIProcessor processor = new JpegDPIProcessor();
        byte[] img = processor.setDPI(image,dpi);
        //将img字节写到本地
        try (FileOutputStream outputStream = new FileOutputStream(imagePath);){
            outputStream.write(img);
        }
    }

    /**
     * 文字生成
     * @param filePath
     * @param chineseCharacter
     */
    public static void generateTextImage(String filePath, String chineseCharacter) {
        // Image dimensions
        int width = 100;
        int height = 100;

        // Create a buffered image in which to draw
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Create a graphics context on the buffered image
        Graphics2D g2d = bufferedImage.createGraphics();

        // Set background color to white and fill the image with it
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Set the font and color
        g2d.setFont(new Font("Serif", Font.BOLD, 50));
        g2d.setColor(Color.BLACK);

        // Get font metrics to calculate the positioning of the text
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int stringWidth = fontMetrics.stringWidth(chineseCharacter);
        int stringHeight = fontMetrics.getAscent();

        // Calculate the position to start drawing the text
        int x = (width - stringWidth) / 2;
        int y = (height + stringHeight) / 2;

        // Draw the string
        g2d.drawString(chineseCharacter, x, y);

        // Dispose of the graphics context and release any system resources
        g2d.dispose();

        // Write the image to a file
        try {
            ImageIO.write(bufferedImage, "jpg", new File(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void geneTextTest() {
        String basePath = "C:\\Users\\86181\\Desktop\\dir";
        int idx = 0;
        for(char c = '\u4E00'; c <= '\u9FA5'; c++) {
            generateTextImage(basePath + File.separator + String.valueOf(c) + ".jpg", String.valueOf(c));
            idx++;
            if(idx > 1000) {
                break;
            }
        }

    }

    // @Test
    public void copyAndReNameTest() {
        try {
            String outputDir = "C:\\Users\\86181\\Desktop\\dir\\";
            BufferedImage originalImage = ImageIO.read(new File("input.jpg"));
            for (int i = 1; i <= 5; i++) {
                String outputImagePath = outputDir + String.format("%06d.jpg", i);
                ImageIO.write(originalImage, "jpg", new File(outputImagePath));
            }
        } catch (IOException e) {

        }
    }
}
