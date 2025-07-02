package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

public class Jbig2ToJpgConverter {

    // @Test
    public void convertTest() throws IOException {
        try {
            String jb2ePath = "C:\\Users\\86181\\Desktop\\003.jb2e";
            String jb2gPath = "C:\\Users\\86181\\Desktop\\003.jb2g";
            String outputJpg = "C:\\Users\\86181\\Desktop\\003.jpg";

            Jbig2ToJpgConverter.convert(jb2ePath, jb2gPath, outputJpg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void convert(String jb2ePath, String jb2gPath, String outputJpgPath) throws IOException {
        byte[] imageBytes = readFile(jb2ePath);
        byte[] globalBytes = readFile(jb2gPath);

        // 拼接 globals + image
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        if (globalBytes != null) {
            merged.write(globalBytes);
        }
        merged.write(imageBytes);

        // 使用 ImageIO 解码（确保 JBIG2 reader 可用）
        ByteArrayInputStream bais = new ByteArrayInputStream(merged.toByteArray());
        MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(bais);

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JBIG2");
        if (!readers.hasNext()) {
            throw new RuntimeException("No JBIG2 reader found. Please maybe add levigo-jbig2-imageio to classpath.");
        }

        ImageReader reader = readers.next();
        reader.setInput(iis);

        BufferedImage bufferedImage = reader.read(0);

        // 写成 JPG
        File output = new File(outputJpgPath);
        ImageIO.write(bufferedImage, "jpg", output);

        System.out.println("Converted JBIG2 to: " + output.getAbsolutePath());
    }

    private static byte[] readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = new FileInputStream(file)) {
            byte[] temp = new byte[4096];
            int len;
            while ((len = is.read(temp)) != -1) {
                buffer.write(temp, 0, len);
            }
        }
        return buffer.toByteArray();
    }
}

