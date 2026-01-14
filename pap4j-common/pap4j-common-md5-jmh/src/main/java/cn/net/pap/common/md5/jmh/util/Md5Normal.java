package cn.net.pap.common.md5.jmh.util;

public final class Md5Normal {

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
