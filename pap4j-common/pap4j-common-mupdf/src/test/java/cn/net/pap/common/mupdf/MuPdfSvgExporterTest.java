package cn.net.pap.common.mupdf;

import cn.net.pap.common.mupdf.service.MuPdfFontExtractor;
import cn.net.pap.common.mupdf.service.MuPdfService;
import cn.net.pap.common.mupdf.service.MuPdfSvgExporter;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Page;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link MuPdfSvgExporter} 技术验证：逐字坐标 SVG、字体内嵌与宋体回退。
 *
 * @author alexgaoyh
 */
public class MuPdfSvgExporterTest {

    private static final String CJK_PDF = "ctex.pdf";
    private static final String MATH_PDF = "testmath.pdf";

    @Test
    public void testExportWithoutWebFontsFallsBackToSimSun() throws Exception {
        Path svg = Path.of("target", "svg", "ctex-page1-simsun.svg");

        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdfPath(CJK_PDF), 0, null, svg);
        String content = Files.readString(svg, StandardCharsets.UTF_8);

        Assertions.assertTrue(Files.size(svg) > 0, "SVG 文件不应为空");
        Assertions.assertFalse(content.contains("@font-face"), "没有可用的 TTF/OTF/WOFF 时不应生成 @font-face");
        Assertions.assertTrue(result.embeddedFonts().isEmpty());
        Assertions.assertFalse(result.fallbackFonts().isEmpty(), "未提供字体文件时应全量回退");

        Assertions.assertTrue(content.contains("SimSun") || content.contains("serif"), "样式里应包含回退字体");
        Assertions.assertTrue(count(content, "<text ") > 0, "应按字符输出 <text>");
        Assertions.assertTrue(content.contains("class=\"f0\""), "应按字体分类复用 CSS class");

        // 校验为合法 XML
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    @Test
    public void testExportMathPdfType1AsOutlines() throws Exception {
        String pdf = pdfPath(MATH_PDF);
        Path fontDir = Path.of("target", "svg", "fonts-math-" + System.currentTimeMillis());
        MuPdfFontExtractor.exportFonts(pdf, fontDir);

        Path svg = Path.of("target", "svg", "testmath-page1-outlines.svg");
        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdf, 0, fontDir, svg);
        String content = Files.readString(svg, StandardCharsets.UTF_8);

        Assertions.assertFalse(result.outlineFonts().isEmpty(), "testmath.pdf 应成功提取并使用 Type1 轮廓字体");
        Assertions.assertTrue(content.contains("<path id=\"g"), "应输出字形轮廓 <path>");
        Assertions.assertTrue(content.contains("<use xlink:href=\"#g"), "应复用字形轮廓 <use>");
        Assertions.assertTrue(count(content, "<use ") > 0, "应有数学公式轮廓字形");

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    @Test
    public void testExportType1AsOutlines() throws Exception {
        Assumptions.assumeTrue(hasResource("jjsjwlyjzs_zfy.pdf"), "缺少 jjsjwlyjzs_zfy.pdf，跳过 Type1 矢量轮廓专项测试");
        String pdf = pdfPath("jjsjwlyjzs_zfy.pdf");
        Path fontDir = Path.of("target", "svg", "fonts-" + System.currentTimeMillis());
        MuPdfFontExtractor.exportFonts(pdf, fontDir);

        Path svg = Path.of("target", "svg", "jjsjwlyjzs_zfy-page3-outlines.svg");
        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdf, 2, fontDir, svg);
        String content = Files.readString(svg, StandardCharsets.UTF_8);

        Assertions.assertTrue(result.outlineFonts().contains("DY1+ZGXSfW-1"), "Type1 子集应使用矢量轮廓");
        Assertions.assertTrue(result.outlineFonts().contains("DY6+ZGXSfW-6"), "Type1 子集应使用矢量轮廓");
        Assertions.assertTrue(result.fallbackFonts().contains("方正书宋_GBK"), "没有字体文件的中文应回退宋体");
        Assertions.assertTrue(content.contains("<path id=\"g"), "应输出字形轮廓 <path>");
        Assertions.assertTrue(content.contains("<use xlink:href=\"#g"), "应复用字形轮廓 <use>");

