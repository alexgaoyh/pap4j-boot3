package cn.net.pap.common.mupdf.service;

import cn.net.pap.common.mupdf.loader.MuPdfNativeLoader;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Font;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.PDFObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 MuPDF JNI 的 PDF 字体清单与字体文件提取工具。
 *
 * <p>目标只有两件事：</p>
 * <ol>
 *   <li>列出 PDF 用到的字体名称（含是否嵌入、字体程序类型）；</li>
 *   <li>把嵌入字体程序读出来（内存字节或写文件），供后续处理使用。</li>
 * </ol>
 *
 * <p>为什么需要这段代码（而不是直接调 mutool）：</p>
 * <ul>
 *   <li>JNI 绑定没有字体枚举 API：{@code Page/PDFPage} 没有 {@code getFonts()}，
 *       {@code Font#getName()} 对 GBK 字节会丢字节（如 {@code 方正书宋_GBK} 只剩 {@code ·½ÕýÊéËÎ_G}）；</li>
 *   <li>{@code mutool info -F} 只扫页面级 {@code /Resources/Font}，字体包在 Form XObject 里时列不出；</li>
 *   <li>{@code mutool extract} 只按对象号导出无名字的 {@code font-XXXX.pfa}，且不报告未嵌入字体。</li>
 * </ul>
 *
 * <p>所以这里用 JNI 的 {@link PDFObject} 递归资源树（含 Form XObject），从 {@code /BaseFont}
 * 还原原始字节并按 UTF-8 / GB18030 解码出可读名称，再从 {@code /FontDescriptor} 读取
 * {@code FontFile / FontFile2 / FontFile3} 字体程序。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * // 1. 只要名称
 * List<MuPdfFontExtractor.FontInfo> fonts = MuPdfFontExtractor.listFonts("a.pdf");
 *
 * // 2. 要字体文件（内存）
 * Map<String, byte[]> programs = MuPdfFontExtractor.extractFonts("a.pdf");
 *
 * // 3. 要字体文件（落盘）
 * Map<String, Path> files = MuPdfFontExtractor.exportFonts("a.pdf", Path.of("tmp", "fonts"));
 * }</pre>
 *
 * <p>未嵌入字体（如本测试 PDF 中的 4 个方正字体）会出现在 {@link #listFonts(String)} 结果里并标记
 * {@code embedded=false}，但不会出现在 {@link #extractFonts(String)} / {@link #exportFonts(String, Path)}
 * 的返回值中。</p>
 *
 * @author alexgaoyh
 */
public final class MuPdfFontExtractor {

    private static final Logger log = LoggerFactory.getLogger(MuPdfFontExtractor.class);

    /**
     * Form XObject 递归深度上限，防止畸形 PDF 的资源循环引用。
     */
    private static final int MAX_FORM_DEPTH = 8;

    /**
     * 单个嵌入字体程序的大小上限，避免畸形 PDF 把 JVM 堆抽爆。
     */
    private static final long MAX_FONT_BYTES = 64L * 1024 * 1024;

    /**
     * 导出文件名允许的最大长度。
     */
    private static final int MAX_FILE_NAME_LENGTH = 80;

    private MuPdfFontExtractor() {
    }

    /**
     * 字体信息。
     *
     * @param name     可读字体名（{@code /BaseFont} 原始字节按 UTF-8 / GB18030 解码，如 {@code 方正书宋_GBK}）
     * @param embedded 是否带嵌入字体程序
     * @param fileType 嵌入字体程序类型（{@code ttf / otf / cff / type1}）；未嵌入或未读取时为 null
     */
    public record FontInfo(String name, boolean embedded, String fileType) {
    }

    /**
     * 列出整册 PDF 的字体（不读取字体程序字节）。
     *
     * @param pdfPath PDF 文件物理路径
     * @return 字体列表（按首次出现顺序，名称去重）
     * @throws IllegalArgumentException 文件不存在、不可读或不是 PDF
     */
    public static List<FontInfo> listFonts(String pdfPath) {
        List<FontInfo> fonts = new ArrayList<>();
        for (FontEntry entry : scan(pdfPath, false).values()) {
            fonts.add(new FontInfo(entry.name(), entry.embedded(), entry.fileType()));
        }
        return fonts;
    }

    /**
     * 读取整册 PDF 的嵌入字体程序（不落盘）。
     *
     * @param pdfPath PDF 文件物理路径
     * @return 可读字体名 ➔ 字体程序字节（仅包含嵌入字体）
     * @throws IllegalArgumentException 文件不存在、不可读或不是 PDF
     */
    public static Map<String, byte[]> extractFonts(String pdfPath) {
        Map<String, byte[]> programs = new LinkedHashMap<>();
        for (FontEntry entry : scan(pdfPath, true).values()) {
            if (entry.program() != null) {
                programs.put(entry.name(), entry.program());
            }
        }
        return programs;
    }

    /**
     * 导出整册 PDF 的嵌入字体程序到目录。
     *
     * <p>文件名规则：{@code <字体名>.<ttf|otf|cff|pfa>}，重名时追加 {@code -2}、{@code -3}。</p>
     *
     * @param pdfPath   PDF 文件物理路径
     * @param outputDir 输出目录（不存在时自动创建）
     * @return 可读字体名 ➔ 已写出的字体文件
     * @throws IllegalArgumentException 文件不存在、不可读、不是 PDF，或输出目录为空
     * @throws IOException              文件写入失败
     */
    public static Map<String, Path> exportFonts(String pdfPath, Path outputDir) throws IOException {
        if (outputDir == null) {
            throw new IllegalArgumentException("输出目录不能为空");
        }
        Files.createDirectories(outputDir);

        Map<String, Path> files = new LinkedHashMap<>();
        Set<String> usedNames = new HashSet<>();
        int count = 0;
        for (FontEntry entry : scan(pdfPath, true).values()) {
            if (entry.program() == null) {
                continue;
            }
            try {
                Path file = outputDir.resolve(uniqueFileName(entry.name(), entry.fileType(), usedNames));
                Files.write(file, entry.program());
                files.put(entry.name(), file);
                count++;
            } catch (Throwable t) {
                // 单个字体写出失败（磁盘满 / 文件名非法）不影响其余字体导出
                log.error("[MuPDF-Font] 导出字体 {} 失败（已跳过）", entry.name(), t);
            }
        }
        log.info("[MuPDF-Font] 导出完成: {} | 嵌入字体 {} 个 | 目录 {}", pdfPath, count, outputDir.toAbsolutePath());
        return files;
    }

    /**
     * 构建页面级字体名解析器：把 MuPDF 运行期字体名（{@code /BaseFont} 字节的 Latin-1 映射，可能被截断）
     * 映射为可读名称，供坐标提取等场景使用。
     *
     * @param pdf     PDF 文档
     * @param pageNum 页码（0-indexed）
     * @return 页面级字体名解析器
     */
    static FontNameResolver pageFontNameResolver(PDFDocument pdf, int pageNum) {
        List<FontName> names = new ArrayList<>();
        if (pdf == null) {
            return new FontNameResolver(names);
        }
        try {
            PDFObject page = pdf.findPage(pageNum);
            PDFObject resources = page == null ? null : page.getInheritable("Resources");
            walk(resources, 0, new HashSet<>(), fontObject -> {
                FontEntry entry = parse(fontObject, false);
                if (entry != null) {
                    names.add(new FontName(entry.rawName(),
                            new String(entry.rawName(), StandardCharsets.ISO_8859_1), entry.name()));
                }
            });
        } catch (Throwable t) {
            // 字体名解析属于"锦上添花"：失败时返回已收集部分，调用方回退为 MuPDF 原始字体名
            log.error("[MuPDF-Font] 解析第 {} 页字体名失败，回退为 MuPDF 原始名称", pageNum + 1, t);
        }
        return new FontNameResolver(names);
    }

    // ------------------------------------------------------------------
    // 扫描实现
    // ------------------------------------------------------------------

    /**
     * 扫描整册 PDF 的字体，按名称去重；同名时优先保留嵌入了字体程序的条目。
     */
    private static Map<String, FontEntry> scan(String pdfPath, boolean readPrograms) {
        MuPdfNativeLoader.ensureLoaded();
        File file = requireReadableFile(pdfPath);

        Document doc;
        try {
            doc = Document.openDocument(file.getAbsolutePath());
        } catch (Throwable t) {
            throw new IllegalStateException("打开 PDF 失败（文件损坏、被加密或格式不支持）: "
                                            + file.getAbsolutePath() + " -> " + t.getMessage(), t);
        }
        if (doc == null) {
            throw new IllegalStateException("打开 PDF 失败：MuPDF 返回空文档句柄: " + file.getAbsolutePath());
        }
        try {
            if (!(doc instanceof PDFDocument pdf)) {
                throw new IllegalArgumentException("仅支持 PDF 文档（当前格式: "
                                                   + doc.getMetaData(Document.META_FORMAT) + "）: " + pdfPath);
            }

            Map<String, FontEntry> fonts = new LinkedHashMap<>();
            int pageCount = pdf.countPages();
            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                // 单页资源异常（损坏页 / 循环引用 / 权限问题）不应当中断整册字体的扫描
                try {
                    PDFObject page = pdf.findPage(pageNum);
                    PDFObject resources = page == null ? null : page.getInheritable("Resources");
                    walk(resources, 0, new HashSet<>(), fontObject -> {
                        FontEntry entry = parse(fontObject, readPrograms);
                        if (entry == null) {
                            return;
                        }
                        FontEntry existing = fonts.get(entry.name());
                        if (existing == null || (!existing.embedded() && entry.embedded())) {
                            fonts.put(entry.name(), entry);
                        }
                    });
                } catch (Throwable t) {
                    log.error("[MuPDF-Font] 扫描第 {} 页字体失败（已跳过该页）: {}", pageNum + 1, pdfPath, t);
                }
            }
            return fonts;
        } finally {
            try {
                doc.destroy();
            } catch (Throwable t) {
                log.error("[MuPDF-Font] 释放文档句柄失败: {}", pdfPath, t);
            }
        }
    }

    /**
     * 遍历资源树（含嵌套 Form XObject）中的全部字体对象。
     */
    private static void walk(PDFObject resources, int depth, Set<Integer> visitedForms,
                             Consumer<PDFObject> visitor) {
        if (resources == null || resources.isNull() || depth > MAX_FORM_DEPTH) {
            return;
        }

        PDFObject fontDict = getObject(resources, "Font");
        if (fontDict != null && !fontDict.isNull()) {
            for (String resourceName : dictKeys(fontDict)) {
                PDFObject fontObject = getObject(fontDict, resourceName);
                if (fontObject != null && !fontObject.isNull()) {
                    try {
                        visitor.accept(fontObject);
                    } catch (Throwable t) {
                        log.error("[MuPDF-Font] 处理单个字体对象失败（已跳过）: {}", resourceName, t);
                    }
                }
            }
        }

        PDFObject xObjects = getObject(resources, "XObject");
        if (xObjects == null || xObjects.isNull()) {
            return;
        }
        for (String resourceName : dictKeys(xObjects)) {
            PDFObject xObject = getObject(xObjects, resourceName);
            if (xObject == null || xObject.isNull()) {
                continue;
            }
            if (!"/Form".equals(nameOf(getObject(xObject, "Subtype")))) {
                continue;
            }
            int xref = -1;
            try {
                xref = xObject.asIndirect();
            } catch (Throwable ignored) {
                // 直接对象没有 xref，不影响递归
            }
            if (xref > 0 && !visitedForms.add(xref)) {
                continue;
            }
            PDFObject nested = getObject(xObject, "Resources");
            if (nested != null && !nested.isNull()) {
                walk(nested, depth + 1, visitedForms, visitor);
            }
        }
    }

    /**
     * 安全读取字典项：无论对象是否间接、是否损坏，都不抛异常到调用方。
     */
    private static PDFObject getObject(PDFObject dict, String key) {
        if (dict == null || dict.isNull()) {
            return null;
        }
        try {
            return dict.get(key);
        } catch (Throwable t) {
            log.debug("[MuPDF-Font] 读取字典项 {} 失败: {}", key, t.getMessage());
            return null;
        }
    }

    /**
     * 解析单个字体字典：可读名称、嵌入状态、字体程序类型，按需读取字体程序字节。
     */
    /**
     * 解析单个字体字典：异常一律降级为 "跳过该字体"，绝不影响整册扫描。
     */
    private static FontEntry parse(PDFObject fontObject, boolean readPrograms) {
        if (fontObject == null || fontObject.isNull()) {
            return null;
        }
        try {
            return parseInternal(fontObject, readPrograms);
        } catch (Throwable t) {
            log.error("[MuPDF-Font] 解析字体字典失败（已跳过该字体）", t);
            return null;
        }
    }

    private static FontEntry parseInternal(PDFObject fontObject, boolean readPrograms) {
        PDFObject baseFontObject = getObject(fontObject, "BaseFont");
        PDFObject descriptor = getObject(fontObject, "FontDescriptor");

        // Type0 的 BaseFont / FontDescriptor 在 DescendantFonts[0] 上
        PDFObject descendants = getObject(fontObject, "DescendantFonts");
        if (descendants != null && !descendants.isNull()) {
            try {
                if (descendants.size() > 0) {
                    PDFObject descendant = descendants.get(0);
                    if (descendant != null && !descendant.isNull()) {
                        PDFObject descendantBaseFont = getObject(descendant, "BaseFont");
                        if (descendantBaseFont != null && !descendantBaseFont.isNull()) {
                            baseFontObject = descendantBaseFont;
                        }
                        PDFObject descendantDescriptor = getObject(descendant, "FontDescriptor");
                        if (descendantDescriptor != null && !descendantDescriptor.isNull()) {
                            descriptor = descendantDescriptor;
                        }
                    }
                }
            } catch (Throwable t) {
                // 畸形 DescendantFonts 按字体字典自身的信息处理
                log.debug("[MuPDF-Font] 读取 DescendantFonts 失败，按顶层字体字典处理: {}", t.getMessage());
            }
        }

        byte[] rawName = pdfNameBytes(baseFontObject);
        String name = decodeName(rawName);
        if (name.isEmpty()) {
            return null;
        }

        boolean embedded = false;
        String fileType = null;
        if (descriptor != null && !descriptor.isNull()) {
            if (hasStream(descriptor, "FontFile2")) {
                embedded = true;
                fileType = "ttf";
            } else if (hasStream(descriptor, "FontFile3")) {
                embedded = true;
                fileType = "/OpenType".equals(nameOf(getObject(getObject(descriptor, "FontFile3"), "Subtype"))) ? "otf" : "cff";
            } else if (hasStream(descriptor, "FontFile")) {
                embedded = true;
                fileType = "type1";
            }
        }

        byte[] program = readPrograms && embedded ? readProgram(descriptor, name) : null;
        return new FontEntry(name, rawName, embedded, fileType, program);
    }

    /**
     * 读取字体的嵌入程序字节（FontFile2 / FontFile3 / FontFile 三选一）。
     */
    private static byte[] readProgram(PDFObject descriptor, String fontName) {
        String[] keys = {"FontFile2", "FontFile3", "FontFile"};
        for (String key : keys) {
            try {
                PDFObject stream = getObject(descriptor, key);
                if (stream == null || stream.isNull()) {
                    continue;
                }
                long declaredLength = declaredLength(stream);
                if (declaredLength > MAX_FONT_BYTES) {
                    log.warn("[MuPDF-Font] 字体 {} 的 {} 声明长度 {} 超过上限 {}，跳过",
                            fontName, key, declaredLength, MAX_FONT_BYTES);
                    continue;
                }
                byte[] bytes = stream.readStream();
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                if (bytes.length > MAX_FONT_BYTES) {
                    log.warn("[MuPDF-Font] 字体 {} 的 {} 解压后 {} 字节超过上限 {}，跳过",
                            fontName, key, bytes.length, MAX_FONT_BYTES);
                    continue;
                }
                return bytes;
            } catch (Throwable t) {
                // 某个候选流读取失败时继续尝试下一个（FontFile2 → FontFile3 → FontFile）
                log.error("[MuPDF-Font] 读取字体 {} 的 {} 失败，尝试下一个候选流", fontName, key, t);
            }
        }
        return null;
    }

    private static long declaredLength(PDFObject stream) {
        try {
            PDFObject length = stream.get("Length");
            if (length == null || length.isNull()) {
                return -1;
            }
            PDFObject resolved = length.isIndirect() ? length.resolve() : length;
            return resolved != null && resolved.isNumber() ? resolved.asInteger() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // PDF 名称 / 编码处理
    // ------------------------------------------------------------------

    /**
     * 解析 PDF 名称对象，支持 {@code #XX} 十六进制转义。
     */
    private static byte[] pdfNameBytes(PDFObject nameObject) {
        if (nameObject == null || nameObject.isNull()) {
            return new byte[0];
        }
        String text;
        try {
            // ascii=true 时 MuPDF 会把非 ASCII 字节输出为 #XX，避免 Java 侧再次乱码
            text = nameObject.toString(true, true);
        } catch (Throwable ignored) {
            return new byte[0];
        }
        if (text == null || text.isEmpty() || text.charAt(0) != '/') {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length());
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '#' && i + 2 < text.length()) {
                int high = Character.digit(text.charAt(i + 1), 16);
                int low = Character.digit(text.charAt(i + 2), 16);
                if (high >= 0 && low >= 0) {
                    out.write((high << 4) | low);
                    i += 2;
                    continue;
                }
            }
            if (c <= 0xFF) {
                out.write(c);
            } else {
                out.write('?');
            }
        }
        return out.toByteArray();
    }

    /**
     * 字体名解码：纯 ASCII 直接返回；否则先试 UTF-8，再试 GB18030（兼容 GBK / GB2312）。
     */
    private static String decodeName(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        boolean ascii = true;
        for (byte b : raw) {
            if (b < 0) {
                ascii = false;
                break;
            }
        }
        if (ascii) {
            return new String(raw, StandardCharsets.ISO_8859_1);
        }

        String utf8 = tryDecode(raw, StandardCharsets.UTF_8);
        if (utf8 != null && !hasControlChars(utf8)) {
            return utf8;
        }
        String gb = tryDecode(raw, Charset.forName("GB18030"));
        if (gb != null && !hasControlChars(gb)) {
            return gb;
        }
        return new String(raw, StandardCharsets.ISO_8859_1);
    }

    private static String tryDecode(byte[] raw, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static boolean hasControlChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 && c != '\t') {
                return true;
            }
        }
        return false;
    }

    private static String nameOf(PDFObject object) {
        return object == null || object.isNull() ? null : String.valueOf(object);
    }

    private static boolean hasStream(PDFObject descriptor, String key) {
        PDFObject stream = getObject(descriptor, key);
        return stream != null && !stream.isNull();
    }

    /**
     * 解析字典的 key 列表（MuPDF Java 绑定没有暴露 key 迭代 API）。
     */
    private static List<String> dictKeys(PDFObject object) {
        if (object == null || object.isNull()) {
            return new ArrayList<>();
        }
        try {
            PDFObject resolved = object.isIndirect() ? object.resolve() : object;
            if (resolved == null || resolved.isNull()) {
                return new ArrayList<>();
            }
            String text = resolved.toString();
            if (text == null || text.indexOf('/') < 0) {
                return new ArrayList<>();
            }
            Matcher matcher = Pattern.compile("/([A-Za-z0-9+._-]+)").matcher(text);
            Set<String> keys = new LinkedHashSet<>();
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
            return new ArrayList<>(keys);
        } catch (Throwable t) {
            log.error("[MuPDF-Font] 解析字典 key 列表失败（已跳过该资源）", t);
            return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------------
    // 导出辅助
    // ------------------------------------------------------------------

    private static String uniqueFileName(String fontName, String fileType, Set<String> usedNames) {
        String safeName = sanitizeFileName(fontName);
        String extension = extensionOf(fileType);
        String candidate = safeName + extension;
        int index = 2;
        while (!usedNames.add(candidate)) {
            candidate = safeName + "-" + index + extension;
            index++;
        }
        return candidate;
    }

    private static String sanitizeFileName(String name) {
        String safe = name == null || name.isEmpty() ? "font" : name.trim()
                                                                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                                                                .replaceAll("^[.]+", "");
        if (safe.isEmpty()) {
            safe = "font";
        }
        if (safe.length() > MAX_FILE_NAME_LENGTH) {
            safe = safe.substring(0, MAX_FILE_NAME_LENGTH);
        }
        return safe;
    }

    private static String extensionOf(String fileType) {
        if ("ttf".equals(fileType)) {
            return ".ttf";
        }
        if ("otf".equals(fileType)) {
            return ".otf";
        }
        if ("cff".equals(fileType)) {
            return ".cff";
        }
        return ".pfa";
    }

    private static File requireReadableFile(String pdfPath) {
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("PDF 路径不能为空");
        }
        File file = new File(pdfPath);
        if (!file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("PDF 文件不存在或不可读: " + pdfPath);
        }
        return file;
    }

    /**
     * 扫描结果条目。
     *
     * @param name     可读字体名
     * @param rawName  {@code /BaseFont} 原始字节
     * @param embedded 是否带嵌入字体程序
     * @param fileType 字体程序类型（ttf / otf / cff / type1）
     * @param program  字体程序字节（未读取或未嵌入时为 null）
     */
    private record FontEntry(String name, byte[] rawName, boolean embedded, String fileType, byte[] program) {
    }

    /**
     * 页面级字体名解析器：MuPDF 运行期字体名 ➔ 可读名称。
     */
    static final class FontNameResolver {

        private final List<FontName> fonts;
        private final Map<String, String> cache = new HashMap<>();

        private FontNameResolver(List<FontName> fonts) {
            this.fonts = fonts;
        }

        /**
         * 解析 MuPDF 字体句柄的名称。
         *
         * @param font MuPDF 字体句柄
         * @return 可读名称；无法匹配时返回 null
         */
        String resolve(Font font) {
            if (font == null) {
                return null;
            }
            try {
                return resolve(font.getName());
            } catch (Throwable t) {
                log.debug("[MuPDF-Font] 读取运行期字体名失败: {}", t.getMessage());
                return null;
            }
        }

        /**
         * 解析 MuPDF 报告的字体名。
         *
         * @param muPdfName MuPDF {@code Font#getName()} 的返回值
         * @return 可读名称；无法匹配时返回 null
         */
        String resolve(String muPdfName) {
            if (muPdfName == null || muPdfName.isEmpty()) {
                return null;
            }
            if (cache.containsKey(muPdfName)) {
                return cache.get(muPdfName);
            }
            String readable = match(muPdfName);
            cache.put(muPdfName, readable);
            return readable;
        }

        private String match(String muPdfName) {
            FontName best = null;
            try {
                for (FontName font : fonts) {
                    if (startsWithLatin1(font.rawLatin1(), muPdfName)) {
                        if (best == null || font.rawName().length < best.rawName().length) {
                            best = font;
                        }
                    }
                }
                if (best == null) {
                    for (FontName font : fonts) {
                        if (matchesMask(muPdfName, font.rawName())) {
                            if (best == null || font.rawName().length < best.rawName().length) {
                                best = font;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                log.debug("[MuPDF-Font] 字体名匹配失败: {}", t.getMessage());
                return null;
            }
            return best == null ? null : best.readableName();
        }
    }

    /**
     * 页面字体名条目。
     *
     * @param rawName      {@code /BaseFont} 原始字节
     * @param rawLatin1    原始字节按 ISO-8859-1 映射的字符串（与 MuPDF 运行期字体名前缀一致）
     * @param readableName 可读名称
     */
    private record FontName(byte[] rawName, String rawLatin1, String readableName) {
    }

    private static boolean startsWithLatin1(String rawLatin1, String muPdfName) {
        return rawLatin1.length() >= muPdfName.length()
               && rawLatin1.regionMatches(0, muPdfName, 0, muPdfName.length());
    }

    /**
     * JDK 8 等会把非法字节替换成 {@code ?}，此时按可打印 ASCII 字符做兜底匹配。
     */
    private static boolean matchesMask(String muPdfName, byte[] rawName) {
        if (rawName == null || muPdfName.length() > rawName.length) {
            return false;
        }
        for (int i = 0; i < muPdfName.length(); i++) {
            char c = muPdfName.charAt(i);
            if (c == '?' || c < 0x20 || c > 0x7E) {
                continue;
            }
            if ((rawName[i] & 0xFF) != c) {
                return false;
            }
        }
        return true;
    }
}
