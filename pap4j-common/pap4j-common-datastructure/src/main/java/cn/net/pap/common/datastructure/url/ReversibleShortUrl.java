package cn.net.pap.common.datastructure.url;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * <p><strong>ReversibleShortUrl</strong> 提供了将 URL 编码和解码为可逆短字符串的实用方法。</p>
 *
 * <p>它使用标准的 Base64 编码以及自定义的字符替换映射来混淆结果，在标准 Base64 之上提供了额外的安全层。</p>
 *
 * <ul>
 *     <li>将 URL 编码为类似 Base64 的短字符串。</li>
 *     <li>将短字符串解码回原始 URL。</li>
 *     <li>使用字符替换进行混淆。</li>
 * </ul>
 */
public class ReversibleShortUrl {

    private static final Base64.Encoder b64Encoder = Base64.getUrlEncoder();

    private static final Base64.Decoder b64Decoder = Base64.getUrlDecoder();

    /**
     * <p>标准的 Base64 字符数组。</p>
     */
    private static final char[] STANDARD_CHARS =
            new StringBuilder("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_=").toString().toCharArray();

    /**
     * <p>用于混淆的标准字符数组的翻转版本。</p>
     */
    private static final char[] CUSTOM_CHARS =
            new StringBuilder("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_=0123456789").reverse().toString().toCharArray();

    /**
     * <p>使用标准 URL 安全的 Base64 对 URL 字符串进行编码。</p>
     *
     * @param url 要编码的原始 URL。
     * @return Base64 编码的字符串。
     */
    public static String encode(String url) {
        String compressed = b64Encoder.encodeToString(url.getBytes(StandardCharsets.UTF_8));
        return compressed;
    }

    /**
     * <p>将标准 URL 安全的 Base64 字符串解码回其原始 URL。</p>
     *
     * @param shortUrl Base64 编码的字符串。
     * @return 解码后的原始 URL。
     */
    public static String decode(String shortUrl) {
        return new String(b64Decoder.decode(shortUrl), StandardCharsets.UTF_8);
    }

    /**
     * <p>对 URL 进行编码，并用自定义字符集替换标准 Base64 字符以混淆结果。</p>
     * 
     * <p>这使得生成的短 URL 在没有对应的自定义字符映射的情况下更难解码。</p>
     *
     * @param url 要编码的原始 URL。
     * @return 混淆后的短 URL 字符串。
     */
    public static String encodeReverse(String url) {
        return replaceCharsSafe(encode(url), STANDARD_CHARS, CUSTOM_CHARS);
    }

    /**
     * <p>恢复混淆的字符并将 URL 解码回其原始形式。</p>
     *
     * @param url 混淆的短 URL 字符串。
     * @return 解码后的原始 URL。
     */
    public static String decodeReverse(String url) {
        String restored = replaceCharsSafe(url, CUSTOM_CHARS, STANDARD_CHARS);
        return decode(restored);
    }

    /**
     * <p>基于提供的源和目标映射数组安全地替换字符串中的字符。</p>
     *
     * @param input 要处理的输入字符串。
     * @param from  要替换的字符数组。
     * @param to    替换的字符数组。
     * @return 字符被替换后的新字符串。
     * @throws IllegalArgumentException 如果在源数组中找不到输入字符。
     */
    private static String replaceCharsSafe(String input, char[] from, char[] to) {
        char[] out = new char[input.length()];
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            boolean found = false;
            for (int j = 0; j < from.length; j++) {
                if (c == from[j]) {
                    out[i] = to[j];
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("非法字符: " + c);
            }
        }
        return new String(out);
    }

}