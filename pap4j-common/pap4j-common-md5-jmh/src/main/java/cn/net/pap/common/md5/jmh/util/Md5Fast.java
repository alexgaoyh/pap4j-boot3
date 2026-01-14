package cn.net.pap.common.md5.jmh.util;

public final class Md5Fast {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static final ThreadLocal<java.security.MessageDigest> MD5 =
            ThreadLocal.withInitial(() -> {
                try {
                    return java.security.MessageDigest.getInstance("MD5");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

    private static final ThreadLocal<char[]> CHAR_BUF =
            ThreadLocal.withInitial(() -> new char[32]);

    public static String md5(String input) {
        var md = MD5.get();
        md.reset();

        byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        char[] out = CHAR_BUF.get();

        int j = 0;
        for (byte b : digest) {
            out[j++] = HEX[(b >>> 4) & 0x0F];
            out[j++] = HEX[b & 0x0F];
        }
        return new String(out);
    }
}

