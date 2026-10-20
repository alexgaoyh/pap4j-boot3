package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.enums.ChineseFont;
import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfPageEventHelper;
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
 * iText 与 PDFBox 旋转特性验证测试用例
 * <p>
 * 测试向 PDF 写入未旋转与多角度旋转文字（如 0°、45°、90°、180°、270° 以及特殊大写字母 'L' 等），
 * 以及生成包含页面旋转（如 /Rotate 90、180、270）的 PDF 文件，
 * 并分别使用 iText 和 PDFBox 验证解析出的字符基线方向、旋转角度及页面旋转属性。
 * </p>
 */
public class ItextFontRotationTest {

    private static final Logger log = LoggerFactory.getLogger(ItextFontRotationTest.class);

    /**
     * 单字解析结果
     *
     * @param text       单字文本内容
     * @param angle      旋转角度（单位：度）
     * @param dx         X轴偏移分量
     * @param dy         Y轴偏移分量
     * @param rectWidth  字符边界矩形宽度
     * @param rectHeight 字符边界矩形高度
     */
    public record CharInfo(String text, double angle, float dx, float dy, float rectWidth, float rectHeight) {
        public CharInfo(String text, double angle, float dx, float dy) {
            this(text, angle, dx, dy, 0f, 0f);
        }
    }

    /**
     * 待写入的旋转字符定义项
     *
     * @param text          字符或文本内容
     * @param x             X 轴物理坐标
     * @param y             Y 轴物理坐标
     * @param rotationAngle 旋转角度（单位：度，如 0、45、90、180、270）
     */
    public record RotatedCharItem(String text, float x, float y, float rotationAngle) {
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
     * 测试向 PDF 写入多角度旋转字符（0°、45°、90°、180°、270° 及特殊英文字母 'L'），
     * 验证 iText 基线方向与 PDFBox 方向解析，并验证旋转后字符的高度与边界矩形不为零。
     *
     * @throws Exception 测试异常
     */
    @Test
    public void testMultiAngleFontRotation() throws Exception {
        File outputDir = new File(System.getProperty("java.io.tmpdir"));
        File tempPdf = new File(outputDir, "multi_angle_rotation_test.pdf");
        log.info("Generating multi-angle test PDF at: {}", tempPdf.getAbsolutePath());

        List<RotatedCharItem> items = List.of(
                new RotatedCharItem("正", 100, 600, 0.0f),
                new RotatedCharItem("斜", 150, 600, 45.0f),
                new RotatedCharItem("转", 200, 600, 90.0f),
                new RotatedCharItem("倒", 250, 600, 180.0f),
                new RotatedCharItem("反", 300, 600, 270.0f),
                new RotatedCharItem("L", 350, 600, 90.0f)
        );

        generatePdfWithMultiAngleRotatedChars(tempPdf, items);

        // 1. 使用 iText 解析多角度字符并核对
        List<CharInfo> itextChars = parseWithItext(tempPdf);
        log.info("iText parsed multi-angle chars: {}", itextChars);
        Assertions.assertEquals(items.size(), itextChars.size(), "解析出的字符数量应与输入一致");

        // 0 度：正
        Assertions.assertEquals("正", itextChars.get(0).text());
        Assertions.assertEquals(0.0, itextChars.get(0).angle(), 1e-2, "0度文字角度应为 0");

        // 45 度：斜
        Assertions.assertEquals("斜", itextChars.get(1).text());
        Assertions.assertEquals(45.0, itextChars.get(1).angle(), 1e-2, "45度文字角度应为 45");

        // 90 度：转
        Assertions.assertEquals("转", itextChars.get(2).text());
        Assertions.assertEquals(90.0, itextChars.get(2).angle(), 1e-2, "90度文字角度应为 90");

        // 180 度：倒 (Math.atan2 对 (-1, 0) 返回 180 或 -180)
        Assertions.assertEquals("倒", itextChars.get(3).text());
        Assertions.assertEquals(180.0, Math.abs(itextChars.get(3).angle()), 1e-2, "180度文字绝对角度应为 180");

        // 270 度：反 (Math.atan2 对 (0, -1) 返回 -90)
        Assertions.assertEquals("反", itextChars.get(4).text());
        double normalizedAngle270 = itextChars.get(4).angle() < 0 ? itextChars.get(4).angle() + 360.0 : itextChars.get(4).angle();
        Assertions.assertEquals(270.0, normalizedAngle270, 1e-2, "270度文字归一化角度应为 270");

        // 90 度：大写字母 L (针对原注释中关于字符L旋转后高度/边界是否异常的验证)
        CharInfo charL = itextChars.get(5);
        Assertions.assertEquals("L", charL.text());
        Assertions.assertEquals(90.0, charL.angle(), 1e-2, "旋转90度的字母L其基线角度应为 90");
        Assertions.assertTrue(charL.rectWidth() > 0 || charL.rectHeight() > 0, "旋转90度的字母L边界尺寸不应为 0");

        // 2. 使用 PDFBox 解析正交方向角度
        List<Float> pdfboxDirections = parseWithPdfBox(tempPdf);
        log.info("PDFBox parsed directions: {}", pdfboxDirections);
        Assertions.assertEquals(items.size(), pdfboxDirections.size());
        Assertions.assertEquals(0.0f, pdfboxDirections.get(0), 1e-2);
        Assertions.assertEquals(90.0f, pdfboxDirections.get(2), 1e-2);
        Assertions.assertEquals(180.0f, pdfboxDirections.get(3), 1e-2);
        Assertions.assertEquals(270.0f, pdfboxDirections.get(4), 1e-2);
        Assertions.assertEquals(90.0f, pdfboxDirections.get(5), 1e-2);
    }

