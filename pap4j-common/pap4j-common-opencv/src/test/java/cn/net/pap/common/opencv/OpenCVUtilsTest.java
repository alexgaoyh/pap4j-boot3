package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;
import java.util.List;

public class OpenCVUtilsTest {

    // @Test
    public void templateTest() {
        String sourceImg = "origin.jpg";
        String templateImg = "template.jpg";
        String targetPath = "target.jpg";
        OpenCVUtils.templateMatching(sourceImg, templateImg, targetPath);
    }

    // @Test
    public void searchingTest() {
        String image1Path = "image1.jpg";
        String image2Path = "image2.jpg";
        List<String> typeList = Arrays.asList(new String[]{"Histogram"});
        for (String type : typeList) {
            double v = OpenCVUtils.similarityImage(image1Path, image2Path, type);
            System.out.println(v);
        }
    }

    // @Test
    public void matOfKeyPointImageTest() {
        String imagePath = "pap.jpg";
        byte[] bytes = OpenCVUtils.matOfKeyPointImage(imagePath);
        byte[] bytes1 = OpenCVUtils.matOfKeyPointImage(imagePath);
        // 两个图像相同，equal >= 1
        double equal = SimilarityUtils.cosineSimilarity(bytes, bytes1);

        String imagePath2 = "pap-similarity.jpg";
        byte[] bytes2 = OpenCVUtils.matOfKeyPointImage(imagePath2);
        // 两个图像相似 (确定一个阈值)， 0 < similarity < 1
        double similarity = SimilarityUtils.cosineSimilarity(bytes, bytes2);

        String imagePath3 = "pap-different.jpg";
        byte[] bytes3 = OpenCVUtils.matOfKeyPointImage(imagePath3);
        // 两个图像不相似 (确定一个阈值)， 0 < different < 1
        double different = SimilarityUtils.cosineSimilarity(bytes, bytes3);

        // 持久化 图像特征
//        try (FileWriter fw = new FileWriter("pap.txt")) {
//            fw.write(new String(bytes, StandardCharsets.UTF_8));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

    // @Test
    public void dctWaterMarkConvert() {
        OpenCVUtils.dctWaterMarkEncode("origin.jpg", "alexgaoyh", "inner.jpg");
        OpenCVUtils.dctWaterMarkDecode("inner.jpg", "textWatermark.jpg");
    }

    /**
     * 图像尺寸需相同
     */
    // @Test
    public void imgCompare() {
        Mat image1 = OpenCVUtils.imread("pap1.jpg");
        Mat image2 = OpenCVUtils.imread("pap2.jpg");

        Imgproc.cvtColor(image1, image1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(image2, image2, Imgproc.COLOR_BGR2GRAY);

        Mat difference = new Mat();
        Core.absdiff(image1, image2, difference);

        Imgcodecs.imwrite("diff.jpg", difference);

        Imgproc.threshold(difference, difference, 128, 255, Imgproc.THRESH_BINARY);
        Imgcodecs.imwrite("tdiff.jpg", difference);
    }
}
