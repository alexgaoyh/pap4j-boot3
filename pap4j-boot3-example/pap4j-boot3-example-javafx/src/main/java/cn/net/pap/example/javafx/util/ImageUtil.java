package cn.net.pap.example.javafx.util;

import cn.net.pap.example.javafx.dto.ImageViewDTO;
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
     * @return 处理结果对象
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
     * @return 处理结果对象
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
     * @return 处理结果对象
     * @throws Exception
     */
    public static ImageViewDTO readFXImageEfficiently(String path, int targetWidthLimit) throws Exception {
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
            Image writableImage = new WritableImage(pixelBuffer);

            return new ImageViewDTO(writableImage, path, originalWidth, originalHeight, rawImage.getWidth());
        }
    }

    /**
     * 判断文件名是否为支持的图片文件。
     *
     * @param fileName 文件名
     * @return 是图片文件返回 true
     */
    public static boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".tif") || lower.endsWith(".tiff");
    }

}