    /**
     * 测试生成不同页面旋转角度（/Rotate 90、180、270）的 PDF，并验证 iText 与 PDFBox 对页面旋转属性的正确识别。
     *
     * @throws Exception 测试异常
     */
    @Test
    public void testPageRotation() throws Exception {
        int[] rotations = new int[]{90, 180, 270};
        File outputDir = new File(System.getProperty("java.io.tmpdir"));

        for (int rotation : rotations) {
            File tempPdf = new File(outputDir, "page_rotation_" + rotation + "_test.pdf");
            log.info("Generating page rotation PDF ({} deg) at: {}", rotation, tempPdf.getAbsolutePath());

            generatePdfWithPageRotation(tempPdf, "页面旋转测试-" + rotation, rotation);

            // 1. iText 校验页面旋转属性
            int itextRotation = getPageRotationWithItext(tempPdf, 1);
            log.info("iText detected page rotation: {} (expected: {})", itextRotation, rotation);
            Assertions.assertEquals(rotation, itextRotation, "iText 读取到的页面旋转角度不符");

            // 2. PDFBox 校验页面旋转属性
            int pdfboxRotation = getPageRotationWithPdfBox(tempPdf, 0);
            log.info("PDFBox detected page rotation: {} (expected: {})", pdfboxRotation, rotation);
            Assertions.assertEquals(rotation, pdfboxRotation, "PDFBox 读取到的页面旋转角度不符");

            // 3. 校验能够正常解析字符
            List<CharInfo> itextChars = parseWithItext(tempPdf);
            Assertions.assertFalse(itextChars.isEmpty(), "旋转页面中应包含可读取文本字符");
        }
    }

