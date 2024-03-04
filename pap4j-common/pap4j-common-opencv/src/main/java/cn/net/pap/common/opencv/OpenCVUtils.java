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

        // PCA dimensionality reduction
        Mat pcaData = new Mat();
        descriptors.convertTo(pcaData, CvType.CV_32F);
        Mat mean = new Mat();
        Core.PCACompute(pcaData, mean, descriptors);

        // Convert descriptors to byte array
        MatOfByte matOfByte = new MatOfByte();
        descriptors.convertTo(matOfByte, CvType.CV_8U);

        // Convert MatOfByte to byte array
        byte[] descriptorsData = new byte[(int) (matOfByte.total() * matOfByte.channels())];
        matOfByte.get(0, 0, descriptorsData);

        return descriptorsData;
    }

    /**
     * 将byte类型的arr转换成float
     *
     * @return
     */
    public static List<Float> byteArrayToFloatList(byte[] bytes) {
        List<Float> d = new ArrayList<>(bytes.length / 8);
        byte[] doubleBuffer = new byte[4];
        for (int j = 0; j < bytes.length; j += 4) {
            System.arraycopy(bytes, j, doubleBuffer, 0, doubleBuffer.length);
            d.add(bytes2Float(doubleBuffer));
        }
        return d;
    }

    /**
     * 将byte数组数据转换成float
     *
     * @param arr
     * @return
     */
    public static float bytes2Float(byte[] arr) {
        int accum = 0;
        accum = accum | (arr[0] & 0xff) << 0;
        accum = accum | (arr[1] & 0xff) << 8;
        accum = accum | (arr[2] & 0xff) << 16;
        accum = accum | (arr[3] & 0xff) << 24;
        return Float.intBitsToFloat(accum);
    }

    public static float[] convertArray(List<Float> floatList, Integer maxLength) {
        Integer arrayLength = floatList.size() > maxLength ? maxLength : floatList.size();
        float[] array = new float[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            if(floatList.get(i).isNaN()) {
                array[i] = 0.0f;
            } else {
                array[i] = floatList.get(i);
            }
        }
        return array;
    }

    /**
     * 最大最小  归一化
     * @param data
     * @return
     */
    public static float[] normalize(float[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data array cannot be null or empty");
        }

        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;

        for (float value : data) {
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }

        if (min == max) {
            throw new IllegalArgumentException("Data array cannot contain all the same values for normalization");
        }

        float[] normalizedData = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            float v = (data[i] - min) / (max - min);
            if(Float.isNaN(v)) {
                normalizedData[i] = 0.0f;
            } else {
                normalizedData[i] = (data[i] - min) / (max - min);
            }
        }
        return normalizedData;
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

    /**
     * 旋转图像，并保持图像大小不变。
     * @param inputPath 待旋转图像的绝对路径
     * @param outputPath    旋转后图像的绝对路径
     * @param angle 角度，逆时针为正， 传入 45 代表 逆时针旋转45度。
     */
    public static void rotation(String inputPath, String outputPath, double angle) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 定义旋转中心
        Point center = new Point(src.cols() / 2, src.rows() / 2);
        // 计算旋转后的图像边界
        Rect rotatedRect = Imgproc.boundingRect(new MatOfPoint2f(new Point(0, 0), new Point(src.cols() - 1, 0), new Point(src.cols() - 1, src.rows() - 1), new Point(0, src.rows() - 1)));
        Point rotatedCorner = new Point(rotatedRect.width, rotatedRect.height);
        // 计算旋转矩阵
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        // 进行图像旋转
        Mat rotated = new Mat();
        Imgproc.warpAffine(src, rotated, rotationMatrix, rotatedRect.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));
        // 保存旋转后的图像
        Imgcodecs.imwrite(outputPath, rotated);
    }

    public static void rotation2(String inputPath, String outputPath, double angle) {
        // 读取图像
        Mat originalImage = Imgcodecs.imread(inputPath);
        // 计算旋转后的图像大小
        int newWidth = (int) (originalImage.width() * Math.abs(Math.cos(Math.toRadians(angle))) +
                originalImage.height() * Math.abs(Math.sin(Math.toRadians(angle))));
        int newHeight = (int) (originalImage.width() * Math.abs(Math.sin(Math.toRadians(angle))) +
                originalImage.height() * Math.abs(Math.cos(Math.toRadians(angle))));
        // 定义旋转矩阵
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(new Point(originalImage.cols() / 2, originalImage.rows() / 2), angle, 1);
        // 计算旋转后的图像平移量，使其居中显示
        double offsetX = (newWidth - originalImage.width()) / 2.0;
        double offsetY = (newHeight - originalImage.height()) / 2.0;
        rotationMatrix.put(0, 2, rotationMatrix.get(0, 2)[0] + offsetX);
        rotationMatrix.put(1, 2, rotationMatrix.get(1, 2)[0] + offsetY);
        // 执行旋转
        Mat rotatedImage = new Mat();
        Imgproc.warpAffine(originalImage, rotatedImage, rotationMatrix, new Size(newWidth, newHeight), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(255, 255, 255));
        Imgcodecs.imwrite(outputPath, rotatedImage);
    }

    /**
     * 边缘像素加深/加浅，使用 Canny边缘检测找到边缘部分，如果是边缘，则进行边缘像素调整。
     * @param inputPath
     * @param outputPath
     * @param scope -60 加深
     */
    public static void edgeWeight(String inputPath, String outputPath, Integer scope) {
        // 读取图像
        Mat image = Imgcodecs.imread(inputPath);
        // 使用Canny边缘检测算法检测边缘
        Mat edges = new Mat();
        Imgproc.Canny(image, edges, 50, 150);
        // 加深边缘像素点
        for (int y = 0; y < edges.rows(); y++) {
            for (int x = 0; x < edges.cols(); x++) {
                if (edges.get(y, x)[0] != 0) {
                    double[] rgb  = image.get(y, x);
                    if(rgb != null) {
                        // 将原图和边缘图叠加，这里可以根据需求进行调整
                        int combinedRed = Math.min(255, (int)rgb[0] + scope);
                        int combinedGreen = Math.min(255, (int)rgb[1] + scope);
                        int combinedBlue = Math.min(255, (int)rgb[2] + scope);
                        // 设置叠加后的像素值
                        double[] newrgb = new double[]{combinedRed, combinedGreen, combinedBlue};
                        image.put(y, x, newrgb);
                    }
                }
            }
        }
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, image);
    }

}
