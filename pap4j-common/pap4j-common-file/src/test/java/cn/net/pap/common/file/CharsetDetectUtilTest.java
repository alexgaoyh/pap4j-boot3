package cn.net.pap.common.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class CharsetDetectUtilTest {

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        // 每次测试前创建一个临时文件
        tempFile = Files.createTempFile("charset_test", ".txt");
    }

    @AfterEach
    void tearDown() throws IOException {
        // 测试结束后清理临时文件，保持环境干净
        Files.deleteIfExists(tempFile);
    }

    /**
     * 辅助方法：以指定编码将字符串写入临时文件
     */
    private void writeToFile(String content, String charsetName) throws IOException {
        Files.write(tempFile, content.getBytes(Charset.forName(charsetName)));
    }

    /**
     * 测试 1：标准的 UTF-8 文件
     * 预期：ICU4J 直接准确识别，不进入 guessEncoding 降级逻辑
     */
    @Test
    public void testDetect_UTF8() throws Exception {
        writeToFile("《史记》：天地玄黄，宇宙洪荒。这是一段用于测试古籍数字化的UTF-8文本。", "UTF-8");

        // 假设你的工具类名为 ReadTxtToStringUtil
        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        assertEquals("UTF-8", detectedCharset, "UTF-8 编码应该被 ICU4J 直接准确识别");
    }

    /**
     * 测试 2：触发 Big5 拦截重计算
     * 预期：ICU4J 识别出 Big5，命中你的 if ("Big5".equals(name)) 拦截，
     * 然后进入 guessEncoding 进行重新打分，最终依然胜出返回 Big5。
     */
    @Test
    public void testDetect_Big5_TriggerFallback() throws Exception {
        // 使用繁体中文特征字
        writeToFile("這是一個繁體中文測試文檔。天地玄黃，宇宙洪荒。", "Big5");

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        assertEquals("Big5", detectedCharset, "Big5 编码应该在经过 guessEncoding 打分后依然返回 Big5");
    }

    /**
     * 测试 3：短 GBK 文本触发 ICU4J 误判 (ISO-8859-1) 并成功被纠正
     * 预期：ICU4J 对极短的无 BOM 的 GBK 中文经常误判为 ISO-8859-1 或 ISO-8859-7，
     * 这将命中你的拦截条件，进入 guessEncoding，最终通过统计中文字符数纠正为 GBK。
     */
    @Test
    public void testDetect_GBK_CorrectedFromISO8859() throws Exception {
        // 构造一段简短的 GBK 中文，通常 ICU4J 会对此类无特征短文本产生误判
        writeToFile("中文测试", "GBK");

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        // 由于你的 guessEncoding 逻辑中优先选择 GBK (兼容 GB2312)
        assertTrue("GBK".equals(detectedCharset) || "GB2312".equals(detectedCharset) || "GB18030".equals(detectedCharset), "短 GBK 文本应该被 guessEncoding 成功挽救并识别为 GBK 系列，实际为：" + detectedCharset);
    }

    /**
     * 测试 4：包含较多生僻字的 GBK/GB2312 文本
     * 预期：验证你新修改的 codePoint 遍历逻辑是否能正常工作且不抛出异常。
     */
    @Test
    public void testDetect_GBK_WithRareChars() throws Exception {
        // 包含一些在 GBK 范围内的生僻/繁体字
        writeToFile("龘靐齉爩，古籍数字化生僻字测试。", "GBK");

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        assertTrue("GBK".equals(detectedCharset) || "GB18030".equals(detectedCharset), "包含生僻字的 GBK 文本应该被正确识别");
    }

    /**
     * 测试 5：空文件测试
     * 预期：ICU4J 可能会返回 null，代码应健壮处理不抛出 NullPointerException
     */
    @Test
    public void testDetect_EmptyFile() throws Exception {
        writeToFile("", "UTF-8"); // 写入空内容

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        // 具体返回 null 还是某些默认值取决于 ICU4J，但关键是不能报错
        assertDoesNotThrow(() -> ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString()), "空文件不应导致异常抛出");
    }

    /**
     * 辅助方法：将特定的 BOM 头和文本内容合并写入临时文件
     */
    private void writeToFileWithBOM(byte[] bom, String content, String charsetName) throws IOException {
        byte[] textBytes = content.getBytes(Charset.forName(charsetName));
        byte[] fullBytes = new byte[bom.length + textBytes.length];

        // 拼接 BOM 和 实际文本字节
        System.arraycopy(bom, 0, fullBytes, 0, bom.length);
        System.arraycopy(textBytes, 0, fullBytes, bom.length, textBytes.length);

        Files.write(tempFile, fullBytes);
    }

    /**
     * 测试 6：带有 BOM 头的 UTF-8 文件
     * 预期：ICU4J 捕获到 EF BB BF，直接返回 UTF-8，不进入重算逻辑
     */
    @Test
    public void testDetect_UTF8_BOM() throws Exception {
        // UTF-8 的 BOM 头
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        writeToFileWithBOM(bom, "带 BOM 的 UTF-8 测试文本，天地玄黄。", "UTF-8");

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        assertEquals("UTF-8", detectedCharset, "UTF-8 BOM 应该被 ICU4J 准确识别为 UTF-8");
    }

    /**
     * 测试 7：带有 BOM 头的 UTF-16LE 文件 (Windows 默认的 Unicode)
     * 预期：ICU4J 捕获到 FF FE，直接返回 UTF-16LE
     */
    @Test
    public void testDetect_UTF16LE() throws Exception {
        // UTF-16LE 的 BOM 头 (Little Endian)
        byte[] bom = {(byte) 0xFF, (byte) 0xFE};
        writeToFileWithBOM(bom, "UTF-16LE 编码测试，宇宙洪荒。", "UTF-16LE");

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        assertEquals("UTF-16LE", detectedCharset, "包含 BOM 的 UTF-16LE 应该被准确识别");
    }

    /**
     * 测试 8：带有 BOM 头的 UTF-16BE 文件
     * 预期：ICU4J 捕获到 FE FF，直接返回 UTF-16BE
     */
    @Test
    public void testDetect_UTF16BE() throws Exception {
        // UTF-16BE 的 BOM 头 (Big Endian)
        byte[] bom = {(byte) 0xFE, (byte) 0xFF};
        writeToFileWithBOM(bom, "UTF-16BE 编码测试，日月盈昃。", "UTF-16BE");

        String detectedCharset = ReadTxtToStringUtil.detectCharsetUsingICU4J(tempFile.toString());

        assertEquals("UTF-16BE", detectedCharset, "包含 BOM 的 UTF-16BE 应该被准确识别");
    }

}
