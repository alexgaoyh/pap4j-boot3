package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CharsetDetectorUtil 单元测试")
class CharsetDetectorUtilTest {

    @Test
    @DisplayName("测试中文字符码点判定")
    void testIsChineseCharacter() {
        // 常见汉字
        assertTrue(CharsetDetectorUtil.isChineseCharacter("中".codePointAt(0)));
        assertTrue(CharsetDetectorUtil.isChineseCharacter("文".codePointAt(0)));
        assertTrue(CharsetDetectorUtil.isChineseCharacter("测".codePointAt(0)));
        assertTrue(CharsetDetectorUtil.isChineseCharacter("试".codePointAt(0)));

        // 扩展区汉字
        assertTrue(CharsetDetectorUtil.isChineseCharacter(0x3400));
        assertTrue(CharsetDetectorUtil.isChineseCharacter(0x4DBF));
        assertTrue(CharsetDetectorUtil.isChineseCharacter(0x20000));

        // 英文字母与数字标点
        assertFalse(CharsetDetectorUtil.isChineseCharacter('A'));
        assertFalse(CharsetDetectorUtil.isChineseCharacter('z'));
        assertFalse(CharsetDetectorUtil.isChineseCharacter('1'));
        assertFalse(CharsetDetectorUtil.isChineseCharacter('!'));
        assertFalse(CharsetDetectorUtil.isChineseCharacter(' '));
    }

    @Test
    @DisplayName("测试 GBK 编码识别")
    void testGuessEncodingGbk() throws IOException {
        String text = "你好，世界！这是一段用于测试中文字符编码识别的文本。";
        byte[] gbkBytes = text.getBytes(Charset.forName("GBK"));

        String detected = CharsetDetectorUtil.guessEncoding(gbkBytes);
        assertNotNull(detected);
        assertTrue("GBK".equalsIgnoreCase(detected) || "GB2312".equalsIgnoreCase(detected));
    }

    @Test
    @DisplayName("测试 Big5 编码识别")
    void testGuessEncodingBig5() throws IOException {
        String text = "繁體中文測試文本，這是一段用於測試編碼的字串。";
        byte[] big5Bytes = text.getBytes(Charset.forName("Big5"));

        String detected = CharsetDetectorUtil.guessEncoding(big5Bytes);
        assertNotNull(detected);
        assertEquals("Big5", detected);
    }

    @Test
    @DisplayName("测试空输入与边界条件")
    void testEdgeCases() throws IOException {
        assertNull(CharsetDetectorUtil.guessEncoding(null));
        assertNull(CharsetDetectorUtil.guessEncoding(new byte[0]));

        assertEquals(-1, CharsetDetectorUtil.scoreDecode(null, StandardCharsets.UTF_8));
        assertEquals(-1, CharsetDetectorUtil.scoreDecode(new byte[]{1, 2, 3}, null));
    }
}
