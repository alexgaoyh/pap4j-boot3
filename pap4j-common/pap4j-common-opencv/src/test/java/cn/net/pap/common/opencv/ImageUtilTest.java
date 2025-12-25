package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageUtilTest {

    //@Test
    public void imageioTest() throws IOException {
        List<String> formatList = new ArrayList<>();
        for (String format : ImageIO.getReaderFormatNames()) {
            formatList.add(format);
        }

        List<String> mimeList = new ArrayList<>();
        for (String mime : ImageIO.getReaderMIMETypes()) {
            String spiClass = "";
            Iterator<ImageReader> imageReadersByMIMEType = ImageIO.getImageReadersByMIMEType(mime);
            while (imageReadersByMIMEType.hasNext()) {
                ImageReader spi = imageReadersByMIMEType.next();
                spiClass = spiClass + spi.getClass().getName() + " ; ";
            }
            mimeList.add(mime + " : " + spiClass);
        }

        boolean canReadJpeg2000 = false;
        for (String format : ImageIO.getReaderFormatNames()) {
            if ("JPEG2000".equalsIgnoreCase(format)) {
                canReadJpeg2000 = true;
                break;
            }
        }
        if(canReadJpeg2000 == false) {
//            com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi j2KImageReaderSpi = new com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi();
//            IIORegistry registry = IIORegistry.getDefaultInstance();
//            registry.registerServiceProvider(j2KImageReaderSpi);
        }
        ImageIO.scanForPlugins();

        for (String format : ImageIO.getReaderFormatNames()) {
            formatList.add(format);
        }

    }

    //@Test
    public void scaleAndGrayTest() {
        File file = new File("\\Images");
        File[] files = file.listFiles();
        for(File file1 : files) {
            boolean b = ImageUtil.scaleAndGray(file1.getPath(), "\\after\\" + file1.getName(), 100);
            System.out.println(b);
        }
    }

    // @Test
    public void cropImageCutListTest() {
        List<Rectangle> regions = new ArrayList<>();
        Rectangle rect1 = new Rectangle(000, 000, 050, 050);
        Rectangle rect2 = new Rectangle(050, 050, 050, 050);
        Rectangle rect3 = new Rectangle(100, 100, 050, 050);
        Rectangle rect4 = new Rectangle(150, 150, 050, 050);
        regions.add(rect1);
        regions.add(rect2);
        regions.add(rect3);
        regions.add(rect4);

        List<String> base64s = ImageUtil.cropImageCutList("input.jpg", regions);
        assertTrue(base64s.size() == regions.size());
    }

    // @Test
    public void mergeImagesTest() throws Exception {
        BufferedImage bufferedImage = ImageUtil.mergeImages("1.png",
                "2.png",
                0, 0, 0, 100);
        ImageIO.write(bufferedImage, "png", new File("out.png"));
    }

    // @Test
    public void rotateImageTest() throws Exception {
        boolean b = ImageUtil.rotateImage("C:\\Users\\86181\\Desktop\\origin.jpg",
                "C:\\Users\\86181\\Desktop\\123456.jpg",
                10);
        System.out.println(b);
    }

    // @Test
    public void coverTest() throws Exception {
        BufferedImage originalImage = ImageIO.read(new File("C:\\Users\\86181\\Desktop\\origin.jpg"));
        BufferedImage targetImage = ImageUtil.cover(originalImage, 100, 100, 100, 100, 0, 0);
        ImageIO.write(targetImage, "jpg", new File("C:\\Users\\86181\\Desktop\\out.png"));
    }

    // @Test
    public void mergeByPointInTwoPicTest() throws Exception {
        BufferedImage leftImage = ImageIO.read(new File("C:\\Users\\86181\\Desktop\\100.jpg"));
        BufferedImage rightImage = ImageIO.read(new File("C:\\Users\\86181\\Desktop\\1002.jpg"));

        BufferedImage mergedImage = ImageUtil.mergeByPointInTwoPic(leftImage, 100, 0, 100, 100,
                rightImage, 0, 0, 0, 100);
        ImageIO.write(mergedImage, "jpg", new File("C:\\Users\\86181\\Desktop\\out.jpg"));

    }

    // @Test
    public void createImageWithRegionsTest() throws Exception {
        int width = 5432;
        int height = 9967;

        int[][] regions = {
                {0, 0, 200, 200, Color.RED.getRGB()},
                {0, 200, 200, 200, Color.GREEN.getRGB()}
        };

        BufferedImage image = ImageUtil.createImageWithRegions(width, height, Color.YELLOW, regions);
        ImageIO.write(image, "jpg", new File("C:\\Users\\86181\\Desktop\\regions.jpg"));

    }

    // @Test
    public void base64ToImageTest() throws Exception {
        String content = new String(java.nio.file.Files.readAllBytes(Paths.get("C:\\Users\\86181\\Desktop\\base64.txt")));
        ImageUtil.base64ToImage(content, "C:\\Users\\86181\\Desktop\\base64.jpg");
    }

    // @Test
    public void rotate90ClockwiseAndOverwriteTest() throws Exception {
        ImageUtil.rotate90ClockwiseAndOverwrite(new File("C:\\Users\\86181\\Desktop\\0.jpg"));
    }

    // @Test
    public void pointTest() {
        try {
            // 1. 读取两张图像（替换为你的图像路径）
            BufferedImage imageA = ImageIO.read(new File("C:\\Users\\86181\\Desktop\\left.jpg"));
            BufferedImage imageB = ImageIO.read(new File("C:\\Users\\86181\\Desktop\\right.jpg"));

            // 2. 定义对应的点（替换为你的坐标）
            Point a1 = new Point(641, 0); // 图像A中的点a1
            Point a2 = new Point(641, 424); // 图像A中的点a2
            Point b1 = new Point(100, 0);   // 图像B中的点b1
            Point b2 = new Point(100, 424); // 图像B中的点b2

            // 3. 计算变换参数
            AffineTransform transform = calculateTransform(a1, a2, b1, b2);

            // 4. 对图像B应用变换
            BufferedImage transformedB = transformImage(imageB, transform);

            // 5. 拼接图像A和变换后的图像B
            BufferedImage result = stitchImages(imageA, transformedB);

            // 6. 保存结果
            ImageIO.write(result, "jpg", new File("C:\\Users\\86181\\Desktop\\result.jpg"));
            System.out.println("拼接完成！");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 计算仿射变换矩阵
    private static AffineTransform calculateTransform(Point a1, Point a2, Point b1, Point b2) {
        // 计算向量A和B
        double vAx = a2.x - a1.x;
        double vAy = a2.y - a1.y;
        double vBx = b2.x - b1.x;
        double vBy = b2.y - b1.y;

        // 计算缩放因子
        double scale = Math.sqrt(vAx * vAx + vAy * vAy) / Math.sqrt(vBx * vBx + vBy * vBy);

        // 计算旋转角度（弧度）
        double angleA = Math.atan2(vAy, vAx);
        double angleB = Math.atan2(vBy, vBx);
        double angle = angleA - angleB;

        // 构造变换矩阵：先平移b1到原点，再缩放旋转，最后平移到a1
        AffineTransform transform = new AffineTransform();
        transform.translate(a1.x, a1.y);      // 平移至a1
        transform.rotate(angle);               // 旋转
        transform.scale(scale, scale);        // 缩放
        transform.translate(-b1.x, -b1.y);     // 平移b1到原点

        return transform;
    }

    // 对图像应用仿射变换
    private static BufferedImage transformImage(BufferedImage image, AffineTransform transform) {
        // 计算变换后的图像边界（可能含负坐标）
        Rectangle originalBounds = new Rectangle(image.getWidth(), image.getHeight());
        Rectangle transformedBounds = transform.createTransformedShape(originalBounds).getBounds();

        // 创建目标图像（确保宽度/高度为正）
        int width = Math.max(1, transformedBounds.width);
        int height = Math.max(1, transformedBounds.height);
        BufferedImage transformed = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 调整变换矩阵：将图像平移到 (0, 0) 起始点
        AffineTransform adjustedTransform = new AffineTransform(transform);
        adjustedTransform.translate(-transformedBounds.x, -transformedBounds.y);

        // 绘制图像
        Graphics2D g = transformed.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, adjustedTransform, null);
        g.dispose();

        return transformed;
    }

    // 拼接两张图像
    private static BufferedImage stitchImages(BufferedImage imageA, BufferedImage imageB) {
        // 计算拼接后的总宽度和高度
        int width = imageA.getWidth() + imageB.getWidth();
        int height = Math.max(imageA.getHeight(), imageB.getHeight());

        // 创建画布（支持透明度）
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();

        // 绘制图像A（左侧）
        g.drawImage(imageA, 0, 0, null);

        // 绘制变换后的图像B（右侧）
        g.drawImage(imageB, imageA.getWidth(), 0, null);
        g.dispose();

        return result;
    }

    /**
     * ImageMagick 仿射变换
     */
    @Test
    public void magickAffineProjectionTest() {
        Point a1 = new Point(0, 0);
        Point a2 = new Point(0, 424);
        Point a3 = generateThirdPoint(a1, a2);

        Point b1 = new Point(0, 0);
        Point b2 = new Point(100, 424);
        Point b3 = generateThirdPoint(b1, b2);

        System.out.println(String.format("\"%d,%d\"", a3.x, a3.y));
        System.out.println(String.format("\"%d,%d\"", b3.x, b3.y));

        System.out.println(String.format("\"%d,%d %d,%d %d,%d %d,%d %d,%d %d,%d\"", a1.x, a1.y, b1.x, b1.y, a2.x, a2.y, b2.x, b2.y, a3.x, a3.y, b3.x, b3.y));

        System.out.println(String.format("\"%d,%d %d,%d %d,%d %d,%d %d,%d %d,%d\"", a1.x, a1.y, b1.x, b1.y, a2.x, a2.y, b2.x, b2.y, a3.x, a3.y, b3.x, b3.y));

        ProcessBuilder magickCommand = new ProcessBuilder(
                "magick", "right.jpg", "-distort", "Affine",
                String.format("\" %d,%d %d,%d %d,%d %d,%d %d,%d %d,%d \"", a1.x, a1.y, b1.x, b1.y, a2.x, a2.y, b2.x, b2.y, a3.x, a3.y, b3.x, b3.y), "transformedB.jpg"
        );
        String commandStr = String.join(" ", magickCommand.command());
        System.out.println(commandStr);

        // 一个拼接命令 需要首先计算出来拼接后的图像的大小，然后映射点的话，只取一组映射点的 X坐标，然后设置一个偏移，即可完成拼接. 需要动态算出来图像的尺寸.
        // magick -size 2000x2000 canvas:none ( transformedB.jpg -geometry +0+0 ) -composite ( left.jpg -geometry +600+0 ) -composite output.jpg

    }

    private static double[] computeAffineMatrix(Point b1, Point b2, Point a1, Point a2) {
        // 平移量
        double dx = a1.x - b1.x;
        double dy = a1.y - b1.y;

        // 向量差
        double bx = b2.x - b1.x;
        double by = b2.y - b1.y;
        double ax = a2.x - a1.x;
        double ay = a2.y - a1.y;

        // 缩放 + 旋转
        double scale = Math.hypot(ax, ay) / Math.hypot(bx, by);
        double angleA = Math.atan2(ay, ax);
        double angleB = Math.atan2(by, bx);
        double theta = angleA - angleB;

        double cos = Math.cos(theta) * scale;
        double sin = Math.sin(theta) * scale;

        // 构造仿射矩阵：[cos -sin dx] [sin cos dy]
        return new double[]{
                cos, sin,
                -sin, cos,
                a1.x - (cos * b1.x - sin * b1.y),
                a1.y - (sin * b1.x + cos * b1.y)
        };
    }

    // @Test
    public void generateThirdPointTest() {
        Point p1 = new Point(100, 100);
        Point p2 = new Point(300, 100);

        Point p3 = generateThirdPoint(p1, p2);

        System.out.printf("第三个点为：(%d, %d)%n", p3.x, p3.y);
    }

    /**
     * 测试图像是 36.2MB 的 JPG， 如下单元测试先预热，然后执行进行验证。
     * 使用 jconsole 来监控下面的单元测试，观察堆内存使用量，也能看出来区别。
     * 	1、先执行5次基于采用的缩略图
     * 	2、再执行5次普通的缩略图：原图图像读到内存，然后缩放绘制
     * 	3、最后是预热完毕后的时长和内存的统计
     * 基于采样的输出是 23024 bytes 和 10756 ms
     * 基于默认的输出是 26080 bytes 和 17678 ms
     * 内存节省 12%， 执行时间提升 39%
     * @throws Exception
     */
    // @Test
    public void bufferedImageCompareTest1() throws Exception {
        // 预热一下
        for(int i = 0; i < 5; i++) {
            BufferedImage lowMemoryThumbnail = ImageUtil.getLowMemoryThumbnail("D:\\knowledge\\big-plane-yes.jpg", 100);
        }
        for(int i = 0; i < 5; i++) {
            BufferedImage scaleImage = ImageUtil.scaleImage("D:\\knowledge\\big-plane-yes.jpg", 100);
        }

        long l = System.currentTimeMillis();
        BufferedImage lowMemoryThumbnail = ImageUtil.getLowMemoryThumbnail("D:\\knowledge\\big-plane-yes.jpg", 100);
        long l1 = System.currentTimeMillis();
        System.out.println("Total size: " + org.openjdk.jol.info.GraphLayout.parseInstance(lowMemoryThumbnail).totalSize() + " bytes");
        System.out.println(l1 - l);

        long l2 = System.currentTimeMillis();
        BufferedImage scaleImage = ImageUtil.scaleImage("D:\\knowledge\\big-plane-yes.jpg", 100);
        long l3 = System.currentTimeMillis();
        System.out.println("Total size: " + org.openjdk.jol.info.GraphLayout.parseInstance(scaleImage).totalSize() + " bytes");
        System.out.println(l3 - l2);
    }

    /**
     * 根据两个点生成第三个点，构成仿射三角形
     * 方向为：从 p1 到 p2 的向量垂直旋转 90°
     */
    public static Point generateThirdPoint(Point p1, Point p2) {
        int dx = p2.x - p1.x;
        int dy = p2.y - p1.y;
        // 垂直向量 (逆时针 90° 旋转): (-dy, dx)
        int x3 = p1.x - dy;
        int y3 = p1.y + dx;
        return new Point(x3, y3);
    }

}
