package cn.net.pap.common.mupdf;

import cn.net.pap.common.mupdf.service.MuPdfFontExtractor;
import cn.net.pap.common.mupdf.service.MuPdfFontExtractor.FontInfo;
import cn.net.pap.common.mupdf.service.MuPdfService;
import cn.net.pap.common.mupdf.service.MuPdfSvgExporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link MuPdfFontExtractor} 技术验证：字体名称、字体文件，以及与本机 mutool 的对照。
 *
 * <p>对照项：</p>
 * <ul>
 *   <li>字体名称集合 == {@code mutool show file.pdf grep} 中的 {@code /BaseFont} 集合；</li>
 *   <li>导出的字体文件 SHA-256 集合 == {@code mutool extract} 导出的 {@code font-*} 集合；</li>
 *   <li>常规 PDF：{@code mutool info -F} 能列出的字体，本工具同样能列出。</li>
 * </ul>
 *
 * <p>mutool 查找顺序：{@code -Dmutool.path=...} → 环境变量 {@code MUTOOL} → 常见安装路径 → PATH；
 * 找不到时自动跳过对照，不影响其它断言。</p>
 *
 * @author alexgaoyh
 */
public class MuPdfFontExtractorTest {

    /**
     * 中文公文样本：正文全部包在多层 Form XObject 内，字体名是 GBK 字节。
     */
    private static final String CNKI_PDF = "jjsjwlyjzs_zfy.pdf";

    /**
     * 中文与多字体排版样本（含数学公式、TTF、CFF、文楷、仿宋等）。
     */
    private static final String CTEX_PDF = "ctex.pdf";

    /**
     * 数学公式排版样本（含 29 个 Type1 嵌入式数学字体）。
     */
    private static final String MATH_PDF = "testmath.pdf";

    /**
     * 常规样本：字体在页面级 /Resources 内，用于和 mutool info -F 对照。
     */
    private static final String SAMPLE_PDF = "sample.pdf";

    private static final Pattern BASE_FONT = Pattern.compile("/BaseFont\\s*/([^\\s/>]+)");

    @Test
    public void testListFonts() {
        // 1. 验证数学公式样本（testmath.pdf: 29 个 Type1 嵌入字体）
        List<FontInfo> mathFonts = MuPdfFontExtractor.listFonts(pdfPath(MATH_PDF));
        Assertions.assertEquals(29, mathFonts.size(), "testmath.pdf 应列出 29 个 Type1 字体");
        Assertions.assertTrue(mathFonts.stream().allMatch(FontInfo::embedded), "testmath.pdf 字体应全部嵌入");
        Assertions.assertTrue(mathFonts.stream().allMatch(f -> "type1".equals(f.fileType())), "testmath.pdf 字体类型应均为 type1");

        // 2. 验证多字体与中文样本（ctex.pdf: 18 个字体，包含数学公式、TTF、CFF 字体）
        List<FontInfo> ctexFonts = MuPdfFontExtractor.listFonts(pdfPath(CTEX_PDF));
        Assertions.assertEquals(18, ctexFonts.size(), "ctex.pdf 应列出 18 个字体");
        Assertions.assertTrue(ctexFonts.stream().anyMatch(f -> f.name().contains("TeXGyrePagellaMath")),
                "ctex.pdf 应包含数学公式字体 TeXGyrePagellaMath");
        Assertions.assertTrue(ctexFonts.stream().anyMatch(f -> f.name().contains("WenKai")),
                "ctex.pdf 应包含中文文楷字体");

        // 3. 验证常规样本 sample.pdf
        List<FontInfo> sampleFonts = MuPdfFontExtractor.listFonts(pdfPath(SAMPLE_PDF));
        FontInfo fangSong = sampleFonts.stream()
                .filter(font -> "FangSong".equals(font.name()))
                .findFirst().orElse(null);
        Assertions.assertNotNull(fangSong, "sample.pdf 应列出 FangSong，实际: " + sampleFonts);
        Assertions.assertTrue(fangSong.embedded(), "sample.pdf 中的 FangSong 应是嵌入字体");

        // 4. 若本地存在中文公文样本 CNKI_PDF，执行专项断言
        if (hasResource(CNKI_PDF)) {
            List<FontInfo> cnkiFonts = MuPdfFontExtractor.listFonts(pdfPath(CNKI_PDF));
            Assertions.assertEquals(33, cnkiFonts.size());
            Set<String> names = cnkiFonts.stream().map(FontInfo::name).collect(Collectors.toCollection(LinkedHashSet::new));
            Assertions.assertTrue(names.containsAll(List.of("方正书宋_GBK", "方正黑体_GBK", "方正仿宋_GBK", "方正楷体_GBK")));
        }
    }

