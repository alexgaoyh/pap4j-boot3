package cn.net.pap.common.opencv;

import cn.net.pap.common.opencv.jpeg.JpegDPIProcessor;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneImageTest {

    private static final int SIZE = 200;
    private static final int FONT_SIZE = 120;
    private static final String[] CHARACTERS = {"一", "丶", "冖", "丷", "冫", "乛", "亠", "亻", "丿", "亅"};

    @Test
    void generateImages() throws IOException {
        Font font = new Font("微软雅黑", Font.PLAIN, FONT_SIZE);
        // 如果微软雅黑不存在，使用系统默认字体
        if (!font.canDisplay('中')) {
            font = new Font("宋体", Font.PLAIN, FONT_SIZE);
        }

        File dir = Files.createTempDirectory("generateImages").toFile();
        dir.mkdirs();

        try {
            for (String ch : CHARACTERS) {
                BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();

                // 白色背景
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, SIZE, SIZE);

                // 黑色文字
                g.setColor(Color.BLACK);
                g.setFont(font);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 居中绘制
                FontMetrics fm = g.getFontMetrics();
                int x = (SIZE - fm.stringWidth(ch)) / 2;
                int y = (SIZE - fm.getHeight()) / 2 + fm.getAscent();
                g.drawString(ch, x, y);

                g.dispose();

                File output = new File(dir, ch + ".jpg");
                ImageIO.write(image, "jpg", output);
                assertTrue(output.exists(), "文件应成功创建: " + output.getName());
                System.out.println("已生成: " + output.getAbsolutePath());
            }
        } finally {
            Files.walkFileTree(Paths.get(dir.getAbsolutePath().toString()), new SimpleFileVisitor<Path>() {
                // 先删除文件
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                // 再删除文件夹（此时文件夹已为空）
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

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

    @Test
    public void generateImageTest() throws Exception {
        // 保持为 Path 对象，方便后续操作
        Path basePath = Files.createTempDirectory("generateImageTest");
        try {
            for(int dirIdx = 0; dirIdx < 1; dirIdx++) {
                // 使用 resolve 自动处理路径分隔符
                Path detailPath = basePath.resolve("tmp").resolve(String.format("%04d", dirIdx));
                // 使用 NIO API 创建多级目录
                Files.createDirectories(detailPath);

                for(int idx = 0; idx < 1; idx++) {
                    // 确保传递给生成方法的路径是正确的，如果 generateImage 需要尾部斜杠，手动加上 File.separator
                    generateImage(detailPath.toString() + File.separator, idx, String.format("%04d", dirIdx));
                }
            }
        } finally {
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Test
    public void generateImageTest2() throws IOException {
        Path basePath = Files.createTempDirectory("generateImageTest");
        try {
            List<String> paths = Files.readAllLines(Paths.get(TestResourceUtil.getFile("filename.txt").getAbsolutePath()));
            for(String path : paths) {
                // resolve 会安全地将 path 追加到 basePath 后面
                // 即使 path 是 "a.jpg" 或 "/a.jpg"，都能正确生成 /temp/.../a.jpg
                Path targetFile = basePath.resolve(path);
                generateEmptyJpeg(targetFile.toString());
            }
        } finally {
            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
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

    @Test
    public void test() throws Exception {
        Path sourceFilePath = TestResourceUtil.getFile("0.jpg").toPath();
        Path destinationFolderPath = Files.createTempDirectory("test");
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
        } finally {
            Files.walkFileTree(destinationFolderPath, new SimpleFileVisitor<Path>() {
                // 先删除文件
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                // 再删除文件夹（此时文件夹已为空）
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Test
    public void geneAndCheckImageTest() throws Exception {
        File file = TestResourceUtil.getFile("1.jpg");
        createImageWithDPI(file.getAbsolutePath(), 1, 300);
        Integer dpi = getDPI(file.getAbsolutePath());
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

    @Test
    public void geneTextTest() throws Exception {
        String basePath = Files.createTempDirectory("test").toAbsolutePath().toString();
        try {
            int idx = 0;
            for(char c = '\u4E00'; c <= '\u4E00' + 10; c++) {
                generateTextImage(basePath + File.separator + String.valueOf(c) + ".jpg", String.valueOf(c));
                idx++;
                if(idx > 1000) {
                    break;
                }
            }
        } finally {
            Files.walkFileTree(Paths.get(basePath), new SimpleFileVisitor<Path>() {
                // 先删除文件
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                // 再删除文件夹（此时文件夹已为空）
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

    }

    @Test
    public void copyAndReNameTest() {
        String outputDir = null;
        try {
            outputDir = Files.createTempDirectory("test").toAbsolutePath().toString() + File.separator;
            BufferedImage originalImage = ImageIO.read(TestResourceUtil.getFile("0.jpg"));
            for (int i = 1; i <= 5; i++) {
                String outputImagePath = outputDir + String.format("%06d.jpg", i);
                ImageIO.write(originalImage, "jpg", new File(outputImagePath));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputDir != null) {
                try {
                    Files.walkFileTree(Paths.get(outputDir), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
