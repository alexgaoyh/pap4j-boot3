package cn.net.pap.common.datastructure.crc8;

/**
 * CRC 循环冗余校验
 */
public class CRC8 {

    // CRC-8 多项式
    private static final int POLYNOMIAL = 0x07; // x^8 + x^2 + x^1 + x^0

    // 计算给定字节数组的CRC-8值
    public static byte computeCRC8(byte[] data) {
        int crc = 0; // 初始值设为0

        for (byte b : data) {
            crc ^= b; // 将当前字节与CRC寄存器异或

            for (int j = 0; j < 8; j++) { // 对每个比特位进行处理
                if ((crc & 0x80) != 0) { // 如果最高位为1
                    crc = (crc << 1) ^ POLYNOMIAL; // 左移并应用多项式
                } else {
                    crc <<= 1; // 否则只左移
                }
            }

            crc &= 0xFF; // 确保CRC寄存器保持8位
        }

        return (byte)crc; // 返回计算出的CRC-8值
    }

}
