package cn.net.pap.common.file;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 十进制与六十四进制转换
 */
public class BinaryConvertUtilTest {

    @Test
    public void convertBase64() {
        for(long i = 1000000000000000l; i < 9999999999999999l; i++) {
            String base64 = decimalToBase64(i);
            long l = base64ToDecimal(base64);
            System.out.println(i + " : " + base64);
            assertTrue(i == l);
            break;
        }
    }

    // 定义六十四进制的字符集
    private static final String BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    // 将十进制数转换为六十四进制
    public static String decimalToBase64(long decimal) {
        if (decimal == 0) {
            return "A";
        }

        StringBuilder base64 = new StringBuilder();
        while (decimal > 0) {
            int remainder = (int) (decimal % 64);
            base64.append(BASE64_CHARS.charAt(remainder));
            decimal /= 64;
        }

        return base64.reverse().toString();
    }

    // 将六十四进制数转换为十进制
    public static long base64ToDecimal(String base64) {
        long decimal = 0;
        int length = base64.length();

        for (int i = 0; i < length; i++) {
            char c = base64.charAt(i);
            int value = BASE64_CHARS.indexOf(c);
            decimal = decimal * 64 + value;
        }

        return decimal;
    }


}
