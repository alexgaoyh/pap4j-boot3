package cn.net.pap.common.mupdf;

import cn.net.pap.common.mupdf.service.MuPdfFontExtractor;
import cn.net.pap.common.mupdf.service.MuPdfSvgExporter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link MuPdfSvgExporter} 技术验证：逐字坐标 SVG、字体内嵌与宋体回退。
 *
 * @author alexgaoyh
 */
public class MuPdfSvgExporterTest {

    private static final String PDF = "jjsjwlyjzs_zfy.pdf";

    @Test
    public void testExportWithoutWebFontsFallsBackToSimSun() throws Exception {
        Path svg = Path.of("target", "svg", "jjsjwlyjzs_zfy-page3-simsun.svg");

        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdfPath(), 2, null, svg);
        String content = Files.readString(svg, StandardCharsets.UTF_8);

        Assertions.assertTrue(Files.size(svg) > 0, "SVG 文件不应为空");
        Assertions.assertFalse(content.contains("@font-face"), "没有可用的 TTF/OTF/WOFF 时不应生成 @font-face");
        Assertions.assertTrue(result.embeddedFonts().isEmpty());
        Assertions.assertTrue(result.fallbackFonts().contains("方正书宋_GBK"), "中文字体应回退宋体");
        Assertions.assertTrue(result.fallbackFonts().contains("DY1+ZGXSfW-1"), "Type1 子集应回退宋体");

        Assertions.assertTrue(content.contains("SimSun"), "样式里应包含宋体回退");
        Assertions.assertTrue(content.contains("'方正书宋_GBK', SimSun, serif"), "中文应使用可读名 + 宋体回退");
        Assertions.assertTrue(content.contains("'DY1+ZGXSfW-1', SimSun, serif"), "Type1 子集应回退宋体");
        Assertions.assertTrue(count(content, "<text ") > 1000, "应按字符输出 <text>，实际: " + count(content, "<text "));
        Assertions.assertTrue(content.contains("class=\"f0\""), "应按字体分类复用 CSS class");

        // 校验为合法 XML
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    @Test
    public void testExportType1AsOutlines() throws Exception {
        String pdf = pdfPath();
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
    public void testEmbedWebFontWhenProvided() throws Exception {
        Path systemTtf = firstExisting(
                Path.of("C:/Windows/Fonts/arial.ttf"),
                Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
                Path.of("/System/Library/Fonts/Supplemental/Arial.ttf"));
        Assumptions.assumeTrue(systemTtf != null, "本机没有可用于内嵌测试的 TTF 字体，跳过");

        Path fontDir = Files.createTempDirectory("mupdf-svg-fonts-");
        Files.copy(systemTtf, fontDir.resolve("方正书宋_GBK.ttf"));
        Path svg = Path.of("target", "svg", "jjsjwlyjzs_zfy-page3-embedded.svg");

        MuPdfSvgExporter.ExportResult result = MuPdfSvgExporter.export(pdfPath(), 2, fontDir, svg);
        String content = Files.readString(svg, StandardCharsets.UTF_8);

        Assertions.assertTrue(result.embeddedFonts().contains("方正书宋_GBK"), "应内嵌提供的 TTF 字体");
        Assertions.assertTrue(result.fallbackFonts().contains("DY1+ZGXSfW-1"), "未提供文件的字体仍应回退");
        Assertions.assertTrue(content.contains("@font-face"), "应生成 @font-face");
        Assertions.assertTrue(content.contains("data:font/ttf;base64,"), "应以 base64 内嵌 TTF");
        Assertions.assertTrue(content.contains("format('truetype')"));
        Assertions.assertTrue(content.contains("'方正书宋_GBK'"), "内嵌字体的 family 名应为可读名");

        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.toFile());
    }

    private static String pdfPath() {
        URL url = MuPdfSvgExporterTest.class.getResource("/" + PDF);
        Assertions.assertNotNull(url, "未找到测试资源: " + PDF);
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
