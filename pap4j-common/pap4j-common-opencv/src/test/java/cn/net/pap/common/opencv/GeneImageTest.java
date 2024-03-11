package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
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

}
