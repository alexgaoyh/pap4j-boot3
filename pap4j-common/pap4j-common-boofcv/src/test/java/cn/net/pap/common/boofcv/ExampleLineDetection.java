package cn.net.pap.common.boofcv;

import boofcv.abst.feature.detect.line.DetectLine;
import boofcv.abst.feature.detect.line.DetectLineSegment;
import boofcv.alg.filter.blur.GBlurImageOps;
import boofcv.factory.feature.detect.line.ConfigHoughFootSubimage;
import boofcv.factory.feature.detect.line.ConfigHoughGradient;
import boofcv.factory.feature.detect.line.ConfigLineRansac;
import boofcv.factory.feature.detect.line.FactoryDetectLine;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.feature.ImageLinePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import georegression.struct.line.LineParametric2D_F32;
import georegression.struct.line.LineSegment2D_F32;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * https://github.com/lessthanoptimal/BoofCV/blob/v1.1.4/examples/src/main/java/boofcv/examples/features/ExampleLineDetection.java
 * <p>
 * 添加了使用霍夫变换获得倾斜角度的单元测试
 */
public class ExampleLineDetection {

    private String getTestImagePath() throws Exception {
        URL resourceUrl = getClass().getClassLoader().getResource("input.jpg");
        assertNotNull(resourceUrl, "Test image 'input.jpg' not found in resources!");
        return Paths.get(resourceUrl.toURI()).toAbsolutePath().toString();
    }

    /**
     * 获得图像的倾斜角度. 针对原图的情况
     * 可以使用当前类文件的 main 方法进行可视化的图像霍夫变换直线的展示
     */
    @Test
    public void getAnglesByOriginPic() throws Exception {
        String imagePath = getTestImagePath();
        BufferedImage bufferedImage = UtilImageIO.loadImageNotNull(imagePath);
        Double angle = DetectLineUtilss.getAngleByHoughLines(bufferedImage, 10, 1);
        System.out.println(angle);
    }

    /**
     * 针对已经进行 FourierTransform 傅里叶变换后的图像，去获得倾斜角度
     * 可以使用当前类文件的 main 方法进行可视化的图像霍夫变换直线的展示
     */
    @Test
    public void getAnglesByFourierTransformPic() throws Exception {
        String imagePath = getTestImagePath();
        BufferedImage bufferedImage = UtilImageIO.loadImageNotNull(imagePath);
        Double angle = DetectLineUtilss.getAngleByHoughLines(bufferedImage, 10, 2);
        System.out.println(angle);
    }


    // adjusts edge threshold for identifying pixels belonging to a line
    private static final float edgeThreshold = 25;
    // adjust the maximum number of found lines in the image
    private static final int maxLines = 10;

    private static ListDisplayPanel listPanel = new ListDisplayPanel();

    /**
     * Detects lines inside the image using different types of Hough detectors
     *
     * @param buffered  Input image.
     * @param imageType Type of image processed by line detector.
     */
    public static <T extends ImageGray<T>>
    void detectLines(BufferedImage buffered, Class<T> imageType) {
        // convert the line into a single band image
        T input = ConvertBufferedImage.convertFromSingle(buffered, null, imageType);
        T blurred = input.createSameShape();

        // Blur smooths out gradient and improves results
        GBlurImageOps.gaussian(input, blurred, 0, 5, null);

        // Detect edges of objects using gradient based hough detectors. If you have nice binary lines which are thin
        // there's another type of hough detector available
        DetectLine<T> detectorPolar = FactoryDetectLine.houghLinePolar(
                new ConfigHoughGradient(maxLines), null, imageType);
        DetectLine<T> detectorFoot = FactoryDetectLine.houghLineFoot(
                new ConfigHoughGradient(maxLines), null, imageType);
        DetectLine<T> detectorFootSub = FactoryDetectLine.houghLineFootSub(
                new ConfigHoughFootSubimage(3, 8, 5, edgeThreshold, maxLines, 2, 2), imageType);

        detectLines(buffered, blurred, detectorPolar, "Hough Polar");
        detectLines(buffered, blurred, detectorFoot, "Hough Foot");
        detectLines(buffered, blurred, detectorFootSub, "Hough Foot-Sub");
    }

    private static <T extends ImageGray<T>>
    void detectLines(BufferedImage buffered, T gray, DetectLine<T> detector, String name) {
        List<LineParametric2D_F32> found = detector.detect(gray);

        // display the results
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            ImageLinePanel gui = new ImageLinePanel();
            gui.setImage(buffered);
            gui.setLines(found);
            gui.setPreferredSize(new Dimension(gray.getWidth(), gray.getHeight()));

            listPanel.addItem(gui, name);
        }
    }

    /**
     * Detects segments inside the image
     *
     * @param image     Input image.
     * @param imageType Type of image processed by line detector.
     */
    public static <T extends ImageGray<T>, D extends ImageGray<D>>
    void detectLineSegments(BufferedImage image,
                            Class<T> imageType) {
        // convert the line into a single band image
        T input = ConvertBufferedImage.convertFromSingle(image, null, imageType);

        // Comment/uncomment to try a different type of line detector
        DetectLineSegment<T> detector = FactoryDetectLine.lineRansac(new ConfigLineRansac(40, 30, 2.36, true), imageType);

        List<LineSegment2D_F32> found = detector.detect(input);

        // display the results
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            ImageLinePanel gui = new ImageLinePanel();
            gui.setImage(image);
            gui.setLineSegments(found);
            gui.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

            listPanel.addItem(gui, "Line Segments");
        }
    }

    public static void main(String[] args) throws Exception {
        URL resourceUrl = ExampleLineDetection.class.getClassLoader().getResource("input.jpg");
        assertNotNull(resourceUrl, "Test image 'input.jpg' not found in resources!");
        String imagePath = Paths.get(resourceUrl.toURI()).toAbsolutePath().toString();
        BufferedImage input = UtilImageIO.loadImageNotNull(imagePath);

        detectLines(input, GrayU8.class);

        // line segment detection is still under development and only works for F32 images right now
        detectLineSegments(input, GrayF32.class);

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            ShowImages.showWindow(listPanel, "Detected Lines", true);
        }
    }
}