        int textCount = count(content, "<text ");
        int useCount = count(content, "<use ");
        Assertions.assertTrue(useCount > 0, "应有轮廓字形");
        Assertions.assertTrue(textCount > 0, "中文仍应以文本输出");
        Assertions.assertEquals(1516, textCount + useCount, "每个字符要么是文本要么是轮廓");

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    @Test
    public void testExportFullDensePage() throws Exception {
        String pdf = pdfPath(CJK_PDF);
        MuPdfService service = new MuPdfService();
        int bestPage = 0;
        int maxChars = 0;
        try {
            int totalPages = service.getPageCount(pdf);
            // 扫描前 15 页，找到文本量最丰富、排版最完整的一页（避开空白较多的封面/目录）
            for (int p = 0; p < Math.min(15, totalPages); p++) {
                var page = service.extractCoordinates(pdf, p).pages().get(0);
                int charCount = page.blocks().stream()
                        .filter(b -> b.lines() != null)
                        .flatMap(b -> b.lines().stream())
                        .mapToInt(l -> l.chars() != null ? l.chars().size() : 0)
                        .sum();
                System.out.println("ctex.pdf Page " + (p + 1) + " char count: " + charCount);
                if (charCount > maxChars) {
                    maxChars = charCount;
                    bestPage = p;
                }
            }
        } finally {
            service.close();
        }

        System.out.println("Selected dense page: " + (bestPage + 1) + " with " + maxChars + " chars");
        Path svg = Path.of("target", "svg", "ctex-page-full.svg");
        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdf, bestPage, null, svg);

        Assertions.assertTrue(Files.size(svg) > 0);
        Assertions.assertTrue(maxChars > 500, "密集页字符数应大于 500");

        String content = Files.readString(svg, StandardCharsets.UTF_8);
        int textElementCount = count(content, "<text ");
        System.out.println("Exported full SVG text elements: " + textElementCount);
        Assertions.assertTrue(textElementCount > 500, "密集页导出的 <text> 元素应大于 500");

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    @Test
    public void testEmbedWebFontWhenProvided() throws Exception {
        Path systemTtf = firstExisting(
                Path.of("C:/Windows/Fonts/arial.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
                Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"));
        Assumptions.assumeTrue(systemTtf != null, "本机没有可用于内嵌测试的 TTF 字体，跳过");

        int pageNum = 3;
        MuPdfService service = new MuPdfService();
        String targetFontName;
        try {
            MuPdfService.PageResult page = service.extractCoordinates(pdfPath(CJK_PDF), pageNum).pages().get(0);
            targetFontName = page.blocks().stream()
                    .flatMap(b -> b.lines().stream())
                    .flatMap(l -> l.chars().stream())
                    .map(MuPdfService.CharResult::font)
                    .filter(f -> f != null && !f.isBlank())
                    .findFirst().orElseThrow();
        } finally {
            service.close();
        }

        Path fontDir = Files.createTempDirectory("mupdf-svg-fonts-");
        Files.copy(systemTtf, fontDir.resolve(targetFontName + ".ttf"));
        Path svg = Path.of("target", "svg", "ctex-page4-embedded.svg");

        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdfPath(CJK_PDF), pageNum, fontDir, svg);
        String content = Files.readString(svg, StandardCharsets.UTF_8);

        Assertions.assertTrue(result.embeddedFonts().contains(targetFontName), "应内嵌提供的 TTF 字体: " + targetFontName);
        Assertions.assertTrue(content.contains("@font-face"), "应生成 @font-face");
        Assertions.assertTrue(content.contains("data:font/ttf;base64,"), "应以 base64 内嵌 TTF");
        Assertions.assertTrue(content.contains("format('truetype')"));
        Assertions.assertTrue(content.contains("'" + targetFontName + "'"), "内嵌字体的 family 名应为对应名称");

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    private static boolean hasResource(String fileName) {
        return MuPdfSvgExporterTest.class.getResource("/" + fileName) != null;
    }

    private static String pdfPath(String fileName) {
        URL url = MuPdfSvgExporterTest.class.getResource("/" + fileName);
        Assertions.assertNotNull(url, "未找到测试资源: " + fileName);
        return new File(url.getFile()).getAbsolutePath();
    }

    private static Path firstExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
