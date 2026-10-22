package cn.net.pap.common.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图像处理工具类
 * <p>
 * 基于纯 JDK（javax.imageio 与 java.awt）实现，零外部图像库依赖。
 * 提供低内存占用大图降采样缩略图加载（防 OOM）、1x1 占位图生成与高效区域裁剪。
 * </p>
 */
public final class ImageUtil {

    private static final Logger log = LoggerFactory.getLogger(ImageUtil.class);

    /**
     * 1x1 像素纯白 JPEG 图像的 Base64 编码字符串。
     * 可直接用于前端占位或默认图渲染。
     */
    public static final String ONE_X_ONE_JPG = "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigD//2Q==";

    private ImageUtil() {
        // 私有构造器，防止实例化
    }

    /**
     * 创建 1x1 像素纯白色最小 JPEG 图像字节流。
     * 可直接写入 HTTP 响应流，如 {@code response.getOutputStream().write(generateEmptyJpeg());}
     *
     * @return 1x1 白色 JPEG 字节数组，发生异常时返回空字节数组
     */
    public static byte[] generateEmptyJpeg() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xFFFFFF);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("生成空 JPEG 失败: ", e);
            return new byte[0];
        }
    }

    /**
     * 高效低内存缩略图加载（通过文件路径）。
     * <p>
     * 使用 ImageIO 子采样 (Subsampling)，在解码阶段直接按比例跳过像素，
     * 避免全分辨率加载大图到内存导致的 OOM。
     * </p>
     *
     * @param inputFileStr 文件物理路径
     * @param targetWidth  目标宽度限制
     * @return 采样后的图像对象，文件不存在或读取失败返回 null
     */
    public static BufferedImage getLowMemoryThumbnail(String inputFileStr, int targetWidth) {
        if (inputFileStr == null || inputFileStr.isBlank()) {
            return null;
        }
        return getLowMemoryThumbnail(new File(inputFileStr), targetWidth);
    }

    /**
     * 高效低内存缩略图加载（通过 File 对象）。
     *
     * @param file        图片文件
     * @param targetWidth 目标宽度限制
     * @return 采样后的图像对象，文件不存在或读取失败返回 null
     */
    public static BufferedImage getLowMemoryThumbnail(File file, int targetWidth) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

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
                int actualWidth = reader.getWidth(0);
                int sampling = Math.max(actualWidth / targetWidth, 1);

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(sampling, sampling, 0, 0);

                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.error("Failed to get low memory thumbnail for file: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * 高效图像裁剪。
     * <p>
     * 相比于 {@code ImageIO.read().getSubimage()} 在大图时性能提升数倍，
     * 通过 {@link ImageReadParam#setSourceRegion(Rectangle)} 仅解码目标矩形区域。
     * </p>
     *
     * @param inputFilePath  原始图像路径
     * @param outputFilePath 裁剪后输出路径
     * @param x              起始 X 坐标
     * @param y              起始 Y 坐标
     * @param width          裁剪宽度
     * @param height         裁剪高度
     * @return 是否裁剪成功
     */
    public static Boolean cropImageCut(String inputFilePath, String outputFilePath, int x, int y, int width, int height) {
        if (inputFilePath == null || outputFilePath == null) {
            return false;
        }

        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();

            try (ImageInputStream iis = ImageIO.createImageInputStream(new File(inputFilePath))) {
                if (iis == null) {
                    return false;
                }
                reader.setInput(iis);

                ImageReadParam param = reader.getDefaultReadParam();
                Rectangle rect = new Rectangle(x, y, width, height);
                param.setSourceRegion(rect);
                BufferedImage image = reader.read(0, param);

                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
                if (!writers.hasNext()) {
                    return false;
                }
                ImageWriter writer = writers.next();

                try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File(outputFilePath))) {
                    writer.setOutput(ios);
                    writer.write(image);
                } finally {
                    writer.dispose();
                }
            } finally {
                reader.dispose();
            }
            return true;
        } catch (IOException e) {
            log.error("裁剪图像失败: input={}, output={}", inputFilePath, outputFilePath, e);
            return false;
        }
    }
}