    @Test
    public void testExtractFonts() {
        // 1. 验证数学公式样本
        String mathPdf = pdfPath(MATH_PDF);
        Map<String, byte[]> mathPrograms = MuPdfFontExtractor.extractFonts(mathPdf);
        Assertions.assertEquals(29, mathPrograms.size(), "testmath.pdf 应读取 29 个嵌入字体程序");
        for (Map.Entry<String, byte[]> entry : mathPrograms.entrySet()) {
            Assertions.assertNotNull(entry.getValue(), "字体程序不应为空: " + entry.getKey());
            Assertions.assertTrue(entry.getValue().length > 0, "字体程序不应为空: " + entry.getKey());
        }
        Set<String> listedNames = MuPdfFontExtractor.listFonts(mathPdf).stream()
                .map(FontInfo::name)
                .collect(Collectors.toSet());
        Assertions.assertTrue(listedNames.containsAll(mathPrograms.keySet()), "提取结果必须是字体清单的子集");

        // 2. 验证多字体中文样本
        String ctexPdf = pdfPath(CTEX_PDF);
        Map<String, byte[]> ctexPrograms = MuPdfFontExtractor.extractFonts(ctexPdf);
        Assertions.assertEquals(18, ctexPrograms.size(), "ctex.pdf 应读取 18 个嵌入字体程序");

        // 3. 若本地存在 CNKI_PDF，验证未嵌入字体被过滤
        if (hasResource(CNKI_PDF)) {
            Map<String, byte[]> cnkiPrograms = MuPdfFontExtractor.extractFonts(pdfPath(CNKI_PDF));
            Assertions.assertEquals(29, cnkiPrograms.size());
            Assertions.assertFalse(cnkiPrograms.containsKey("方正书宋_GBK"));
        }
    }

    @Test
    public void testCoordinateJsonUsesReadableFontNames() throws Exception {
        String pdf = pdfPath(CTEX_PDF);
        Set<String> listedNames = MuPdfFontExtractor.listFonts(pdf).stream()
                .map(FontInfo::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        MuPdfService service = new MuPdfService();
        try {
            Set<String> jsonFonts = new LinkedHashSet<>();
            int pageCount = Math.min(service.getPageCount(pdf), 5);
            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                String json = service.extractCoordinatesJson(pdf, pageNum);
                collectFonts(new ObjectMapper().readTree(json), jsonFonts);
            }
            Assertions.assertFalse(jsonFonts.isEmpty(), "坐标 JSON 中的字体名集合不应为空");
            Assertions.assertTrue(listedNames.containsAll(jsonFonts),
                    "坐标 JSON 中的字体名应是 listFonts 的子集");
            Assertions.assertFalse(jsonFonts.stream().anyMatch(name -> name.startsWith("·")),
                    "不应再出现 MuPDF 乱码字体名，实际: " + jsonFonts);
        } finally {
            service.clearCache();
        }
    }

    @Test
    public void testSvgUsesReadableFontFamilies() throws Exception {
        String pdf = pdfPath(CTEX_PDF);
        Path svgFile = Path.of("target", "mupdf-svg-" + System.currentTimeMillis() + ".svg");

        MuPdfSvgExporter.export(pdf, 3, null, svgFile);

        String svg = Files.readString(svgFile, StandardCharsets.UTF_8);
        Assertions.assertTrue(svg.contains("font-family"), "SVG 中应包含 font-family 声明");
        Assertions.assertFalse(svg.contains("·½"), "SVG 中不应残留 MuPDF 乱码字体名");
    }

