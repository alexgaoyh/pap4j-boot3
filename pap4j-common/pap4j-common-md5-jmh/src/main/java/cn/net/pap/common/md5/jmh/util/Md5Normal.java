package cn.net.pap.common.md5.jmh.util;

public final class Md5Normal {

    /**
     * 常规方式计算 MD5 摘要（每次新建 MessageDigest）。
     *
     * @param input 原始字符串
     * @return 小写十六进制摘要
     * @throws Exception 摘要算法异常
     */
    public static String md5(String input) throws Exception {
        var md = java.security.MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
