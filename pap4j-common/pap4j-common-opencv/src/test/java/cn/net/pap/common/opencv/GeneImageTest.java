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

public class GeneImageTest {

    /**
     * 在指定文件夹按照传入数字生成图像，可循环生成多张图像
     * @param fileDirPath
     * @param number
     */
    private static void generateImage(String fileDirPath, int number) {
        int width = 8000; // 图片宽度
        int height = 8000; // 图片高度
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // 设置背景颜色
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);

        // 设置文本颜色
        g2d.setColor(Color.WHITE);
        Font font = new Font("Arial", Font.PLAIN, 500); // 设置字体和大小
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int x = (width - fm.stringWidth(String.valueOf(number))) / 2; // 计算文本的水平位置
        int y = (height + fm.getAscent() - fm.getDescent()) / 2; // 计算文本的垂直位置
        g2d.drawString(String.valueOf(number), x, y); // 绘制文本

        g2d.dispose(); // 释放资源

        try {
            // 将图片写入文件
            String newFileName = String.format("%06d.jpg", number);
            File file = new File(fileDirPath + newFileName);
            ImageIO.write(image, "jpg", file);
            System.out.println("Image " + number + " has been generated.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void generateImageTest() {
        String basePath = "";
        for(int idx = 0; idx < 2000; idx++) {
            generateImage(basePath + "\\dir\\", idx);
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

    @Test
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

}
