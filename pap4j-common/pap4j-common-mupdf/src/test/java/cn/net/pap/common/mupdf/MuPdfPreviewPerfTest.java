package cn.net.pap.common.mupdf;

import cn.net.pap.common.mupdf.service.MuPdfService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * 基于 MuPDF 与 LibVips 原生直连（JNI / JNA）的单页渲染与文本物理坐标提取技术验证.
 *
 * <p>核心架构策略：
 * <ul>
 *   <li><b>文本物理坐标提取：</b>MuPDF 以 PDF Points (72 DPI) 为物理几何基准提取单页字符边界坐标 JSON。
 *       前端通过百分比 (x / pageWidth * 100%) 直接定位，彻底解耦 DPI 与分辨率差异；</li>
 *   <li><b>高性能 WebP / PNG 渲染：</b>
 *       在进程内通过 JNI 直接调用 MuPDF 渲染点阵，并通过 JNA 注入 LibVips 完成纯内存 WebP 转码，
 *       彻底消灭外部进程创建、命令行管道及 PAM 磁盘临时文件中继开销。</li>
 * </ul>
 * </p>
 *
 * @author alexgaoyh
 */
public class MuPdfPreviewPerfTest {

    private static final Logger log = LoggerFactory.getLogger(MuPdfPreviewPerfTest.class);

    private static final String PDF = resolvePath("test.pdf.path", "knowledge/0823.pdf", "sample.pdf");
    private static final int DEFAULT_DPI = Integer.getInteger("pdf.dpi", 150);
    private static final int DEFAULT_QUALITY = Integer.getInteger("pdf.quality", 75);

    private static MuPdfService service;
    private static String resolvedPdfPath;

    @BeforeAll
    public static void setUp() {
        service = new MuPdfService();
        if (PDF != null && new File(PDF).isFile()) {
            resolvedPdfPath = new File(PDF).getAbsolutePath();
        } else {
            URL url = MuPdfPreviewPerfTest.class.getResource("/sample.pdf");
            if (url != null) {
                resolvedPdfPath = new File(url.getFile()).getAbsolutePath();
            }
        }
    }

    @AfterAll
    public static void tearDown() {
        if (service != null) {
            service.clearCache();
        }
    }

    private static String resolvePath(String propKey, String... candidates) {
        String prop = System.getProperty(propKey);
        if (prop != null && new File(prop).isFile()) {
            return prop;
        }
        for (String candidate : candidates) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return candidates.length > 0 ? candidates[0] : null;
    }

    /**
     * 提取 PDF 指定单页的文本物理坐标与文字元数据 (JSON 格式).
     *
     * <p><b>关键几何设计：</b>MuPDF 提取出的坐标以 PDF Points (72 DPI) 为基准，
     * 前端通过 {@code (x / pageWidth * 100%)} 百分比直接定位，彻底解耦渲染 DPI 与屏幕分辨率差异。</p>
     *
     * @param pdfPath PDF 文件物理路径
     * @param page    目标页码 (从 1 开始)
     * @return 文本物理坐标 JSON 字符串
     */
    public String generateTextCoordinates(String pdfPath, int page) {
        return service.extractCoordinatesJson(pdfPath, Math.max(0, page - 1));
    }

    /**
     * 渲染 PDF 指定单页并直接编码为 WebP 底图字节数组（纯内存流：MuPDF PAM ➔ LibVips WebP）.
     *
     * @param pdfPath PDF 文件路径
     * @param page    目标页码 (从 1 开始)
     * @param dpi     渲染 DPI
     * @param quality WebP 图像压缩质量 (0-100，推荐 75)
     * @return WebP 图像字节数组
     * @throws IOException 如果转码失败
     */
    public byte[] generateWebpImage(String pdfPath, int page, int dpi, int quality) throws IOException {
        return service.renderPageToWebp(pdfPath, Math.max(0, page - 1), dpi, quality);
    }

