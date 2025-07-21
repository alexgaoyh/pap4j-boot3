package cn.net.pap.common.datastructure.compressor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class RectangleCompressorTest {

    @Test
    public void test1() {
        double x1 = 12.34, y1 = 56.78, x2 = 78.90, y2 = 98.76;
        // 压缩
        String compressed = RectangleCompressor.compress(x1, y1, x2, y2);
        System.out.println("压缩结果: " + compressed);
        // 解压
        double[] decompressed = RectangleCompressor.decompress(compressed);
        System.out.println("解压结果: " + Arrays.toString(decompressed));

        String compressed2 = RectangleCompressor.compress2(x1, y1, x2, y2);
        System.out.println("压缩结果: " + compressed2);
        // 解压
        double[] decompressed2 = RectangleCompressor.decompress2(compressed2);
        System.out.println("解压结果: " + Arrays.toString(decompressed2));
    }

}
