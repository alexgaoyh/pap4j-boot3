package cn.net.pap.common.datastructure.trace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * <p><strong>VarIntUtil</strong> 提供了使用可变长度格式对整数进行编码和解码的实用方法。</p>
 *
 * <p>VarInt 用于在存储或传输通常较小的数字时节省空间。
 * 此类支持无符号和有符号整数，并利用 ZigZag 编码处理有符号值。</p>
 *
 * <ul>
 *     <li>整数的高效序列化。</li>
 *     <li>支持无符号 VarInt（Base 128 Varint）。</li>
 *     <li>通过 ZigZag 编码支持有符号 VarInt。</li>
 * </ul>
 */
public class VarIntUtil {

    // =========================
    // 1. Unsigned VarInt
    // =========================

    /**
     * <p>将无符号整数作为 VarInt 写入 {@link ByteArrayOutputStream}。</p>
     *
     * @param value 要写入的无符号整数值。
     * @param out   用于写入字节的输出流。
     * @throws IOException 如果在写入期间发生 I/O 错误。
     */
    public static void writeUnsignedVarInt(int value, ByteArrayOutputStream out) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) { // 7 位数据+1个 continuation
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    /**
     * <p>从 {@link ByteArrayInputStream} 读取无符号 VarInt。</p>
     *
     * @param in 用于读取字节的输入流。
     * @return 解码后的无符号整数值。
     * @throws IOException 如果发生 I/O 错误，或者如果 VarInt 过长。
     */
    public static int readUnsignedVarInt(ByteArrayInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        int b;
        while (true) {
            if (shift >= 35) { // 防止超长
                throw new IOException("VarInt too long");
            }
            b = in.read();
            if (b == -1) {
                throw new IOException("Unexpected EOF while reading VarInt");
            }
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return value;
    }

    // =========================
    // 2. Signed VarInt（ZigZag 编码）
    // =========================

    /**
     * <p>使用 ZigZag 编码对有符号整数进行编码。</p>
     *
     * <p>ZigZag 编码将有符号整数映射到无符号整数，以便绝对值小的数字具有较小的 varint 编码值。</p>
     *
     * @param n 要编码的有符号整数。
     * @return ZigZag 编码后的整数。
     */
    public static int encodeZigZag(int n) {
        return (n << 1) ^ (n >> 31);
    }

    /**
     * <p>将 ZigZag 编码的整数解码回有符号整数。</p>
     *
     * @param n 要解码的 ZigZag 编码整数。
     * @return 原始的有符号整数。
     */
    public static int decodeZigZag(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * <p>使用 ZigZag 编码将有符号整数作为 VarInt 写入 {@link ByteArrayOutputStream}。</p>
     *
     * @param value 要写入的有符号整数值。
     * @param out   用于写入字节的输出流。
     * @throws IOException 如果在写入期间发生 I/O 错误。
     */
    public static void writeSignedVarInt(int value, ByteArrayOutputStream out) throws IOException {
        writeUnsignedVarInt(encodeZigZag(value), out);
    }

    /**
     * <p>使用 ZigZag 解码从 {@link ByteArrayInputStream} 读取有符号 VarInt。</p>
     *
     * @param in 用于读取字节的输入流。
     * @return 解码后的有符号整数值。
     * @throws IOException 如果发生 I/O 错误，或者如果 VarInt 过长。
     */
    public static int readSignedVarInt(ByteArrayInputStream in) throws IOException {
        int raw = readUnsignedVarInt(in);
        return decodeZigZag(raw);
    }

}
