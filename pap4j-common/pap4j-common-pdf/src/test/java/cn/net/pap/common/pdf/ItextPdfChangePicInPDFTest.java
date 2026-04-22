package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.ProcessResult;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 把 pdf 内的图像进行转换(jp2)，并进行替换，达到压缩pdf的效果
 */
public class ItextPdfChangePicInPDFTest {

    private static final Logger log = LoggerFactory.getLogger(ItextPdfChangePicInPDFTest.class);

    @Test
    public void extractImagesTest() throws Exception {
        File tempFile = File.createTempFile("input-jp2", ".pdf");
        tempFile.deleteOnExit();
        extractAndConvertImagesToJp2(TestResourceUtil.getFile("jpg.pdf").getAbsolutePath(), tempFile.getAbsolutePath());
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
        List<String> command = Arrays.asList("magick", tempInputFile.getAbsolutePath(), "-quality", defaultQuality, tempInputFile.getAbsolutePath().replace(sourceFormat, targetFormat));

        ExecutorService tempExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1),
                r -> new Thread(r, "magick-executor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        try {
            ProcessResult result = ProcessPoolUtil.runCommand(command, 10, tempExecutor);
            return Files.readAllBytes(Paths.get(tempInputFile.getAbsolutePath().replace(sourceFormat, targetFormat)));
        } catch (IOException e) {
            log.warn("Magick command not found or execution failed", e);
            throw e;
        } finally {
            // 务必关闭临时线程池，防止内存/线程泄漏
            if (tempExecutor != null) {
                tempExecutor.shutdown(); // 停止接收新任务
                try {
                    // 等待 30 秒，给正在运行的任务一点时间
                    if (!tempExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("部分线程池任务未在 30 秒内结束，强制关闭");
                        tempExecutor.shutdownNow(); // 超时强制关闭
                    }
                } catch (InterruptedException e) {
                    log.error("关闭线程池时被中断", e);
                    tempExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            tempInputFile.delete();
            new File(tempInputFile.getAbsolutePath().replace(sourceFormat, targetFormat)).delete();
        }
    }



}