    @Test
    public void testExportFontsAndCrossCheckWithMutool() throws Exception {
        String mutool = findMutool();
        Assumptions.assumeTrue(mutool != null,
                "未找到 mutool（可通过 -Dmutool.path=/path/to/mutool 指定），跳过与 mutool 的对照");

        String pdf = pdfPath(MATH_PDF);

        // 1. 导出字体文件
        Path outputDir = Path.of("target", "mupdf-fonts-" + System.currentTimeMillis());
        Map<String, Path> files = MuPdfFontExtractor.exportFonts(pdf, outputDir);
        Assertions.assertEquals(29, files.size(), "应导出 29 个字体文件");
        for (Map.Entry<String, Path> entry : files.entrySet()) {
            Assertions.assertTrue(Files.isRegularFile(entry.getValue()), "字体文件应存在: " + entry.getValue());
            Assertions.assertTrue(Files.size(entry.getValue()) > 0, "字体文件不应为空: " + entry.getValue());
        }

        // 2. 字体名称对照：mutool show file.pdf grep 输出全文档所有对象，包含 Form XObject 内的字体
        String dump = capture(null, mutool, "show", pdf, "grep");
        Set<String> mutoolBaseFonts = parseBaseFonts(dump);
        Set<String> ourBaseFonts = MuPdfFontExtractor.listFonts(pdf).stream()
                .map(FontInfo::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Assertions.assertEquals(mutoolBaseFonts, ourBaseFonts, "字体集合应与 mutool show grep 完全一致");

        // 3. 字体文件对照：SHA-256 集合必须完全一致
        Path extractDir = Files.createTempDirectory("mutool-extract-");
        runWithoutOutput(extractDir, mutool, "extract", pdf);
        Set<String> mutoolHashes = hashFiles(extractDir, "font-");
        Set<String> ourHashes = files.values().stream()
                .map(MuPdfFontExtractorTest::sha256)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Assertions.assertEquals(29, mutoolHashes.size(), "mutool extract 应导出 29 个字体程序");
        Assertions.assertEquals(mutoolHashes, ourHashes, "导出的字体文件字节应与 mutool extract 完全一致");

        // 4. 常规 PDF：mutool info -F 能列出的字体，本工具同样能列出
        String sampleInfo = capture(null, mutool, "info", "-F", pdfPath(SAMPLE_PDF));
        Assertions.assertTrue(sampleInfo.contains("\"FangSong\""), "mutool info -F 应列出 FangSong");
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static void collectFonts(JsonNode node, Set<String> names) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("font".equals(entry.getKey()) && entry.getValue().isTextual()) {
                    names.add(entry.getValue().asText());
                } else {
                    collectFonts(entry.getValue(), names);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectFonts(child, names));
        }
    }

    private static boolean hasResource(String resourceName) {
        return MuPdfFontExtractorTest.class.getResource("/" + resourceName) != null;
    }

    private static String pdfPath(String resourceName) {
        URL url = MuPdfFontExtractorTest.class.getResource("/" + resourceName);
        Assertions.assertNotNull(url, "未找到测试资源: " + resourceName);
        return new File(url.getFile()).getAbsolutePath();
    }

    private static String findMutool() {
        String[] candidates = {
                System.getProperty("mutool.path"),
                System.getenv("MUTOOL"),
                "D:/mupdf-1.28.0-windows/mutool.exe",
                "C:/Program Files/mupdf/mutool.exe",
                "/usr/bin/mutool",
                "/usr/local/bin/mutool",
                "/opt/homebrew/bin/mutool"
        };
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            File file = new File(candidate);
            if (file.isFile() && file.canExecute()) {
                return candidate;
            }
        }
        try {
            Process process = new ProcessBuilder("mutool", "-v").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                return "mutool";
            }
        } catch (Exception ignored) {
            // PATH 中没有 mutool
        }
        return null;
    }

    private static String capture(Path workDir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        Process process = builder.start();
        byte[] output;
        try (InputStream in = process.getInputStream()) {
            output = in.readAllBytes();
        }
        Assertions.assertTrue(process.waitFor(180, TimeUnit.SECONDS), "mutool 执行超时: " + String.join(" ", command));
        return new String(output, StandardCharsets.ISO_8859_1);
    }

    private static void runWithoutOutput(Path workDir, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        Process process = builder.start();
        Assertions.assertTrue(process.waitFor(180, TimeUnit.SECONDS), "mutool 执行超时: " + String.join(" ", command));
        Assertions.assertEquals(0, process.exitValue(), "mutool 退出码应为 0: " + String.join(" ", command));
    }

    /**
     * 解析 {@code mutool show file.pdf grep} 输出中的所有 {@code /BaseFont}，并解码为可读名称。
     */
    private static Set<String> parseBaseFonts(String dump) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = BASE_FONT.matcher(dump);
        while (matcher.find()) {
            names.add(decodePdfName(matcher.group(1)));
        }
        return names;
    }

    /**
     * 独立实现的 PDF 名称解码（{@code #XX} → 字节 → GB18030），用于和工具类的结果交叉验证。
     */
    private static String decodePdfName(String escaped) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '#' && i + 2 < escaped.length()) {
                int high = Character.digit(escaped.charAt(i + 1), 16);
                int low = Character.digit(escaped.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    out.write((high << 4) | low);
                    i += 2;
                    continue;
                }
            }
            out.write(c);
        }
        return new String(out.toByteArray(), Charset.forName("GB18030"));
    }

    private static Set<String> hashFiles(Path dir, String prefix) throws Exception {
        try (var paths = Files.list(dir)) {
            Set<String> hashes = new HashSet<>();
            for (Path file : paths.filter(path -> path.getFileName().toString().startsWith(prefix)).toList()) {
                hashes.add(sha256(file));
            }
            return hashes;
        }
    }

    private static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("计算 SHA-256 失败: " + file, e);
        }
    }
}
