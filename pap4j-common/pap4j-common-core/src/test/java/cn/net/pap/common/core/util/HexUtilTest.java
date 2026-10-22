package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HexUtilTest {

    @Test
    @DisplayName("测试字节数组转十六进制（小写与大写）")
    void testToHex() {
        byte[] bytes = new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe};
        assertEquals("cafebabe", HexUtil.toHex(bytes));
        assertEquals("cafebabe", HexUtil.toHex(bytes, true));
        assertEquals("CAFEBABE", HexUtil.toHex(bytes, false));

        assertNull(HexUtil.toHex(null));
        assertEquals("", HexUtil.toHex(new byte[0]));
    }

    @Test
    @DisplayName("测试十六进制转字节数组")
    void testFromHex() {
        byte[] expected = new byte[]{(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe};
        assertArrayEquals(expected, HexUtil.fromHex("cafebabe"));
        assertArrayEquals(expected, HexUtil.fromHex("CAFEBABE"));
        assertArrayEquals(expected, HexUtil.fromHex("CaFeBaBe"));

        assertNull(HexUtil.fromHex(null));
        assertArrayEquals(new byte[0], HexUtil.fromHex(""));
    }

    @Test
    @DisplayName("测试编解码往返一致性")
    void testRoundTrip() {
        String text = "pap4j-common-core-hex-test-123456";
        byte[] original = text.getBytes(StandardCharsets.UTF_8);
        String hex = HexUtil.toHex(original);
        byte[] decoded = HexUtil.fromHex(hex);
        assertArrayEquals(original, decoded);
        assertEquals(text, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("测试非法十六进制输入抛出异常")
    void testInvalidHex() {
        // 奇数长度
        assertThrows(IllegalArgumentException.class, () -> HexUtil.fromHex("abc"));
        // 非法字符
        assertThrows(IllegalArgumentException.class, () -> HexUtil.fromHex("abcz"));
    }

}
