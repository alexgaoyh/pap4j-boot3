package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 极简技术储备：基于 MuPDF 与 LibVips 的单页切片与文本坐标抽取.
 */
public class MuPdfPageExtractTest {

    private static final Logger log = LoggerFactory.getLogger(MuPdfPageExtractTest.class);

    // 静态路径配置（支持 -D 参数覆盖，外部未指定则采用本地默认路径）
    private static final String MUTOOL = System.getProperty("mutool.path", "mupdf-1.28.0-windows/mutool.exe");
    private static final String VIPS = System.getProperty("vips.path", "vips-dev-8.18/bin/vips.exe");
    private static final String PDF = System.getProperty("test.pdf.path", "knowledge/0823.pdf");

    /**
     * 技术验证：输入 PDF 和页码，输出 WebP 底图字节与文本坐标 JSON.
     */
    @Test
    public void testSimpleExtract() throws Exception {
        if (!new File(MUTOOL).isFile() || !new File(VIPS).isFile() || !new File(PDF).isFile()) {
            log.warn("环境文件缺失，跳过技术验证");
            return;
        }

        int page = 2;   // 目标页码
        int dpi = 150;  // 渲染分辨率

        // 1. 抽取单页文本物理坐标 JSON (耗时 ~50ms，体积 ~3KB)
        // 注意：输出的坐标单位是 PDF Points (72 DPI 基准)，前端使用百分比 (x / pageWidth * 100%) 对齐，即可无视 DPI 差异
        Process textProc = new ProcessBuilder(MUTOOL, "draw", "-q", "-F", "stext.json", "-o", "-", PDF, String.valueOf(page)).start();
        String textJson;
        try (InputStream in = textProc.getInputStream()) {
            textJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        textProc.waitFor();

        // 2. 渲染单页并转为 WebP 底图 (耗时 ~100ms，体积 ~8KB)
        Path tmpPng = Files.createTempFile("page_", ".png");
        Path tmpWebp = Files.createTempFile("page_", ".webp");
        byte[] webpBytes;
        try {
            // 2.1 MuPDF 单页光栅化为 PNG
            new ProcessBuilder(MUTOOL, "draw", "-q", "-F", "png", "-r", String.valueOf(dpi), "-o", tmpPng.toString(), PDF, String.valueOf(page)).start().waitFor();

            // 2.2 LibVips 压缩为 WebP
            new ProcessBuilder(VIPS, "webpsave", tmpPng.toString(), tmpWebp.toString(), "--Q", "75").start().waitFor();

            webpBytes = Files.readAllBytes(tmpWebp);
        } finally {
            Files.deleteIfExists(tmpPng);
            Files.deleteIfExists(tmpWebp);
        }

        // 打印验证结果
        log.info(">> 成功提取第 {} 页 WebP: {} 字节 (约 {} KB)", page, webpBytes.length, String.format("%.2f", webpBytes.length / 1024.0));
        log.info(">> 成功提取第 {} 页文本 JSON 长度: {} 字符", page, textJson.length());
    }
}
