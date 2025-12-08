package cn.net.pap.example.admin.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

public class TimestampCryptoUtil {

    // 固定盐值（16字节，对应AES-128）
    private static final byte[] FIXED_SALT = {(byte) 0x1A, (byte) 0x2B, (byte) 0x3C, (byte) 0x4D, (byte) 0x5E, (byte) 0x6F, (byte) 0x78, (byte) 0x91, (byte) 0xA2, (byte) 0xB3, (byte) 0xC4, (byte) 0xD5, (byte) 0xE6, (byte) 0xF7, (byte) 0x08, (byte) 0x19};

    // 算法配置
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    // 预先生成的密钥（提高性能）
    private static final SecretKeySpec SECRET_KEY;

    static {
        // 使用固定盐值直接作为密钥
        SECRET_KEY = new SecretKeySpec(FIXED_SALT, ALGORITHM);
    }

    private TimestampCryptoUtil() {
        // 私有构造器
    }

    /**
     * 加密当前时间戳（最简单版本）
     */
    public static String encryptNow() {
        return encrypt(System.currentTimeMillis());
    }

    /**
     * 加密指定时间戳
     */
    public static String encrypt(long timestamp) {
        try {
            // 添加随机干扰防止相同时间戳加密结果相同
            byte[] timestampWithNoise = addNoise(timestamp);

            // 加密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY);
            byte[] encrypted = cipher.doFinal(timestampWithNoise);

            // Base64编码返回
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密时间戳
     */
    public static long decrypt(String encryptedStr) {
        try {
            // Base64解码
            byte[] encrypted = Base64.getUrlDecoder().decode(encryptedStr);

            // 解密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY);
            byte[] decrypted = cipher.doFinal(encrypted);

            // 提取时间戳（去除干扰）
            return extractTimestamp(decrypted);

        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 验证时间戳是否在有效期内
     */
    public static boolean isValid(String encryptedStr, long maxAgeMillis) {
        try {
            long timestamp = decrypt(encryptedStr);
            return (System.currentTimeMillis() - timestamp) <= maxAgeMillis;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 快速验证（5分钟内有效）
     */
    public static boolean isValid5Min(String encryptedStr) {
        return isValid(encryptedStr, 5 * 60 * 1000);
    }

    /**
     * 快速验证（1分钟内有效）
     */
    public static boolean isValid1Min(String encryptedStr) {
        return isValid(encryptedStr, 60 * 1000);
    }

    /**
     * 为时间戳添加随机干扰
     */
    private static byte[] addNoise(long timestamp) {
        ByteBuffer buffer = ByteBuffer.allocate(16); // AES块大小
        buffer.putLong(timestamp);

        // 添加4字节随机数
        buffer.putInt(ThreadLocalRandom.current().nextInt());

        // 填充4字节固定值（可以自定义）
        buffer.putInt(0x12345678);

        return buffer.array();
    }

    /**
     * 从解密数据中提取时间戳
     */
    private static long extractTimestamp(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return buffer.getLong();
    }

    /**
     * 生成带干扰的密钥（可选）
     */
    private static byte[] generateKeyWithNoise(long timestamp) {
        byte[] key = new byte[16];
        byte[] tsBytes = longToBytes(timestamp);

        // 将时间戳与固定盐值混合
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (FIXED_SALT[i] ^ tsBytes[i % tsBytes.length]);
        }

        return key;
    }

    /**
     * long转byte数组（高效版）
     */
    private static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

}
