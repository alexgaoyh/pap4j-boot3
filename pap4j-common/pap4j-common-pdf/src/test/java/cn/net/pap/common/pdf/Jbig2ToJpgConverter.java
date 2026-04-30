package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

public class Jbig2ToJpgConverter {

    @Test
    public void convertTest() throws IOException {
        File jb2eFile = null;
        File jb2gFile = null;
        File tempFile = null;
        try {
            jb2eFile = TestResourceUtil.getFile("003.jb2e");
            jb2gFile = TestResourceUtil.getFile("003.jb2g");
            tempFile = File.createTempFile("003-", ".jpg");
            String outputJpg = tempFile.getAbsolutePath();

            Jbig2ToJpgConverter.convert(jb2eFile.getAbsolutePath(), jb2gFile.getAbsolutePath(), outputJpg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jb2eFile != null && jb2eFile.exists()) jb2eFile.delete();
            if (jb2gFile != null && jb2gFile.exists()) jb2gFile.delete();
            if (tempFile != null && tempFile.exists()) tempFile.delete();
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

