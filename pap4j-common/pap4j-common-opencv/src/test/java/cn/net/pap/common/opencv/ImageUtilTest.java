package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageUtilTest {

    //@Test
    public void scaleAndGrayTest() {
        File file = new File("\\Images");
        File[] files = file.listFiles();
        for(File file1 : files) {
            boolean b = ImageUtil.scaleAndGray(file1.getPath(), "\\after\\" + file1.getName(), 100);
            System.out.println(b);
        }
    }

    // @Test
    public void cropImageCutListTest() {
        List<Rectangle> regions = new ArrayList<>();
        Rectangle rect1 = new Rectangle(000, 000, 050, 050);
        Rectangle rect2 = new Rectangle(050, 050, 050, 050);
        Rectangle rect3 = new Rectangle(100, 100, 050, 050);
        Rectangle rect4 = new Rectangle(150, 150, 050, 050);
        regions.add(rect1);
        regions.add(rect2);
        regions.add(rect3);
        regions.add(rect4);

        List<String> base64s = ImageUtil.cropImageCutList("input.jpg", regions);
        assertTrue(base64s.size() == regions.size());
    }

    // @Test
    public void mergeImagesTest() throws Exception {
        BufferedImage bufferedImage = ImageUtil.mergeImages("1.png",
                "2.png",
                0, 0, 0, 100);
        ImageIO.write(bufferedImage, "png", new File("out.png"));
    }

    // @Test
    public void rotateImageTest() throws Exception {
        boolean b = ImageUtil.rotateImage("C:\\Users\\86181\\Desktop\\origin.jpg",
                "C:\\Users\\86181\\Desktop\\123456.jpg",
                10);
        System.out.println(b);
    }

}
