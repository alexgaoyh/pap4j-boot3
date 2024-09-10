package cn.net.pap.common.datastructure.crc8;

import org.junit.jupiter.api.Test;

public class CRC8Test {

    @Test
    public void testCRC8() {
        byte[] testData = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        byte crcValue = CRC8.computeCRC8(testData);
        System.out.println("CRC-8 value: " + String.format("%02X", crcValue));
    }

}
