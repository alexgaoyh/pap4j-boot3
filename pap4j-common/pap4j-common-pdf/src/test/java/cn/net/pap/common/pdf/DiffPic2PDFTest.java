package cn.net.pap.common.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 使用 itextpdf ，把 jp2 类型的图像转换为 pdf
 */
public class DiffPic2PDFTest {

    private static final Logger log = LoggerFactory.getLogger(DiffPic2PDFTest.class);

    public static String imagePath = "0.jp2";

    @Test
    public void pic2PDF() throws IOException {
        FileOutputStream pdfOutputStream = null;
        Document document = null;
        java.io.File tempPdf = null;
        try {
            Image jp2Image = Image.getInstance(TestResourceUtil.getFile(imagePath).getAbsolutePath());
            Rectangle pageSize = new Rectangle(jp2Image.getScaledWidth(), jp2Image.getScaledHeight());
            document = new Document(pageSize);
            tempPdf = java.io.File.createTempFile("pic2pdf", ".pdf");
            pdfOutputStream = new FileOutputStream(tempPdf);
            PdfWriter.getInstance(document, pdfOutputStream);
            document.open();
            document.add(jp2Image);
            document.close();
            log.info("PDF 生成成功！{}", tempPdf.toPath().toAbsolutePath());
        } catch (Exception e) {
            log.error("错误: ", e);
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
            if (pdfOutputStream != null) {
                try {
                    pdfOutputStream.close();
                } catch (IOException e) {
                    log.error("关闭文件流时出错: ", e);
                }
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

}
