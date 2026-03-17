package cn.net.pap.common.boofcv;

import boofcv.alg.misc.ImageMiscOps;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.*;
import cn.net.pap.common.boofcv.util.TempDirUtils;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BoofcvUtilTest {

    static {
        // System.setProperty("temp.dir.utils.dev", "true");
    }

    // ========== 辅助方法 ==========

    /**
     * 提取公共的图片加载逻辑：统一从 resources/input.jpg 读取
     * 避免每个测试方法中存在大量重复的加载与断言代码
     */
    private BufferedImage loadTestImage() throws Exception {
        URL resourceUrl = getClass().getClassLoader().getResource("input.jpg");
        assertNotNull(resourceUrl, "Test image 'input.jpg' not found in resources!");
        String imagePath = Paths.get(resourceUrl.toURI()).toAbsolutePath().toString();
        BufferedImage inputImage = UtilImageIO.loadImage(imagePath);
        assertNotNull(inputImage, "Failed to load image into BufferedImage");
        return inputImage;
    }

    // ========== 单元测试 ==========

    @Test
    public void brightnessAndContrastedImageTest() throws Exception {
        BufferedImage inputImage = loadTestImage();

        TempDirUtils.withTempDir("boofcv_test_brightness_contrast_", (Path tempDir) -> {
            BufferedImage brightenedImage = BoofcvUtil.adjustBrightness(inputImage, 0.5f);
            Path brightenedPath = tempDir.resolve("brightened_origin.jpg").toAbsolutePath();
            UtilImageIO.saveImage(brightenedImage, brightenedPath.toString());
            assertTrue(Files.exists(brightenedPath), "Brightened image should be saved successfully");

            BufferedImage brightenedImage2 = BoofcvUtil.adjustBrightness2(inputImage, 0.5f);
            Path brightenedPath2 = tempDir.resolve("brightened_origin.jpg").toAbsolutePath();
            UtilImageIO.saveImage(brightenedImage2, brightenedPath2.toString());
            assertTrue(Files.exists(brightenedPath2), "Brightened image should be saved successfully");

            BufferedImage contrastedImage = BoofcvUtil.adjustContrast(inputImage, 100);
            Path contrastedPath = tempDir.resolve("contrasted_origin.jpg").toAbsolutePath();
            UtilImageIO.saveImage(contrastedImage, contrastedPath.toString());
            assertTrue(Files.exists(contrastedPath), "Contrasted image should be saved successfully");

            BufferedImage contrastedImage2 = BoofcvUtil.adjustContrast2(inputImage, 100);
            Path contrastedPath2 = tempDir.resolve("contrasted_origin.jpg").toAbsolutePath();
            UtilImageIO.saveImage(contrastedImage2, contrastedPath2.toString());
            assertTrue(Files.exists(contrastedPath2), "Contrasted image should be saved successfully");
        });
    }

    @Test
    public void adjustTwoTest() throws Exception {
        BufferedImage inputImage = loadTestImage();

        TempDirUtils.withTempDir("boofcv_test_adjustTwo_", (Path tempDir) -> {
            BufferedImage brightenedImage = BoofcvUtil.adjustTwo(inputImage, 0f, 100f);
            assertNotNull(brightenedImage, "Adjusted image should not be null");

            Path outputPath = tempDir.resolve("adjustTwo_out.jpg").toAbsolutePath();
            UtilImageIO.saveImage(brightenedImage, outputPath.toString());
            assertTrue(Files.exists(outputPath), "Adjusted image should be saved successfully");
        });
    }

    @Test
    public void rotateTest() throws Exception {
        BufferedImage inputImage = loadTestImage();

        TempDirUtils.withTempDir("boofcv_test_rotate_", (Path tempDir) -> {
            InterleavedF32 imageInterleaved = new InterleavedF32();
            ConvertBufferedImage.convertFromInterleaved(inputImage, imageInterleaved, true);

            // 旋转
            InterleavedF32 rotatedF32 = ImageMiscOps.rotateCW(imageInterleaved, null);
            assertNotNull(rotatedF32, "Rotated image should not be null");

            Path outputPath = tempDir.resolve("rotated_out.jpg").toAbsolutePath();
            UtilImageIO.saveImage(rotatedF32, outputPath.toString());
            assertTrue(Files.exists(outputPath), "Rotated image should be saved successfully");
        });
    }

    @Test
    public void cropTest() throws Exception {
        BufferedImage inputImage = loadTestImage();

        TempDirUtils.withTempDir("boofcv_test_crop_", (Path tempDir) -> {
            // 注意：请确保 input.jpg 的分辨率足够大 (宽度和高度 > 1100)，否则此裁剪参数会抛出异常
            BufferedImage crop = BoofcvUtil.crop(inputImage, 100, 100, 400, 400);
            assertNotNull(crop, "Cropped image should not be null");

            Path outputPath = tempDir.resolve("cropped_out.jpg").toAbsolutePath();
            UtilImageIO.saveImage(crop, outputPath.toString());
            assertTrue(Files.exists(outputPath), "Cropped image should be saved successfully");
        });
    }

    @Test
    public void base64Test() throws Exception {
        BufferedImage inputImage = loadTestImage();

        // 纯内存转换测试，不涉及文件落盘，所以不需要放进 TempDirUtils 里面
        String jpgBase64 = BoofcvUtil.getBase64(inputImage, "jpg");

        assertNotNull(jpgBase64, "Base64 string should not be null");
        assertTrue(jpgBase64.length() > 0, "Base64 string should not be empty");
    }

}