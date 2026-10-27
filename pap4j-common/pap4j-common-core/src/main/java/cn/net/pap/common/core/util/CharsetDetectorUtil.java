package cn.net.pap.common.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * 文本字符编码探测工具类
 * <p>
 * 基于纯 JDK 实现，通过针对中文字符（包含 CJK 基本区及扩展区 A~I）的解码打分，
 * 智能探测无 BOM 字节流的字符编码（如 GBK、GB2312、Big5）。
 * </p>
 */
public final class CharsetDetectorUtil {

    private static final Logger log = LoggerFactory.getLogger(CharsetDetectorUtil.class);

    private static final Charset CHARSET_GBK = Charset.forName("GBK");
    private static final Charset CHARSET_GB2312 = Charset.forName("GB2312");
    private static final Charset CHARSET_BIG5 = Charset.forName("Big5");

    private CharsetDetectorUtil() {
        // 私有构造器，防止实例化
    }

    /**
     * 猜测给定字节数组的中文编码格式。
     *
     * @param data 字节数组
     * @return 识别出的编码名称（"GBK", "GB2312", "Big5"），若输入为空则返回 null
     * @throws IOException 当解码计算发生 IO 异常时抛出
     */
    public static String guessEncoding(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return null;
        }

        int scoreGbk = scoreDecode(data, CHARSET_GBK);
        int scoreGb2312 = scoreDecode(data, CHARSET_GB2312);
        int scoreBig5 = scoreDecode(data, CHARSET_BIG5);

        // 优先选择 GBK，其次为 GB2312，否则为 Big5
        if (scoreGbk > 0 && scoreGbk >= scoreBig5) {
            return "GBK";
        } else if (scoreGb2312 > 0 && scoreGb2312 >= scoreBig5) {
            return "GB2312";
        } else {
            return "Big5";
        }
    }

    /**
     * 使用指定字符集尝试解码并按中文字符统计打分。
     *
     * @param data    字节数组
     * @param charset 待尝试字符集
     * @return 成功解码的中文字符数量；若解码发生畸形或不可映射错误则返回 -1
     * @throws IOException 解码异常
     */
    public static int scoreDecode(byte[] data, Charset charset) throws IOException {
        if (data == null || charset == null) {
            return -1;
        }

        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String decoded = decoder.decode(ByteBuffer.wrap(data)).toString();
            int score = 0;
            int length = decoded.length();

            for (int offset = 0; offset < length; ) {
                int codePoint = decoded.codePointAt(offset);
                if (isChineseCharacter(codePoint)) {
                    score++;
                }
                offset += Character.charCount(codePoint);
            }
            return score;
        } catch (CharacterCodingException ignored) {
            log.debug("Charset {} decode failed for data: ", charset.name(), ignored);
            return -1;
        }
    }

    /**
     * 判断指定 Unicode CodePoint 是否属于中文字符。
     * <p>
     * 覆盖范围：
     * <ul>
     *     <li>CJK 统一表意文字基本区 (0x4E00 - 0x9FFF)</li>
     *     <li>CJK 扩展 A 区 (0x3400 - 0x4DBF)</li>
     *     <li>CJK 扩展 B 区 (0x20000 - 0x2A6DF)</li>
     *     <li>CJK 扩展 C 区 (0x2A700 - 0x2B73F)</li>
     *     <li>CJK 扩展 D 区 (0x2B740 - 0x2B81F)</li>
     *     <li>CJK 扩展 E 区 (0x2B820 - 0x2CEAF)</li>
     *     <li>CJK 扩展 F 区 (0x2CEB0 - 0x2EBEF)</li>
     *     <li>CJK 扩展 G 区 (0x30000 - 0x3134F)</li>
     *     <li>CJK 扩展 H 区 (0x31350 - 0x323AF)</li>
     *     <li>CJK 扩展 I 区 (0x2EBF0 - 0x2EE5F)</li>
     *     <li>CJK 兼容汉字区 (0xF900 - 0xFAFF)</li>
     *     <li>CJK 兼容汉字补充区 (0x2F800 - 0x2FA1F)</li>
     * </ul>
     * </p>
     *
     * @param codePoint 字符的 Unicode 码点
     * @return 若是中文字符返回 true，否则返回 false
     */
    public static boolean isChineseCharacter(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
                || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
                || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
                || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
                || (codePoint >= 0x2CEB0 && codePoint <= 0x2EBEF)
                || (codePoint >= 0x30000 && codePoint <= 0x3134F)
                || (codePoint >= 0x31350 && codePoint <= 0x323AF)
                || (codePoint >= 0x2EBF0 && codePoint <= 0x2EE5F)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
    }
}
