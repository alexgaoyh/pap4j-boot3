package cn.net.pap.common.mupdf.service;

import com.artifex.mupdf.fitz.Buffer;
import com.artifex.mupdf.fitz.ColorSpace;
import com.artifex.mupdf.fitz.DefaultColorSpaces;
import com.artifex.mupdf.fitz.Device;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Font;
import com.artifex.mupdf.fitz.Image;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Pixmap;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.Shade;
import com.artifex.mupdf.fitz.StrokeState;
import com.artifex.mupdf.fitz.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.PathIterator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 基于坐标（{@link MuPdfService#extractCoordinates(String, int)}）生成 SVG。
 *
 * <p>每个字符使用自己的字体、坐标、字号与旋转角输出；字体分三种处理：</p>
 * <ol>
 *   <li><b>Type1 字体（.pfa/.pfb）</b>：用 JDK 自带的 Type1 支持按 MuPDF 的 glyph id 取原始字形轮廓，
 *       输出为 SVG path + use，字形与 PDF 原字形完全一致（公式/符号不再错位）；</li>
 *   <li><b>Web 字体（.ttf/.otf/.woff/.woff2）</b>：base64 内嵌 {@code @font-face}，按 Unicode 渲染；</li>
 *   <li><b>其它（未提供字体文件）</b>：回退 {@code SimSun}（宋体），并在结果中列出。</li>
 * </ol>
 *
 * <p>字体文件名（不含后缀）需与坐标 JSON 中的字体名一致，例如
 * {@code 方正书宋_GBK.ttf}、{@code DY1+ZGXSfW-1.pfa}。Type1 字体文件可直接使用
 * {@link MuPdfFontExtractor#exportFonts(String, Path)} 导出的结果。</p>
 *
 * <h3>异常处理与降级策略</h3>
 * <p>SVG 还原定位为「尽力而为」：<b>单个字形 / 单条路径 / 单张图片 / 单个字体文件</b>
 * 的失败只降级该元素（跳过或退回文本渲染）并记录带页码与文件路径的日志，
 * 不会中断整页导出；<b>页面级</b>失败（无法打开文档、页码越界、写出失败）
 * 则抛出携带上下文的异常。所有资源上限均可用 JVM {@code -D} 参数调整：</p>
 * <ul>
 *   <li>{@code pap.mupdf.svg.maxImagePixels}（默认 4000 万）：单张图片光栅化像素上限；</li>
 *   <li>{@code pap.mupdf.svg.maxImages}（默认 128）：单页内嵌图片数量上限；</li>
 *   <li>{@code pap.mupdf.svg.maxImageBytes}（默认 48MB）：单页内嵌图片字节上限；</li>
 *   <li>{@code pap.mupdf.svg.maxPaths}（默认 20 万）：单页矢量路径数量上限；</li>
 *   <li>{@code pap.mupdf.svg.maxFontBytes}（默认 32MB）：单字体文件字节上限；</li>
 *   <li>{@code pap.mupdf.svg.fontCacheDirs}（默认 8）：字体目录缓存条目上限，目录内容变化时按指纹自动失效；</li>
 *   <li>{@code pap.mupdf.svg.outlineCacheSize}（默认 200000）：跨页复用的字形轮廓缓存条目上限。</li>
 * </ul>
 *
 * <h3>批量导出</h3>
 * <p>逐页调用 {@link #export(String, int, Path, Path)} 时，每页都会重新打开文档（坐标提取 + 图形采集各一次）、
 * 重新扫描并读取字体目录、重新做 {@code @font-face} 的 base64 编码。批量场景请改用
 * {@link #openBatch(String, Path)} + {@link Batch#exportPage(int, Path)}：整个批次只打开 2 个文档句柄、
 * 只读取一次字体目录，字体 base64 与字形轮廓 path 跨页复用，<b>导出的每页 SVG 与逐页调用完全一致</b>。
 * 字体目录与字形轮廓缓存均按“目录指纹（文件名 + 大小 + 修改时间）”自动失效，不改变任何导出语义。</p>
 *
 * @author alexgaoyh
 */
public final class MuPdfSvgExporter {

    private static final Logger log = LoggerFactory.getLogger(MuPdfSvgExporter.class);

    /**
     * 未提供字体文件时的统一回退字体栈（按当前约定回退到宋体）。
     */
    private static final String FALLBACK_FAMILY = "SimSun, serif";

    /**
     * 轮廓提取使用的字体渲染上下文。
     */
    private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(null, true, true);

    /**
     * 单张图片光栅化像素上限，避免畸形 PDF 的图像容器把堆/堆外内存抽爆。
     */
    private static final long MAX_IMAGE_PIXELS = Long.getLong("pap.mupdf.svg.maxImagePixels", 40_000_000L);

    /**
     * 单页内嵌图片数量上限。
     */
    private static final int MAX_IMAGE_COUNT = Integer.getInteger("pap.mupdf.svg.maxImages", 128);

    /**
     * 单页内嵌图片原始字节总量上限（超出后剩余图片被跳过，避免 SVG 体积与内存失控）。
     */
    private static final long MAX_IMAGE_BYTES = Long.getLong("pap.mupdf.svg.maxImageBytes", 48L * 1024 * 1024);

    /**
     * 单页矢量路径数量上限（表格线 / 公式线条密集页的保护）。
     */
    private static final int MAX_PATH_COUNT = Integer.getInteger("pap.mupdf.svg.maxPaths", 200_000);

    /**
     * 单个字体文件字节上限。
     */
    private static final long MAX_FONT_FILE_BYTES = Long.getLong("pap.mupdf.svg.maxFontBytes", 32L * 1024 * 1024);

    /**
     * 字体目录缓存条目上限（按字体目录绝对路径索引，内容变化时按指纹失效）。
     */
    private static final int FONT_TABLE_CACHE_MAX = Integer.getInteger("pap.mupdf.svg.fontCacheDirs", 8);

    /**
     * 跨页复用的字形轮廓缓存条目上限；超出后仅不再新增缓存，不影响导出结果。
     */
    private static final int MAX_OUTLINE_CACHE = Integer.getInteger("pap.mupdf.svg.outlineCacheSize", 200_000);

    /**
     * 单个字体目录允许常驻缓存的字体总字节数上限；超出则该目录不做缓存（仍可导出，只是退化为每次重新读取），
     * 避免把超大字体目录的字节长期留在堆里。
     */
    private static final long MAX_CACHED_FONT_BYTES = Long.getLong("pap.mupdf.svg.fontCacheMaxBytes", 128L * 1024 * 1024);

    /**
     * 字体目录缓存（访问序 LRU，读多写少，统一用 {@code synchronized} 保护），避免批量导出每页重复读盘与 base64 编码。
     */
    private static final LinkedHashMap<String, FontTables> FONT_TABLE_CACHE = new LinkedHashMap<>(8, 0.75f, true);

    private MuPdfSvgExporter() {
    }

    /**
     * SVG 导出结果。
     *
     * @param svgFile       生成的 SVG 文件
     * @param embeddedFonts 以 {@code @font-face} 内嵌的 Web 字体名
     * @param outlineFonts  以矢量轮廓（path）还原的 Type1 字体名
     * @param fallbackFonts 回退到宋体的字体名
     * @param pathCount     导出的矢量路径数量（表格边框、分割线、数学公式标尺等）
     * @param imageCount    导出的内嵌图片数量
     */
    public record ExportResult(Path svgFile, List<String> embeddedFonts,
                               List<String> outlineFonts, List<String> fallbackFonts,
                               int pathCount, int imageCount) {
        public ExportResult(Path svgFile, List<String> embeddedFonts,
                            List<String> outlineFonts, List<String> fallbackFonts) {
            this(svgFile, embeddedFonts, outlineFonts, fallbackFonts, 0, 0);
        }
    }

    /**
     * 导出单页 SVG。
     *
     * @param pdfPath PDF 文件物理路径
     * @param pageNum 页码（0-indexed）
     * @param fontDir 字体目录（可传 null）；文件名需与坐标中的字体名一致
     * @param svgFile 输出 SVG 文件
     * @return 导出结果（内嵌字体、轮廓字体、回退字体）
     * @throws IOException              文件读写失败
     * @throws IllegalArgumentException 参数非法或页码越界
     * @throws IllegalStateException    坐标提取失败
     */
    public static ExportResult export(String pdfPath, int pageNum, Path fontDir, Path svgFile) throws IOException {
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("PDF 路径不能为空");
        }
        if (pageNum < 0) {
            throw new IllegalArgumentException("页码必须 >= 0（当前 " + pageNum + "）");
        }
        if (svgFile == null) {
            throw new IllegalArgumentException("SVG 输出文件不能为空");
        }
        try (Batch batch = openBatch(pdfPath, fontDir)) {
            return batch.exportPage(pageNum, svgFile);
        }
    }

    /**
     * 打开批量导出上下文：<b>整个批次只打开文档句柄（坐标提取 1 个 + 图形采集 1 个）、
     * 只扫描并读取一次字体目录</b>，后续逐页调用 {@link Batch#exportPage(int, Path)} 即可复用。
     *
     * <p>相比逐页调用 {@link #export(String, int, Path, Path)}，本方法省去每页重复的
     * 文档打开与解析、字体目录扫描、字体文件读取与 {@code @font-face} base64 编码，
     * 字形轮廓也会跨页复用；导出的单页 SVG 内容与 {@code export(...)} 完全一致。</p>
     *
     * <p><b>使用约定：</b>同一个 {@code Batch} 必须<b>单线程</b>使用（MuPDF 要求同一文档句柄
     * 同一时刻只能被一个线程访问）；用完后务必 {@link Batch#close()} 释放原生句柄，
     * 推荐用 try-with-resources。不同线程可各自打开独立的 {@code Batch}。</p>
     *
     * @param pdfPath PDF 文件物理路径
     * @param fontDir 字体目录（可传 null）；文件名需与坐标中的字体名一致
     * @return 批量导出上下文
     * @throws IllegalArgumentException PDF 路径为空
     */
    public static Batch openBatch(String pdfPath, Path fontDir) throws IOException {
        if (pdfPath == null || pdfPath.trim().isEmpty()) {
            throw new IllegalArgumentException("PDF 路径不能为空");
        }
        Path effectiveFontDir = resolveFontDir(fontDir);
        MuPdfService service = new MuPdfService();
        boolean success = false;
        try {
            Batch batch = new Batch(pdfPath, service, fontTables(effectiveFontDir));
            success = true;
            return batch;
        } finally {
            if (!success) {
                service.close();
            }
        }
    }

    /**
     * 批量导出上下文：复用同一个文档句柄、同一份字体表与字形轮廓缓存。
     *
     * <p><b>非线程安全</b>，请单线程使用；实例用完后必须 {@link #close()}。</p>
     */
    public static final class Batch implements AutoCloseable {

        private final String pdfPath;
        private final MuPdfService service;
        private final FontTables fontTables;
        private Document graphicsDocument;
        private int pageCount = -1;
        private boolean closed;

        private Batch(String pdfPath, MuPdfService service, FontTables fontTables) {
            this.pdfPath = pdfPath;
            this.service = service;
            this.fontTables = fontTables;
        }

        /**
         * 当前 PDF 的总页数（首次调用时读取并缓存）。
         *
         * @return 总页数
         * @throws IllegalStateException 批次已关闭，或文档损坏/加密无法读取页数
         */
        public int pageCount() {
            if (pageCount < 0) {
                pageCount = service.getPageCount(pdfPath);
            }
            return pageCount;
        }

        /**
         * 懒加载图形采集用的文档句柄：整个批次只打开一次；打开失败时返回 null，
         * 调用方自动降级为“仅文字图层”（与逐页导出的降级行为一致）。
         *
         * @return 文档句柄，或 null
         */
        private Document graphicsDocument() {
            if (graphicsDocument == null) {
                graphicsDocument = Document.openDocument(pdfPath);
            }
            return graphicsDocument;
        }

        /**
         * 导出批次内指定页的 SVG，复用批次的文档句柄与字体/字形缓存。
         *
         * @param pageNum 页码（0-indexed）
         * @param svgFile 输出 SVG 文件
         * @return 导出结果（内嵌字体、轮廓字体、回退字体）
         * @throws IOException              文件读写失败
         * @throws IllegalArgumentException 页码为负或输出文件为空
         * @throws IllegalStateException    批次已关闭，或坐标提取失败
         */
        public ExportResult exportPage(int pageNum, Path svgFile) throws IOException {
            if (closed) {
                throw new IllegalStateException("批次已关闭，无法继续导出: " + pdfPath);
            }
            if (pageNum < 0) {
                throw new IllegalArgumentException("页码必须 >= 0（当前 " + pageNum + "）");
            }
            if (svgFile == null) {
                throw new IllegalArgumentException("SVG 输出文件不能为空");
            }

            MuPdfService.PageResult page;
            try {
                List<MuPdfService.PageResult> pages = service.extractCoordinates(pdfPath, pageNum).pages();
                if (pages == null || pages.isEmpty()) {
                    throw new IllegalStateException("坐标提取结果为空");
                }
                page = pages.get(0);
            } catch (RuntimeException coordEx) {
                throw new IllegalStateException("提取第 " + (pageNum + 1) + " 页坐标失败: " + pdfPath
                                                + " -> " + coordEx.getMessage(), coordEx);
            }
            List<MuPdfService.CharResult> chars = flatten(page);
            Set<String> fontNames = collectFontNames(chars);
            if (chars.isEmpty()) {
                log.warn("[MuPDF-SVG] 第 {} 页未提取到任何字符，将只输出矢量/图片图层: {}", pageNum + 1, pdfPath);
            }

            Map<String, WebFont> webFonts = fontTables.webFonts();
            Map<String, java.awt.Font> type1Fonts = fontTables.type1Fonts();
            // 图形采集：整个批次只打开一个独立句柄（坐标提取用的是 MuPdfService 的缓存句柄），
            // 打开失败时降级为仅文字图层
            PageGraphics graphics;
            Document doc = null;
            try {
                doc = graphicsDocument();
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 采集第 {} 页图形失败（导出继续，仅文字图层）: {}", pageNum + 1, pdfPath, t);
            }
            graphics = doc == null
                    ? new PageGraphics(new ArrayList<>(), new ArrayList<>(), new ArrayList<>())
                    : collectPageGraphics(doc, pdfPath, pageNum);
            Map<Integer, GlyphRef> glyphs = matchGlyphs(graphics.glyphs(), chars, pageNum);

            List<String> embeddedFonts = new ArrayList<>();
            List<String> outlineFonts = new ArrayList<>();
            List<String> fallbackFonts = new ArrayList<>();
            Map<String, String> classByFont = new LinkedHashMap<>();
            StringBuilder css = new StringBuilder(2048);

            for (String fontName : fontNames) {
                if (!glyphs.isEmpty() && type1Fonts.containsKey(fontName)) {
                    outlineFonts.add(fontName);
                } else if (webFonts.containsKey(fontName)) {
                    embeddedFonts.add(fontName);
                    // base64 结果按字体名缓存并跨页复用，避免每页重新编码整份字体
                    String fontFaceCss = fontTables.fontFaceCss(fontName);
                    if (fontFaceCss != null) {
                        css.append(fontFaceCss);
                    }
                    classByFont.put(fontName, "f" + classByFont.size());
                    appendTextClass(css, classByFont.get(fontName), fontName, null);
                } else {
                    fallbackFonts.add(fontName);
                    classByFont.put(fontName, "f" + classByFont.size());
                    appendTextClass(css, classByFont.get(fontName), fontName, FALLBACK_FAMILY);
                }
            }

            String svg = buildSvg(page, chars, glyphs, graphics.paths(), graphics.images(),
                    type1Fonts, classByFont, css, fontTables);
            try {
                Path parent = svgFile.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(svgFile, svg, StandardCharsets.UTF_8);
            } catch (IOException writeEx) {
                throw new IOException("写出 SVG 失败: " + svgFile.toAbsolutePath()
                                      + " -> " + writeEx.getMessage(), writeEx);
            }

            log.info("[MuPDF-SVG] 导出完成: {} 第 {} 页 | 矢量路径 {} 条 | 内嵌图片 {} 张 | 轮廓字体 {} 个 | 内嵌字体 {} 个 | 回退宋体 {} 个 | {}",
                    pdfPath, pageNum + 1, graphics.paths().size(), graphics.images().size(),
                    outlineFonts.size(), embeddedFonts.size(), fallbackFonts.size(), svgFile.toAbsolutePath());
            return new ExportResult(svgFile, embeddedFonts, outlineFonts, fallbackFonts,
                    graphics.paths().size(), graphics.images().size());
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Document doc = graphicsDocument;
            graphicsDocument = null;
            try {
                if (doc != null) {
                    doc.destroy();
                }
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 释放图形采集文档句柄失败: {}", pdfPath, t);
            } finally {
                service.close();
            }
        }
    }

    /**
     * 校验并归一化字体目录：不存在时告警并降级为“不使用字体文件”。
     *
     * @param fontDir 原始字体目录
     * @return 可用的字体目录，或 null
     */
    private static Path resolveFontDir(Path fontDir) {
        if (fontDir != null && !Files.isDirectory(fontDir)) {
            log.warn("[MuPDF-SVG] 字体目录不存在或不是目录，忽略字体并统一回退宋体: {}", fontDir.toAbsolutePath());
            return null;
        }
        return fontDir;
    }

    // ------------------------------------------------------------------
    // SVG 构建
    // ------------------------------------------------------------------

    private static String buildSvg(MuPdfService.PageResult page, List<MuPdfService.CharResult> chars,
                                   Map<Integer, GlyphRef> glyphs, List<SvgPath> paths, List<SvgImage> images,
                                   Map<String, java.awt.Font> type1Fonts,
                                   Map<String, String> classByFont, StringBuilder css, FontTables fontTables) {
        if (page == null) {
            // 上层已校验，这里仅作兜底，避免 NPE 掩盖真实原因
            throw new IllegalArgumentException("页面坐标结果不能为空");
        }
        double width = finiteOrDefault(page.width(), 595.0);
        double height = finiteOrDefault(page.height(), 842.0);
        List<MuPdfService.CharResult> safeChars = chars == null ? List.of() : chars;
        Map<String, String> safeClasses = classByFont == null ? Map.of() : classByFont;
        Map<String, java.awt.Font> safeType1Fonts = type1Fonts == null ? Map.of() : type1Fonts;

        StringBuilder defs = new StringBuilder(8192);
        StringBuilder body = new StringBuilder(16384);
        Map<String, String> outlinedGlyphs = new LinkedHashMap<>();
        if (css != null && !css.isEmpty()) {
            defs.append("<style type=\"text/css\"><![CDATA[\n").append(css).append("]]></style>\n");
        }

        // 1. 页面底板
        body.append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");

        // 2. 图像图层（底图、配图、印章等）
        if (images != null && !images.isEmpty()) {
            for (SvgImage img : images) {
                img.appendSvg(body);
            }
        }

        // 3. 矢量图层（表格边框、分割线、数学公式分式横线/横梁等）
        if (paths != null && !paths.isEmpty()) {
            for (SvgPath p : paths) {
                p.appendSvg(body);
            }
        }

        // 4. 文字图层（Type1 轮廓 + 文本）
        Map<Integer, GlyphRef> safeGlyphs = glyphs == null ? Map.of() : glyphs;
        Set<String> unoutlinedGlyphs = new HashSet<>();
        for (int index = 0; index < safeChars.size(); index++) {
            MuPdfService.CharResult ch = safeChars.get(index);
            if (ch == null || ch.c() == null || ch.c().isEmpty()) {
                continue;
            }
            GlyphRef glyph = safeGlyphs.get(index);
            java.awt.Font type1Font = ch.font() == null ? null : safeType1Fonts.get(ch.font());
            if (glyph != null && glyph.gid() >= 0 && type1Font != null) {
                String key = ch.font() + '|' + glyph.gid();
                String pathId = outlinedGlyphs.get(key);
                if (pathId == null && !unoutlinedGlyphs.contains(key)) {
                    // 轮廓提取失败（字体文件与 PDF 不匹配 / gid 越界）时该字形退回文本渲染，不中断整页导出
                    String pathData = fontTables == null
                            ? toSvgPath(type1Font, glyph.gid())
                            : fontTables.outline(ch.font(), glyph.gid(), type1Font);
                    if (pathData == null) {
                        unoutlinedGlyphs.add(key);
                    } else {
                        pathId = "g" + outlinedGlyphs.size();
                        outlinedGlyphs.put(key, pathId);
                        defs.append("<path id=\"").append(pathId).append("\" d=\"")
                                .append(pathData).append("\"/>\n");
                    }
                }
                if (pathId != null) {
                    appendGlyphUse(body, pathId, glyph);
                    continue;
                }
            }
            if (glyph != null) {
                // 非 Type1 但已对齐到 glyph：同样用完整 trm，保证拉伸/旋转/剪切一致
                appendTextWithMatrix(body, ch, safeClasses, glyph);
            } else {
                appendText(body, ch, safeClasses);
            }
        }

        StringBuilder svg = new StringBuilder(defs.length() + body.length() + 1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
                .append(" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n")
                .append("  <defs>\n").append(defs).append("  </defs>\n")
                .append(body)
                .append("</svg>\n");
        return svg.toString();
    }

    private static void appendGlyphUse(StringBuilder body, String pathId, GlyphRef glyph) {
        body.append("  <use xlink:href=\"#").append(pathId).append('"')
                .append(" transform=\"").append(svgTransform(glyph.trm())).append('"')
                .append(" fill=\"#000000\"/>\n");
    }

    private static void appendTextWithMatrix(StringBuilder body, MuPdfService.CharResult ch,
                                             Map<String, String> classByFont, GlyphRef glyph) {
        String className = classByFont.getOrDefault(ch.font(), "f0");
        body.append("  <text x=\"0\" y=\"0\" font-size=\"1\" class=\"").append(className).append('"')
                .append(" xml:space=\"preserve\"")
                .append(" transform=\"").append(svgTransform(glyph.trm())).append('"')
                .append(" fill=\"#000000\">")
                .append(escapeXml(ch.c()))
                .append("</text>\n");
    }

    private static void appendText(StringBuilder body, MuPdfService.CharResult ch,
                                   Map<String, String> classByFont) {
        double x = ch.origin() != null ? ch.origin().x() : 0.0;
        double y = ch.origin() != null ? ch.origin().y() : 0.0;
        double size = ch.size() > 0 ? ch.size() : 12.0;
        double angle = ch.rotation() != null ? ch.rotation().visualAngle() : 0.0;
        String className = classByFont.getOrDefault(ch.font(), "f0");

        body.append("  <text x=\"").append(num(x)).append("\" y=\"").append(num(y))
                .append("\" font-size=\"").append(num(size))
                .append("\" class=\"").append(className).append('"')
                .append(" xml:space=\"preserve\"")
                .append(" transform=\"rotate(").append(num(angle)).append(',').append(num(x)).append(',').append(num(y)).append(")\"")
                .append(" fill=\"#000000\">")
                .append(escapeXml(ch.c()))
                .append("</text>\n");
    }

    private static List<MuPdfService.CharResult> flatten(MuPdfService.PageResult page) {
        List<MuPdfService.CharResult> chars = new ArrayList<>();
        if (page == null || page.blocks() == null) {
            return chars;
        }
        for (MuPdfService.BlockResult block : page.blocks()) {
            if (block == null || block.lines() == null) {
                continue;
            }
            for (MuPdfService.LineResult line : block.lines()) {
                if (line == null || line.chars() == null) {
                    continue;
                }
                for (MuPdfService.CharResult ch : line.chars()) {
                    if (ch != null && ch.c() != null && !ch.c().isEmpty()) {
                        chars.add(ch);
                    }
                }
            }
        }
        return chars;
    }

    private static Set<String> collectFontNames(List<MuPdfService.CharResult> chars) {
        Set<String> names = new LinkedHashSet<>();
        if (chars == null) {
            return names;
        }
        for (MuPdfService.CharResult ch : chars) {
            if (ch != null && ch.font() != null && !ch.font().isEmpty()) {
                names.add(ch.font());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------
    // 字形 id 对齐（MuPDF Text.walk 与结构化文本逐字对齐）
    // ------------------------------------------------------------------

    /**
     * 采集 MuPDF 渲染时实际使用的 glyph id，并与坐标字符逐一对齐。
     *
     * <p>遇到由 StructuredText 合成的空白字符（PDF 流中无对应 glyph）时自动跳过；
     * 对可见字符校验 Unicode 与字体名，支持小范围滑动窗口同步，避免单字符失配导致整页回退。</p>
     */
    private static Map<Integer, GlyphRef> matchGlyphs(List<GlyphRef> glyphs,
                                                     List<MuPdfService.CharResult> chars,
                                                     int pageNum) {
        if (chars == null || chars.isEmpty() || glyphs == null || glyphs.isEmpty()) {
            return Map.of();
        }

        Map<Integer, GlyphRef> result = new LinkedHashMap<>();
        int glyphIndex = 0;

        for (int charIndex = 0; charIndex < chars.size() && glyphIndex < glyphs.size(); charIndex++) {
            MuPdfService.CharResult ch = chars.get(charIndex);
            if (ch == null || ch.c() == null || ch.c().isEmpty()) {
                continue;
            }

            int charCodePoint = ch.c().codePointAt(0);

            GlyphRef currentGlyph = glyphs.get(glyphIndex);
            if (currentGlyph == null) {
                // 字形槽位为空（异常采集结果）：跳过该槽位并重试当前字符
                glyphIndex++;
                charIndex--;
                continue;
            }

            // 1. 若当前字符为空白，但底层 glyph 不是空白，说明是 StructuredText 合成的词间距空格，直接跳过
            if (isWhitespace(charCodePoint) && !isWhitespace(currentGlyph.unicode())) {
                continue;
            }

            GlyphRef glyph = currentGlyph;

            // 2. 比对实际字符（Unicode 匹配或字体名匹配）
            if (normalizeSpace(glyph.unicode()) == normalizeSpace(charCodePoint)
                    || Objects.equals(glyph.fontName(), ch.font())) {
                result.put(charIndex, glyph);
                glyphIndex++;
            } else {
                // 3. 局部失配自愈：尝试向前探查至多 3 个字形
                int syncOffset = findSyncOffset(charCodePoint, glyphs, glyphIndex, 3);
                if (syncOffset > 0) {
                    glyphIndex += syncOffset;
                    GlyphRef synced = glyphs.get(glyphIndex);
                    if (synced != null) {
                        result.put(charIndex, synced);
                    }
                    glyphIndex++;
                }
            }
        }

        log.info("[MuPDF-SVG] 第 {} 页字形对齐完成: 匹配 {} / {} 字符 (底层字形 {})",
                pageNum + 1, result.size(), chars.size(), glyphs.size());

        return result;
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || codePoint == 0x00A0 || codePoint == 0x3000;
    }

    private static int findSyncOffset(int charCodePoint, List<GlyphRef> glyphs, int glyphIndex, int maxLookahead) {
        int target = normalizeSpace(charCodePoint);
        for (int offset = 1; offset <= maxLookahead && glyphIndex + offset < glyphs.size(); offset++) {
            GlyphRef candidate = glyphs.get(glyphIndex + offset);
            if (candidate != null && normalizeSpace(candidate.unicode()) == target) {
                return offset;
            }
        }
        return -1;
    }

    private static PageGraphics collectPageGraphics(Document doc, String pdfPath, int pageNum) {
        List<GlyphRef> glyphs = new ArrayList<>();
        List<SvgPath> paths = new ArrayList<>();
        List<SvgImage> images = new ArrayList<>();

        try {
            if (!(doc instanceof PDFDocument pdf)) {
                return new PageGraphics(glyphs, paths, images);
            }

            MuPdfFontExtractor.FontNameResolver resolver;
            try {
                resolver = MuPdfFontExtractor.pageFontNameResolver(pdf, pageNum);
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 构建第 {} 页字体名解析器失败，改用 MuPDF 原始字体名", pageNum + 1, t);
                resolver = null;
            }

            Page page;
            try {
                page = pdf.loadPage(pageNum);
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 加载第 {} 页失败，图形图层（矢量/图片/字形对齐）将被跳过: {}",
                        pageNum + 1, pdfPath, t);
                return new PageGraphics(glyphs, paths, images);
            }

            try {
                GlyphCollectorDevice device = new GlyphCollectorDevice(resolver, glyphs, paths, images);
                try {
                    page.run(device, Matrix.Identity());
                } catch (Throwable t) {
                    // 内容流中断时保留已采集部分：文字仍可回退为 <text> 输出
                    log.error("[MuPDF-SVG] 遍历第 {} 页内容流中断，保留已采集的 {} 个字形 / {} 条路径 / {} 张图片",
                            pageNum + 1, glyphs.size(), paths.size(), images.size(), t);
                } finally {
                    try {
                        device.destroy();
                    } catch (Throwable t) {
                        log.error("[MuPDF-SVG] 释放采集设备句柄失败", t);
                    }
                }
            } finally {
                try {
                    page.destroy();
                } catch (Throwable t) {
                    log.error("[MuPDF-SVG] 释放页面句柄失败", t);
                }
            }
        } catch (Throwable t) {
            log.error("[MuPDF-SVG] 采集第 {} 页图形失败（导出继续，仅文字图层）: {}", pageNum + 1, pdfPath, t);
        }
        return new PageGraphics(glyphs, paths, images);
    }

    /**
     * 统一空白字符：结构化文本会把 U+3000（全角空格）归一成 U+0020，比对 glyph 时需对齐。
     */
    private static int normalizeSpace(int codePoint) {
        return codePoint == 0x3000 || codePoint == 0x00A0 ? 0x20 : codePoint;
    }

    /**
     * 收集单页字形引用、矢量路径与位图的设备。
     */
    private static final class GlyphCollectorDevice extends Device {

        private final MuPdfFontExtractor.FontNameResolver resolver;
        private final List<GlyphRef> glyphs;
        private final List<SvgPath> paths;
        private final List<SvgImage> images;
        private long imageBytes;
        private boolean imageLimitWarned;
        private boolean pathLimitWarned;

        private GlyphCollectorDevice(MuPdfFontExtractor.FontNameResolver resolver,
                                     List<GlyphRef> glyphs, List<SvgPath> paths, List<SvgImage> images) {
            this.resolver = resolver;
            this.glyphs = glyphs;
            this.paths = paths;
            this.images = images;
        }

        @Override
        public void close() {
        }

        @Override
        public void fillPath(com.artifex.mupdf.fitz.Path path, boolean evenOdd, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
            if (path == null || ctm == null) {
                return;
            }
            if (paths.size() >= MAX_PATH_COUNT) {
                if (!pathLimitWarned) {
                    pathLimitWarned = true;
                    log.warn("[MuPDF-SVG] 矢量路径数达到上限 {}，后续路径将被跳过（可通过 -Dpap.mupdf.svg.maxPaths 调整）", MAX_PATH_COUNT);
                }
                return;
            }
            String d = toPathData(path, ctm);
            if (d.isEmpty()) {
                return;
            }
            String fillColor = svgColor(cs, color);
            paths.add(new SvgPath(d, "none", fillColor, 0.0f, null, null, alpha, evenOdd));
        }

        @Override
        public void strokePath(com.artifex.mupdf.fitz.Path path, StrokeState stroke, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
            if (path == null || ctm == null) {
                return;
            }
            if (paths.size() >= MAX_PATH_COUNT) {
                if (!pathLimitWarned) {
                    pathLimitWarned = true;
                    log.warn("[MuPDF-SVG] 矢量路径数达到上限 {}，后续路径将被跳过（可通过 -Dpap.mupdf.svg.maxPaths 调整）", MAX_PATH_COUNT);
                }
                return;
            }
            String d = toPathData(path, ctm);
            if (d.isEmpty()) {
                return;
            }
            float lineWidth = stroke != null ? stroke.getLineWidth() : 1.0f;
            float scale = (float) Math.hypot(ctm.a, ctm.b);
            if (scale > 0 && Math.abs(scale - 1.0f) > 1e-4) {
                lineWidth *= scale;
            }
            String strokeColor = svgColor(cs, color);
            String cap = stroke != null ? svgLineCap(stroke.getLineCap()) : null;
            String join = stroke != null ? svgLineJoin(stroke.getLineJoin()) : null;
            paths.add(new SvgPath(d, strokeColor, "none", lineWidth, cap, join, alpha, false));
        }

        @Override
        public void clipPath(com.artifex.mupdf.fitz.Path path, boolean evenOdd, Matrix ctm) {
        }

        @Override
        public void clipStrokePath(com.artifex.mupdf.fitz.Path path, StrokeState stroke, Matrix ctm) {
        }

        @Override
        public void fillText(Text text, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
            if (text == null || ctm == null) {
                return;
            }
            try {
                text.walk((font, trm, glyph, unicode, wmode) -> {
                    if (font == null || trm == null) {
                        return;
                    }
                    String readable = null;
                    try {
                        readable = resolver == null ? null : resolver.resolve(font);
                    } catch (Throwable t) {
                        log.error("[MuPDF-SVG] 解析字体名失败，改用 MuPDF 原始名", t);
                    }
                    glyphs.add(new GlyphRef(readable != null ? readable : safeFontName(font),
                            unicode, glyph, compose(ctm, trm)));
                });
            } catch (Throwable t) {
                // 单个文本对象遍历失败只丢失该对象，不影响整页其他文字
                log.error("[MuPDF-SVG] 遍历文本对象失败（该文本对象已被跳过）", t);
            }
        }

        /**
         * 安全读取 MuPDF 运行期字体名，失败时返回占位名。
         */
        private static String safeFontName(Font font) {
            try {
                String name = font.getName();
                return name == null || name.trim().isEmpty() ? "unknown" : name;
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 读取 MuPDF 字体名失败，使用占位名", t);
                return "unknown";
            }
        }

        @Override
        public void strokeText(Text text, StrokeState stroke, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
        }

        @Override
        public void clipText(Text text, Matrix ctm) {
        }

        @Override
        public void clipStrokeText(Text text, StrokeState stroke, Matrix ctm) {
        }

        @Override
        public void ignoreText(Text text, Matrix ctm) {
        }

        @Override
        public void fillShade(Shade shd, Matrix ctm, float alpha, int cp) {
        }

        @Override
        public void fillImage(Image img, Matrix ctm, float alpha, int cp) {
            if (img == null || ctm == null) {
                return;
            }
            if (images.size() >= MAX_IMAGE_COUNT) {
                if (!imageLimitWarned) {
                    imageLimitWarned = true;
                    log.warn("[MuPDF-SVG] 内嵌图片数达到上限 {}，后续图片将被跳过（可通过 -Dpap.mupdf.svg.maxImages 调整）", MAX_IMAGE_COUNT);
                }
                return;
            }
            long pixels = (long) img.getWidth() * (long) img.getHeight();
            if (pixels <= 0 || pixels > MAX_IMAGE_PIXELS) {
                log.warn("[MuPDF-SVG] 图片尺寸 {}x{} 像素超出安全上限 {}，已跳过该图片",
                        img.getWidth(), img.getHeight(), MAX_IMAGE_PIXELS);
                return;
            }
            Pixmap pix = null;
            Buffer buf = null;
            try {
                pix = img.toPixmap();
                if (pix == null) {
                    log.warn("[MuPDF-SVG] 图片光栅化返回空 Pixmap，已跳过该图片");
                    return;
                }
                buf = pix.asPNG();
                if (buf == null) {
                    log.warn("[MuPDF-SVG] 图片 PNG 编码返回空缓冲区，已跳过该图片");
                    return;
                }
                byte[] bytes = buf.asByteArray();
                if (bytes == null || bytes.length == 0) {
                    log.warn("[MuPDF-SVG] 图片 PNG 编码结果为空，已跳过该图片");
                    return;
                }
                if (imageBytes + bytes.length > MAX_IMAGE_BYTES) {
                    if (!imageLimitWarned) {
                        imageLimitWarned = true;
                        log.warn("[MuPDF-SVG] 内嵌图片总字节数达到上限 {}，后续图片将被跳过（可通过 -Dpap.mupdf.svg.maxImageBytes 调整）", MAX_IMAGE_BYTES);
                    }
                    return;
                }
                imageBytes += bytes.length;
                Transform imgTr = composeImageTransform(ctm);
                images.add(new SvgImage(imgTr, bytes, img.getWidth(), img.getHeight()));
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 提取页面内嵌图片失败（已跳过该图片）", t);
            } finally {
                if (buf != null) {
                    try {
                        buf.destroy();
                    } catch (Throwable ignored) {
                    }
                }
                if (pix != null) {
                    try {
                        pix.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        @Override
        public void fillImageMask(Image img, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
            fillImage(img, ctm, alpha, cp);
        }

        @Override
        public void clipImageMask(Image img, Matrix ctm) {
        }

        @Override
        public void popClip() {
        }

        @Override
        public void beginMask(Rect area, boolean luminosity, ColorSpace cs, float[] bc, int cp) {
        }

        @Override
        public void endMask() {
        }

        @Override
        public void beginGroup(Rect area, ColorSpace cs, boolean isolated, boolean knockout, int blendmode, float alpha) {
        }

        @Override
        public void endGroup() {
        }

        @Override
        public int beginTile(Rect area, Rect view, float xstep, float ystep, Matrix ctm, int id, int doc_id) {
            return 0;
        }

        @Override
        public void endTile() {
        }

        @Override
        public void renderFlags(int set, int clear) {
        }

        @Override
        public void setDefaultColorSpaces(DefaultColorSpaces dcs) {
        }

        @Override
        public void beginLayer(String name) {
        }

        @Override
        public void endLayer() {
        }

        @Override
        public void beginStructure(int standard, String raw, int idx) {
        }

        @Override
        public void endStructure() {
        }

        @Override
        public void beginMetatext(int meta, String text) {
        }

        @Override
        public void endMetatext() {
        }
    }

    private static String toPathData(com.artifex.mupdf.fitz.Path path, Matrix ctm) {
        if (path == null || ctm == null) {
            return "";
        }
        StringBuilder d = new StringBuilder(64);
        try {
            path.walk(pathWalker(d, ctm));
        } catch (Throwable t) {
            // 单条路径的畸形指令不应当中断整页：保留已解析的子路径
            log.error("[MuPDF-SVG] 解析矢量路径失败（已保留部分子路径）", t);
        }
        return d.toString();
    }

    private static com.artifex.mupdf.fitz.PathWalker pathWalker(StringBuilder d, Matrix ctm) {
        return new com.artifex.mupdf.fitz.PathWalker() {
            @Override
            public void moveTo(float x, float y) {
                float tx = ctm.a * x + ctm.c * y + ctm.e;
                float ty = ctm.b * x + ctm.d * y + ctm.f;
                if (d.length() > 0) {
                    d.append(' ');
                }
                d.append('M').append(num(tx)).append(' ').append(num(ty));
            }

            @Override
            public void lineTo(float x, float y) {
                float tx = ctm.a * x + ctm.c * y + ctm.e;
                float ty = ctm.b * x + ctm.d * y + ctm.f;
                if (d.length() > 0) {
                    d.append(' ');
                }
                d.append('L').append(num(tx)).append(' ').append(num(ty));
            }

            @Override
            public void curveTo(float cx1, float cy1, float cx2, float cy2, float ex, float ey) {
                float tx1 = ctm.a * cx1 + ctm.c * cy1 + ctm.e;
                float ty1 = ctm.b * cx1 + ctm.d * cy1 + ctm.f;
                float tx2 = ctm.a * cx2 + ctm.c * cy2 + ctm.e;
                float ty2 = ctm.b * cx2 + ctm.d * cy2 + ctm.f;
                float tex = ctm.a * ex + ctm.c * ey + ctm.e;
                float tey = ctm.b * ex + ctm.d * ey + ctm.f;
                if (d.length() > 0) {
                    d.append(' ');
                }
                d.append('C').append(num(tx1)).append(' ').append(num(ty1))
                        .append(' ').append(num(tx2)).append(' ').append(num(ty2))
                        .append(' ').append(num(tex)).append(' ').append(num(tey));
            }

            @Override
            public void closePath() {
                if (d.length() > 0) {
                    d.append(' ');
                }
                d.append('Z');
            }
        };
    }

    private static String svgColor(ColorSpace cs, float[] color) {
        if (color == null || color.length == 0) {
            return "#000000";
        }
        if (color.length == 1) {
            int v = clampColor(color[0]);
            return String.format(Locale.ROOT, "#%02x%02x%02x", v, v, v);
        }
        if (color.length == 3) {
            int r = clampColor(color[0]);
            int g = clampColor(color[1]);
            int b = clampColor(color[2]);
            return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
        }
        if (color.length == 4) { // CMYK
            float c = color[0], m = color[1], y = color[2], k = color[3];
            int r = clampColor((1.0f - c) * (1.0f - k));
            int g = clampColor((1.0f - m) * (1.0f - k));
            int b = clampColor((1.0f - y) * (1.0f - k));
            return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
        }
        return "#000000";
    }

    private static int clampColor(float val) {
        if (!Float.isFinite(val)) {
            return 0;
        }
        int v = Math.round(val * 255.0f);
        return Math.max(0, Math.min(255, v));
    }

    private static String svgLineCap(int cap) {
        return switch (cap) {
            case StrokeState.LINE_CAP_ROUND -> "round";
            case StrokeState.LINE_CAP_SQUARE -> "square";
            default -> null; // 默认 butt
        };
    }

    private static String svgLineJoin(int join) {
        return switch (join) {
            case StrokeState.LINE_JOIN_ROUND -> "round";
            case StrokeState.LINE_JOIN_BEVEL -> "bevel";
            default -> null; // 默认 miter
        };
    }

    private static Transform composeImageTransform(Matrix ctm) {
        if (ctm == null) {
            return Transform.IDENTITY;
        }
        java.awt.geom.AffineTransform transform =
                new java.awt.geom.AffineTransform(ctm.a, ctm.b, ctm.c, ctm.d, ctm.e, ctm.f);
        transform.concatenate(new java.awt.geom.AffineTransform(1, 0, 0, -1, 0, 1));
        return new Transform((float) transform.getScaleX(), (float) transform.getShearY(),
                (float) transform.getShearX(), (float) transform.getScaleY(),
                (float) transform.getTranslateX(), (float) transform.getTranslateY());
    }

    private record PageGraphics(List<GlyphRef> glyphs, List<SvgPath> paths, List<SvgImage> images) {
    }

    private record SvgPath(String d, String stroke, String fill, float strokeWidth,
                           String lineCap, String lineJoin, float opacity, boolean evenOdd) {
        void appendSvg(StringBuilder sb) {
            sb.append("  <path d=\"").append(d).append('"');
            if (stroke != null && !"none".equals(stroke)) {
                sb.append(" stroke=\"").append(stroke).append('"');
                if (strokeWidth > 0) {
                    sb.append(" stroke-width=\"").append(num(strokeWidth)).append('"');
                }
                if (lineCap != null) {
                    sb.append(" stroke-linecap=\"").append(lineCap).append('"');
                }
                if (lineJoin != null) {
                    sb.append(" stroke-linejoin=\"").append(lineJoin).append('"');
                }
            }
            if (fill != null && !"none".equals(fill)) {
                sb.append(" fill=\"").append(fill).append('"');
                if (evenOdd) {
                    sb.append(" fill-rule=\"evenodd\"");
                }
            } else {
                sb.append(" fill=\"none\"");
            }
            if (opacity >= 0 && opacity < 0.999f) {
                sb.append(" opacity=\"").append(num(opacity)).append('"');
            }
            sb.append("/>\n");
        }
    }

    private record SvgImage(Transform transform, byte[] pngBytes, int width, int height) {
        void appendSvg(StringBuilder sb) {
            if (pngBytes == null || pngBytes.length == 0) {
                return;
            }
            Transform tr = transform == null ? Transform.IDENTITY : transform;
            sb.append("  <image x=\"0\" y=\"0\" width=\"1\" height=\"1\"")
                    .append(" transform=\"").append(svgTransform(tr)).append('"')
                    .append(" preserveAspectRatio=\"none\"")
                    .append(" xlink:href=\"data:image/png;base64,")
                    .append(Base64.getEncoder().encodeToString(pngBytes))
                    .append("\"/>\n");
        }
    }

    /**
     * MuPDF 渲染期字形引用。
     *
     * @param fontName 可读字体名
     * @param unicode  字符 Unicode
     * @param gid      MuPDF glyph id
     * @param trm      最终页面显示变换矩阵（含原点、字号、拉伸/旋转）
     */
    private record GlyphRef(String fontName, int unicode, int gid, Transform trm) {
    }

    /**
     * 2D 仿射矩阵（与 MuPDF Matrix / SVG matrix 的 a b c d e f 顺序一致）。
     *
     * @param a x 缩放
     * @param b y 剪切
     * @param c x 剪切
     * @param d y 缩放
     * @param e x 平移
     * @param f y 平移
     */
    private record Transform(float a, float b, float c, float d, float e, float f) {
        private static final Transform IDENTITY = new Transform(1, 0, 0, 1, 0, 0);
    }

    /**
     * 组合出“字形设计空间 ➔ 页面显示坐标（Y 向下）”的矩阵：
     * {@code ctm * trm * flipY}。
     */
    private static Transform compose(Matrix ctm, Matrix trm) {
        if (trm == null) {
            return Transform.IDENTITY;
        }
        Matrix safeCtm = ctm == null ? Matrix.Identity() : ctm;
        java.awt.geom.AffineTransform transform =
                new java.awt.geom.AffineTransform(safeCtm.a, safeCtm.b, safeCtm.c, safeCtm.d, safeCtm.e, safeCtm.f);
        transform.concatenate(new java.awt.geom.AffineTransform(trm.a, trm.b, trm.c, trm.d, trm.e, trm.f));
        transform.concatenate(new java.awt.geom.AffineTransform(1, 0, 0, -1, 0, 0));
        return new Transform((float) transform.getScaleX(), (float) transform.getShearY(),
                (float) transform.getShearX(), (float) transform.getScaleY(),
                (float) transform.getTranslateX(), (float) transform.getTranslateY());
    }

    private static String svgTransform(Transform transform) {
        Transform safeTransform = transform == null ? Transform.IDENTITY : transform;
        double epsilon = 1e-6;
        if (Math.abs(safeTransform.b()) < epsilon && Math.abs(safeTransform.c()) < epsilon) {
            if (Math.abs(safeTransform.a() - safeTransform.d()) < epsilon) {
                return "translate(" + num(safeTransform.e()) + " " + num(safeTransform.f()) + ") scale(" + num(safeTransform.a()) + ")";
            }
            return "translate(" + num(safeTransform.e()) + " " + num(safeTransform.f()) + ") scale("
                    + num(safeTransform.a()) + " " + num(safeTransform.d()) + ")";
        }
        return "matrix(" + num(safeTransform.a()) + " " + num(safeTransform.b()) + " " + num(safeTransform.c()) + " "
                + num(safeTransform.d()) + " " + num(safeTransform.e()) + " " + num(safeTransform.f()) + ")";
    }

    // ------------------------------------------------------------------
    // 轮廓路径
    // ------------------------------------------------------------------

    private static String toSvgPath(java.awt.Font font, int glyphId) {
        try {
            GlyphVector glyphVector = font.createGlyphVector(FONT_RENDER_CONTEXT, new int[]{glyphId});
            if (glyphVector == null || glyphVector.getNumGlyphs() <= 0) {
                return null;
            }
            Shape outline = glyphVector.getGlyphOutline(0);
            if (outline == null) {
                return null;
            }
            String d = toPathData(outline);
            return d.isEmpty() ? null : d;
        } catch (Throwable t) {
            // 字体文件与 PDF 不匹配 / gid 越界时 AWT 会报错：该字形回退为文本渲染
            log.error("[MuPDF-SVG] 提取字形轮廓失败（gid={}），该字符回退为文本渲染", glyphId, t);
            return null;
        }
    }

    private static String toPathData(Shape shape) {
        PathIterator iterator = shape.getPathIterator(null);
        double[] coords = new double[6];
        StringBuilder data = new StringBuilder(256);
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    data.append('M').append(num(coords[0])).append(' ').append(num(coords[1]));
                    break;
                case PathIterator.SEG_LINETO:
                    data.append('L').append(num(coords[0])).append(' ').append(num(coords[1]));
                    break;
                case PathIterator.SEG_QUADTO:
                    data.append('Q').append(num(coords[0])).append(' ').append(num(coords[1]))
                            .append(' ').append(num(coords[2])).append(' ').append(num(coords[3]));
                    break;
                case PathIterator.SEG_CUBICTO:
                    data.append('C').append(num(coords[0])).append(' ').append(num(coords[1]))
                            .append(' ').append(num(coords[2])).append(' ').append(num(coords[3]))
                            .append(' ').append(num(coords[4])).append(' ').append(num(coords[5]));
                    break;
                case PathIterator.SEG_CLOSE:
                    data.append('Z');
                    break;
                default:
                    break;
            }
            iterator.next();
        }
        return data.toString();
    }

    // ------------------------------------------------------------------
    // 字体加载
    // ------------------------------------------------------------------

    /**
     * 构建 {@code @font-face} 规则文本（含 base64 数据），结果按字体名缓存后跨页复用。
     *
     * @param fontName 字体名（与字体文件名一致）
     * @param webFont  字体数据
     * @return 可直接追加进 CSS 的规则文本（含结尾换行）
     */
    private static String buildFontFaceCss(String fontName, WebFont webFont) {
        return "@font-face{font-family:" + cssString(fontName)
                + ";src:url(data:" + webFont.mimeType() + ";base64,"
                + Base64.getEncoder().encodeToString(webFont.data())
                + ") format('" + webFont.format() + "');}\n";
    }

    private static void appendTextClass(StringBuilder css, String className, String fontName, String fallback) {
        css.append('.').append(className).append("{font-family:").append(cssString(fontName));
        if (fallback != null) {
            css.append(", ").append(fallback);
        }
        css.append(";}\n");
    }

    /**
     * 扫描字体目录，按“文件名（不含后缀）”索引 Web 字体。
     */
    private static Map<String, WebFont> loadWebFonts(Path fontDir) {
        Map<String, WebFont> fonts = new LinkedHashMap<>();
        if (fontDir == null || !Files.isDirectory(fontDir)) {
            return fonts;
        }
        List<Path> candidates;
        try (Stream<Path> files = Files.list(fontDir)) {
            candidates = files.toList();
        } catch (Throwable t) {
            log.error("[MuPDF-SVG] 扫描字体目录失败，全部字体将回退宋体: {}", fontDir, t);
            return fonts;
        }
        for (Path file : candidates) {
            try {
                String fileName = String.valueOf(file.getFileName());
                int dot = fileName.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                String mimeType = mimeType(extension);
                String format = cssFormat(extension);
                if (mimeType == null || format == null) {
                    continue;
                }
                long size = Files.size(file);
                if (size <= 0) {
                    continue;
                }
                if (size > MAX_FONT_FILE_BYTES) {
                    log.warn("[MuPDF-SVG] 字体文件 {} 大小 {} 字节超过上限 {}，已跳过", file, size, MAX_FONT_FILE_BYTES);
                    continue;
                }
                fonts.putIfAbsent(fileName.substring(0, dot),
                        new WebFont(Files.readAllBytes(file), mimeType, format));
            } catch (Throwable t) {
                // 单个字体文件不可读 / 无权限不影响其余字体
                log.error("[MuPDF-SVG] 读取 Web 字体失败（已跳过该文件）: {}", file, t);
            }
        }
        return fonts;
    }

    /**
     * 扫描字体目录，按“文件名（不含后缀）”索引 Type1 字体（.pfa/.pfb）。
     */
    private static Map<String, java.awt.Font> loadType1Fonts(Path fontDir) {
        Map<String, java.awt.Font> fonts = new LinkedHashMap<>();
        if (fontDir == null || !Files.isDirectory(fontDir)) {
            return fonts;
        }
        List<Path> candidates;
        try (Stream<Path> files = Files.list(fontDir)) {
            candidates = files.toList();
        } catch (Throwable t) {
            log.error("[MuPDF-SVG] 扫描字体目录失败，全部字体将回退宋体: {}", fontDir, t);
            return fonts;
        }
        for (Path file : candidates) {
            try {
                String fileName = String.valueOf(file.getFileName());
                int dot = fileName.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!"pfa".equals(extension) && !"pfb".equals(extension)) {
                    continue;
                }
                long size = Files.size(file);
                if (size <= 0 || size > MAX_FONT_FILE_BYTES) {
                    log.warn("[MuPDF-SVG] Type1 字体文件 {} 大小 {} 字节不可用（上限 {}），已跳过",
                            file, size, MAX_FONT_FILE_BYTES);
                    continue;
                }
                java.awt.Font type1Font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, file.toFile());
                fonts.putIfAbsent(fileName.substring(0, dot), type1Font);
            } catch (Throwable t) {
                log.error("[MuPDF-SVG] 加载 Type1 字体失败（已跳过该文件）: {}", file, t);
            }
        }
        return fonts;
    }

    /**
     * 字体目录缓存条目：目录指纹 + 字体表 + {@code @font-face} 与字形轮廓缓存。
     *
     * <p>同一字体目录在进程内只读取一次；字体字节对应的 base64 文本、字形轮廓 path 数据
     * 均按字体名缓存，跨页 / 跨批次复用，避免批量导出时重复编码同一份字体。</p>
     */
    private static final class FontTables {

        private final String fingerprint;
        private final Map<String, WebFont> webFonts;
        private final Map<String, java.awt.Font> type1Fonts;
        private final ConcurrentHashMap<String, String> fontFaceCssCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> outlineCache = new ConcurrentHashMap<>();

        private FontTables(String fingerprint, Map<String, WebFont> webFonts,
                           Map<String, java.awt.Font> type1Fonts) {
            this.fingerprint = fingerprint;
            this.webFonts = webFonts;
            this.type1Fonts = type1Fonts;
        }

        /**
         * 字体字节总量，用于判断该目录是否适合常驻缓存。
         *
         * @return 总字节数
         */
        private long totalFontBytes() {
            long total = 0L;
            for (WebFont font : webFonts.values()) {
                if (font.data() != null) {
                    total += font.data().length;
                }
            }
            return total;
        }

        private Map<String, WebFont> webFonts() {
            return webFonts;
        }

        private Map<String, java.awt.Font> type1Fonts() {
            return type1Fonts;
        }

        /**
         * 取字体名对应的 {@code @font-face} 规则（含 base64），首次调用时编码并缓存。
         *
         * @param fontName 字体名
         * @return 规则文本；该字体名不是 Web 字体时返回 null
         */
        private String fontFaceCss(String fontName) {
            String cached = fontFaceCssCache.get(fontName);
            if (cached != null) {
                return cached;
            }
            WebFont webFont = webFonts.get(fontName);
            if (webFont == null || webFont.data() == null || webFont.data().length == 0) {
                return null;
            }
            String built = buildFontFaceCss(fontName, webFont);
            fontFaceCssCache.put(fontName, built);
            return built;
        }

        /**
         * 取字形轮廓 path 数据（跨页复用）；提取失败不缓存，保持与逐页导出相同的降级行为。
         *
         * @param fontName 字体名
         * @param glyphId  MuPDF glyph id
         * @param font     Type1 字体句柄
         * @return SVG path 的 d 属性；无法提取时返回 null
         */
        private String outline(String fontName, int glyphId, java.awt.Font font) {
            String key = fontName + '|' + glyphId;
            String cached = outlineCache.get(key);
            if (cached != null) {
                return cached;
            }
            String pathData = toSvgPath(font, glyphId);
            if (pathData != null && outlineCache.size() < MAX_OUTLINE_CACHE) {
                outlineCache.put(key, pathData);
            }
            return pathData;
        }
    }

    /**
     * 取（或构建）字体目录缓存条目；目录内容变化（文件名 / 大小 / 修改时间）时自动重建。
     *
     * @param fontDir 字体目录（可为 null）
     * @return 字体表（永不为 null）
     */
    private static FontTables fontTables(Path fontDir) {
        String key = fontDir == null ? "<none>" : fontDir.toAbsolutePath().normalize().toString();
        String fingerprint = fingerprint(fontDir);
        if (fingerprint == null) {
            // 目录不可用：不复用缓存，避免把一次临时故障固化下来
            return new FontTables("unavailable", new LinkedHashMap<>(), new LinkedHashMap<>());
        }
        synchronized (FONT_TABLE_CACHE) {
            FontTables cached = FONT_TABLE_CACHE.get(key);
            if (cached != null && cached.fingerprint.equals(fingerprint)) {
                return cached;
            }
        }
        FontTables built = new FontTables(fingerprint, loadWebFonts(fontDir), loadType1Fonts(fontDir));
        long totalFontBytes = built.totalFontBytes();
        if (totalFontBytes > MAX_CACHED_FONT_BYTES) {
            log.warn("[MuPDF-SVG] 字体目录字体总量 {} 字节超过缓存上限 {}，该目录不做常驻缓存（仍可导出，仅退化为每次重新读取）: {}",
                    totalFontBytes, MAX_CACHED_FONT_BYTES, key);
            return built;
        }
        synchronized (FONT_TABLE_CACHE) {
            FONT_TABLE_CACHE.put(key, built);
            while (FONT_TABLE_CACHE.size() > FONT_TABLE_CACHE_MAX) {
                Iterator<String> eldest = FONT_TABLE_CACHE.keySet().iterator();
                if (!eldest.hasNext()) {
                    break;
                }
                eldest.next();
                eldest.remove();
            }
        }
        log.info("[MuPDF-SVG] 字体目录已加载并缓存: {} | Web 字体 {} 个 | Type1 字体 {} 个",
                key, built.webFonts.size(), built.type1Fonts.size());
        return built;
    }

    /**
     * 计算字体目录指纹（文件名 + 大小 + 修改时间），用于判断缓存是否仍然有效。
     *
     * @param fontDir 字体目录（可为 null）
     * @return 指纹字符串；目录不存在或扫描失败时返回 null
     */
    private static String fingerprint(Path fontDir) {
        if (fontDir == null) {
            return "-";
        }
        if (!Files.isDirectory(fontDir)) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        try (Stream<Path> files = Files.list(fontDir)) {
            for (Path file : files.toList()) {
                entries.add(file.getFileName() + "/" + Files.size(file) + "/"
                            + Files.getLastModifiedTime(file).toMillis());
            }
        } catch (Throwable t) {
            log.error("[MuPDF-SVG] 扫描字体目录失败，全部字体将回退宋体: {}", fontDir, t);
            return null;
        }
        Collections.sort(entries);
        return String.join("|", entries);
    }

    private static String mimeType(String extension) {
        switch (extension) {
            case "woff2":
                return "font/woff2";
            case "woff":
                return "font/woff";
            case "otf":
                return "font/otf";
            case "ttf":
                return "font/ttf";
            default:
                return null;
        }
    }

    private static String cssFormat(String extension) {
        switch (extension) {
            case "woff2":
                return "woff2";
            case "woff":
                return "woff";
            case "otf":
                return "opentype";
            case "ttf":
                return "truetype";
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------
    // 转义与格式化
    // ------------------------------------------------------------------

    private static String cssString(String value) {
        String safeValue = value == null ? "" : value;
        return "'" + safeValue.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * XML 1.0 转义：除常规实体转义外，额外剔除非法控制字符（否则整份 SVG 无法被 XML 解析器打开）。
     */
    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (isValidXmlChar(c)) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static boolean isValidXmlChar(char c) {
        return c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF);
    }

    private static String num(double value) {
        // NaN / Infinity 会产出非法 SVG 坐标（如 "NaN"），统一归零
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (value == 0.0 || value == -0.0) {
            return "0";
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        int len = text.length();
        if (text.indexOf('.') >= 0) {
            while (len > 0 && text.charAt(len - 1) == '0') {
                len--;
            }
            if (len > 0 && text.charAt(len - 1) == '.') {
                len--;
            }
            text = text.substring(0, len);
        }
        return text.isEmpty() || "-0".equals(text) ? "0" : text;
    }

    private static double finiteOrDefault(double value, double defaultValue) {
        return Double.isFinite(value) && value > 0 ? value : defaultValue;
    }

    /**
     * Web 字体资源。
     *
     * @param data     字体文件字节
     * @param mimeType data URI 的 MIME 类型
     * @param format   CSS {@code format()} 取值
     */
    private record WebFont(byte[] data, String mimeType, String format) {
    }
}
