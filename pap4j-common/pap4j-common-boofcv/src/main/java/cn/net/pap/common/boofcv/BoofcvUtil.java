package cn.net.pap.common.boofcv;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.Planar;
import org.apache.commons.io.output.ByteArrayOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.IntStream;

public class BoofcvUtil {

    /**
     * 亮度
     *
     * @param input
     * @param brightness >0增加亮度， <0减少亮度
     * @return
     */
    public static BufferedImage adjustBrightness(BufferedImage input, float brightness) {
        Planar<GrayF32> image = ConvertBufferedImage.convertFromPlanar(input, null, true, GrayF32.class);

        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                for (int band = 0; band < image.getNumBands(); band++) {
                    float value = image.getBand(band).get(x, y);
                    value = value + brightness * 255;  // Adjust brightness
                    value = Math.max(0, Math.min(value, 255)); // Ensure value is within [0, 255]
                    image.getBand(band).set(x, y, value);
                }
            }
        }
        return ConvertBufferedImage.convertTo(image, null, true);
    }

    /**
     * 亮度 并行
     * @param input
     * @param brightness
     * @return
     */
    public static BufferedImage adjustBrightness2(BufferedImage input, float brightness) {
        Planar<GrayF32> image = ConvertBufferedImage.convertFromPlanar(input, null, true, GrayF32.class);
        int width = image.width;
        int height = image.height;

        // 使用并行流来加速处理
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                float[] values = new float[image.getNumBands()];
                for (int band = 0; band < image.getNumBands(); band++) {
                    values[band] = image.getBand(band).get(x, y);
                }

                // 调整每个通道的亮度
                for (int band = 0; band < image.getNumBands(); band++) {
                    values[band] += brightness * 255;
                    values[band] = Math.max(0, Math.min(values[band], 255));
                }

                // 设置调整后的像素值
                for (int band = 0; band < image.getNumBands(); band++) {
                    image.getBand(band).set(x, y, values[band]);
                }
            }
        });

        return ConvertBufferedImage.convertTo(image, null, true);
    }

    /**
     * 对比度
     *
     * @param input
     * @param contrast ==1无变化， >1增加对比度，<1减少对比度
     * @return
     */
    public static BufferedImage adjustContrast(BufferedImage input, float contrast) {
        Planar<GrayF32> image = ConvertBufferedImage.convertFromPlanar(input, null, true, GrayF32.class);

        float factor = (259 * (contrast + 255)) / (255 * (259 - contrast));

        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                for (int band = 0; band < image.getNumBands(); band++) {
                    float value = image.getBand(band).get(x, y);
                    value = factor * (value - 128) + 128;  // Adjust contrast
                    value = Math.max(0, Math.min(value, 255)); // Ensure value is within [0, 255]
                    image.getBand(band).set(x, y, value);
                }
            }
        }
        return ConvertBufferedImage.convertTo(image, null, true);
    }

    /**
     * 对比度 并行
     * @param input
     * @param contrast
     * @return
     */
    public static BufferedImage adjustContrast2(BufferedImage input, float contrast) {
        Planar<GrayF32> image = ConvertBufferedImage.convertFromPlanar(input, null, true, GrayF32.class);
        int width = image.width;
        int height = image.height;

        // 计算调整对比度所需的因子
        float factor = (259 * (contrast + 255)) / (255 * (259 - contrast));

        // 使用并行流来加速处理
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                float[] values = new float[image.getNumBands()];
                for (int band = 0; band < image.getNumBands(); band++) {
                    values[band] = image.getBand(band).get(x, y);
                }

                // 调整每个通道的对比度
                for (int band = 0; band < image.getNumBands(); band++) {
                    values[band] = factor * (values[band] - 128) + 128;
                    values[band] = Math.max(0, Math.min(values[band], 255));
                }

                // 设置调整后的像素值
                for (int band = 0; band < image.getNumBands(); band++) {
                    image.getBand(band).set(x, y, values[band]);
                }
            }
        });

        return ConvertBufferedImage.convertTo(image, null, true);
    }

    /**
     * 同时调整亮度和对比度
     * @param input
     * @param brightness brightness 参数可以平滑地控制图像的亮度，从完全变黑（-1）到正常亮度（0），再到完全变白（1）
     * @param contrast contrast 参数的有效范围应该是 -255 到 255（不包括 259），因为当 contrast 为 -255 时，因子 factor 将变为 0，这将导致图像变黑；而当 contrast 接近 259 时，因子 factor 将趋向于无穷大，这可能导致图像过曝或完全变白。
     * @return
     */
    public static BufferedImage adjustTwo(BufferedImage input, float brightness, float contrast) {
        Planar<GrayF32> image = ConvertBufferedImage.convertFromPlanar(input, null, true, GrayF32.class);
        int width = image.width;
        int height = image.height;

        // 计算调整对比度所需的因子
        float factor = (259 * (contrast + 255)) / (255 * (259 - contrast));

        // 使用并行流来加速处理
        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                float[] values = new float[image.getNumBands()];
                for (int band = 0; band < image.getNumBands(); band++) {
                    values[band] = image.getBand(band).get(x, y);
                }

                for (int band = 0; band < image.getNumBands(); band++) {
                    // 亮度
                    values[band] += brightness * 255;
                    // 对比度
                    values[band] = factor * (values[band] - 128) + 128;
                    values[band] = Math.max(0, Math.min(values[band], 255));
                }

                // 设置调整后的像素值
                for (int band = 0; band < image.getNumBands(); band++) {
                    image.getBand(band).set(x, y, values[band]);
                }
            }
        });

        return ConvertBufferedImage.convertTo(image, null, true);
    }

    /**
     * 裁剪
     * @param input
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    public static BufferedImage crop(BufferedImage input, Integer x, Integer y, Integer width, Integer height) {
        Planar<GrayU8> colorImage = ConvertBufferedImage.convertFromPlanar(input, null, true, GrayU8.class);

        // 创建一个新的图像用于存储裁剪结果
        Planar<GrayU8> croppedImage = colorImage.subimage(x, y, x + width, y + height, null);

        // 转换回 BufferedImage 格式
        BufferedImage outputImage = ConvertBufferedImage.convertTo(croppedImage, null, true);
        return outputImage;
    }

    /**
     * BufferedImage to Base64
     * @param image
     * @param type
     * @return
     */
    public static String getBase64(BufferedImage image, String type) {
        String imageString = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, type, bos);
            byte[] imageBytes = bos.toByteArray();

            Base64.Encoder encoder = Base64.getEncoder();
            imageString = encoder.encodeToString(imageBytes);

            bos.close();
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
        return imageString;
    }

}
