package cn.net.pap.common.datastructure.md5;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5HexUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 生成MD5哈希的十六进制字符串表示
     *
     * @param source         原始字符串
     * @param salt           盐值(可选)
     * @param hashIterations 哈希迭代次数
     * @return 十六进制字符串
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
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
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

    // 简化版本，不带salt和迭代
    public static String md5Hex(String source) {
        return md5Hex(source, null, 1);
    }

}
