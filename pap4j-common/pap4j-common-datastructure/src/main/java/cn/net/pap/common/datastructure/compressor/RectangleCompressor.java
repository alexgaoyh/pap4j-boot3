package cn.net.pap.common.datastructure.compressor;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * 矩形坐标压缩工具（Base62方案，精度0.01）
 */
public class RectangleCompressor {

    private static final double PRECISION = 0.01; // 保留2位小数
    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    // ================== 压缩 ==================
    public static String compress(double x1, double y1, double x2, double y2) {
        // 1. 坐标转为整数（精度0.01）
        int[] ints = {(int) (x1 / PRECISION), (int) (y1 / PRECISION), (int) ((x2 - x1) / PRECISION), (int) ((y2 - y1) / PRECISION)};

        // 2. 整数数组 -> 字节数组
        byte[] bytes = intArrayToBytes(ints);

        // 3. 字节数组 -> Base62字符串
        return bytesToBase62(bytes);
    }

    // ================== 解压 ==================
    public static double[] decompress(String compressed) {
        // 1. Base62字符串 -> 字节数组
        byte[] bytes = base62ToBytes(compressed);

        // 2. 字节数组 -> 整数数组
        int[] ints = bytesToIntArray(bytes);

        // 3. 恢复原始坐标
        double x1 = ints[0] * PRECISION;
        double y1 = ints[1] * PRECISION;
        double x2 = x1 + ints[2] * PRECISION;
        double y2 = y1 + ints[3] * PRECISION;

        return new double[]{x1, y1, x2, y2};
    }


    // ================== 压缩 ==================
    public static String compress2(double x1, double y1, double x2, double y2) {
        // 1. 转为整数（x1, y1, w, h）
        int[] ints = {(int) (x1 / PRECISION), (int) (y1 / PRECISION), (int) ((x2 - x1) / PRECISION), (int) ((y2 - y1) / PRECISION)};

        // 2. 每个整数单独转 Base62
        StringBuilder sb = new StringBuilder();
        for (int num : ints) {
            sb.append(intToBase62(num)).append(":"); // 用":"分隔
        }
        return sb.deleteCharAt(sb.length() - 1).toString(); // 去掉最后一个":"
    }

    // ================== 解压 ==================
    public static double[] decompress2(String compressed) {
        // 1. 按":"分割字符串
        String[] parts = compressed.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid compressed string");
        }

        // 2. 每个部分转回整数
        int[] ints = new int[4];
        for (int i = 0; i < 4; i++) {
            ints[i] = base62ToInt(parts[i]);
        }

        // 3. 恢复原始坐标
        double x1 = ints[0] * PRECISION;
        double y1 = ints[1] * PRECISION;
        double x2 = x1 + ints[2] * PRECISION;
        double y2 = y1 + ints[3] * PRECISION;

        return new double[]{x1, y1, x2, y2};
    }

    // ================== 内部工具方法 ==================
    private static byte[] intArrayToBytes(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        for (int num : ints) {
            buffer.putInt(num);
        }
        return buffer.array();
    }

    private static int[] bytesToIntArray(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int[] ints = new int[4];
        for (int i = 0; i < 4; i++) {
            ints[i] = buffer.getInt();
        }
        return ints;
    }

    private static String bytesToBase62(byte[] data) {
        BigInteger bigInt = new BigInteger(1, data);
        StringBuilder sb = new StringBuilder();
        while (bigInt.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divMod = bigInt.divideAndRemainder(BigInteger.valueOf(62));
            sb.insert(0, BASE62_CHARS.charAt(divMod[1].intValue()));
            bigInt = divMod[0];
        }
        return sb.toString();
    }

    private static byte[] base62ToBytes(String encoded) {
        BigInteger bigInt = BigInteger.ZERO;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int digit = BASE62_CHARS.indexOf(c);
            bigInt = bigInt.multiply(BigInteger.valueOf(62)).add(BigInteger.valueOf(digit));
        }

        // 确保返回的字节数组长度为 16（4个int）
        byte[] originalBytes = bigInt.toByteArray();
        byte[] paddedBytes = new byte[16];

        // 如果原字节数组长度 < 16，则前面补 0
        if (originalBytes.length < 16) {
            int offset = 16 - originalBytes.length;
            System.arraycopy(originalBytes, 0, paddedBytes, offset, originalBytes.length);
        } else {
            // 如果长度 >= 16，直接取后 16 字节（BigInteger 可能返回带符号位的数组）
            System.arraycopy(originalBytes, originalBytes.length - 16, paddedBytes, 0, 16);
        }

        return paddedBytes;
    }

    // ================== Base62 转换工具 ==================
    private static String intToBase62(int num) {
        if (num == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.insert(0, BASE62_CHARS.charAt(num % 62));
            num /= 62;
        }
        return sb.toString();
    }

    private static int base62ToInt(String str) {
        int num = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int digit = BASE62_CHARS.indexOf(c);
            num = num * 62 + digit;
        }
        return num;
    }

}
