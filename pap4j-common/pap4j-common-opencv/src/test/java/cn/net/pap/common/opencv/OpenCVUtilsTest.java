package cn.net.pap.common.opencv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OpenCVUtilsTest {

    @Test
    void testOpenCVLoaded() {
        Mat mat = null;
        try {
            String version = Core.VERSION;
            System.out.println("OpenCV version: " + version);
            assertNotNull(version);

            mat = OpenCVUtils.eye(3, 3, CvType.CV_8UC1);
            assertFalse(mat.empty());
            assertEquals(3, mat.rows());
            assertEquals(3, mat.cols());
        } finally {
            if (mat != null) mat.release();
        }
    }

    @Test
    public void templateTest() throws Exception {
        String sourceImg = TestResourceUtil.getFile("origin.jpg").getAbsolutePath();
        String templateImg = TestResourceUtil.getFile("100.jpg").getAbsolutePath();
        String targetPath = TestResourceUtil.createTempFile("target", ".jpg").getAbsolutePath();
        OpenCVUtils.templateMatching(sourceImg, templateImg, targetPath);
    }

    @Test
    public void searchingTest() throws Exception {
        String image1Path = TestResourceUtil.getFile("left.jpg").getAbsolutePath();
        String image2Path = TestResourceUtil.getFile("right.jpg").getAbsolutePath();
        List<String> typeList = Arrays.asList(new String[]{"Histogram"});
        for (String type : typeList) {
            double v = OpenCVUtils.similarityImage(image1Path, image2Path, type);
            System.out.println(v);
        }
    }

    @Test
    public void matOfKeyPointImageTest() throws Exception {
        String imagePath = TestResourceUtil.getFile("origin.jpg").getAbsolutePath();
        byte[] bytes = OpenCVUtils.matOfKeyPointImage(imagePath, false, null, null);
        byte[] bytes1 = OpenCVUtils.matOfKeyPointImage(imagePath, false, null, null);
        // 两个图像相同，equal >= 1
        double equal = SimilarityUtils.cosineSimilarity(bytes, bytes1);

        String imagePath2 = TestResourceUtil.getFile("100.jpg").getAbsolutePath();
        byte[] bytes2 = OpenCVUtils.matOfKeyPointImage(imagePath2, false, null, null);
        // 两个图像相似 (确定一个阈值)， 0 < similarity < 1
        double similarity = SimilarityUtils.cosineSimilarity(bytes, bytes2);

        String imagePath3 = TestResourceUtil.getFile("1002.jpg").getAbsolutePath();
        byte[] bytes3 = OpenCVUtils.matOfKeyPointImage(imagePath3, false, null, null);
        // 两个图像不相似 (确定一个阈值)， 0 < different < 1
        double different = SimilarityUtils.cosineSimilarity(bytes, bytes3);

        // 持久化 图像特征
//        try (FileWriter fw = new FileWriter(TestResourceUtil.createTempFile("pap", ".txt").getAbsolutePath())) {
//            fw.write(new String(bytes, StandardCharsets.UTF_8));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    @Test
    public void dctWaterMarkConvert() throws Exception {
        OpenCVUtils.dctWaterMarkEncode(TestResourceUtil.getFile("origin.jpg").getAbsolutePath(), "alexgaoyh", TestResourceUtil.createTempFile("inner", ".jpg").getAbsolutePath());
        OpenCVUtils.dctWaterMarkDecode(TestResourceUtil.getFile("0.jpg").getAbsolutePath(), TestResourceUtil.getFile("0.jpg").getAbsolutePath());
    }

    /**
     * 图像尺寸需相同
     */
    @Test
    public void imgCompare() throws Exception {
        Mat image1 = null;
        Mat image2 = null;
        Mat difference = null;
        try {
            image1 = OpenCVUtils.imread(TestResourceUtil.getFile("origin.jpg").getAbsolutePath());
            image2 = OpenCVUtils.imread(TestResourceUtil.getFile("origin.jpg").getAbsolutePath());

            Imgproc.cvtColor(image1, image1, Imgproc.COLOR_BGR2GRAY);
            Imgproc.cvtColor(image2, image2, Imgproc.COLOR_BGR2GRAY);

            difference = new Mat();
            Core.absdiff(image1, image2, difference);

            Imgcodecs.imwrite(TestResourceUtil.createTempFile("diff", ".jpg").getAbsolutePath(), difference);

            Imgproc.threshold(difference, difference, 128, 255, Imgproc.THRESH_BINARY);
            Imgcodecs.imwrite(TestResourceUtil.createTempFile("tdiff", ".jpg").getAbsolutePath(), difference);
        } finally {
            if (image1 != null) image1.release();
            if (image2 != null) image2.release();
            if (difference != null) difference.release();
        }
    }

    /**
     * 将两张图像进行缝合，左侧图像和右侧图像有重叠的部分，合并起来生成新的图像.
     */
    @Test
    public void stitchImagesTest() throws Exception {
        OpenCVUtils.stitchImages(TestResourceUtil.getFile("left.jpg").getAbsolutePath(),
                TestResourceUtil.getFile("right.jpg").getAbsolutePath(),
                TestResourceUtil.createTempFile("stitched", ".jpg").getAbsolutePath());
    }

    @Test
    public void rotate1Test() throws Exception {
        Mat src = null;
        Mat rotationMatrix = null;
        Mat rotated = null;
        try {
            // 读取图像
            src = Imgcodecs.imread(TestResourceUtil.getFile("origin.jpg").getAbsolutePath());
            // 旋转角度 非90度的倍数
            double angle = 45.0;
            // 获取图像中心
            org.opencv.core.Point center = new org.opencv.core.Point(src.width() / 2.0, src.height() / 2.0);
            // 生成旋转矩阵
            rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);
            // 计算旋转后的图像尺寸
            org.opencv.core.Size size = new org.opencv.core.Size(src.width(), src.height());
            // 进行仿射变换（旋转），使用双三次插值
            rotated = new Mat();
            Imgproc.warpAffine(src, rotated, rotationMatrix, size, Imgproc.INTER_CUBIC);
            // 保存结果
            Imgcodecs.imwrite(TestResourceUtil.createTempFile("output", ".jpg").getAbsolutePath(), rotated);
        } finally {
            if (src != null) src.release();
            if (rotationMatrix != null) rotationMatrix.release();
            if (rotated != null) rotated.release();
        }
    }

    @Test
    public void rotate2Test() throws Exception {
        Mat src = null;
        Mat rotated = null;
        try {
            src = OpenCVUtils.imread(TestResourceUtil.getFile("origin.jpg").getAbsolutePath());
            rotated = new Mat();
            Core.transpose(src, rotated);
            // 第三个参数为 1、-1、0，对应不同的90倍旋转的参数。
            Core.flip(rotated, rotated, 1);
            Imgcodecs.imwrite(TestResourceUtil.createTempFile("out", ".jpg").getAbsolutePath(), rotated);
        } finally {
            if (src != null) src.release();
            if (rotated != null) rotated.release();
        }
    }

    @Test
    public void stitchImagesByPointTest() throws Exception {
        Mat imageA = null;
        Mat imageB = null;
        Mat result = null;
        try {
            // 读取两张图像
            imageA = OpenCVUtils.imread(TestResourceUtil.getFile("left.jpg").getAbsolutePath());
            imageB = OpenCVUtils.imread(TestResourceUtil.getFile("right.jpg").getAbsolutePath());

            // 定义每张图像的 i 和 j 点
            Point iA = new Point(100, 0); // 图像 A 的 i 点
            Point jA = new Point(100, 200); // 图像 A 的 j 点
            Point iB = new Point(0, 0);   // 图像 B 的 i 点
            Point jB = new Point(0, 400);  // 图像 B 的 j 点

            // 拼接图像
            result = OpenCVUtils.stitchImagesByPoint(imageA, imageB, iA, jA, iB, jB);

            // 保存拼接后的图像
            Imgcodecs.imwrite(TestResourceUtil.createTempFile("stitched_image", ".jpg").getAbsolutePath(), result);
        } finally {
            if (imageA != null) imageA.release();
            if (imageB != null) imageB.release();
            if (result != null) result.release();
        }
    }

    @Test
    public void jp2TojpgTest() throws Exception {
        Mat imageJP2 = null;
        try {
            long start = System.currentTimeMillis();
            imageJP2 = OpenCVUtils.imread(TestResourceUtil.getFile("origin.jpg").getAbsolutePath());
            boolean success = Imgcodecs.imwrite(TestResourceUtil.createTempFile("jpg", ".jpg").getAbsolutePath(), imageJP2);
            System.out.println("" + success + (System.currentTimeMillis() - start));
        } finally {
            if (imageJP2 != null) imageJP2.release();
        }
    }

    @Test
    public void autoCorrectionGetAngle2Test() throws Exception {
        Double v = OpenCVUtils.autoCorrectionGetAngle2(TestResourceUtil.getFile("edges.png").getAbsolutePath());
        System.out.println(v);
    }

    // @Test
    @DisplayName("对照函数，Mat对象的release调用：不调用release，会有 cv::OutOfMemoryError")
    public void testMatReleaseMemoryLeakTrue() throws Exception {
        OpenCVUtils.empty();
        System.out.println("--- 开始执行内存泄露测试 ---");
        try {
            // 模拟处理 10000 图像
            for (int i = 1; i <= 10000; i++) {
                // 模拟创建一个 1080p 的全高清彩色图像 (1920x1080, 3通道)
                // 每一张图在原生内存中大约占用: 1920 * 1080 * 3 字节 ≈ 6.2 MB
                Mat frame = new Mat(1080, 1920, CvType.CV_8UC3);
                // 【致命错误】：没有调用 frame.release()
                // 每 100 次打印一次进度
                if (i % 100 == 0) {
                    System.out.println("已处理 " + i + " 帧，预计原生内存已占用: " + (i * 6.2) + " MB");
                }
            }
        } catch (Throwable t) {
            // 捕获 Throwable 是因为内存溢出通常抛出的是 java.lang.OutOfMemoryError，而不是 Exception
            System.err.println("程序崩溃！捕获到异常/错误: " + t.getMessage());
        }
        System.out.println("--- 内存泄露测试结束 ---");
    }

    @Test
    @DisplayName("对照函数，Mat对象的release调用：调用release，正常执行完毕")
    public void testMatReleaseMemoryLeakFalse() throws Exception {
        OpenCVUtils.empty();
        System.out.println("--- 开始执行内存安全测试 ---");
        try {
            for (int i = 1; i <= 10000; i++) {
                Mat frame = null;
                try {
                    frame = new Mat(1080, 1920, CvType.CV_8UC3);
                } finally {
                    // 【正确做法】：无论处理是否成功，确保释放该帧占据的原生内存
                    if (frame != null) {
                        frame.release();
                    }
                }
                if (i % 1000 == 0) {
                    System.out.println("已安全处理 " + i + " 帧。");
                }
            }
            System.out.println("成功处理全部 10000 帧，没有发生内存崩溃！");
        } catch (Throwable t) {
            System.err.println("发生意外异常: " + t.getMessage());
        }
        System.out.println("--- 内存安全测试结束 ---");
    }


    // todo 文档透视畸变矫正


}
