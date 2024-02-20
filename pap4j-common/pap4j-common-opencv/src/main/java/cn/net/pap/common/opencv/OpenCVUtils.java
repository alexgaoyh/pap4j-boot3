package cn.net.pap.common.opencv;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.net.URL;

public class OpenCVUtils {

    static {
        URL url = ClassLoader.getSystemResource("opencv_java401.dll");
        System.load(url.getPath());
    }

    /**
     * 大图找小图
     *
     * @param sourceImg  原始大图   image abs path test image(https://sm.ms/image/S4wj2dLm5N1pM8c)
     * @param smallImg   模板小图   image abs path test image(https://sm.ms/image/9RV7wI6QfYJlxhn)
     * @param targetPath 匹配出来的结果   image abs path test image(https://sm.ms/image/QZPycMl3FSgihJ1)
     */
    public static void templateMatching(String sourceImg, String smallImg, String targetPath) {

        Mat src = Imgcodecs.imread(sourceImg);
        Mat template = Imgcodecs.imread(smallImg);

        Mat outputImage = new Mat(src.rows(), src.cols(), src.type());
        Imgproc.matchTemplate(src, template, outputImage, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult result = Core.minMaxLoc(outputImage);
        Point matchLoc = result.maxLoc;
        double similarity = result.maxVal;
        int x = (int) matchLoc.x;
        int y = (int) matchLoc.y;

        Imgproc.rectangle(src, new Point(x, y), new Point(x + template.cols(), y + template.rows()),
                new Scalar(0, 0, 255), 2, Imgproc.LINE_AA);

        Imgcodecs.imwrite(targetPath, src);
    }

}
