package cn.net.pap.example.javafx.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;

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

}
