package cn.net.pap.common.datastructure.unicode;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class UnicodeEscapeTest {

    private static final Logger log = LoggerFactory.getLogger(UnicodeEscapeTest.class);

    @Test
    public void testHanCharacterDifference() {
        // 扩展区 E 的字符：𫠠 (U+2E820)
        // 在 Java 中，增补平面字符需要用代理对表示，或直接用字符串
        String extEChar = "\uD87A\uDC20";

        // 1. 传统硬编码正则 (只覆盖基础块)
        String legacyRegex = "[\\u4e00-\\u9fa5]";
        assertFalse(Pattern.compile(legacyRegex).matcher(extEChar).find(),
                "传统正则在任何版本都匹配不到扩展区字符");

        // 2. Unicode 属性正则 (依赖 JDK 内置 Unicode 版本)
        // 注意：Java 中匹配汉字属性通常用 \p{IsHan} 或 \p{script=Han}
        String unicodeRegex = "\\p{IsHan}";

        boolean isMatch = Pattern.compile(unicodeRegex).matcher(extEChar).find();

        log.info("当前 JDK 版本: " + System.getProperty("java.version"));
        log.info("字符 " + extEChar + " 是否被识别为汉字: " + isMatch);

        // 结果预测：
        // JDK 8: isMatch 为 false (Unicode 6.2 不含扩展区 E)
        // JDK 17: isMatch 为 true (Unicode 13.0 包含扩展区 E)
    }

}
