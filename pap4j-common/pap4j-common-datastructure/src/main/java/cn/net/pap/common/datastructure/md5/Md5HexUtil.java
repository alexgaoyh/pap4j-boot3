package cn.net.pap.common.datastructure.md5;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p><strong>Md5HexUtil</strong> 提供了生成十六进制格式 MD5 哈希值的实用方法。</p>
 *
 * <ul>
 *     <li>支持原始字符串哈希。</li>
 *     <li>支持使用自定义盐值和迭代次数进行哈希。</li>
 *     <li>提供字节数组到十六进制字符串的转换。</li>
 * </ul>
 */
public class Md5HexUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * <p>生成 MD5 哈希值的十六进制字符串表示。</p>
     *
     * @param source         要进行哈希的原始字符串。
     * @param salt           可选的盐值。
     * @param hashIterations 哈希迭代次数。
     * @return 生成的十六进制字符串。
     * @throws RuntimeException 如果 MD5 算法不可用。
     */
    public static String md5Hex(String source, String salt, int hashIterations) {
        try {
            byte[] bytes = source.getBytes();
            byte[] saltBytes = salt != null ? salt.getBytes() : new byte[0];

            MessageDigest digest = MessageDigest.getInstance("MD5");
            if (saltBytes.length > 0) {
                digest.reset();
                digest.update(saltBytes);
            }

            byte[] hashed = digest.digest(bytes);

            for (int i = 1; i < hashIterations; i++) {
                digest.reset();
                hashed = digest.digest(hashed);
            }

            return toHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * <p>将字节数组转换为十六进制字符串。</p>
     *
     * @param bytes 输入的字节数组。
     * @return 对应的十六进制字符串。
     */
    public static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * <p>没有盐值且仅使用 1 次迭代的简化版 MD5 哈希。</p>
     *
     * @param source 原始字符串。
     * @return MD5 十六进制哈希字符串。
     */
    public static String md5Hex(String source) {
        return md5Hex(source, null, 1);
    }

}
