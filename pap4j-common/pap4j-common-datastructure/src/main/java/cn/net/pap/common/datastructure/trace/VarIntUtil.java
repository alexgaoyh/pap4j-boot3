package cn.net.pap.common.datastructure.trace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class VarIntUtil {

    // =========================
    // 1. Unsigned VarInt
    // =========================

    /**
     * 写入无符号 VarInt 到 ByteArrayOutputStream
     */
    public static void writeUnsignedVarInt(int value, ByteArrayOutputStream out) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) { // 7 位数据+1个 continuation
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    /**
     * 从 ByteArrayInputStream 读取无符号 VarInt
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
     * ZigZag 编码
     */
    public static int encodeZigZag(int n) {
        return (n << 1) ^ (n >> 31);
    }

    /**
     * ZigZag 解码
     */
    public static int decodeZigZag(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * 写入有符号 VarInt
     */
    public static void writeSignedVarInt(int value, ByteArrayOutputStream out) throws IOException {
        writeUnsignedVarInt(encodeZigZag(value), out);
    }

    /**
     * 读取有符号 VarInt
     */
    public static int readSignedVarInt(ByteArrayInputStream in) throws IOException {
        int raw = readUnsignedVarInt(in);
        return decodeZigZag(raw);
    }

}

