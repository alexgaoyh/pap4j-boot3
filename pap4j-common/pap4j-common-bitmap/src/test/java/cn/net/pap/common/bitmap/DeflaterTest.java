package cn.net.pap.common.bitmap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 字符串压缩工具类
 */
public class DeflaterTest {

    @Test
    public void deflater() throws Exception {
        // 要压缩的字符串
        String originalString = "Hello, World! This is a test string for compression.";

        // originalString = new String(Files.readAllBytes(Paths.get("")));

        // 将字符串转换为字节数组
        byte[] input = originalString.getBytes(StandardCharsets.UTF_8);

        // 创建Deflater对象
        Deflater deflater = new Deflater();

        // 设置输入数据
        deflater.setInput(input);

        // 完成压缩并清空输入缓冲区
        deflater.finish();

        // 创建输出缓冲区
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);

        // 压缩数据
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int bytesCompressed = deflater.deflate(buffer);
            baos.write(buffer, 0, bytesCompressed);
        }

        // 获取压缩后的数据
        byte[] compressedData = baos.toByteArray();

        // 打印压缩前后的数据大小
        System.out.println("Original size: " + input.length);
        System.out.println("Compressed data: " + bytesToHex(compressedData));
        System.out.println("Compressed size: " + compressedData.length);

        // 解压缩数据
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        byte[] outputBuffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int bytesInflated = inflater.inflate(outputBuffer);
                out.write(outputBuffer, 0, bytesInflated);
            }
            // 转换回字符串
            String decompressedString = new String(out.toByteArray(), StandardCharsets.UTF_8);
            System.out.println("Decompressed string: " + decompressedString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 辅助方法，将字节数组转换为十六进制字符串
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
