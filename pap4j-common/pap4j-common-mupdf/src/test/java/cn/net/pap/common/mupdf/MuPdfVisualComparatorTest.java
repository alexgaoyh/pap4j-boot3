package cn.net.pap.common.mupdf;

import cn.net.pap.common.mupdf.service.MuPdfFontExtractor;
import cn.net.pap.common.mupdf.service.MuPdfSvgExporter;
import cn.net.pap.common.mupdf.service.MuPdfVisualComparator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link MuPdfVisualComparator} 视觉对比与保真度量化测试：
 * <ul>
 *   <li>验证 PDF 原生位图 vs 通过 JSON 坐标模型还原的 SVG；</li>
 *   <li>验证 MuPDF 原生直接导出的 SVG（不依赖 JSON）vs 通过 JSON 坐标模型还原的 SVG。</li>
 * </ul>
 */
public class MuPdfVisualComparatorTest {

    private static final String CJK_PDF = "ctex.pdf";
    private static final String MATH_PDF = "testmath.pdf";

    private String pdfPath(String fileName) {
        URL url = MuPdfVisualComparatorTest.class.getResource("/" + fileName);
        Assertions.assertNotNull(url, "测试资源文件不存在: " + fileName);
        return new File(url.getFile()).getAbsolutePath();
    }

    @Test
    public void testIdenticalImageComparison() {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.BLACK);
        g.drawString("Test", 20, 50);
        g.dispose();

