package cn.net.pap.common.core.util;

/**
 * 十六进制编解码工具类。
 * <p>基于纯 JDK 实现高性能、无第三方依赖的字节数组与十六进制字符串相互转换。</p>
 */
public final class HexUtil {

    private static final char[] DIGITS_LOWER = "0123456789abcdef".toCharArray();
    private static final char[] DIGITS_UPPER = "0123456789ABCDEF".toCharArray();

    private HexUtil() {
    }

    /**
     * 将字节数组转换为小写十六进制字符串。
     *
     * @param bytes 输入字节数组，若为 null 则返回 null
     * @return 小写十六进制字符串
     */
    public static String toHex(byte[] bytes) {
        return toHex(bytes, true);
    }

    /**
     * 将字节数组转换为十六进制字符串，可指定大小写。
     *
     * @param bytes       输入字节数组，若为 null 则返回 null
     * @param toLowerCase 是否转换为小写（true 为小写，false 为大写）
     * @return 十六进制字符串
     */
    public static String toHex(byte[] bytes, boolean toLowerCase) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length == 0) {
            return "";
        }

        char[] digits = toLowerCase ? DIGITS_LOWER : DIGITS_UPPER;
        char[] out = new char[bytes.length << 1];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            out[j++] = digits[(0xF0 & bytes[i]) >>> 4];
            out[j++] = digits[0x0F & bytes[i]];
        }
        return new String(out);
    }

    /**
     * 将十六进制字符串解码为原始字节数组。
     *
     * @param hex 十六进制字符串（不区分大小写），若为 null 则返回 null
     * @return 解码后的字节数组
     * @throws IllegalArgumentException 若字符串长度为奇数或包含非法字符
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            return null;
        }
        if (hex.isEmpty()) {
            return new byte[0];
        }

        int len = hex.length();
        if ((len & 0x01) != 0) {
            throw new IllegalArgumentException("Hex string length must be even: " + hex);
        }

        byte[] data = new byte[len >> 1];
        for (int i = 0; i < len; i += 2) {
            int d1 = Character.digit(hex.charAt(i), 16);
            int d2 = Character.digit(hex.charAt(i + 1), 16);
            if (d1 == -1 || d2 == -1) {
                throw new IllegalArgumentException("Illegal hexadecimal character in: " + hex);
            }
            data[i >> 1] = (byte) ((d1 << 4) | d2);
        }
        return data;
    }

}
