package cn.net.pap.common.boofcv;

import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.Planar;

import java.awt.image.BufferedImage;

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


}
