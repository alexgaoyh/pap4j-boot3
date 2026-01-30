package cn.net.pap.common.datastructure.trace;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VarIntUtilTest {

    @Test
    void test1() throws IOException {
        for (int i = 1; i <= 100_000; i++) {
            // 单个数字压缩
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            VarIntUtil.writeUnsignedVarInt(i, out);
            byte[] compressed = out.toByteArray();

            // 解压
            ByteArrayInputStream in = new ByteArrayInputStream(compressed);
            int decoded = VarIntUtil.readUnsignedVarInt(in);

            // 验证
            assertEquals(i, decoded, "Mismatch for value: " + i);
            // 可选：打印压缩长度（前 20 个示例，避免太多输出）
            if (i <= 20 || i % 10000 == 0) {
//                String encoded = Base64.getUrlEncoder().encodeToString(compressed);
//                byte[] original = Base64.getUrlDecoder().decode(encoded);
                System.out.println("Value: " + i + ", compressed bytes: " + compressed.length);
            }
        }
    }

}
