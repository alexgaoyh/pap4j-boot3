package cn.net.pap.common.datastructure.crc8;

/**
 * <h1>CRC-8 循环冗余校验计算工具类</h1>
 * <p>该工具类用于通过指定的生成多项式（Polynomial: {@code 0x07}）快速计算字节数组的 CRC-8 校验值。</p>
 * <p>常用于通信协议、传感器数据读取与微控制器之间的数据完整性验证。</p>
 *
 * @author alexgaoyh
 */
public class CRC8 {

    /**
     * <p>CRC-8 多项式，对应数学表达式：<strong>x^8 + x^2 + x^1 + x^0</strong></p>
     */
    private static final int POLYNOMIAL = 0x07;

    /**
     * <p>计算给定字节数组的 CRC-8 校验值。</p>
     * <strong>计算过程:</strong>
     * <ul>
     *     <li>初始值设为 {@code 0}。</li>
     *     <li>将当前字节与 CRC 寄存器进行异或运算。</li>
     *     <li>对每个比特位逐位移位处理，最高位为 {@code 1} 时，左移并与多项式异或；否则只进行左移。</li>
     * </ul>
     *
     * @param data 需要进行 CRC-8 校验计算的字节数组
     * @return 计算出的 CRC-8 字节值
     */
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
