package cn.net.pap.common.opencv;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.HOGDescriptor;
import org.opencv.photo.Photo;

import java.net.URL;
import java.util.*;

import static org.opencv.core.Core.*;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.core.CvType.CV_8UC1;
import static org.opencv.imgproc.Imgproc.*;

/**
 * java opencv4.0.1 handle image
 */
public class OpenCVUtils {

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            URL url = ClassLoader.getSystemResource("opencv_java401.dll");
            if(url != null) {
                System.load(url.getPath());
            } else {
                System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            }
        }
        if (osName.contains("linux")) {
            URL url = ClassLoader.getSystemResource("libopencv_java401.so");
            if(url != null) {
                System.load(url.getPath());
            } else {
                System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            }
        }

    }

    /**
     * 显示调用一下，初始化加载 library。
     * @return
     */
    public static Boolean empty() {
        return true;
    }

    /**
     * 大图找小图
     *
     * @param sourceImg   原始大图   image abs path test image(https://sm.ms/image/S4wj2dLm5N1pM8c)
     * @param templateImg 模板小图   image abs path test image(https://sm.ms/image/9RV7wI6QfYJlxhn)
     * @param targetImg   匹配出来的结果   image abs path test image(https://sm.ms/image/QZPycMl3FSgihJ1)
     */
    public static Boolean templateMatching(String sourceImg, String templateImg, String targetImg) {

        Mat src = Imgcodecs.imread(sourceImg);
        Mat template = Imgcodecs.imread(templateImg);

        Mat outputImage = new Mat(src.rows(), src.cols(), src.type());
        Imgproc.matchTemplate(src, template, outputImage, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult result = Core.minMaxLoc(outputImage);
        Point matchLoc = result.maxLoc;
        double similarity = result.maxVal;
        int x = (int) matchLoc.x;
        int y = (int) matchLoc.y;
        if(similarity > 0.5) {
            Imgproc.rectangle(src, new Point(x, y), new Point(x + template.cols(), y + template.rows()),
                    new Scalar(0, 0, 255), 2, Imgproc.LINE_AA);
            Imgcodecs.imwrite(targetImg, src);
            return true;
        } else {
            return false;
        }

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
     * 根据目标尺寸与原始图像的比例来缩放图像，并确保在新图像中居中显示
     * @param image
     * @param targetWidth
     * @param targetHeight
     * @return
     */
    public static Mat resizeAndCenter(Mat image, int targetWidth, int targetHeight) {
        // 计算缩放比例
        double scaleX = (double) targetWidth / image.cols();
        double scaleY = (double) targetHeight / image.rows();
        double scale = Math.min(scaleX, scaleY);

        // 计算调整后的尺寸
        int newWidth = (int) (image.cols() * scale);
        int newHeight = (int) (image.rows() * scale);

        // 调整图像大小
        Mat resizedImage = new Mat();
        Imgproc.resize(image, resizedImage, new Size(newWidth, newHeight));

        // 创建一个带有背景的新图像
        Mat centeredImage = Mat.zeros(targetHeight, targetWidth, resizedImage.type());

        // 计算居中位置
        int startX = (targetWidth - newWidth) / 2;
        int startY = (targetHeight - newHeight) / 2;

        // 将调整后的图像复制到中心位置
        resizedImage.copyTo(centeredImage.rowRange(startY, startY + newHeight)
                .colRange(startX, startX + newWidth));

        return centeredImage;
    }

    /**
     * 图像特征
     *
     * @param imagePath
     * @return
     */
    public static byte[] matOfKeyPointImage(String imagePath, Boolean resizeFlag, Integer targetWidth, Integer targetHeight) {
        Mat image = Imgcodecs.imread(imagePath);

        // 在相同的尺寸下提前特征
        if(resizeFlag != null && targetWidth != null && targetHeight != null && resizeFlag == true) {
            image = resizeAndCenter(image, targetWidth, targetHeight);
        }

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

    public static float[] matOfKeyPointImage2(String imagePath) {
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

        // 创建一个float数组来存储pcaData中的数据
        // 测试期间，这里直接把数组的长度写死 = 16000 了，从而确保不同图像获得的特征向量的长度是相同的。
        float[] pcaDataArray = new float[(int) (pcaData.total() * pcaData.channels())];

        // 将pcaData中的数据复制到pcaDataArray中
        pcaData.get(0, 0, pcaDataArray);

        return pcaDataArray;
    }

    /**
     * 获得图像特征，可以指定特征长度。
     * @param imagePath
     * @param arrayLength
     * @return
     */
    public static float[] matOfKeyPointImage3(String imagePath, Long arrayLength) {
        // 加载HOG描述符
        HOGDescriptor hog = new HOGDescriptor();
        hog.setSVMDetector(HOGDescriptor.getDefaultPeopleDetector()); // 注意：这通常是用于行人检测的，但你可以自定义HOG参数

        // 读取图像
        Mat image = Imgcodecs.imread(imagePath);

        // 转换为灰度图
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

        // 计算HOG特征
        MatOfFloat descriptor = new MatOfFloat();
        hog.compute(gray, descriptor);

        if(arrayLength == null) {
            arrayLength = descriptor.total() * descriptor.channels();
        }

        // 测试期间，这里直接把数组的长度写死 = 100 了，从而确保不同图像获得的特征向量的长度是相同的。
        float[] pcaDataArray = new float[Integer.parseInt(arrayLength + "")];

        // 将pcaData中的数据复制到pcaDataArray中
        descriptor.get(0, 0, pcaDataArray);

        return pcaDataArray;
    }

    /**
     * HOG 特征提取器， 将原始图像灰度化，并且将原始图像缩放至相同大小后获得特征向量.
     * @param imagePath
     * @param widthOrHeight
     * @return
     */
    public static float[] hogFeatureExtraction(String imagePath, Integer widthOrHeight) {
        Mat image = Imgcodecs.imread(imagePath);

        // 将图像转换为灰度图像
        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);

        Mat resizedImage = new Mat();
        Imgproc.resize(grayImage, resizedImage, new Size(widthOrHeight, widthOrHeight));

        // 创建HOG描述符对象
        HOGDescriptor hog = new HOGDescriptor(new Size(widthOrHeight, widthOrHeight), new Size(16, 16), new Size(8, 8), new Size(4, 4), 9);

        // 计算图像的HOG特征
        MatOfFloat features = new MatOfFloat();
        hog.compute(resizedImage, features);

        // 将特征向量转换为列表
        float[] featureVector = features.toArray();

        return featureVector;

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

    /**
     * 高斯模糊 - 柔化
     * @param inputPath
     * @param outputPath
     */
    public static void gaussianBlur(String inputPath, String outputPath) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 创建一个新的矩阵以存储结果
        Mat dst = new Mat();
        // 使用高斯模糊
        Imgproc.GaussianBlur(src, dst, new Size(5, 5), 0);
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, dst);
    }

    /**
     * Unsharp Masking 锐化 是一种通过从原始图像中减去一个模糊版本的图像来增强图像边缘的方法。
     * @param inputPath
     * @param outputPath
     */
    public static void unsharpMasking(String inputPath, String outputPath) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 创建一个新的矩阵以存储模糊后的图像
        Mat blurred = new Mat();
        // 应用高斯模糊
        Imgproc.GaussianBlur(src, blurred, new Size(0, 0), 5); // 核大小必须是正奇数，这里使用5x5
        // 创建一个新的矩阵以存储锐化后的图像
        Mat sharpened = new Mat();
        // Unsharp Masking 锐化
        // alpha 是一个可调参数，控制锐化的程度
        double alpha = 1.5;
        Core.addWeighted(src, alpha, blurred, -alpha + 1, 0, sharpened);
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, sharpened);
    }

    /**
     * 区域内套红
     * @param inputPath
     * @param outputPath
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public static void drawRedBox(String inputPath, String outputPath, int x, int y, int width, int height) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 定义矩形区域
        Rect rect = new Rect(x, y, width, height);
        // 遍历指定矩形区域内的像素
        for (int yTmp = rect.y; yTmp < rect.y + rect.height; yTmp++) {
            for (int xTmp = rect.x; xTmp < rect.x + rect.width; xTmp++) {
                double[] pixel = src.get(yTmp, xTmp); // 获取像素值
                // 增加红色通道的值，确保不超过255
                pixel[2] = Math.min(pixel[2] + 100, 255);
                src.put(yTmp, xTmp, pixel); // 更新像素值
            }
        }
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, src);
    }

    /**
     * 去黑边
     * @param inputPath
     * @param outputPath
     * @param blackEdgeWidth    外的处理，假设四周黑色边框的宽度是 blackEdgeWidth 个像素
     */
    public static void removeBlackEdge(String inputPath, String outputPath, Integer blackEdgeWidth) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 将图像转换为灰度图像
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        // 使用边缘检测算法检测图像的边缘
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150);
        // 查找轮廓
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        // 计算最大边界框
        Rect boundingRect = Imgproc.boundingRect(contours.get(0));
        for (int i = 1; i < contours.size(); i++) {
            Rect rect = Imgproc.boundingRect(contours.get(i));
            if (boundingRect == null) {
                boundingRect = rect;
            } else {
                boundingRect = new Rect(
                        Math.min(boundingRect.x, rect.x),
                        Math.min(boundingRect.y, rect.y),
                        Math.max(boundingRect.x + boundingRect.width, rect.x + rect.width) - Math.min(boundingRect.x, rect.x),
                        Math.max(boundingRect.y + boundingRect.height, rect.y + rect.height) - Math.min(boundingRect.y, rect.y)
                );
            }
        }
        // 额外的处理，假设四周黑色边框的宽度是 blackEdgeWidth 个像素
        if(blackEdgeWidth != null && blackEdgeWidth > 0) {
            boundingRect = new Rect(boundingRect.x + blackEdgeWidth, boundingRect.y + blackEdgeWidth, boundingRect.width - blackEdgeWidth * 2 , boundingRect.height - blackEdgeWidth * 2);
        }
        // 裁剪图像
        Mat croppedImage = new Mat(src, boundingRect);
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, croppedImage);
    }

    /**
     * 去除区域内
     * @param inputPath
     * @param outputPath
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public static void croppedInnerImage(String inputPath, String outputPath, int x, int y, int width, int height) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 定义矩形的左上角和右下角坐标
        Rect rect = new Rect(x, y, width, height); // 参数依次为：x, y, width, height
        // 创建一个和原始图像同样大小和类型的掩码
        Mat mask = new Mat(src.size(), src.type());
        // 在掩码上绘制一个白色的矩形
        Imgproc.rectangle(mask, rect.tl(), rect.br(), new Scalar(255, 255, 255), Imgproc.FILLED);
        // 使用掩码将原始图像中对应区域设置为白色
        src.setTo(new Scalar(255, 255, 255), mask);
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, src);
    }

    /**
     * 去除区域外
     * @param inputPath
     * @param outputPath
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public static void croppedOuterImage(String inputPath, String outputPath, int x, int y, int width, int height) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 定义矩形的左上角和右下角坐标
        Rect rect = new Rect(50, 50, 150, 150); // 参数依次为：x, y, width, height
        // 创建一个和原始图像同样大小的掩码，并初始化为白色（255）
        Mat mask = new Mat(src.size(), src.type());
        mask.setTo(new Scalar(255, 255, 255));
        // 在掩码上将矩形区域设置为黑色（0）
        Imgproc.rectangle(mask, rect.tl(), rect.br(), new Scalar(0, 0, 0), Imgproc.FILLED);
        // 创建一个和原始图像同样大小的白色图像
        Mat white = new Mat(src.size(), src.type(), Scalar.all(255));
        // 使用掩码将原始图像中矩形区域外部的像素复制到白色图像上
        white.copyTo(src, mask);
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, src);
    }

    /**
     * 裁剪
     * @param inputPath
     * @param outputPath
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public static void cropImage(String inputPath, String outputPath, int x, int y, int width, int height) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 裁剪图像
        Mat croppedImage = new Mat(src, new Rect(x, y, width, height));
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, croppedImage);
    }

    /**
     * 反色
     * @param inputPath
     * @param outputPath
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public static void invertColors(String inputPath, String outputPath, int x, int y, int width, int height) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 定义矩形的左上角和右下角坐标
        Rect rect = new Rect(50, 50, 150, 150); // 参数依次为：x, y, width, height
        // 创建一个和原始图像同样大小的Mat对象用于存储反色后的结果
        Mat dst = new Mat();
        src.copyTo(dst);
        // 遍历矩形区域内的每个像素并取反
        for (int yTmp = rect.y; yTmp < rect.y + rect.height; yTmp++) {
            for (int xTmp = rect.x; xTmp < rect.x + rect.width; xTmp++) {
                // 获取当前像素值
                Scalar pixel = new Scalar(dst.get(yTmp, xTmp)[0], dst.get(yTmp, xTmp)[1], dst.get(yTmp, xTmp)[2]);

                // 对每个通道进行取反操作
                double blue = 255 - pixel.val[0];
                double green = 255 - pixel.val[1];
                double red = 255 - pixel.val[2];

                // 设置新的像素值
                dst.put(yTmp, xTmp, new double[]{blue, green, red});
            }
        }
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, dst);
    }

    /**
     * 补齐图像
     * @param inputPath
     * @param outputPath
     * @param width
     * @param height
     */
    public static void upSizeImage(String inputPath, String outputPath, int width, int height) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // 定义目标尺寸
        Size targetSize = new Size(width, height); // 你可以修改为你想要的尺寸

        // 计算缩放比例
        double scaleWidth = (double) targetSize.width / src.cols();
        double scaleHeight = (double) targetSize.height / src.rows();

        // 确定缩放比例，以较小的那个为准，防止图像变形
        double scale = Math.min(scaleWidth, scaleHeight);

        // 计算新的图像尺寸
        int newWidth = (int) (src.cols() * scale);
        int newHeight = (int) (src.rows() * scale);

        // 创建新的Mat对象来存储调整大小后的图像
        Mat resized = new Mat();
        Imgproc.resize(src, resized, new Size(newWidth, newHeight));

        // 计算补齐图像的边框大小
        double topBorder = (targetSize.height - newHeight) / 2;
        double bottomBorder = targetSize.height - newHeight - topBorder;
        double leftBorder = (targetSize.width - newWidth) / 2;
        double rightBorder = targetSize.width - newWidth - leftBorder;

        // 创建目标Mat对象，其大小为目标尺寸
        Mat padded = new Mat(targetSize, src.type());

        // 在目标Mat对象中填充边框
        Core.copyMakeBorder(resized, padded, (int)Math.round(topBorder), (int)Math.round(bottomBorder), (int)Math.round(leftBorder), (int)Math.round(rightBorder),
                Core.BORDER_CONSTANT, new Scalar(255, 255, 255)); // 使用白色作为边框颜色

        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, padded);
    }

    /**
     * 去噪   非局部均值去噪（Non-Local Means Denoising，NLMeans）
     * @param inputPath
     * @param outputPath
     */
    public static void denoiseImage(String inputPath, String outputPath) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // Convert the image to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        // Apply non-local means denoising
        Mat denoised = new Mat();
        float h = 5; // Strength of the filter
        int templateWindowSize = 7  ; // Window size for searching
        int searchWindowSize = 11; // Window size for matching
        Photo.fastNlMeansDenoisingColored(src, denoised, h, searchWindowSize, templateWindowSize);
        // 显示或保存处理后的图像
        Imgcodecs.imwrite(outputPath, denoised);
    }

    /**
     * 背景色平滑
     * @param inputPath
     * @param outputPath
     * @param colorThreshold 定义颜色相似性阈值 50.0
     */
    public static void smoothBackground(String inputPath, String outputPath, double colorThreshold) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);

        // 找到像素最多的颜色作为背景色
        Scalar backgroundColor = findBackgroundColor(src);

        // 转换图像到HSV颜色空间
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(src, hsvImage, Imgproc.COLOR_BGR2HSV);

        // 进行背景平滑操作
        for (int y = 0; y < src.rows(); y++) {
            for (int x = 0; x < src.cols(); x++) {
                double[] pixel = hsvImage.get(y, x);

                // 计算当前像素与背景色的颜色相似性
                double similarity = calculateColorSimilarity(pixel, backgroundColor.val);
                // 如果颜色相似，则进行平滑操作
                if (similarity < colorThreshold) {
                    Imgproc.blur(src.submat(y, y + 1, x, x + 1), src.submat(y, y + 1, x, x + 1), new Size(3, 3));
                }
            }
        }

        // 保存处理后的图像
        Imgcodecs.imwrite(outputPath, src);
    }

    // 找到像素最多的颜色
    private static Scalar findBackgroundColor(Mat image) {
        List<Scalar> colors = new ArrayList<>();
        for (int y = 0; y < image.rows(); y++) {
            for (int x = 0; x < image.cols(); x++) {
                double[] pixel = image.get(y, x);
                Scalar color = new Scalar(pixel);
                colors.add(color);
            }
        }
        // 统计每种颜色出现的次数
        Collections.sort(colors, new Comparator<Scalar>() {
            @Override
            public int compare(Scalar o1, Scalar o2) {
                return Double.compare(o1.val[0] + o1.val[1] + o1.val[2], o2.val[0] + o2.val[1] + o2.val[2]);
            }
        });
        // 返回出现次数最多的颜色
        return colors.get(colors.size() - 1);
    }

    // 计算颜色相似性
    private static double calculateColorSimilarity(double[] color1, double[] color2) {
        double hueDiff = Math.abs(color1[0] - color2[0]);
        double satDiff = Math.abs(color1[1] - color2[1]);
        double valDiff = Math.abs(color1[2] - color2[2]);

        // 在HSV颜色空间中，Hue的取值范围是0到180
        // 饱和度和值的取值范围是0到255
        // 此处可以根据需要调整权重
        double hueWeight = 2.0;
        double satWeight = 1.0;
        double valWeight = 1.0;

        return Math.sqrt(
                hueWeight * hueDiff * hueDiff +
                        satWeight * satDiff * satDiff +
                        valWeight * valDiff * valDiff
        );
    }

    /**
     * 过滤底色 首先将图像灰度化，像素值为0-255，数值越大越浅，比如可以把数值240以上的区域都填充成白色，就达到了过滤底色的目的。
     * @param inputPath
     * @param outputPath
     * @param colorThreshold 定义底色过滤阈值 240
     */
    public static void filterBackgroundColor(String inputPath, String outputPath, double colorThreshold) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);

        // 转换图像为灰度图像
        Mat grayImage = new Mat();
        Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_BGR2GRAY);

        // 过滤底色
        for (int y = 0; y < grayImage.rows(); y++) {
            for (int x = 0; x < grayImage.cols(); x++) {
                double[] pixel = grayImage.get(y, x);

                // 如果像素值大于阈值，则设置为白色
                if (pixel[0] > colorThreshold) {
                    grayImage.put(y, x, 255);
                }
            }
        }

        // 保存处理后的图像
        Imgcodecs.imwrite(outputPath, grayImage);
    }

    /**
     * 图像倾斜角度
     * @param inputPath
     * @return
     */
    public static double autoCorrectionGetAngle(String inputPath) {
        // 读取图像
        Mat src = Imgcodecs.imread(inputPath);
        // Convert image to grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        // Apply GaussianBlur to reduce noise
        Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);
        // Detect edges using Canny
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150, 3, false);
        // Perform Hough Line Transform to detect lines
        Mat lines = new Mat();
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, 100, 50, 10);
        // Find the longest line
        double maxLength = -1;
        double[] longestLine = null;
        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            double x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];
            double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            if (length > maxLength) {
                maxLength = length;
                longestLine = line;
            }
        }
        // Draw the longest line on a blank image
        Mat longestLineImage = Mat.zeros(src.size(), CvType.CV_8UC3);
        Imgproc.line(longestLineImage, new Point(longestLine[0], longestLine[1]), new Point(longestLine[2], longestLine[3]), new Scalar(0, 255, 0), 1);
        // Overlay the longest line on the original image
        Mat output = new Mat();
        Core.addWeighted(src, 0.7, longestLineImage, 0.3, 0, output);
        // Calculate the angle of the longest line
        double angle = Math.atan2(longestLine[3] - longestLine[1], longestLine[2] - longestLine[0]);
        double angleDegrees = Math.toDegrees(angle);
        if (angleDegrees < 0) {
            angleDegrees = angleDegrees + 90;
        }
        return angleDegrees;
    }

    /**
     * 这个方法，传入的直接是已经生成边缘的图像
     * 可以配合 ImageMagick 生成这个边缘图： magick 20.jpg -canny  0x1+10%+30% edges.png
     * 角度通常是 顺时针为正，逆时针为负
     * @param inputPath
     * @return
     */
    public static Double autoCorrectionGetAngle2(String inputPath) {
        // 1. 读取图像
        Mat src = Imgcodecs.imread(inputPath, Imgcodecs.IMREAD_GRAYSCALE);
        if (src.empty()) {
            return null;
        }

        // 2. 边缘检测（Canny）
        Mat edges = new Mat();
        Imgproc.Canny(src, edges, 50, 150);

        // 3. Hough 变换检测直线
        Mat lines = new Mat();
        Imgproc.HoughLinesP(edges, lines, 1, Math.PI / 180, 100, 100, 10);

        // 4. 计算所有线段的角度（中值滤波减少噪声影响）
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < lines.rows(); i++) {
            double[] line = lines.get(i, 0);
            double x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];
            double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
            // TODO 过滤接近垂直的线（避免90°干扰）, 这个值可以根据实际情况做不同的调整
            // todo 这里的判断也不对 要查一下， 是不是默认返回的都是小于0的比较正确
            if (Math.abs(angle) < 88) {
                angles.add(angle);
            }
        }

        // 5. 输出倾斜角度（中值）todo 这里输出中值的原因是什么？ 没道理
        if (!angles.isEmpty()) {
            angles.sort(Double::compare);
            double medianAngle = angles.get(angles.size() / 2);

            // todo 理解一下 todo 这里要根据图像的宽高，哪个是短边，做一个额外的处理， 长边和短边，对于角度的理解是不同的
            int width = src.width();
            int height = src.height();
            if(width < height) {
                medianAngle = medianAngle + 90;
            }

            return medianAngle;
        } else {
            return null;
        }
    }

    public static Mat imread(String image) {
        return Imgcodecs.imread(image);
    }

    public static Mat imread(String image, int flags) {
        return Imgcodecs.imread(image, flags);
    }

    // 将信息编码到图像中
    public static Mat embedMessage(Mat image, String message) {
        int width = image.cols();
        int height = image.rows();

        StringBuilder binaryMessage = new StringBuilder();
        for (char c : message.toCharArray()) {
            binaryMessage.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }

        int messageLength = binaryMessage.length();
        int bitIndex = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] pixel = image.get(y, x);
                for (int i = 0; i < pixel.length; i++) {
                    if (bitIndex < messageLength) {
                        pixel[i] = (int) pixel[i] & 0xFE | (binaryMessage.charAt(bitIndex++) - '0');
                    } else {
                        break;
                    }
                }
                image.put(y, x, pixel);
            }
        }

        return image;
    }

    // 从图像中提取隐藏的信息
    public static String extractMessage(Mat image) {
        int width = image.cols();
        int height = image.rows();

        StringBuilder binaryMessage = new StringBuilder();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] pixel = image.get(y, x);
                for (double value : pixel) {
                    binaryMessage.append((int) value & 1);
                }
            }
        }
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < binaryMessage.length(); i += 8) {
            int ascii = Integer.parseInt(binaryMessage.substring(i, i + 8), 2);
            message.append((char) ascii);
            if((char)ascii == '.') {
                break;
            }
        }

        return message.toString();
    }

    /**
     * 基于离散余弦变换（DCT）的水印编码功能
     * @param inputImgPath
     * @param textWatermark
     * @param outputImgPath
     */
    public static void dctWaterMarkEncode(String inputImgPath, String textWatermark, String outputImgPath) {
        // 读取图像
        Mat src = OpenCVUtils.imread(inputImgPath, org.opencv.core.CvType.CV_8S);

        List<Mat> channel = new ArrayList<>(3);
        List<Mat> newChannel = new ArrayList<>(3);
        org.opencv.core.Core.split(src, channel);

        for (int i = 0; i < 3; i++) {
            Mat com = dctWaterMarkDCT(channel.get(i)).clone();
            // you can set different location in 'new Point()'， just copy putText method
            putText(com, textWatermark,
                    new Point(com.cols() / 3, com.rows() / 2),
                    FONT_HERSHEY_COMPLEX, 5.0,
                    new Scalar(2, 2, 2, 0), 20, LINE_AA, false);
            idct(com, com);
            newChannel.add(i, com);
        }

        Mat res = new Mat();
        org.opencv.core.Core.merge(newChannel, res);

        if (res.rows() != src.rows() || res.cols() != src.cols()) {
            res = new Mat(res, new Rect(0, 0, src.width(), src.height()));
        }

        org.opencv.imgcodecs.Imgcodecs.imwrite(outputImgPath, res);
    }

    /**
     * 基于离散余弦变换（DCT）的水印解码功能
     * @param inputImgPath
     * @param outputImgPath
     */
    public static void dctWaterMarkDecode(String inputImgPath, String outputImgPath) {
        Mat dctWaterMark = dctWaterMarkDCT(OpenCVUtils.imread(inputImgPath, CV_8U));
        dctWaterMark.convertTo(dctWaterMark, COLOR_RGB2HSV);
        inRange(dctWaterMark, new Scalar(0, 0, 0, 0), new Scalar(16, 16, 16, 0), dctWaterMark);
        org.opencv.core.Core.normalize(dctWaterMark, dctWaterMark, 0, 255, NORM_MINMAX, CV_8UC1);
        org.opencv.imgcodecs.Imgcodecs.imwrite(outputImgPath, dctWaterMark);
    }

    private static Mat dctWaterMarkDCT(Mat src) {
        if ((src.cols() & 1) != 0) {
            copyMakeBorder(src, src, 0, 0, 0, 1, BORDER_CONSTANT, Scalar.all(0));
        }
        if ((src.rows() & 1) != 0) {
            copyMakeBorder(src, src, 0, 1, 0, 0, BORDER_CONSTANT, Scalar.all(0));
        }
        src.convertTo(src, CV_32F);
        dct(src, src);
        return src;
    }

    /**
     * 人脸比对
     *
     * @param img_1
     * @param img_2
     * @return
     */
    public static double faceCompare(String img_1, String img_2) throws Exception {

        Mat mat_1 = convMat(img_1);
        Mat mat_2 = convMat(img_2);
        if(mat_1 != null && mat_2 != null) {
            Mat hist_1 = new Mat();
            Mat hist_2 = new Mat();

            //颜色范围
            MatOfFloat ranges = new MatOfFloat(0f, 256f);
            //直方图大小， 越大匹配越精确 (越慢)
            MatOfInt histSize = new MatOfInt(1000);

            Imgproc.calcHist(Arrays.asList(mat_1), new MatOfInt(0), new Mat(), hist_1, histSize, ranges);
            Imgproc.calcHist(Arrays.asList(mat_2), new MatOfInt(0), new Mat(), hist_2, histSize, ranges);

            // CORREL 相关系数
            double res = Imgproc.compareHist(hist_1, hist_2, Imgproc.CV_COMP_CORREL);
            return res;
        } else {
            return 0.0;
        }
    }

    /**
     * 拼接融合两张图像并保存到指定路径，类似缝合图像的效果（两周图像有重叠的部分，类似分别拍多张图像后合并）
     * @param imgPath1 第一张图像的文件路径
     * @param imgPath2 第二张图像的文件路径
     * @param resultPath 拼接后图像的保存路径
     */
    public static boolean stitchImages(String imgPath1, String imgPath2, String resultPath) {
        // 读取两张图像
        Mat img1 = Imgcodecs.imread(imgPath1);
        Mat img2 = Imgcodecs.imread(imgPath2);

        // 检查图像是否读取成功
        if (img1.empty() || img2.empty()) {
            return false;
        }

        // 特征检测器和描述符
        ORB orb = ORB.create();
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint(), keypoints2 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat(), descriptors2 = new Mat();

        // 检测关键点和描述符
        orb.detectAndCompute(img1, new Mat(), keypoints1, descriptors1);
        orb.detectAndCompute(img2, new Mat(), keypoints2, descriptors2);

        // 特征匹配
        DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors1, descriptors2, matches);

        // 筛选匹配项
        List<DMatch> matchList = matches.toList();
        double maxDist = 0, minDist = 100;
        for (DMatch match : matchList) {
            double dist = match.distance;
            if (dist < minDist) minDist = dist;
            if (dist > maxDist) maxDist = dist;
        }

        LinkedList<DMatch> goodMatchesList = new LinkedList<>();
        for (DMatch match : matchList) {
            if (match.distance <= Math.max(2 * minDist, 30.0)) {
                goodMatchesList.add(match);
            }
        }

        // 提取好的匹配点
        List<KeyPoint> keypoints1List = keypoints1.toList();
        List<KeyPoint> keypoints2List = keypoints2.toList();
        LinkedList<Point> imgPoints1 = new LinkedList<>();
        LinkedList<Point> imgPoints2 = new LinkedList<>();

        for (DMatch goodMatch : goodMatchesList) {
            imgPoints1.addLast(keypoints1List.get(goodMatch.queryIdx).pt);
            imgPoints2.addLast(keypoints2List.get(goodMatch.trainIdx).pt);
        }

        MatOfPoint2f imgPoints1Mat = new MatOfPoint2f();
        imgPoints1Mat.fromList(imgPoints1);
        MatOfPoint2f imgPoints2Mat = new MatOfPoint2f();
        imgPoints2Mat.fromList(imgPoints2);

        // 计算单应性矩阵
        Mat H = Calib3d.findHomography(imgPoints2Mat, imgPoints1Mat, Calib3d.RANSAC, 5);

        // 透视变换
        Mat result = new Mat();
        Imgproc.warpPerspective(img2, result, H, new Size(img1.cols() + img2.cols(), img1.rows() + img2.rows()));

        // 将第一张图像拷贝到结果图像上
        Mat half = new Mat(result, new Rect(0, 0, img1.cols(), img1.rows()));
        img1.copyTo(half);

        // 将拼接结果转换为灰度图
        Mat grayResult = new Mat();
        Imgproc.cvtColor(result, grayResult, Imgproc.COLOR_BGR2GRAY);

        // 找到实际图像区域的最小边界矩形
        Mat mask = new Mat();
        Core.inRange(grayResult, new Scalar(1), new Scalar(255), mask); // 生成二值掩码图像
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // 手动计算边界矩形的联合区域
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            if (rect.x < minX) minX = rect.x;
            if (rect.y < minY) minY = rect.y;
            if (rect.x + rect.width > maxX) maxX = rect.x + rect.width;
            if (rect.y + rect.height > maxY) maxY = rect.y + rect.height;
        }

        Rect boundingRect = new Rect(minX, minY, maxX - minX, maxY - minY);

        // 裁剪掉黑色边框
        Mat croppedResult = new Mat(result, boundingRect);

        // 保存结果图像
        Imgcodecs.imwrite(resultPath, croppedResult);

        return true;
    }

    /**
     * CLAHE（限制对比度自适应直方图均衡化）技术进行图像的色彩矫正
     *
     * @param imgPath 输入图像
     * @param resultPath 输出图像
     * @return 矫正后的图像
     */
    public static boolean correctColor(String imgPath, String resultPath) {
        // 读取图像
        Mat image = Imgcodecs.imread(imgPath);
        // 检查图像是否读取成功
        if (image.empty()) {
            return false;
        }
        Mat labImage = new Mat();
        // 转换到Lab颜色空间
        Imgproc.cvtColor(image, labImage, Imgproc.COLOR_BGR2Lab);
        // 拆分Lab通道
        List<Mat> labPlanes = new ArrayList<>(3);
        Core.split(labImage, labPlanes);
        // 对L通道进行CLAHE均衡化
        CLAHE clahe = Imgproc.createCLAHE();
        clahe.setClipLimit(2.0);
        Mat lChannel = labPlanes.get(0);
        clahe.apply(lChannel, lChannel);
        // 合并Lab通道
        Core.merge(labPlanes, labImage);
        // 转回BGR颜色空间
        Mat correctedImage = new Mat();
        Imgproc.cvtColor(labImage, correctedImage, Imgproc.COLOR_Lab2BGR);
        // 保存结果图像
        Imgcodecs.imwrite(resultPath, correctedImage);
        return true;
    }

    /**
     * "灰度世界" 白平衡算法
     * @param imgPath
     * @param resultPath
     * @return
     */
    public static boolean whiteBalance(String imgPath, String resultPath) {
        Mat image = Imgcodecs.imread(imgPath);
        if (image.empty()) {
            return false;
        }

        List<Mat> splitMat = new ArrayList<>();
        Core.split(image, splitMat);

        double meanB = Core.mean(splitMat.get(0)).val[0];
        double meanG = Core.mean(splitMat.get(1)).val[0];
        double meanR = Core.mean(splitMat.get(2)).val[0];

        double meanGray = (meanB + meanG + meanR) / 3.0;
        double kB = meanGray / meanB;
        double kG = meanGray / meanG;
        double kR = meanGray / meanR;

        Core.multiply(splitMat.get(0), new Scalar(kB), splitMat.get(0));
        Core.multiply(splitMat.get(1), new Scalar(kG), splitMat.get(1));
        Core.multiply(splitMat.get(2), new Scalar(kR), splitMat.get(2));

        Mat balancedImage = new Mat();
        Core.merge(splitMat, balancedImage);

        if (!Imgcodecs.imwrite(resultPath, balancedImage)) {
            return false;
        }

        return true;
    }

    /**
     * 灰度化人脸
     *
     * @param img
     * @return
     */
    private static Mat convMat(String img) throws Exception {
        String haarcascade_frontalface_alt_path = "";
        URL systemResource = ClassLoader.getSystemResource("haarcascade_frontalface_alt.xml");
        if(systemResource != null && systemResource.toURI() != null
                && systemResource.toURI().getPath() != null && systemResource.toURI().getPath().startsWith("/")) {
            haarcascade_frontalface_alt_path = systemResource.toURI().getPath().substring(1);
        }
        if(!haarcascade_frontalface_alt_path.equals("")) {
            CascadeClassifier faceDetector = new CascadeClassifier(haarcascade_frontalface_alt_path);

            Mat image0 = Imgcodecs.imread(img);
            Mat image1 = new Mat();
            // 灰度化
            Imgproc.cvtColor(image0, image1, Imgproc.COLOR_BGR2GRAY);
            // 探测人脸
            MatOfRect faceDetections = new MatOfRect();
            faceDetector.detectMultiScale(image1, faceDetections);
            // rect中人脸图片的范围
            for (Rect rect : faceDetections.toArray()) {
                Mat face = new Mat(image1, rect);
                return face;
            }
        }
        return null;
    }

    /**
     * 拼接 两张图像根据坐标点进行拼接.
     * @param imageA
     * @param imageB
     * @param iA
     * @param jA
     * @param iB
     * @param jB
     * @return
     */
    public static Mat stitchImagesByPoint(Mat imageA, Mat imageB, Point iA, Point jA, Point iB, Point jB) {
        // 计算 A 和 B 图像的旋转角度和缩放比例
        Mat transformMatrix = calculateTransformation(iA, jA, iB, jB);

        // 先将 B 图像按计算的缩放比例进行缩放
        double scale = calculateScale(iA, jA, iB, jB);
        Mat resizedB = new Mat();

        // 使用更高质量的插值方法进行缩放
        Imgproc.resize(imageB, resizedB, new Size(imageB.cols() * scale, imageB.rows() * scale), 0, 0, Imgproc.INTER_CUBIC);

        // 应用旋转变换
        Mat transformedB = new Mat();
        Imgproc.warpAffine(resizedB, transformedB, transformMatrix, resizedB.size(), Imgproc.INTER_CUBIC, BORDER_CONSTANT, new Scalar(255, 255, 255));  // 使用 INTER_CUBIC 插值，并且设置背景色为白色

        // 创建一个足够大的矩阵来容纳拼接后的图像（左右拼接）
        int width = imageA.cols() + transformedB.cols();  // 水平方向拼接
        int height = Math.max(imageA.rows(), transformedB.rows());  // 高度取两者的最大值
        Mat stitchedImage = new Mat(height, width, imageA.type());

        // 将图像 A 和变换后的图像 B 粘贴到拼接图像中
        imageA.copyTo(stitchedImage.rowRange(0, imageA.rows()).colRange(0, imageA.cols()));
        transformedB.copyTo(stitchedImage.rowRange(0, transformedB.rows()).colRange(imageA.cols(), width));

        return stitchedImage;
    }

    public static Mat eye(int rows, int cols, int type) {
        return Mat.eye(rows, cols, type);
    }

    private static double calculateScale(Point iA, Point jA, Point iB, Point jB) {
        // 计算 A 和 B 图像的缩放比例
        double distanceA = Math.sqrt(Math.pow(jA.x - iA.x, 2) + Math.pow(jA.y - iA.y, 2)); // A 图像上 i 和 j 点的距离
        double distanceB = Math.sqrt(Math.pow(jB.x - iB.x, 2) + Math.pow(jB.y - iB.y, 2)); // B 图像上 i 和 j 点的距离

        // 计算缩放比例
        double scale = distanceA / distanceB;
        return scale;
    }

    private static Mat calculateTransformation(Point iA, Point jA, Point iB, Point jB) {
        // 计算 A 和 B 图像的旋转角度差
        double angleA = Math.atan2(jA.y - iA.y, jA.x - iA.x);
        double angleB = Math.atan2(jB.y - iB.y, jB.x - iB.x);
        double angleDiff = angleB - angleA;

        // 计算旋转矩阵
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(iB, angleDiff * 180 / Math.PI, 1); // 旋转矩阵，缩放比例暂时为 1

        return rotationMatrix;
    }

    /**
     * 如下使用 opencv 读取图像为 BufferedImage 的方法，需要依赖如下坐标
     *         <dependency>
     *             <groupId>org.bytedeco</groupId>
     *             <artifactId>javacv-platform</artifactId>
     *             <version>1.5.12</version>
     *         </dependency>
     * @param path
     * @return
     */
