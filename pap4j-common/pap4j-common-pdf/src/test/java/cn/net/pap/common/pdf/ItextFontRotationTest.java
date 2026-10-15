package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.enums.ChineseFont;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.LineSegment;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import com.itextpdf.text.pdf.parser.Vector;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * iText 字体旋转测试用例
 * <p>
 * 测试向 PDF 写入未旋转与旋转文字，并验证解析出的基线方向与旋转角度。
 * </p>
 */
public class ItextFontRotationTest {

    private static final Logger log = LoggerFactory.getLogger(ItextFontRotationTest.class);

    /**
     * 单字解析结果
     *
     * @param text  单字文本内容
     * @param angle 旋转角度（单位：度）
     * @param dx    X轴偏移分量
     * @param dy    Y轴偏移分量
     */
    public record CharInfo(String text, double angle, float dx, float dy) {
    }

    /**
     * 向 PDF 写入两个字，一个未旋转，一个旋转，并分别使用 iText 和 PDFBox 进行解析验证
     *
     * @throws Exception 测试异常
     */
    @Test
    public void testFontRotation() throws Exception {
        File outputDir = new File(System.getProperty("java.io.tmpdir"));
        File tempPdf = new File(outputDir, "font_rotation_test.pdf");
        log.info("Generating test PDF at: {}", tempPdf.getAbsolutePath());

        // 1. 生成包含未旋转和旋转文字的 PDF
        generatePdfWithRotatedFont(tempPdf, "正", "转", 90.0f);

        // 2. 使用 iText 解析并核对文字旋转方向
        List<CharInfo> itextChars = parseWithItext(tempPdf);
        log.info("iText parsed chars: {}", itextChars);
        Assertions.assertEquals(2, itextChars.size());

        CharInfo normalChar = itextChars.get(0);
        CharInfo rotatedChar = itextChars.get(1);

        Assertions.assertEquals("正", normalChar.text());
        Assertions.assertEquals(0.0, normalChar.angle(), 1e-2, "未旋转字符角度应为 0");

        Assertions.assertEquals("转", rotatedChar.text());
        Assertions.assertEquals(90.0, rotatedChar.angle(), 1e-2, "旋转字符角度应为 90");

        // 3. 使用 PDFBox 解析并核对文字方向
        List<Float> pdfboxDirections = parseWithPdfBox(tempPdf);
        log.info("PDFBox parsed directions: {}", pdfboxDirections);
        Assertions.assertEquals(2, pdfboxDirections.size());
        Assertions.assertEquals(0.0f, pdfboxDirections.get(0), 1e-2, "PDFBox 未旋转字符 direction 应为 0");
        Assertions.assertEquals(90.0f, pdfboxDirections.get(1), 1e-2, "PDFBox 旋转字符 direction 应为 90");
    }

    /**
     * 生成包含一个未旋转字和一个旋转字的 PDF
     *
     * @param targetPdf     目标输出 PDF 文件
     * @param unrotatedText 未旋转文本字符
     * @param rotatedText   旋转文本字符
     * @param rotationAngle 旋转角度（例如 90 度）
     * @throws IOException       IO 异常
     * @throws DocumentException iText 文档异常
     */
    public static void generatePdfWithRotatedFont(File targetPdf, String unrotatedText, String rotatedText, float rotationAngle)
            throws IOException, DocumentException {
        try (FileOutputStream fos = new FileOutputStream(targetPdf)) {
            Document document = new Document(PageSize.A4);
            try {
                PdfWriter writer = PdfWriter.getInstance(document, fos);
                document.open();

                BaseFont simSun = BaseFont.createFont(ChineseFont.getLocation("宋体"), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                PdfContentByte cb = writer.getDirectContent();

                cb.beginText();
                cb.setFontAndSize(simSun, 24);

                // 1. 未旋转文字（旋转角 0 度）
                cb.showTextAligned(PdfContentByte.ALIGN_LEFT, unrotatedText, 100, 600, 0);

                // 2. 旋转文字（指定旋转角）
                cb.showTextAligned(PdfContentByte.ALIGN_LEFT, rotatedText, 200, 600, rotationAngle);

                cb.endText();
            } finally {
                if (document.isOpen()) {
                    document.close();
                }
            }
        }
    }

    /**
     * 使用 iText 解析 PDF 每页文本方向与角度
     *
     * @param pdfFile 输入 PDF 文件
     * @return 解析出的字符列表
     * @throws IOException IO 异常
     */
    public static List<CharInfo> parseWithItext(File pdfFile) throws IOException {
        List<CharInfo> charList = new ArrayList<>();
        PdfReader reader = new PdfReader(pdfFile.getAbsolutePath());
        try {
            PdfReaderContentParser parser = new PdfReaderContentParser(reader);
            parser.processContent(1, new RenderListener() {
                @Override
                public void beginTextBlock() {
                }

                @Override
                public void renderText(TextRenderInfo renderInfo) {
                    for (TextRenderInfo charInfo : renderInfo.getCharacterRenderInfos()) {
                        String text = charInfo.getText();
                        LineSegment baseline = charInfo.getBaseline();
                        Vector start = baseline.getStartPoint();
                        Vector end = baseline.getEndPoint();
                        float dx = end.get(Vector.I1) - start.get(Vector.I1);
                        float dy = end.get(Vector.I2) - start.get(Vector.I2);
                        double angle = Math.toDegrees(Math.atan2(dy, dx));
                        charList.add(new CharInfo(text, angle, dx, dy));
                    }
                }

                @Override
                public void endTextBlock() {
                }

                @Override
                public void renderImage(ImageRenderInfo renderInfo) {
                }
            });
        } finally {
            reader.close();
        }
        return charList;
    }

    /**
     * 使用 PDFBox 解析 PDF 字符方向（getDir 返回文本在页面上的角度，如 0, 90, 180, 270）
     *
     * @param pdfFile 输入 PDF 文件
     * @return 字符 direction 列表（单位：度）
     * @throws IOException IO 异常
     */
    public static List<Float> parseWithPdfBox(File pdfFile) throws IOException {
        List<Float> directions = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    directions.add(text.getDir());
                    super.processTextPosition(text);
                }
            };
            stripper.getText(doc);
        }
        return directions;
    }

}
