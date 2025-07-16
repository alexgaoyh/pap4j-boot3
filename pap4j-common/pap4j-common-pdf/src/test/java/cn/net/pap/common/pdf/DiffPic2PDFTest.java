package cn.net.pap.common.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;

public class DiffPic2PDFTest {

    public static String imagePath = "C:\\Users\\86181\\Desktop\\0.jp2";

    // @Test
    public void pic2PDF() throws IOException {
        try {
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(imagePath + ".pdf"));
            document.open();
            Image jp2Image = Image.getInstance(imagePath);
            document.add(jp2Image);
            document.close();
            System.out.println("PDF 生成成功！");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("错误: " + e.getMessage());
        }
    }

}
