package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageUtil 单元测试")
class ImageUtilTest {

    @Test
    @DisplayName("测试生成 1x1 占位 JPEG")
    void testGenerateEmptyJpeg() {
        byte[] bytes = ImageUtil.generateEmptyJpeg();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        assertNotNull(ImageUtil.ONE_X_ONE_JPG);
        assertFalse(ImageUtil.ONE_X_ONE_JPG.isEmpty());
    }

    @Test
    @DisplayName("测试大图子采样与裁剪流程")
    void testSubsamplingAndCrop(@TempDir Path tempDir) throws IOException {
        // 创建测试大图 (600x400)
        BufferedImage original = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        File inputFile = tempDir.resolve("test_image.jpg").toFile();
        ImageIO.write(original, "jpg", inputFile);

        // 测试采样加载，目标宽度 150，预期采样率 ~4
        BufferedImage thumbnail = ImageUtil.getLowMemoryThumbnail(inputFile, 150);
        assertNotNull(thumbnail);
        assertTrue(thumbnail.getWidth() <= 300);

        // 测试路径入参形式
        BufferedImage thumb2 = ImageUtil.getLowMemoryThumbnail(inputFile.getAbsolutePath(), 150);
        assertNotNull(thumb2);

        // 测试区域裁剪
        File croppedFile = tempDir.resolve("cropped.jpg").toFile();
        Boolean cropSuccess = ImageUtil.cropImageCut(inputFile.getAbsolutePath(), croppedFile.getAbsolutePath(), 10, 10, 100, 100);
        assertTrue(cropSuccess);
        assertTrue(croppedFile.exists());
        assertTrue(croppedFile.length() > 0);
    }

    @Test
    @DisplayName("测试异常与边界处理")
    void testEdgeCases() {
        assertNull(ImageUtil.getLowMemoryThumbnail((String) null, 100));
        assertNull(ImageUtil.getLowMemoryThumbnail(new File("non-existent-img.jpg"), 100));
        assertFalse(ImageUtil.cropImageCut(null, null, 0, 0, 10, 10));
    }
}