    /**
     * 技术验证单测：验证单页坐标抽取与 WebP 图像渲染.
     *
     * @throws Exception 测试异常
     */
    @Test
    public void testPageExtractAndRender() throws Exception {
        if (resolvedPdfPath == null || !new File(resolvedPdfPath).isFile()) {
            log.warn("环境文件缺失，跳过技术验证 (PDF={})", resolvedPdfPath);
            return;
        }

        int totalPages = service.getPageCount(resolvedPdfPath);
        int page = totalPages >= 2 ? 2 : 1;
        int dpi = DEFAULT_DPI;
        int quality = DEFAULT_QUALITY;

        // 1. 抽取单页文本物理坐标 JSON
        long startText = System.currentTimeMillis();
        String textJson = generateTextCoordinates(resolvedPdfPath, page);
        long textDuration = System.currentTimeMillis() - startText;

        Assertions.assertNotNull(textJson, "文本坐标 JSON 不应为空");
        Assertions.assertFalse(textJson.trim().isEmpty(), "文本坐标 JSON 内容不应为空");
        Assertions.assertTrue(textJson.contains("\"file\""), "文本坐标 JSON 应包含 file 属性");
        Assertions.assertTrue(textJson.contains("\"pages\""), "文本坐标 JSON 应包含 pages 列表");
        Assertions.assertTrue(textJson.contains("\"blocks\""), "文本坐标 JSON 应包含 blocks");
        Assertions.assertTrue(textJson.contains("\"lines\""), "文本坐标 JSON 应包含 lines");
        Assertions.assertTrue(textJson.contains("\"bbox\""), "文本坐标 JSON 应包含 bbox 物理边界框");
        Assertions.assertTrue(textJson.contains("\"font\""), "文本坐标 JSON 应包含 font 字体元数据");
        Assertions.assertTrue(textJson.contains("\"text\""), "文本坐标 JSON 应包含 text 文本内容");
        log.info(">> [JNI] 成功提取第 {} 页文本坐标 JSON: 耗时 {} ms, 字符数 {}", page, textDuration, textJson.length());
        log.info(">> [JNI] 文本坐标 JSON 前 200 字符: {}", textJson.substring(0, Math.min(200, textJson.length())));

        // 2. 渲染生成单页 WebP 图像（MuPDF PAM ➔ LibVips 内存直连）
        long startWebp = System.currentTimeMillis();
        byte[] webpBytes = generateWebpImage(resolvedPdfPath, page, dpi, quality);
        long webpDuration = System.currentTimeMillis() - startWebp;

        Assertions.assertNotNull(webpBytes, "WebP 字节数组不应为空");
        Assertions.assertTrue(webpBytes.length > 0, "WebP 字节数组长度应大于 0");
        Assertions.assertEquals('R', (char) webpBytes[0]);
        Assertions.assertEquals('I', (char) webpBytes[1]);
        Assertions.assertEquals('F', (char) webpBytes[2]);
        Assertions.assertEquals('F', (char) webpBytes[3]);
        log.info(">> [JNI+Vips] 成功生成第 {} 页 WebP 底图: 耗时 {} ms, 大小 {} 字节 (约 {} KB)",
                page, webpDuration, webpBytes.length, String.format("%.2f", webpBytes.length / 1024.0));
    }

    /**
     * 提取测试 resources 下旋转 PDF 的文本物理坐标 JSON，并输出至日志供核对.
     */
    @Test
    public void testExtractRotatedPdfCoordinates() throws Exception {
        String[] testFiles = {
                "font_rotation_test.pdf",
                "multi_angle_rotation_test.pdf",
                "page_rotation_90_test.pdf",
                "page_rotation_180_test.pdf",
                "page_rotation_270_test.pdf",
                "page_and_char_combined_rotation_test.pdf"
        };

        File outputDir = new File(System.getProperty("java.io.tmpdir"));
        log.info(">> 可视化输出根目录 (tmpdir): {}", outputDir.getAbsolutePath());

        for (String file : testFiles) {
            URL url = getClass().getResource("/" + file);
            if (url == null) {
                log.warn("未找到测试资源文件: {}", file);
                continue;
            }

            String path = new File(url.getFile()).getAbsolutePath();
            long start = System.currentTimeMillis();
            String json = service.extractCoordinatesJson(path, 0);
            long cost = System.currentTimeMillis() - start;

            Assertions.assertNotNull(json, file + " 提取的坐标 JSON 不应为空");
            Assertions.assertFalse(json.trim().isEmpty(), file + " 提取的坐标 JSON 内容不应为空");

            // 纯根据提取出的坐标 JSON 反推还原生成 SVG 可视化文件，输出至 tmpdir
            String baseName = file.substring(0, file.lastIndexOf('.'));
            File svgFile = new File(outputDir, baseName + ".svg");
            MuPdfService.exportCoordinatesToSvg(json, svgFile);

            log.info(">> [{}] 耗时 {} ms | JSON 反推还原 SVG: {}", file, cost, svgFile.getAbsolutePath());
            log.info(">> [{}] 坐标 JSON: {}", file, json);
        }
    }
}