        MuPdfVisualComparator.CompareResult result = MuPdfVisualComparator.compare(img, img, 15);
        Assertions.assertEquals(1.0, result.ssim(), 0.0001, "相同图片 SSIM 应为 1.0");
        Assertions.assertEquals(0.0, result.diffPercent(), 0.0001, "相同图片差异百分比应为 0%");
        Assertions.assertEquals(0, result.diffPixels(), "相同图片差异像素数应为 0");
        Assertions.assertEquals(100.0, result.psnr(), 0.0001, "相同图片 PSNR 应为 100 dB");
    }

    @Test
    public void testComparePdfGroundTruthVsHighFidelitySvg() throws Exception {
        String pdf = pdfPath(CJK_PDF);
        int pageNum = 3; // 第 4 页包含复杂表格

        Path svgPath = Path.of("target", "svg", "ctex-p4-fidelity.svg");
        MuPdfSvgExporter.export(pdf, pageNum, null, svgPath);

        // 与 PDF 原始 Ground Truth 图像对比
        MuPdfVisualComparator.CompareResult result = MuPdfVisualComparator.comparePdfAndSvg(
                pdf, pageNum, svgPath, 96);

        Path outDir = Path.of("target", "svg");
        result.saveAll(outDir, "ctex-p4-ground-truth", "[1] PDF 原生渲染 (Ground Truth)", "[2] 通过 JSON 还原高保真 SVG");

        System.out.println("====== [CTEX P4: PDF 原生 vs 高保真 SVG 对比结果] ======");
        System.out.println("  * 结构相似度 (SSIM): " + String.format("%.2f%%", result.ssim() * 100.0));
        System.out.println("  * 差异像素比例: " + String.format("%.2f%%", result.diffPercent()));
        System.out.println("  * 峰值信噪比 (PSNR): " + String.format("%.2f dB", result.psnr()));
        System.out.println("  * [★] 三联并排直观对比图: " + outDir.resolve("ctex-p4-ground-truth-side-by-side.png").toAbsolutePath());
        System.out.println("=========================================================");

        Assertions.assertTrue(result.ssim() > 0.80, "表格页高保真 SVG 与 PDF 原生图 SSIM 结构相似度应高于 80%");
        Assertions.assertTrue(Files.exists(outDir.resolve("ctex-p4-ground-truth-side-by-side.png")));
    }

    @Test
    public void testCompareJsonSvgVsNativeDirectSvg() throws Exception {
        String pdf = pdfPath(CJK_PDF);
        int pageNum = 3; // 第 4 页包含表格

        Path outDir = Path.of("target", "svg");

        // 1. 直接得到原生 SVG（调用 MuPDF 底层 DocumentWriter，不依赖 JSON）
        Path nativeSvg = outDir.resolve("ctex-p4-direct-native.svg");
        MuPdfVisualComparator.exportNativeSvg(pdf, pageNum, nativeSvg);

        // 2. 通过 JSON 坐标提取模型还原生成的 SVG
        Path jsonSvg = outDir.resolve("ctex-p4-json-fidelity.svg");
        MuPdfSvgExporter.export(pdf, pageNum, null, jsonSvg);

        // 3. 对比：直接生成的原生 SVG vs 通过 JSON 坐标模型还原的 SVG
        MuPdfVisualComparator.CompareResult result = MuPdfVisualComparator.compareSvgs(nativeSvg, jsonSvg, 96);
        result.saveAll(outDir, "ctex-direct-vs-json-svg",
                "[1] MuPDF 原生直接生成 (不依赖 JSON)",
                "[2] 通过 JSON 坐标模型还原 SVG");

        System.out.println("====== [CTEX P4: 直接原生 SVG vs JSON 还原 SVG 对比结果] ======");
        System.out.println("  * 结构相似度 (SSIM): " + String.format("%.2f%%", result.ssim() * 100.0));
        System.out.println("  * 差异像素比例: " + String.format("%.2f%%", result.diffPercent()));
        System.out.println("  * 峰值信噪比 (PSNR): " + String.format("%.2f dB", result.psnr()));
        System.out.println("  * [1] 直接原生 SVG: " + outDir.resolve("ctex-direct-vs-json-svg-1-baseline.png").toAbsolutePath());
        System.out.println("  * [2] JSON 还原 SVG: " + outDir.resolve("ctex-direct-vs-json-svg-2-tested-svg.png").toAbsolutePath());
        System.out.println("  * [3] 差异透视热力图: " + outDir.resolve("ctex-direct-vs-json-svg-3-diff-map.png").toAbsolutePath());
        System.out.println("  * [★] 直接生成 vs JSON 还原 并排大图: " + outDir.resolve("ctex-direct-vs-json-svg-side-by-side.png").toAbsolutePath());
        System.out.println("==================================================================");

        Assertions.assertTrue(result.ssim() > 0.80, "通过 JSON 还原的 SVG 与直接原生导出的 SVG SSIM 相似度应高于 80%");
        Assertions.assertTrue(Files.exists(outDir.resolve("ctex-direct-vs-json-svg-side-by-side.png")));
    }

    @Test
    public void testCompareMathFormulasPdfVsSvg() throws Exception {
        String pdf = pdfPath(MATH_PDF);
        int pageNum = 3; // 第 4 页包含数学公式

        Path fontDir = Path.of("target", "svg", "fonts-math-comp-" + System.currentTimeMillis());
        MuPdfFontExtractor.exportFonts(pdf, fontDir);

        Path svgPath = Path.of("target", "svg", "math-p4-fidelity.svg");
        MuPdfSvgExporter.export(pdf, pageNum, fontDir, svgPath);

        // 与 PDF 原始 Ground Truth 图像对比
        MuPdfVisualComparator.CompareResult result = MuPdfVisualComparator.comparePdfAndSvg(
                pdf, pageNum, svgPath, 96);

        Path outDir = Path.of("target", "svg");
        result.saveAll(outDir, "math-p4", "[1] PDF 原生真值图", "[2] 通过 JSON 还原公式高保真 SVG");

        System.out.println("====== [MATH P4: PDF 原生 vs 公式高保真 SVG 对比结果] ======");
        System.out.println("  * 结构相似度 (SSIM): " + String.format("%.2f%%", result.ssim() * 100.0));
        System.out.println("  * 差异像素比例: " + String.format("%.2f%%", result.diffPercent()));
        System.out.println("  * 峰值信噪比 (PSNR): " + String.format("%.2f dB", result.psnr()));
        System.out.println("  * [★] 三联并排直观对比图: " + outDir.resolve("math-p4-side-by-side.png").toAbsolutePath());
        System.out.println("============================================================");

        Assertions.assertTrue(result.ssim() > 0.75, "数学公式页高保真 SVG 与 PDF 原生图 SSIM 结构相似度应高于 75%");
        Assertions.assertTrue(Files.exists(outDir.resolve("math-p4-side-by-side.png")));
    }

    @Test
    public void testCompareMathFormulasDirectVsJsonSvg() throws Exception {
        String pdf = pdfPath(MATH_PDF);
        int pageNum = 3;

        Path outDir = Path.of("target", "svg");
        Path fontDir = Path.of("target", "svg", "fonts-math-comp2-" + System.currentTimeMillis());
        MuPdfFontExtractor.exportFonts(pdf, fontDir);

        // 1. 直接原生 SVG
        Path nativeSvg = outDir.resolve("math-p4-direct-native.svg");
        MuPdfVisualComparator.exportNativeSvg(pdf, pageNum, nativeSvg);

        // 2. 通过 JSON 还原高保真 SVG
        Path jsonSvg = outDir.resolve("math-p4-json-fidelity.svg");
        MuPdfSvgExporter.export(pdf, pageNum, fontDir, jsonSvg);

        // 3. 对比原生直接生成 vs JSON 坐标模型还原
        MuPdfVisualComparator.CompareResult result = MuPdfVisualComparator.compareSvgs(nativeSvg, jsonSvg, 96);
        result.saveAll(outDir, "math-direct-vs-json-svg",
                "[1] MuPDF 原生直接生成 (不依赖 JSON)",
                "[2] 通过 JSON 坐标模型还原 SVG");

        System.out.println("====== [MATH P4: 直接原生 SVG vs JSON 还原 SVG 对比结果] ======");
        System.out.println("  * 结构相似度 (SSIM): " + String.format("%.2f%%", result.ssim() * 100.0));
        System.out.println("  * 差异像素比例: " + String.format("%.2f%%", result.diffPercent()));
        System.out.println("  * 峰值信噪比 (PSNR): " + String.format("%.2f dB", result.psnr()));
        System.out.println("  * [★] 公式页 直接生成 vs JSON 还原 并排大图: " + outDir.resolve("math-direct-vs-json-svg-side-by-side.png").toAbsolutePath());
        System.out.println("==================================================================");

        Assertions.assertTrue(result.ssim() > 0.85, "数学公式页 JSON 还原 SVG 与直接原生 SVG SSIM 相似度应高于 85%");
        Assertions.assertTrue(Files.exists(outDir.resolve("math-direct-vs-json-svg-side-by-side.png")));
    }
}
