package cn.net.pap.common.opencv;

import org.opencv.core.*;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OpenCVUtils {

    static {
        URL url = ClassLoader.getSystemResource("opencv_java401.dll");
        System.load(url.getPath());
    }

    /**
     * 大图找小图
     *
     * @param sourceImg   原始大图   image abs path test image(https://sm.ms/image/S4wj2dLm5N1pM8c)
     * @param templateImg 模板小图   image abs path test image(https://sm.ms/image/9RV7wI6QfYJlxhn)
     * @param targetImg   匹配出来的结果   image abs path test image(https://sm.ms/image/QZPycMl3FSgihJ1)
     */
    public static void templateMatching(String sourceImg, String templateImg, String targetImg) {

        Mat src = Imgcodecs.imread(sourceImg);
        Mat template = Imgcodecs.imread(templateImg);

        Mat outputImage = new Mat(src.rows(), src.cols(), src.type());
        Imgproc.matchTemplate(src, template, outputImage, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult result = Core.minMaxLoc(outputImage);
        Point matchLoc = result.maxLoc;
        double similarity = result.maxVal;
        int x = (int) matchLoc.x;
        int y = (int) matchLoc.y;

        Imgproc.rectangle(src, new Point(x, y), new Point(x + template.cols(), y + template.rows()),
                new Scalar(0, 0, 255), 2, Imgproc.LINE_AA);

        Imgcodecs.imwrite(targetImg, src);
    }

    /**
     * 图像相似度
     *
     * @param image1Path 图像1路径
     * @param image2Path 图像2路径
     * @param type       相似度算法： Histogram
     * @return
     */
    public static double similarityImage(String image1Path, String image2Path, String type) {
        Mat image1 = Imgcodecs.imread(image1Path);
        Mat image2 = Imgcodecs.imread(image2Path);
        if (type.equals("Histogram")) {
            return similarityHistogram(image1, image2);
        }
        return 0;
    }

    /**
     * 图像特征
     *
     * @param imagePath
     * @return
     */
    public static byte[] matOfKeyPointImage(String imagePath) {
        Mat image = Imgcodecs.imread(imagePath);

        // Convert image to grayscale
        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);

        // Initialize ORB detector
        ORB detector = ORB.create();

        // Detect keypoints
        MatOfKeyPoint keypoints = new MatOfKeyPoint();
        detector.detect(grayImage, keypoints);

        // Compute descriptors
        Mat descriptors = new Mat();
        detector.compute(grayImage, keypoints, descriptors);

        // Convert descriptors to byte array
        MatOfByte matOfByte = new MatOfByte();
        descriptors.convertTo(matOfByte, CvType.CV_8U);

        // Convert MatOfByte to byte array
        byte[] descriptorsData = new byte[(int) (matOfByte.total() * matOfByte.channels())];
        matOfByte.get(0, 0, descriptorsData);

        return descriptorsData;
    }

    // 计算均方差（Histogram）
    private static double similarityHistogram(Mat image1, Mat image2) {
        Mat hist1 = calculateHistogram(image1);
        Mat hist2 = calculateHistogram(image2);
        final double similarity = Imgproc.compareHist(hist1, hist2, Imgproc.CV_COMP_CORREL);
        return similarity;
    }

    private static Mat calculateHistogram(Mat image) {
        Mat hist = new Mat();

        MatOfInt histSize = new MatOfInt(256);
        MatOfFloat ranges = new MatOfFloat(0, 256);
        MatOfInt channels = new MatOfInt(0);
        List<Mat> images = new ArrayList<>();
        images.add(image);
        Imgproc.calcHist(images, channels, new Mat(), hist, histSize, ranges);

        return hist;
    }

}
