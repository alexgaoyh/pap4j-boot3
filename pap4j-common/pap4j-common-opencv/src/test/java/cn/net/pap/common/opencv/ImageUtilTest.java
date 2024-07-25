package cn.net.pap.common.opencv;

import org.junit.jupiter.api.Test;

import java.io.File;

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

}
