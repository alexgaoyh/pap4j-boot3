package cn.net.pap.common.file;

import cn.net.pap.common.file.util.BinaryConvertUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 十进制与高进制转换
 */
public class BinaryConvertUtilTest {

    @Test
    public void convertBase64() {
        for(long i = 1000000000000000l; i < 1000000000001000l; i++) {
            String baseHigh = BinaryConvertUtil.dToB62(i);
            long l = BinaryConvertUtil.b62Tod(baseHigh);
            System.out.println(i + " : " + baseHigh);
            assertTrue(i == l);
        }
    }

}
