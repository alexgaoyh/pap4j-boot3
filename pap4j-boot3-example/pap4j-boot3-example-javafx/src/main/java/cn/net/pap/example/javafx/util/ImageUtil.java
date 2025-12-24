package cn.net.pap.example.javafx.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Iterator;

public class ImageUtil {

    /**
     * 图像读取
     * @param path
     * @return
     */
    public static BufferedImage read(String path) {
        try {
            return ImageIO.read(Paths.get(path).toFile());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 高效生成图像缩略图（低内存占用版本）本方法使用 ImageIO 的子采样（Subsampling）技术，在解码阶段直接跳过像素， 从而大幅降低内存占用，特别适合处理大尺寸图像。
     * @param inputFileStr
     * @param targetWidth
     * @return
     * @throws IOException
     */
    public static BufferedImage getLowMemoryThumbnail(String inputFileStr, int targetWidth) {
        File file = new File(inputFileStr);
        if (file == null || !file.exists()) {
            return null;
        }

        // 使用 try-with-resources 同时管理多个资源
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                //  获取原始尺寸 计算采样率 (Subsampling) 例如：原图 4000，目标 400，则每 10 个像素取 1 个
                int actualWidth = reader.getWidth(0);
                int sampling = Math.max(actualWidth / targetWidth, 1);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sampling, sampling, 0, 0);

                return reader.read(0, param);
            } finally {
                // 确保 reader 被清理
                reader.dispose();
            }
        } catch (IOException e) {
            // 记录异常
            return null;
        }
    }

}
