package cn.net.pap.example.javafx.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import java.util.Iterator;

public class ImageUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    /**
     * 图像读取
     *
     * @param path
     * @return
     */
    public static BufferedImage read(String path) {
        try {
            return ImageIO.read(Paths.get(path).toFile());
        } catch (IOException e) {
            log.error("ImageUtil.read", e);
            return null;
        }
    }

    /**
     * 渲染图像
     *
     * @param path
     * @param currShownImage
     * @return
     */
    public static Image readFXImageWithCurrShownImage(String path, Image currShownImage) {
        BufferedImage read = read(path);
        if (read == null) {
            return null;
        }
        int newWidth = read.getWidth();
        int newHeight = read.getHeight();
        WritableImage destImage = null;
        if (currShownImage != null && currShownImage instanceof WritableImage) {
            if ((int) currShownImage.getWidth() == newWidth &&
                    (int) currShownImage.getHeight() == newHeight) {
                destImage = (WritableImage) currShownImage;
            }
        }
        return SwingFXUtils.toFXImage(read, destImage);
    }

    /**
     * 高效读取图像信息
     * 结合降采样(Subsampling) + PixelBuffer(零拷贝)
     *
     * @param path             文件路径
     * @param targetWidthLimit 限制加载后的最大宽度（例如：屏幕宽度 1920，或者预览区域宽度）
     * @return
     * @throws Exception
     */
    public static Image readFXImageEfficiently(String path, int targetWidthLimit) throws Exception {
        File file = new File(path);

        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            // 1. 获取 Reader
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("无法识别的图片格式: " + path);
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, true, true); // ignoreMetadata=true 提升速度

            // 2. 计算降采样率 (核心优化点：IO层面减少数据量)
            int originalWidth = reader.getWidth(0);
            int originalHeight = reader.getHeight(0);

            int subsampling = 1;
            if (originalWidth > targetWidthLimit) {
                subsampling = originalWidth / targetWidthLimit;
            }

            // 3. 配置读取参数
            ImageReadParam param = reader.getDefaultReadParam();
            if (subsampling > 1) {
                // 每隔 subsampling 个像素读一个，大幅降低内存和IO
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
            }

            // 4. 读取为 BufferedImage (此时已经是缩小后的图)
            BufferedImage rawImage = reader.read(0, param);

            // 5. 格式标准化 (PixelBuffer 需要 INT_ARGB_PRE)
            // 虽然这里有一次绘制开销，但因为对象是缩小后的，速度极快（毫秒级）
            BufferedImage pixelBufferImage;
            if (rawImage.getType() == BufferedImage.TYPE_INT_ARGB_PRE) {
                pixelBufferImage = rawImage;
            } else {
                // 创建一个标准格式的容器
                pixelBufferImage = new BufferedImage(rawImage.getWidth(), rawImage.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
                // 使用 AWT 高速绘图将原图画进去
                Graphics g = pixelBufferImage.getGraphics();
                g.drawImage(rawImage, 0, 0, null);
                g.dispose();
            }

            // 6. 提取底层数组并创建 PixelBuffer (核心优化点：避免 SwingFXUtils 拷贝)
            DataBufferInt dataBuffer = (DataBufferInt) pixelBufferImage.getRaster().getDataBuffer();
            int[] pixels = dataBuffer.getData();

            // 包装为 NIO Buffer
            IntBuffer buffer = IntBuffer.wrap(pixels);
            PixelFormat<IntBuffer> format = PixelFormat.getIntArgbPreInstance();

            // 创建共享内存的 PixelBuffer
            PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(pixelBufferImage.getWidth(), pixelBufferImage.getHeight(), buffer, format);

            // 7. 返回 WritableImage
            return new WritableImage(pixelBuffer);
        }
    }

    /**
     * 高效生成图像缩略图（低内存占用版本）本方法使用 ImageIO 的子采样（Subsampling）技术，在解码阶段直接跳过像素， 从而大幅降低内存占用，特别适合处理大尺寸图像。
     *
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

    public static boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".tif") || lower.endsWith(".tiff");
    }

}
