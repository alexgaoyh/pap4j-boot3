package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;
import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ImageRotationWithLines {

    /**
     *
     */
    @Test
    public void test1() {
        // 读取图像
        Mat srcImage = OpenCVUtils.imread(TestResourceUtil.getFile("0.jpg").getAbsolutePath().toString()); // 替换为你的图像路径

        if (srcImage.empty()) {
            System.out.println("无法加载图像，请检查路径是否正确");
            return;
        }

        // 顺时针旋转90度
        Mat rotatedImage = rotateClockwise90(srcImage);

        // 创建原图和旋转图的副本用于绘制
        Mat srcDisplay = srcImage.clone();
        Mat rotatedDisplay = rotatedImage.clone();

        // 定义特征点
        List<Point> srcPoints = new ArrayList<>();
        List<Point> rotatedPoints = new ArrayList<>();

        // 添加特征点（图像的四个角点和中心点）
        srcPoints.add(new Point(0, 0));                      // 左上角 P0
        srcPoints.add(new Point(srcImage.cols() - 1, 0));    // 右上角 P1
        srcPoints.add(new Point(srcImage.cols() - 1, srcImage.rows() - 1)); // 右下角 P2
        srcPoints.add(new Point(0, srcImage.rows() - 1));    // 左下角 P3
        srcPoints.add(new Point(srcImage.cols() / 2.0, srcImage.rows() / 2.0)); // 中心点 P4

        // 添加一些随机点
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            int x = random.nextInt(srcImage.cols());
            int y = random.nextInt(srcImage.rows());
            srcPoints.add(new Point(x, y));
        }

        // 计算旋转后对应的点位置
        for (Point srcPoint : srcPoints) {
            // 顺时针旋转90度变换：(x,y) -> (y, height-x)
            double rotatedX = srcImage.rows() - srcPoint.y;
            double rotatedY = srcPoint.x ;
            rotatedPoints.add(new Point(rotatedX, rotatedY));
        }

        // 定义颜色数组，每个点使用不同的颜色
        Scalar[] colors = {
                new Scalar(255, 0, 0),    // 蓝色
                new Scalar(0, 255, 0),    // 绿色
                new Scalar(0, 0, 255),    // 红色
                new Scalar(255, 255, 0),  // 青色
                new Scalar(255, 0, 255),  // 洋红色
                new Scalar(0, 255, 255),  // 黄色
                new Scalar(128, 0, 128),  // 紫色
                new Scalar(255, 165, 0),  // 橙色
                new Scalar(0, 128, 128),  // 深青色
                new Scalar(128, 0, 0)     // 深蓝色
        };

        // 在原图上绘制点和标签
        for (int i = 0; i < srcPoints.size(); i++) {
            Point point = srcPoints.get(i);
            Scalar color = colors[i % colors.length];

            // 绘制点（实心圆）
            Imgproc.circle(srcDisplay, point, 8, color, -1);

            // 绘制外圈（白色边框）
            Imgproc.circle(srcDisplay, point, 10, new Scalar(255, 255, 255), 2);

            // 添加标签
            Imgproc.putText(srcDisplay, "P" + i,
                    new Point(point.x + 12, point.y - 12),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                    new Scalar(255, 255, 255), 2);
        }

        // 在旋转图上绘制对应的点和标签
        for (int i = 0; i < rotatedPoints.size(); i++) {
            Point point = rotatedPoints.get(i);
            Scalar color = colors[i % colors.length];

            // 绘制点（实心圆）
            Imgproc.circle(rotatedDisplay, point, 8, color, -1);

            // 绘制外圈（白色边框）
            Imgproc.circle(rotatedDisplay, point, 10, new Scalar(255, 255, 255), 2);

            // 添加标签
            Imgproc.putText(rotatedDisplay, "P" + i,
                    new Point(point.x + 12, point.y - 12),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                    new Scalar(255, 255, 255), 2);
        }

        // 显示坐标变换信息
        System.out.println("=== 坐标变换信息 ===");
        System.out.println("原图尺寸: " + srcImage.cols() + "x" + srcImage.rows());
        System.out.println("旋转图尺寸: " + rotatedImage.cols() + "x" + rotatedImage.rows());
        System.out.println("\n点对应关系:");
        for (int i = 0; i < srcPoints.size(); i++) {
            Point srcPoint = srcPoints.get(i);
            Point rotatedPoint = rotatedPoints.get(i);
            System.out.printf("P%d: (%d,%d) -> (%d,%d)\n",
                    i, (int)srcPoint.x, (int)srcPoint.y,
                    (int)rotatedPoint.x, (int)rotatedPoint.y);
        }

        // 创建两个独立的窗口
        HighGui.namedWindow("原图 - 特征点", HighGui.WINDOW_NORMAL);
        HighGui.namedWindow("旋转90度 - 对应点", HighGui.WINDOW_NORMAL);

        // 调整窗口大小
        HighGui.resizeWindow("原图 - 特征点", srcImage.cols(), srcImage.rows());
        HighGui.resizeWindow("旋转90度 - 对应点", rotatedImage.cols(), rotatedImage.rows());

        // 显示图像
        HighGui.imshow("原图 - 特征点", srcDisplay);
        HighGui.imshow("旋转90度 - 对应点", rotatedDisplay);

        // 等待按键
        int delaySeconds = 5; // 设置你想要延迟关闭的秒数
        System.out.println("\n窗口将在 " + delaySeconds + " 秒后自动关闭，或按任意键提前跳出...");

        // waitKey 接收的是毫秒数。乘以1000转成毫秒。
        // 如果在倒计时内按下任意键，也会提前结束等待。
        HighGui.waitKey(delaySeconds * 1000);

        // 销毁所有窗口，结束测试
        HighGui.destroyAllWindows();
        System.out.println("测试结束，窗口已关闭。");

    }

    // 顺时针旋转90度的方法
    private static Mat rotateClockwise90(Mat src) {
        Mat dst = new Mat();
        Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE);
        return dst;
    }

    @Test
    public void test2() {
        // 读取图像
        Mat srcImage = OpenCVUtils.imread(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "ocr.jpg"); // 替换为你的图像路径

        if (srcImage.empty()) {
            System.out.println("无法加载图像，请检查路径是否正确");
            return;
        }

        // 顺时针旋转90度
        Mat rotatedImage = rotateClockwise90(srcImage);

        // 创建原图和旋转图的副本用于绘制
        Mat srcDisplay = srcImage.clone();
        Mat rotatedDisplay = rotatedImage.clone();

        // 定义特征点
        List<Point> srcPoints = new ArrayList<>();
        List<Point> rotatedPoints = new ArrayList<>();

        // 添加特征点（图像的四个角点和中心点）
        srcPoints.add(new Point(0, 0));                      // 左上角 P0
        srcPoints.add(new Point(srcImage.cols() - 1, 0));    // 右上角 P1
        srcPoints.add(new Point(srcImage.cols() - 1, srcImage.rows() - 1)); // 右下角 P2
        srcPoints.add(new Point(0, srcImage.rows() - 1));    // 左下角 P3
        srcPoints.add(new Point(srcImage.cols() / 2.0, srcImage.rows() / 2.0)); // 中心点 P4

        // 添加一些随机点
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            int x = random.nextInt(srcImage.cols());
            int y = random.nextInt(srcImage.rows());
            srcPoints.add(new Point(x, y));
        }

        // 计算旋转后对应的点位置
        for (Point srcPoint : srcPoints) {
            // 顺时针旋转90度变换：(x,y) -> (y, height-x)
            double rotatedX = srcImage.rows() - srcPoint.y;
            double rotatedY = srcPoint.x;
            rotatedPoints.add(new Point(rotatedX, rotatedY));
        }

        // 定义颜色数组，每个点使用不同的颜色
        Scalar[] colors = {
                new Scalar(255, 0, 0),    // 蓝色
                new Scalar(0, 255, 0),    // 绿色
                new Scalar(0, 0, 255),    // 红色
                new Scalar(255, 255, 0),  // 青色
                new Scalar(255, 0, 255),  // 洋红色
                new Scalar(0, 255, 255),  // 黄色
                new Scalar(128, 0, 128),  // 紫色
                new Scalar(255, 165, 0),  // 橙色
                new Scalar(0, 128, 128),  // 深青色
                new Scalar(128, 0, 0)     // 深蓝色
        };

        // 在原图上绘制点和标签
        for (int i = 0; i < srcPoints.size(); i++) {
            Point point = srcPoints.get(i);
            Scalar color = colors[i % colors.length];

            // 绘制点（实心圆）
            Imgproc.circle(srcDisplay, point, 8, color, -1);

            // 绘制外圈（白色边框）
            Imgproc.circle(srcDisplay, point, 10, new Scalar(255, 255, 255), 2);

            // 添加标签
            Imgproc.putText(srcDisplay, "P" + i,
                    new Point(point.x + 12, point.y - 12),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                    new Scalar(255, 255, 255), 2);
        }

        // 在旋转图上绘制对应的点和标签
        for (int i = 0; i < rotatedPoints.size(); i++) {
            Point point = rotatedPoints.get(i);
            Scalar color = colors[i % colors.length];

            // 绘制点（实心圆）
            Imgproc.circle(rotatedDisplay, point, 8, color, -1);

            // 绘制外圈（白色边框）
            Imgproc.circle(rotatedDisplay, point, 10, new Scalar(255, 255, 255), 2);

            // 添加标签
            Imgproc.putText(rotatedDisplay, "P" + i,
                    new Point(point.x + 12, point.y - 12),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6,
                    new Scalar(255, 255, 255), 2);
        }

        // 创建合并图像 - 宽度为两幅图像宽度之和加上间隔，高度为两幅图像中较大的高度
        int gap = 100; // 图像之间的间隔，用于绘制连线
        int totalWidth = srcDisplay.cols() + rotatedDisplay.cols() + gap;
        int totalHeight = Math.max(srcDisplay.rows(), rotatedDisplay.rows());

        Mat combinedImage = new Mat(totalHeight, totalWidth, srcDisplay.type(), new Scalar(50, 50, 50));

        // 将原图复制到合并图像的左侧
        Mat roiLeft = combinedImage.colRange(0, srcDisplay.cols()).rowRange(0, srcDisplay.rows());
        srcDisplay.copyTo(roiLeft);

        // 将旋转图复制到合并图像的右侧
        Mat roiRight = combinedImage.colRange(srcDisplay.cols() + gap, totalWidth).rowRange(0, rotatedDisplay.rows());
        rotatedDisplay.copyTo(roiRight);

        // 绘制连接线
        for (int i = 0; i < srcPoints.size(); i++) {
            Point srcPoint = srcPoints.get(i);
            Point rotatedPoint = rotatedPoints.get(i);

            // 计算连线在合并图像中的坐标
            Point startPoint = new Point(srcPoint.x, srcPoint.y);
            Point endPoint = new Point(srcDisplay.cols() + gap + rotatedPoint.x, rotatedPoint.y);

            // 绘制连线
            Scalar color = colors[i % colors.length];
            Imgproc.line(combinedImage, startPoint, endPoint, color, 2);

            // 在线条中间添加标签
            Point midPoint = new Point((startPoint.x + endPoint.x) / 2, (startPoint.y + endPoint.y) / 2);
            Imgproc.putText(combinedImage, "P" + i,
                    new Point(midPoint.x + 5, midPoint.y - 5),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2);
        }

        // 显示坐标变换信息
        System.out.println("=== 坐标变换信息 ===");
        System.out.println("原图尺寸: " + srcImage.cols() + "x" + srcImage.rows());
        System.out.println("旋转图尺寸: " + rotatedImage.cols() + "x" + rotatedImage.rows());
        System.out.println("\n点对应关系:");
        for (int i = 0; i < srcPoints.size(); i++) {
            Point srcPoint = srcPoints.get(i);
            Point rotatedPoint = rotatedPoints.get(i);
            System.out.printf("P%d: (%d,%d) -> (%d,%d)\n",
                    i, (int)srcPoint.x, (int)srcPoint.y,
                    (int)rotatedPoint.x, (int)rotatedPoint.y);
        }

        // 创建窗口并显示合并后的图像
        HighGui.namedWindow("图像旋转与对应点连线", HighGui.WINDOW_NORMAL);
        HighGui.resizeWindow("图像旋转与对应点连线", totalWidth, totalHeight);
        HighGui.imshow("图像旋转与对应点连线", combinedImage);

        // 等待按键
        System.out.println("\n按任意键退出...");
        HighGui.waitKey(0);
        HighGui.destroyAllWindows();
    }


}