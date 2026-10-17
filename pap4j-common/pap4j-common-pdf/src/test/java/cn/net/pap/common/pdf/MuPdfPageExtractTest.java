package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 基于 MuPDF 与 LibVips 的单页 WebP 渲染与文本物理坐标提取技术验证.
 *
 * <p>核心架构策略：
 * <ul>
 *   <li><b>文本物理坐标提取：</b>MuPDF 以 PDF Points (72 DPI) 为物理几何基准提取单页字符边界坐标 JSON。
 *       前端通过百分比 (x / pageWidth * 100%) 直接定位，彻底解耦 DPI 与分辨率差异；</li>
 *   <li><b>高性能 WebP 渲染与跨平台适配：</b>
 *       在 Linux 环境下支持单命令行管道直通 (mutool PAM ➔ vips webpsave stdin)；
 *       在 Windows 环境下由于 Win32 API 限制（{@code CreateFileMapping} 无法映射匿名管道），
 *       自适应切换为 PAM 原始未压缩点阵中继，规避 PNG 二次压缩并保持极致性能。</li>
 * </ul>
 * </p>
 *
 * @author alexgaoyh
 */
public class MuPdfPageExtractTest {

    private static final Logger log = LoggerFactory.getLogger(MuPdfPageExtractTest.class);

    // 静态路径配置（支持 -D 参数覆盖，外部未指定则采用本地默认路径）
    private static final String MUTOOL = System.getProperty("mutool.path", "mupdf-1.28.0-windows/mutool.exe");
    private static final String VIPS = System.getProperty("vips.path", "vips-dev-8.18/bin/vips.exe");
    private static final String PDF = System.getProperty("test.pdf.path", "knowledge/0823.pdf");

