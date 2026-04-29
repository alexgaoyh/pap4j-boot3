package cn.net.pap.common.opencv.jpeg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.opencv.util.TempDirUtils;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpegSubsamplingUtilTest {
    private static final Logger log = LoggerFactory.getLogger(JpegSubsamplingUtilTest.class);

//    static {
//        System.setProperty("temp.dir.utils.dev", "true");
//    }

    @Test
    public void testDynamicSubsamplingWithTempDir() throws Exception {
        // 创建测试图像
        BufferedImage image = JpegSubsamplingUtil.createTestImage(800, 600);

        // 使用你的工具类创建一个临时目录，测试结束后会自动清理
        // 使用 Function<Path, T> 的重载版本，方便直接向上抛出 checked 异常 (IOException)
        TempDirUtils.withTempDir("jpeg-subsample-test-", dir -> {

            try {
                Path outputFile420 = dir.resolve("dynamic_subsampled_420.jpg");
                Path outputFile444 = dir.resolve("dynamic_subsampled_444.jpg");

                // 测试 1：高画质 (0.8) 下，强制开启 2x2 子采样 (4:2:0)
                JpegSubsamplingUtil.writeJpegWithSubsampling(image, outputFile420, 0.80f, SubsamplingMode.YUV_420);
                log.info("{}", "成功生成 4:2:0 图像: " + Files.size(outputFile420) + " bytes");
                assertTrue(Files.exists(outputFile420) && Files.size(outputFile420) > 0);

                // 测试 2：同样的高画质 (0.9) 下，强制关闭子采样 (4:4:4)
                JpegSubsamplingUtil.writeJpegWithSubsampling(image, outputFile444, 0.9f, SubsamplingMode.YUV_444);
                log.info("{}", "成功生成 4:4:4 图像: " + Files.size(outputFile444) + " bytes");
                assertTrue(Files.exists(outputFile444) && Files.size(outputFile444) > 0);

                // --- 质量（Quality）验证逻辑 ---
                int dqt420 = JpegSubsamplingUtil.getExactJpegQuality(outputFile420.toFile());
                int dqt444 = JpegSubsamplingUtil.getExactJpegQuality(outputFile444.toFile());
                assertTrue(dqt420 == 80 && dqt444 == 90);

                SubsamplingMode subsamplingMode420 = JpegSubsamplingUtil.readSubsamplingMode(outputFile420.toFile());
                SubsamplingMode subsamplingMode444 = JpegSubsamplingUtil.readSubsamplingMode(outputFile444.toFile());
                assertTrue(subsamplingMode420 == SubsamplingMode.YUV_420);
                assertTrue(subsamplingMode444 == SubsamplingMode.YUV_444);

                // 断言：在相同画质参数下，开启子采样(4:2:0)的文件体积应该明显小于不开启(4:4:4)的体积
                assertTrue(Files.size(outputFile420) < Files.size(outputFile444),
                        "开启 2x2 子采样的图像文件大小应该更小");

                return null; // 满足 Function 的返回值要求
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        image.flush();
    }


}
