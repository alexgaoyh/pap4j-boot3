package cn.net.pap.common.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class FontSubsetUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(FontSubsetUtilsTest.class);

    @DisplayName("生成字体子集")
    @Test
    public void test1() {
        java.io.File sourceFontFile = null;
        try {
            sourceFontFile = TestResourceUtil.getFile("simfang.ttf");
            Path sourceFont = sourceFontFile.toPath();
            if(sourceFont.toFile().exists()) {
                String textToExtract = "这是一个字体子集化测试𪚥";
                Path targetFont = null;
                try {
                    targetFont = java.nio.file.Files.createTempFile("simfang2", ".ttf");
                    FontSubsetUtils.createSubset(sourceFont, targetFont, textToExtract);
                    log.info("字体子集化成功，已保存至: {}", targetFont);
                } catch (IOException e) {
                    log.error("字体处理失败: ", e);
                } finally {
                    if (targetFont != null) {
                        try {
                            java.nio.file.Files.deleteIfExists(targetFont);
                        } catch (IOException e) {
                            log.error("删除临时文件失败: ", e);
                        }
                    }
                }
            }
        } finally {
            if (sourceFontFile != null && sourceFontFile.exists()) {
                sourceFontFile.delete();
            }
        }
    }


}