    /**
     * 路径解析帮助方法，优先使用系统属性，其次尝试候选路径.
     *
     * @param propKey    系统属性键名
     * @param candidates 候选文件路径
     * @return 匹配存在的文件路径或首选路径
     */
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
        return candidates[0];
    }

    /**
     * 提取 PDF 指定单页的文本物理坐标与文字元数据 (JSON 格式).
     *
     * <p><b>关键几何设计：</b>MuPDF 提取出的坐标以 PDF Points (72 DPI) 为基准，
     * 前端通过 {@code (x / pageWidth * 100%)} 百分比直接定位，彻底解耦渲染 DPI 与屏幕分辨率差异。</p>
     *
     * @param pdfPath PDF 文件绝对或相对路径
     * @param page    目标页码 (从 1 开始)
     * @return 文本物理坐标 JSON 字符串
     * @throws IOException          IO 异常
     * @throws InterruptedException 线程中断异常
     */
    public String generateTextCoordinates(String pdfPath, int page) throws IOException, InterruptedException {
        Process textProc = new ProcessBuilder(
                MUTOOL, "draw", "-q", "-F", "stext.json", "-o", "-", pdfPath, String.valueOf(page)
        ).start();

        String textJson;
        try (InputStream in = textProc.getInputStream()) {
            textJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = textProc.waitFor();
        if (exitCode != 0) {
            throw new IOException("MuPDF 抽取文本坐标失败，退出码: " + exitCode);
        }
        return textJson;
    }

    /**
     * 渲染 PDF 指定单页并直接编码为 WebP 底图字节数组 (跨平台 Windows/Linux 自动适配).
     *
     * <p><b>跨平台架构策略：</b>
     * <ul>
     *   <li><b>Linux / POSIX 环境：</b>采用管道命令直连 ({@code mutool draw ... | vips webpsave stdin ...})，
     *       全链路纯内存流；</li>
     *   <li><b>Windows 环境：</b>受限于 Windows 内核 Win32 API 限制（{@code CreateFileMapping} 无法映射匿名管道句柄），
     *       自动平滑切换为未压缩 PAM (Portable Arbitrary Map) 点阵中继，享受 OS Page Cache 高速读写并规避 PNG 二次压缩。</li>
     * </ul>
     * </p>
     *
     * @param pdfPath PDF 文件路径
     * @param page    目标页码 (从 1 开始)
     * @param dpi     渲染 DPI 分辨率 (推荐 150)
     * @param quality WebP 图像压缩质量 (0-100，推荐 75)
     * @return WebP 图像字节数组
     * @throws IOException          IO 异常
     * @throws InterruptedException 线程中断异常
     */
    public byte[] generateWebpImage(String pdfPath, int page, int dpi, int quality)
            throws IOException, InterruptedException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        Path tmpWebp = Files.createTempFile("page_", ".webp");

        if (!isWindows) {
            // Linux / Unix 环境：单行命令管道直连 (mutool ➔ vips)
            try {
                String cmd = String.format("%s draw -q -F pam -r %d -o - %s %d | %s webpsave stdin %s --Q %d",
                        MUTOOL, dpi, pdfPath, page, VIPS, tmpWebp.toString(), quality);
                Process proc = new ProcessBuilder("sh", "-c", cmd).start();
                int exitCode = proc.waitFor();
                if (exitCode != 0) {
                    throw new IOException("Linux 管道渲染 WebP 失败，退出码: " + exitCode);
                }
                return Files.readAllBytes(tmpWebp);
            } finally {
                Files.deleteIfExists(tmpWebp);
            }
        } else {
            // Windows 环境：使用 PAM 原始点阵平滑中继 (规避 Windows CreateFileMapping 匿名管道限制)
            Path tmpPam = Files.createTempFile("page_", ".pam");
            try {
                Process pamProc = new ProcessBuilder(
                        MUTOOL, "draw", "-q", "-F", "pam", "-r", String.valueOf(dpi),
                        "-o", tmpPam.toString(), pdfPath, String.valueOf(page)
                ).start();
                int pamExit = pamProc.waitFor();
                if (pamExit != 0) {
                    throw new IOException("MuPDF 渲染 PAM 失败，退出码: " + pamExit);
                }

                Process vipsProc = new ProcessBuilder(
                        VIPS, "webpsave", tmpPam.toString(), tmpWebp.toString(),
                        "--Q", String.valueOf(quality)
                ).start();
                int vipsExit = vipsProc.waitFor();
                if (vipsExit != 0) {
                    throw new IOException("LibVips 转码 WebP 失败，退出码: " + vipsExit);
                }

                return Files.readAllBytes(tmpWebp);
            } finally {
                Files.deleteIfExists(tmpPam);
                Files.deleteIfExists(tmpWebp);
            }
        }
    }

    /**
     * 技术验证单测：验证单页坐标抽取与 WebP 图像渲染.
     *
     * @throws Exception 测试异常
     */
    @Test
    public void testPageExtractAndRender() throws Exception {
        if (!new File(MUTOOL).isFile() || !new File(VIPS).isFile() || !new File(PDF).isFile()) {
            log.warn("环境文件缺失，跳过技术验证 (MUTOOL={}, VIPS={}, PDF={})", MUTOOL, VIPS, PDF);
            return;
        }

        int page = 2;
        int dpi = 150;
        int quality = 75;

        // 1. 抽取单页文本物理坐标 JSON
        long startText = System.currentTimeMillis();
        String textJson = generateTextCoordinates(PDF, page);
        long textDuration = System.currentTimeMillis() - startText;

        Assertions.assertNotNull(textJson, "文本坐标 JSON 不应为空");
        Assertions.assertFalse(textJson.trim().isEmpty(), "文本坐标 JSON 内容不应为空");
        log.info(">> 成功提取第 {} 页文本坐标 JSON: 耗时 {} ms, 字符数 {}", page, textDuration, textJson.length());

        // 2. 渲染生成单页 WebP 图像
        long startImage = System.currentTimeMillis();
        byte[] webpBytes = generateWebpImage(PDF, page, dpi, quality);
        long imageDuration = System.currentTimeMillis() - startImage;

        Assertions.assertNotNull(webpBytes, "WebP 字节数组不应为空");
        Assertions.assertTrue(webpBytes.length > 0, "WebP 字节数组长度应大于 0");
        log.info(">> 成功生成第 {} 页 WebP 底图: 耗时 {} ms, 大小 {} 字节 (约 {} KB)",
                page, imageDuration, webpBytes.length, String.format("%.2f", webpBytes.length / 1024.0));
    }
}
