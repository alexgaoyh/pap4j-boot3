package cn.net.pap.common.opencv;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 向量
 */
public class VectorUtil {

    public static float[] convertImageToVector(String imagePath) throws Exception {
        BufferedImage image = ImageIO.read(new File(imagePath));
        return convertImageToVector(image);
    }

    /**
     * 图像转向量表示
     *
     * @param image
     * @return
     */
    public static float[] convertImageToVector(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        float[] vector = new float[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);

                vector[y * width + x] = (pixel == 0xFF000000) ? 1 : 0;
            }
        }

        return vector;
    }
}
