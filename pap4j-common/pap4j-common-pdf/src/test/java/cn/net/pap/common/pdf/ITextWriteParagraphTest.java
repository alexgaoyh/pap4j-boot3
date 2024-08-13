package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.enums.ChineseFont;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ITextWriteParagraphTest {

    // @Test
    public void utf16ToPdfTest() throws Exception {
        List<String> paragraphs = new ArrayList<>();
        String filePath = "utf16.txt";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_16))) {
            String line;
            while ((line = reader.readLine()) != null) {
                paragraphs.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        writeParagraph("utf16.pdf", paragraphs);
    }

    /**
     * 写入段落，支持 utf16
     * @param pdfPath
     * @param paragraphList
     * @return
     */
    public static Boolean writeParagraph(String pdfPath, List<String> paragraphList) {
        // 创建 Document 对象
        Document document = new Document();
        try {
            // 创建 PdfWriter 对象
            PdfWriter pdfWriter = PdfWriter.getInstance(document, new FileOutputStream(pdfPath));
            // 打开文档
            document.open();

            BaseFont simSun = BaseFont.createFont(ChineseFont.getLocation("宋体"), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            BaseFont simsunb = BaseFont.createFont(ChineseFont.getLocation("宋体ExtB"), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            Font simSunFont = new Font(simSun, 12);
            Font simsunbFont = new Font(simsunb, 12);

            com.itextpdf.text.pdf.PdfContentByte content = pdfWriter.getDirectContent();
            content.setFontAndSize(simSun, 12);
            content.setFontAndSize(simsunb, 12);

            // 循环写入段落
            for (String paragraphStr : paragraphList) {
                // 创建段落
                Paragraph paragraph = new Paragraph();

                char[] chars = paragraphStr.toCharArray();
                for (int i = 0; i < chars.length;) {
                    String c = "";
                    if (Character.isHighSurrogate(chars[i])) {
                        // 如果是代理项的高位，则跳过两个字符
                        c = new String(Character.toChars(Character.toCodePoint(chars[i], chars[i + 1])));
                        i += 2;
                    } else {
                        // 否则，只跳过一个字符
                        c = chars[i] + "";
                        i++;
                    }

                    Font currentFont = fontContainsCharacter(simSun, simsunb, c + "") ? simSunFont : simsunbFont;
                    Chunk chunk = new Chunk(c, currentFont);
                    paragraph.add(chunk);
                }
                // 添加到文档
                document.add(paragraph);
            }
            return true;
        } catch (DocumentException | IOException e) {
            return false;
        } finally {
            // 关闭文档
            document.close();
        }
    }

    private static boolean fontContainsCharacter(BaseFont simSun, BaseFont simSunb, String c)  {
        try {
            return simSun.getWidth(String.valueOf(c)) > 0;
        } catch (Exception e) {
            return false;
        }
    }

}
