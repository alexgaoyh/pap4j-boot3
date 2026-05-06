package cn.net.pap.common.datastructure.crc8;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CRC8Test {

    private static final Logger log = LoggerFactory.getLogger(CRC8Test.class);

    @Test
    public void testCRC8() {
        byte[] testData = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        byte crcValue = CRC8.computeCRC8(testData);
        log.info(String.format("CRC-8 value: %02X", crcValue));
    }

}
