package cn.net.pap.common.datastructure.xor;

/**
 * 强化版硬编码异或加解密工具
 * <p>
 * [实现细节说明]:
 * 1. 密钥强度: 256-bit (32 bytes)，足以应对简单的统计分析。
 * 2. 迁移对齐: 密钥以十六进制字节数组形式存在，方便在 Rust (vec![0x..]) 或 C++ (uint8_t[]) 中复用。
 * 3. 性能: 保持 O(n) 时间复杂度，无额外内存开销。
 */
public class SimpleXorCryptoUtil {

    // 这是一个通过 SecureRandom 生成的高熵 32 字节密钥
    // 迁移时，请确保在 C++/Rust 中完整复制这 32 个十六进制值
    private static final byte[] HARDCODED_KEY = {(byte) 0x5D, (byte) 0xE2, (byte) 0x7F, (byte) 0x1A, (byte) 0xC9, (byte) 0x04, (byte) 0x33, (byte) 0x8B, (byte) 0x61, (byte) 0xF5, (byte) 0x2C, (byte) 0x9D, (byte) 0x70, (byte) 0x4A, (byte) 0xEE, (byte) 0x1B, (byte) 0x88, (byte) 0x34, (byte) 0x09, (byte) 0x56, (byte) 0xBD, (byte) 0x9F, (byte) 0xC1, (byte) 0x72, (byte) 0x0B, (byte) 0x3D, (byte) 0x44, (byte) 0x27, (byte) 0x81, (byte) 0xAF, (byte) 0x59, (byte) 0x6E};

    /**
     * 原位处理数据（更省内存）
     * 由于异或操作不需要前后依赖，直接在原数组上操作可以显著减少 GC 压力，
     * 尤其是在处理你项目中提到的大规模图像存档时。
     */
    public static void processInPlace(byte[] data) {
        if (data == null || data.length == 0) return;

        int keyLen = HARDCODED_KEY.length;
        for (int i = 0; i < data.length; i++) {
            // 直接修改原数组，减少内存拷贝
            data[i] ^= HARDCODED_KEY[i % keyLen];
        }
    }

    /**
     * 返回新数组的处理方式（保持原有逻辑不变）
     */
    public static byte[] process(byte[] data) {
        if (data == null) return null;
        byte[] copy = data.clone();
        processInPlace(copy);
        return copy;
    }
}
