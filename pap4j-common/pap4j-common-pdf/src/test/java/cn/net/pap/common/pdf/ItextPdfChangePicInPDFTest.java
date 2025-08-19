package cn.net.pap.common.pdf;

import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 把 pdf 内的图像进行转换(jp2)，并进行替换，达到压缩pdf的效果
 */
public class ItextPdfChangePicInPDFTest {

    private static final Logger log = LoggerFactory.getLogger(ItextPdfChangePicInPDFTest.class);

    // @Test
    public void extractImagesTest() throws Exception {
        extractAndConvertImagesToJp2("C:\\Users\\86181\\Desktop\\GBT 9237-2017.pdf", "C:\\Users\\86181\\Desktop\\input-jp2.pdf");
    }

    /**
     * 提取PDF中的所有图像并替换为JP2格式后保存新文件
     * @param pdfFile 输入PDF文件路径
     * @param outputPdfFile 输出PDF文件路径
     * @return 提取的原始图像列表
     */
    public static Boolean extractAndConvertImagesToJp2(String pdfFile, String outputPdfFile)
            throws Exception {
        PdfReader reader = new PdfReader(pdfFile);
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(outputPdfFile));

        PdfDictionary pageDict;
        PdfDictionary resources;
        PdfDictionary xobjects;

        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            pageDict = reader.getPageN(i);
            resources = pageDict.getAsDict(PdfName.RESOURCES);
            if (resources == null) continue;

            xobjects = resources.getAsDict(PdfName.XOBJECT);
            if (xobjects == null) continue;

            for (PdfName name : xobjects.getKeys()) {
                PdfObject obj = xobjects.getDirectObject(name);
                if (obj instanceof PRStream) {
                    PRStream stream = (PRStream) obj;
                    PdfName subtype = stream.getAsName(PdfName.SUBTYPE);
                    if (PdfName.IMAGE.equals(subtype)) {
                        // 1. 提取原始图像
                        PdfImageObject image = new PdfImageObject(stream);
                        BufferedImage bufferedImage = image.getBufferedImage();
                        if (bufferedImage != null) {

                            byte[] jp2Data = convert(bufferedImage, image.getFileType(), "jp2");
                            // 3. Update the stream
                            stream.clear();
                            stream.setData(jp2Data, false); // Don't compress as JP2 is already compressed

                            // 4. 更新字典参数（关键修改）
                            stream.put(PdfName.FILTER, new PdfName("JPXDecode")); // 设置 JP2 解码器
                            stream.put(PdfName.TYPE, PdfName.XOBJECT); // 保持 XObject 类型
                            stream.put(PdfName.SUBTYPE, PdfName.IMAGE); // 仍然必须是 Image

                            // 5. 移除可能冲突的参数（如旧的颜色空间、位深等）
                            stream.remove(PdfName.COLORSPACE);
                            stream.remove(PdfName.BITSPERCOMPONENT);
                            stream.remove(PdfName.DECODE);

                            // 6. 必须设置 Width 和 Height（JP2 仍然需要）
                            stream.put(PdfName.WIDTH, new PdfNumber(bufferedImage.getWidth()));
                            stream.put(PdfName.HEIGHT, new PdfNumber(bufferedImage.getHeight()));

                        }
                    }
                }
            }
        }

        stamper.close();
        reader.close();
        return true;
    }

    public static byte[] convert(BufferedImage sourceImage, String sourceFormat, String targetFormat) throws Exception {
        // 首先将BufferedImage保存为临时文件
        File tempInputFile = File.createTempFile("pdf_change_pic_", "." + sourceFormat);
        ImageIO.write(sourceImage, sourceFormat, tempInputFile);

        // todo 这里可以做一个判断，如果原始图像的大小够小的话，那么这里 quality 可以大一点，避免更小的图像再次变小造成一些失真(一个空白页的png图像的转换)
        String defaultQuality = "35";
        if(tempInputFile.length() < 0.2 * 1024 * 1024) {
            defaultQuality = "100";
        }
        ProcessBuilder processBuilder = new ProcessBuilder("magick", tempInputFile.getAbsolutePath(), "-quality", defaultQuality, tempInputFile.getAbsolutePath().replace(sourceFormat, targetFormat));
        Process process = null;

        try {
            process = processBuilder.start();

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            int timeout = 30; // 超时时间(秒)
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(String.format("Process timed out after %d seconds", timeout));
            }

            int exitCode = process.exitValue();
            String stderr = errorOutput.toString().trim();

            if (exitCode != 0 && !stderr.isEmpty()) {
                // 仅消费 InputStream 防止阻塞
                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (stdReader.readLine() != null) {
                        // 不记录输出，只清空流
                    }
                }
                log.error("Magick convert failed with exit code {}: {}", exitCode, stderr);
                throw new RuntimeException(String.format("Process failed with exit code %d: %s", exitCode, stderr));
            } else {
                // 没有错误输出 → 读取 InputStream 作为有效输出
                StringBuilder stdOutput = new StringBuilder();
                try (BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = stdReader.readLine()) != null) {
                        stdOutput.append(line).append("\n");
                    }
                }
                log.info("Magick convert succeeded: {}", stdOutput.toString().trim());
            }
            return Files.readAllBytes(Paths.get(tempInputFile.getAbsolutePath().replace(sourceFormat, targetFormat)));
        } catch (IOException e) {
            log.warn("Magick command not found or execution failed", e);
            throw e;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly(); // 确保进程被终止
            }
            tempInputFile.delete();
            new File(tempInputFile.getAbsolutePath().replace(sourceFormat, targetFormat)).delete();
        }
    }



}
