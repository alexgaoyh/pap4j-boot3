package cn.net.pap.common.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class FontSubsetUtilsTest {

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
                    System.out.println("字体子集化成功，已保存至: " + targetFont);
                } catch (IOException e) {
                    System.err.println("字体处理失败: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (targetFont != null) {
                        try {
                            java.nio.file.Files.deleteIfExists(targetFont);
                        } catch (IOException e) {
                            e.printStackTrace();
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
