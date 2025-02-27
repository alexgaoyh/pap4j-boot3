package cn.net.pap.common.datastructure.url;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ReversibleShortUrl {

    private static final Base64.Encoder b64Encoder = Base64.getUrlEncoder();

    private static final Base64.Decoder b64Decoder = Base64.getUrlDecoder();

    /**
     * 标准的默认的 Char 数组
     */
    private static final char[] STANDARD_CHARS =
            new StringBuilder("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_").toString().toCharArray();

    /**
     * 翻转一下默认的 Char 数组
     */
    private static final char[] CUSTOM_CHARS =
            new StringBuilder("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_0123456789").reverse().toString().toCharArray();

    /**
     * 编码
     *
     * @param url
     * @return
     */
    public static String encode(String url) {
        String compressed = b64Encoder.encodeToString(url.getBytes(StandardCharsets.UTF_8));
        return compressed;
    }

    /**
     * 解码
     *
     * @param shortUrl
     * @return
     */
    public static String decode(String shortUrl) {
        return new String(b64Decoder.decode(shortUrl), StandardCharsets.UTF_8);
    }

    /**
     * 这里做一个取巧，因为原始的 Base64 编解码 的字符顺序是定义好的.
     * 为了增加破解的难度，这里做一个字符的替换，将原始的字符进行替换为新的.
     * @param url
     * @return
     */
    public static String encodeReverse(String url) {
        return replaceCharsSafe(encode(url), STANDARD_CHARS, CUSTOM_CHARS);
    }

    public static String decodeReverse(String url) {
        String restored = replaceCharsSafe(url, CUSTOM_CHARS, STANDARD_CHARS);
        return decode(restored);
    }

    // 修改替换方法，增加校验
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
