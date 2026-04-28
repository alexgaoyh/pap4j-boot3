package cn.net.pap.common.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.net.pap.common.file.util.BinaryConvertUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 十进制与高进制转换
 */
public class BinaryConvertUtilTest {
    private static final Logger log = LoggerFactory.getLogger(BinaryConvertUtilTest.class);

    @Test
    public void convertBase64() {
        for(long i = 1000000000000000l; i < 1000000000001000l; i++) {
            String baseHigh = BinaryConvertUtil.dToB62(i);
            long l = BinaryConvertUtil.b62Tod(baseHigh);
            log.info("{}", i + " : " + baseHigh);
            assertTrue(i == l);
        }
    }

}
