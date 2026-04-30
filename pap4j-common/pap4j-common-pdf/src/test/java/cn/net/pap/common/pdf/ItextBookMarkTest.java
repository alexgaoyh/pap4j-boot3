package cn.net.pap.common.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfDestination;
import com.itextpdf.text.pdf.PdfOutline;
import com.itextpdf.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;

public class ItextBookMarkTest {

    @Test
    public void bookmarkTest() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("DocumentWithTOC", ".pdf");
        String dest = tempFile.getAbsolutePath();
        try {
            createPdf(dest);
            System.out.println("PDF created successfully: " + dest);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public static void createPdf(String dest) throws DocumentException, IOException {
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(dest));
        document.open();

        // 创建根目录
        PdfOutline rootOutline = writer.getRootOutline();

        // 添加段落和目录项
        addSection(document, writer, rootOutline, "Section 1", "This is the content of section 1.");
        addSection(document, writer, rootOutline, "Section 2", "This is the content of section 2.");
        addSection(document, writer, rootOutline, "Section 3", "This is the content of section 3.");

        document.close();
    }

    private static void addSection(Document document, PdfWriter writer, PdfOutline rootOutline, String title, String content) throws DocumentException {
        // 记录当前页面的编号
        int currentPage = writer.getPageNumber();

        // 添加段落标题
        Paragraph titleParagraph = new Paragraph(title, new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD));
        titleParagraph.setSpacingBefore(20);
        document.add(titleParagraph);

        // 添加段落内容
        Paragraph contentParagraph = new Paragraph(content, new Font(Font.FontFamily.HELVETICA, 12));
        document.add(contentParagraph);

        // 创建书签并设置跳转目标
        PdfDestination destination = new PdfDestination(PdfDestination.FITH); // 跳转到页面顶部
        PdfAction action = PdfAction.gotoLocalPage(currentPage, destination, writer); // 创建跳转动作
        PdfOutline outline = new PdfOutline(rootOutline, action, title); // 创建书签并绑定跳转动作

        document.newPage();
    }

}
