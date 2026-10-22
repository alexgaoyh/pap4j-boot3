package cn.net.pap.example.admin.util;

import cn.net.pap.common.core.util.DigestUtil;
import cn.net.pap.common.core.util.HexUtil;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具类（委托至 core 模块）。
 */
public class DigestUtils {

    /**
     * MD5 计算
     *
     * @param inputStream 输入流
     * @return 32位小写十六进制字符串
     * @throws NoSuchAlgorithmException 算法不可用
     * @throws IOException 读取流异常
     */
    public static String calculateMD5(InputStream inputStream) throws NoSuchAlgorithmException, IOException {
        return DigestUtil.md5Hex(inputStream);
    }

    /**
     * 将字节数组转为小写十六进制字符串。
     *
     * @param bytes 输入字节数组
     * @return 十六进制字符串
     */
    public static String bytesToHex(byte[] bytes) {
        return HexUtil.toHex(bytes);
    }

}
