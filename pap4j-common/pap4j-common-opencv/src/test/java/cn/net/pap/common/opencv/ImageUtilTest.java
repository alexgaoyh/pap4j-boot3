package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.PixelInterleavedSampleModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageUtilTest {

    @Test
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

    @Test
    public void scaleAndGrayTest() throws Exception {
        File origin = TestResourceUtil.getFile("origin.jpg");
        File afterOut = TestResourceUtil.createTempFile("after", ".jpg");
        afterOut.deleteOnExit();
        boolean b = ImageUtil.scaleAndGray(origin.getAbsolutePath(), afterOut.getAbsolutePath(), 100);
        System.out.println(b);
    }

    @Test
    public void cropImageCutListTest() {
        List<Rectangle> regions = new ArrayList<>();
        Rectangle rect1 = new Rectangle(0, 0, 25, 25);
        Rectangle rect2 = new Rectangle(25, 25, 25, 25);
        Rectangle rect3 = new Rectangle(50, 50, 25, 25);
        Rectangle rect4 = new Rectangle(75, 75, 25, 25);
        regions.add(rect1);
        regions.add(rect2);
        regions.add(rect3);
        regions.add(rect4);

        List<String> base64s = ImageUtil.cropImageCutList(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(), regions);
        assertTrue(base64s.size() == regions.size());
    }

    @Test
    public void mergeImagesTest() throws Exception {
        BufferedImage bufferedImage = ImageUtil.mergeImages(TestResourceUtil.getFile("left.jpg").getAbsolutePath(),
                TestResourceUtil.getFile("right.jpg").getAbsolutePath(),
                0, 0, 0, 100);
        ImageIO.write(bufferedImage, "jpg", TestResourceUtil.createTempFile("out", ".jpg"));
    }

    @Test
    public void rotateImageTest() throws Exception {
        boolean b = ImageUtil.rotateImage(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(),
                TestResourceUtil.createTempFile("123456", ".jpg").getAbsolutePath(),
                10);
        System.out.println(b);
    }

    @Test
    public void coverTest() throws Exception {
        BufferedImage originalImage = ImageIO.read(TestResourceUtil.getFile("origin.jpg"));
        BufferedImage targetImage = ImageUtil.cover(originalImage, 10, 10, 10, 10, 0, 0);
        ImageIO.write(targetImage, "jpg", TestResourceUtil.createTempFile("out", ".jpg"));
    }

    @Test
    public void mergeByPointInTwoPicTest() throws Exception {
        BufferedImage leftImage = ImageIO.read(TestResourceUtil.getFile("100.jpg"));
        BufferedImage rightImage = ImageIO.read(TestResourceUtil.getFile("1002.jpg"));

        BufferedImage mergedImage = ImageUtil.mergeByPointInTwoPic(leftImage, 100, 0, 100, 100,
                rightImage, 0, 0, 0, 100);
        ImageIO.write(mergedImage, "jpg", TestResourceUtil.createTempFile("out", ".jpg"));

    }

    @Test
    public void createImageWithRegionsTest() throws Exception {
        int width = 5432;
        int height = 9967;

        int[][] regions = {
                {0, 0, 200, 200, Color.RED.getRGB()},
                {0, 200, 200, 200, Color.GREEN.getRGB()}
        };

        BufferedImage image = ImageUtil.createImageWithRegions(width, height, Color.YELLOW, regions);
        ImageIO.write(image, "jpg", TestResourceUtil.createTempFile("regions", ".jpg"));

    }

    @Test
    public void base64ToImageTest() throws Exception {
        String content = new String(java.nio.file.Files.readAllBytes(Paths.get(TestResourceUtil.getFile("base64.txt").getAbsolutePath())));
        ImageUtil.base64ToImage(content, TestResourceUtil.createTempFile("base64", ".jpg").getAbsolutePath());
    }

    @Test
    public void rotate90ClockwiseAndOverwriteTest() throws Exception {
        ImageUtil.rotate90ClockwiseAndOverwrite(TestResourceUtil.getFile("0.jpg"));
    }

    @Test
    public void pointTest() {
        try {
            // 1. 读取两张图像（替换为你的图像路径）
            BufferedImage imageA = ImageIO.read(TestResourceUtil.getFile("left.jpg"));
            BufferedImage imageB = ImageIO.read(TestResourceUtil.getFile("right.jpg"));

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
            ImageIO.write(result, "jpg", TestResourceUtil.createTempFile("result", ".jpg"));
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

        List<String> command = Arrays.asList(
                "magick", "right.jpg", "-distort", "Affine",
                String.format("\" %d,%d %d,%d %d,%d %d,%d %d,%d %d,%d \"", a1.x, a1.y, b1.x, b1.y, a2.x, a2.y, b2.x, b2.y, a3.x, a3.y, b3.x, b3.y), "transformedB.jpg"
        );
        String commandStr = String.join(" ", command);
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

    @Test
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
     * 内存节省 12%， 执行时间提升 39%，并且前者在执行过程中堆的占用相对稳定
     *  后者比前者多出来的内存开销，怀疑几乎全是 对象头、渲染缓存引用和内部关联对象 的重量。因为 Graphics2D 的操作改变了 BufferedImage 内部的状态，导致它关联了一系列复杂的“支撑对象”
     *  Graphics2D 本身是一个临时对象（你调用了 dispose()），但它在 BufferedImage 内部留下的“足迹”（缓存和状态对象）是持久存在的。
     * @throws Exception
     */
    @Test
    public void bufferedImageCompareTest1() throws Exception {
        // 预热一下
        for(int i = 0; i < 5; i++) {
            BufferedImage lowMemoryThumbnail = ImageUtil.getLowMemoryThumbnail(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(), 100);
        }
        for(int i = 0; i < 5; i++) {
            BufferedImage scaleImage = ImageUtil.scaleImage(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(), 100);
        }

        long l = System.currentTimeMillis();
        BufferedImage lowMemoryThumbnail = ImageUtil.getLowMemoryThumbnail(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(), 100);
        long l1 = System.currentTimeMillis();
        System.out.println("Total size: " + org.openjdk.jol.info.GraphLayout.parseInstance(lowMemoryThumbnail).totalSize() + " bytes");
        System.out.println(l1 - l);

        long l2 = System.currentTimeMillis();
        BufferedImage scaleImage = ImageUtil.scaleImage(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(), 100);
        long l3 = System.currentTimeMillis();
        System.out.println("Total size: " + org.openjdk.jol.info.GraphLayout.parseInstance(scaleImage).totalSize() + " bytes");
        System.out.println(l3 - l2);

        System.out.println("=== 方法1 (getLowMemoryThumbnail) 内存详情 ===");
        System.out.println(org.openjdk.jol.info.GraphLayout.parseInstance(lowMemoryThumbnail).toFootprint());

        System.out.println("\n=== 方法2 (scaleImage) 内存详情 ===");
        System.out.println(org.openjdk.jol.info.GraphLayout.parseInstance(scaleImage).toFootprint());

        BufferedImage img1 = lowMemoryThumbnail;
        BufferedImage img2 = scaleImage;
        System.out.println("=== 方法1: getLowMemoryThumbnail ===");
        System.out.println("类型: " + img1.getType() + " (" + getTypeName(img1.getType()) + ")");
        System.out.println("色彩模型: " + img1.getColorModel().getClass().getName());
        System.out.println("像素位数: " + img1.getColorModel().getPixelSize());
        System.out.println("Raster: " + img1.getRaster().getClass().getName());

        System.out.println("\n=== 方法2: scaleImage ===");
        System.out.println("类型: " + img2.getType() + " (" + getTypeName(img2.getType()) + ")");
        System.out.println("色彩模型: " + img2.getColorModel().getClass().getName());
        System.out.println("像素位数: " + img2.getColorModel().getPixelSize());
        System.out.println("Raster: " + img2.getRaster().getClass().getName());

        // 检查是否真的相同
        System.out.println("\n=== 实际内存差异 ===");
        System.out.println("img1.equals(img2): " + img1.equals(img2));
        System.out.println("img1.getType() == img2.getType(): " + (img1.getType() == img2.getType()));

        // 手动检查像素数据
        System.out.println("\n=== 像素数据格式 ===");
        System.out.println("img1 SampleModel: " + img1.getSampleModel().getClass().getName());
        System.out.println("img2 SampleModel: " + img2.getSampleModel().getClass().getName());

        // 添加以下代码来检查详细信息
        System.out.println("\n=== 详细比较 ===");
        System.out.println("img1 Raster: " + img1.getRaster());
        System.out.println("img2 Raster: " + img2.getRaster());

        System.out.println("\nimg1 SampleModel: " + img1.getSampleModel());
        System.out.println("img2 SampleModel: " + img2.getSampleModel());

        // 检查DataBuffer
        java.awt.image.DataBuffer db1 = img1.getRaster().getDataBuffer();
        java.awt.image.DataBuffer db2 = img2.getRaster().getDataBuffer();
        System.out.println("\nimg1 DataBuffer: " + db1.getClass() + ", size: " + db1.getSize());
        System.out.println("img2 DataBuffer: " + db2.getClass() + ", size: " + db2.getSize());

        // 检查SampleModel的参数
        java.awt.image.SampleModel sm1 = img1.getSampleModel();
        java.awt.image.SampleModel sm2 = img2.getSampleModel();
        if (sm1 instanceof PixelInterleavedSampleModel && sm2 instanceof PixelInterleavedSampleModel) {
            PixelInterleavedSampleModel psm1 = (PixelInterleavedSampleModel) sm1;
            PixelInterleavedSampleModel psm2 = (PixelInterleavedSampleModel) sm2;
            System.out.println("\nPixelInterleavedSampleModel 参数比较:");
            System.out.println("img1 扫描行跨距: " + psm1.getScanlineStride());
            System.out.println("img2 扫描行跨距: " + psm2.getScanlineStride());
            System.out.println("img1 像素跨距: " + psm1.getPixelStride());
            System.out.println("img2 像素跨距: " + psm2.getPixelStride());
        }
    }

    private static String getTypeName(int type) {
        switch (type) {
            case BufferedImage.TYPE_INT_RGB: return "TYPE_INT_RGB";
            case BufferedImage.TYPE_INT_ARGB: return "TYPE_INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE: return "TYPE_INT_ARGB_PRE";
            case BufferedImage.TYPE_3BYTE_BGR: return "TYPE_3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR: return "TYPE_4BYTE_ABGR";
            case BufferedImage.TYPE_BYTE_GRAY: return "TYPE_BYTE_GRAY";
            case BufferedImage.TYPE_BYTE_INDEXED: return "TYPE_BYTE_INDEXED";
            case BufferedImage.TYPE_USHORT_565_RGB: return "TYPE_USHORT_565_RGB";
            case BufferedImage.TYPE_USHORT_555_RGB: return "TYPE_USHORT_555_RGB";
            default: return "未知类型: " + type;
        }
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