    /**
     * 测试页面旋转（90度）与字符自身旋转（90度）复合场景，验证页面属性与字符基线方向的相互独立性。
     *
     * @throws Exception 测试异常
     */
    @Test
    public void testPageAndCharRotationCombined() throws Exception {
        File outputDir = new File(System.getProperty("java.io.tmpdir"));
        File tempPdf = new File(outputDir, "page_and_char_combined_rotation_test.pdf");
        log.info("Generating combined rotation PDF at: {}", tempPdf.getAbsolutePath());

        List<RotatedCharItem> items = List.of(
                new RotatedCharItem("平", 100, 500, 0.0f),
                new RotatedCharItem("直", 200, 500, 90.0f)
        );

        generatePdfWithPageAndCharRotation(tempPdf, 90, items);

        // 校验页面旋转为 90
        Assertions.assertEquals(90, getPageRotationWithItext(tempPdf, 1));
        Assertions.assertEquals(90, getPageRotationWithPdfBox(tempPdf, 0));

        // 校验单字字符角度
        List<CharInfo> itextChars = parseWithItext(tempPdf);
        Assertions.assertEquals(2, itextChars.size());
        Assertions.assertEquals("平", itextChars.get(0).text());
        Assertions.assertEquals(0.0, itextChars.get(0).angle(), 1e-2);
        Assertions.assertEquals("直", itextChars.get(1).text());
        Assertions.assertEquals(90.0, itextChars.get(1).angle(), 1e-2);
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
     * 生成包含多个不同坐标与不同旋转角度字符的 PDF
     *
     * @param targetPdf 目标输出 PDF 文件
     * @param items     待写入的旋转字符列表
     * @throws IOException       IO 异常
     * @throws DocumentException iText 文档异常
     */
    public static void generatePdfWithMultiAngleRotatedChars(File targetPdf, List<RotatedCharItem> items)
            throws IOException, DocumentException {
        generatePdfWithPageAndCharRotation(targetPdf, 0, items);
    }

    /**
     * 生成指定页面旋转角度（/Rotate 90、180、270）并写入文本内容的 PDF
     *
     * @param targetPdf    目标输出 PDF 文件
     * @param text         写入文本内容
     * @param pageRotation 页面旋转角度（单位：度，如 90、180、270）
     * @throws IOException       IO 异常
     * @throws DocumentException iText 文档异常
     */
    public static void generatePdfWithPageRotation(File targetPdf, String text, int pageRotation)
            throws IOException, DocumentException {
        List<RotatedCharItem> items = List.of(new RotatedCharItem(text, 100, 500, 0.0f));
        generatePdfWithPageAndCharRotation(targetPdf, pageRotation, items);
    }

    /**
     * 生成指定页面旋转角度并在指定坐标绘制多角度旋转文本的 PDF
     *
     * @param targetPdf    目标输出 PDF 文件
     * @param pageRotation 页面旋转角度（如 0、90、180、270）
     * @param items        待写入字符项列表
     * @throws IOException       IO 异常
     * @throws DocumentException iText 文档异常
     */
    public static void generatePdfWithPageAndCharRotation(File targetPdf, int pageRotation, List<RotatedCharItem> items)
            throws IOException, DocumentException {
        try (FileOutputStream fos = new FileOutputStream(targetPdf)) {
            Document document = new Document(PageSize.A4);
            try {
                PdfWriter writer = PdfWriter.getInstance(document, fos);
                if (pageRotation != 0) {
                    writer.setPageEvent(new PdfPageEventHelper() {
                        @Override
                        public void onStartPage(PdfWriter writer, Document document) {
                            writer.addPageDictEntry(PdfName.ROTATE, new PdfNumber(pageRotation));
                        }
                    });
                }
                document.open();

                BaseFont simSun = BaseFont.createFont(ChineseFont.getLocation("宋体"), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                PdfContentByte cb = writer.getDirectContent();

                cb.beginText();
                cb.setFontAndSize(simSun, 24);

                for (RotatedCharItem item : items) {
                    cb.showTextAligned(PdfContentByte.ALIGN_LEFT, item.text(), item.x(), item.y(), item.rotationAngle());
                }

                cb.endText();
            } finally {
                if (document.isOpen()) {
                    document.close();
                }
            }
        }
    }

    /**
     * 使用 iText 读取指定 PDF 单页的旋转角度（/Rotate 属性）
     *
     * @param pdfFile 输入 PDF 文件
     * @param pageNum 页码 (1-indexed)
     * @return 旋转角度（如 0、90、180、270）
     * @throws IOException IO 异常
     */
    public static int getPageRotationWithItext(File pdfFile, int pageNum) throws IOException {
        PdfReader reader = new PdfReader(pdfFile.getAbsolutePath());
        try {
            return reader.getPageRotation(pageNum);
        } finally {
            reader.close();
        }
    }

    /**
     * 使用 PDFBox 读取指定 PDF 单页的旋转角度（/Rotate 属性）
     *
     * @param pdfFile   输入 PDF 文件
     * @param pageIndex 页面索引 (0-indexed)
     * @return 旋转角度（如 0、90、180、270）
     * @throws IOException IO 异常
     */
    public static int getPageRotationWithPdfBox(File pdfFile, int pageIndex) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            return doc.getPage(pageIndex).getRotation();
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
                        Rectangle2D rect = charInfo.getDescentLine().getBoundingRectange();
                        float rw = rect != null ? (float) rect.getWidth() : 0f;
                        float rh = rect != null ? (float) rect.getHeight() : 0f;
                        charList.add(new CharInfo(text, angle, dx, dy, rw, rh));
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
