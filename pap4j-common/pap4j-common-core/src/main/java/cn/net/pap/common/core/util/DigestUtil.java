package cn.net.pap.common.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 消息摘要与哈希计算工具类。
 * <p>基于纯 JDK {@link MessageDigest} 实现 MD5 与 SHA-256 散列计算，提供字符串、字节数组及流式接口。</p>
 */
public final class DigestUtil {

    private static final int BUFFER_SIZE = 8192;
    private static final String MD5_ALGORITHM = "MD5";
    private static final String SHA256_ALGORITHM = "SHA-256";

    private DigestUtil() {
    }

    /**
     * 计算字符串的 MD5 十六进制摘要（UTF-8 编码）。
     *
     * @param data 原始字符串，若为 null 则返回 null
     * @return 32 位小写 MD5 十六进制字符串
     */
    public static String md5Hex(String data) {
        if (data == null) {
            return null;
        }
        return md5Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 十六进制摘要。
     *
     * @param bytes 原始字节数组，若为 null 则返回 null
     * @return 32 位小写 MD5 十六进制字符串
     */
    public static String md5Hex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return digestHex(MD5_ALGORITHM, bytes);
    }

    /**
     * 以流式缓冲方式（8KB 缓冲区）计算输入流的 MD5 十六进制摘要。
     * <p>适合大文件或海量数据，避免将整个流全部载入内存产生 GC 压力。</p>
     *
     * @param in 输入流，调用方负责关闭该流
     * @return 32 位小写 MD5 十六进制字符串
     * @throws IOException 读取流异常
     */
    public static String md5Hex(InputStream in) throws IOException {
        return digestHex(MD5_ALGORITHM, in);
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要（UTF-8 编码）。
     *
     * @param data 原始字符串，若为 null 则返回 null
     * @return 64 位小写 SHA-256 十六进制字符串
     */
    public static String sha256Hex(String data) {
        if (data == null) {
            return null;
        }
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-256 十六进制摘要。
     *
     * @param bytes 原始字节数组，若为 null 则返回 null
     * @return 64 位小写 SHA-256 十六进制字符串
     */
    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return digestHex(SHA256_ALGORITHM, bytes);
    }

    /**
     * 以流式缓冲方式（8KB 缓冲区）计算输入流的 SHA-256 十六进制摘要。
     *
     * @param in 输入流，调用方负责关闭该流
     * @return 64 位小写 SHA-256 十六进制字符串
     * @throws IOException 读取流异常
     */
    public static String sha256Hex(InputStream in) throws IOException {
        return digestHex(SHA256_ALGORITHM, in);
    }

    /**
     * 计算指定算法的消息摘要。
     *
     * @param algorithm 算法名称，如 MD5, SHA-256
     * @param bytes     字节数组
     * @return 小写十六进制摘要
     */
    public static String digestHex(String algorithm, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        MessageDigest md = getDigest(algorithm);
        return HexUtil.toHex(md.digest(bytes));
    }

    /**
     * 以流式方式计算指定算法的消息摘要。
     *
     * @param algorithm 算法名称，如 MD5, SHA-256
     * @param in        输入流
     * @return 小写十六进制摘要
     * @throws IOException 读取流异常
     */
    public static String digestHex(String algorithm, InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        MessageDigest md = getDigest(algorithm);
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        return HexUtil.toHex(md.digest());
    }

    private static MessageDigest getDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unsupported digest algorithm: " + algorithm, e);
        }
    }

}
