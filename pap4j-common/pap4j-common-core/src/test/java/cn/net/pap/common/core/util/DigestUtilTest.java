package cn.net.pap.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DigestUtilTest {

    @Test
    @DisplayName("测试 MD5 计算 - 字符串、字节数组与流式")
    void testMd5Hex() throws IOException {
        String input = "hello world";
        // md5("hello world") = 5eb63bbbe01eeed093cb22bb8f5acdc3
        String expected = "5eb63bbbe01eeed093cb22bb8f5acdc3";

        assertEquals(expected, DigestUtil.md5Hex(input));
        assertEquals(expected, DigestUtil.md5Hex(input.getBytes(StandardCharsets.UTF_8)));

        try (ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))) {
            assertEquals(expected, DigestUtil.md5Hex(in));
        }

        assertNull(DigestUtil.md5Hex((String) null));
        assertNull(DigestUtil.md5Hex((byte[]) null));
    }

    @Test
    @DisplayName("测试 SHA-256 计算 - 字符串、字节数组与流式")
    void testSha256Hex() throws IOException {
        String input = "hello world";
        // sha256("hello world") = b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
        String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";

        assertEquals(expected, DigestUtil.sha256Hex(input));
        assertEquals(expected, DigestUtil.sha256Hex(input.getBytes(StandardCharsets.UTF_8)));

        try (ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))) {
            assertEquals(expected, DigestUtil.sha256Hex(in));
        }

        assertNull(DigestUtil.sha256Hex((String) null));
        assertNull(DigestUtil.sha256Hex((byte[]) null));
    }

}
