package cn.net.pap.example.javafx.util;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ImageUtil {

    /**
     * 图像读取
     * @param path
     * @return
     */
    public static BufferedImage opencvRead(String path) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(path));
            if (data.length == 0) return null;

            // Create Mat that wraps raw bytes (no copy)
            Mat encoded = new Mat(1, data.length, opencv_core.CV_8UC1);
            encoded.data().put(data);

            // Decode image (still no disk I/O)
            Mat image = opencv_imgcodecs.imdecode(encoded, opencv_imgcodecs.IMREAD_UNCHANGED);
            encoded.close(); // release early

            if (image == null || image.empty()) {
                image.close();
                return null;
            }

            int width = image.cols();
            int height = image.rows();
            int channels = image.channels();

            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

            UByteIndexer idx = image.createIndexer();

            if (channels == 4) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int b = idx.get(y, x, 0) & 0xFF;
                        int g = idx.get(y, x, 1) & 0xFF;
                        int r = idx.get(y, x, 2) & 0xFF;
                        int a = idx.get(y, x, 3) & 0xFF;
                        pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            } else if (channels == 3) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int b = idx.get(y, x, 0) & 0xFF;
                        int g = idx.get(y, x, 1) & 0xFF;
                        int r = idx.get(y, x, 2) & 0xFF;
                        pixels[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            } else {
                // grayscale
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int gray = idx.get(y, x) & 0xFF;
                        pixels[y * width + x] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
                    }
                }
            }

            idx.close();
            image.close();
            return result;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