//    public static BufferedImage opencvRead(String path) {
//        try {
//            byte[] data = Files.readAllBytes(Paths.get(path));
//            if (data.length == 0) return null;
//
//            // Create Mat that wraps raw bytes (no copy)
//            Mat encoded = new Mat(1, data.length, opencv_core.CV_8UC1);
//            encoded.data().put(data);
//
//            // Decode image (still no disk I/O)
//            Mat image = opencv_imgcodecs.imdecode(encoded, opencv_imgcodecs.IMREAD_UNCHANGED);
//            encoded.close(); // release early
//
//            if (image == null || image.empty()) {
//                image.close();
//                return null;
//            }
//
//            int width = image.cols();
//            int height = image.rows();
//            int channels = image.channels();
//
//            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
//            int[] pixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();
//
//            UByteIndexer idx = image.createIndexer();
//
//            if (channels == 4) {
//                for (int y = 0; y < height; y++) {
//                    for (int x = 0; x < width; x++) {
//                        int b = idx.get(y, x, 0) & 0xFF;
//                        int g = idx.get(y, x, 1) & 0xFF;
//                        int r = idx.get(y, x, 2) & 0xFF;
//                        int a = idx.get(y, x, 3) & 0xFF;
//                        pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
//                    }
//                }
//            } else if (channels == 3) {
//                for (int y = 0; y < height; y++) {
//                    for (int x = 0; x < width; x++) {
//                        int b = idx.get(y, x, 0) & 0xFF;
//                        int g = idx.get(y, x, 1) & 0xFF;
//                        int r = idx.get(y, x, 2) & 0xFF;
//                        pixels[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
//                    }
//                }
//            } else {
//                // grayscale
//                for (int y = 0; y < height; y++) {
//                    for (int x = 0; x < width; x++) {
//                        int gray = idx.get(y, x) & 0xFF;
//                        pixels[y * width + x] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
//                    }
//                }
//            }
//
//            idx.close();
//            image.close();
//            return result;
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

}
