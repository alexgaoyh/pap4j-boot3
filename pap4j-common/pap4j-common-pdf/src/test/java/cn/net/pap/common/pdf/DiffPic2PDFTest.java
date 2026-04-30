package cn.net.pap.common.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 使用 itextpdf ，把 jp2 类型的图像转换为 pdf
 */
public class DiffPic2PDFTest {

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
            System.out.println("PDF 生成成功！" + tempPdf.toPath().toAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("错误: " + e.getMessage());
        } finally {
            if (document != null && document.isOpen()) {
                document.close();
            }
            if (pdfOutputStream != null) {
                try {
                    pdfOutputStream.close();
                } catch (IOException e) {
                    System.err.println("关闭文件流时出错: " + e.getMessage());
                }
            }
            if (tempPdf != null && tempPdf.exists()) {
                tempPdf.delete();
            }
        }
    }

}
