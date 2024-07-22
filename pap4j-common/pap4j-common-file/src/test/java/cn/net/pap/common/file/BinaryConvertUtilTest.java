package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 十进制与高进制转换
 */
public class BinaryConvertUtilTest {

    @Test
    public void convertBase64() {
        for(long i = 1000000000000000l; i < 9999999999999999l; i++) {
            String baseHigh = dToB62(i);
            long l = b62Tod(baseHigh);
            System.out.println(i + " : " + baseHigh);
            assertTrue(i == l);
        }
    }

    public static String dToB62(long decimal) {
        return decimalToBaseHigh(decimal, BASE62_CHARS);
    }

    public static long b62Tod(String baseHigh) {
        return baseHighToDecimal(baseHigh, BASE62_CHARS);
    }

    // 定义六十二进制的字符集
    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    // 将十进制数转换为高进制
    private static String decimalToBaseHigh(long decimal, String chars) {
        if (decimal == 0) {
            return "0";
        }

        StringBuilder base64 = new StringBuilder();
        while (decimal > 0) {
            int remainder = (int) (decimal % chars.length());
            base64.append(chars.charAt(remainder));
            decimal /= chars.length();
        }

        return base64.reverse().toString();
    }

    // 将高进制数转换为十进制
    private static long baseHighToDecimal(String baseHigh, String chars) {
        long decimal = 0;
        int length = baseHigh.length();

        for (int i = 0; i < length; i++) {
            char c = baseHigh.charAt(i);
            int value = chars.indexOf(c);
            decimal = decimal * chars.length() + value;
        }

        return decimal;
    }


}
