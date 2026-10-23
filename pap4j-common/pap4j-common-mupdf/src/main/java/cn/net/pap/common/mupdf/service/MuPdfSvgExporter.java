package cn.net.pap.common.mupdf.service;

import com.artifex.mupdf.fitz.ColorSpace;
import com.artifex.mupdf.fitz.DefaultColorSpaces;
import com.artifex.mupdf.fitz.Device;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Image;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Page;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private MuPdfSvgExporter() {
    }

    /**
     * SVG 导出结果。
     *
     * @param svgFile       生成的 SVG 文件
     * @param embeddedFonts 以 {@code @font-face} 内嵌的 Web 字体名
     * @param outlineFonts  以矢量轮廓（path）还原的 Type1 字体名
     * @param fallbackFonts 回退到宋体的字体名
     */
    public record ExportResult(Path svgFile, List<String> embeddedFonts,
                               List<String> outlineFonts, List<String> fallbackFonts) {
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
        if (svgFile == null) {
            throw new IllegalArgumentException("SVG 输出文件不能为空");
        }

        MuPdfService service = new MuPdfService();
        try {
            MuPdfService.PageResult page = service.extractCoordinates(pdfPath, pageNum).pages().get(0);
            List<MuPdfService.CharResult> chars = flatten(page);
            Set<String> fontNames = collectFontNames(chars);

            Map<String, WebFont> webFonts = loadWebFonts(fontDir);
            Map<String, java.awt.Font> type1Fonts = loadType1Fonts(fontDir);
            GlyphWalk glyphWalk = matchGlyphs(pdfPath, pageNum, chars);

            List<String> embeddedFonts = new ArrayList<>();
            List<String> outlineFonts = new ArrayList<>();
            List<String> fallbackFonts = new ArrayList<>();
            Map<String, String> classByFont = new LinkedHashMap<>();
            StringBuilder css = new StringBuilder(2048);

            for (String fontName : fontNames) {
                if (!glyphWalk.glyphs().isEmpty() && type1Fonts.containsKey(fontName)) {
                    outlineFonts.add(fontName);
                } else if (webFonts.containsKey(fontName)) {
                    embeddedFonts.add(fontName);
                    appendFontFace(css, fontName, webFonts.get(fontName));
                    classByFont.put(fontName, "f" + classByFont.size());
                    appendTextClass(css, classByFont.get(fontName), fontName, null);
                } else {
                    fallbackFonts.add(fontName);
                    classByFont.put(fontName, "f" + classByFont.size());
                    appendTextClass(css, classByFont.get(fontName), fontName, FALLBACK_FAMILY);
                }
            }

            String svg = buildSvg(page, chars, glyphWalk, type1Fonts, classByFont, css);
            Path parent = svgFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(svgFile, svg, StandardCharsets.UTF_8);

            log.info("[MuPDF-SVG] 导出完成: {} 第 {} 页 | 轮廓字体 {} 个 | 内嵌字体 {} 个 | 回退宋体 {} 个 | {}",
                    pdfPath, pageNum + 1, outlineFonts.size(), embeddedFonts.size(),
                    fallbackFonts.size(), svgFile.toAbsolutePath());
            return new ExportResult(svgFile, embeddedFonts, outlineFonts, fallbackFonts);
        } finally {
            service.close();
        }
    }

    // ------------------------------------------------------------------
    // SVG 构建
    // ------------------------------------------------------------------

    private static String buildSvg(MuPdfService.PageResult page, List<MuPdfService.CharResult> chars,
                                   GlyphWalk glyphWalk, Map<String, java.awt.Font> type1Fonts,
                                   Map<String, String> classByFont, StringBuilder css) {
        double width = page.width() > 0 ? page.width() : 595.0;
        double height = page.height() > 0 ? page.height() : 842.0;

        StringBuilder defs = new StringBuilder(8192);
        StringBuilder body = new StringBuilder(16384);
        Map<String, String> outlinedGlyphs = new LinkedHashMap<>();
        if (!css.isEmpty()) {
            defs.append("<style type=\"text/css\"><![CDATA[\n").append(css).append("]]></style>\n");
        }

        for (int index = 0; index < chars.size(); index++) {
            MuPdfService.CharResult ch = chars.get(index);
            if (ch == null || ch.c() == null || ch.c().isEmpty()) {
                continue;
            }
            GlyphRef glyph = glyphWalk.glyphs().get(index);
            java.awt.Font type1Font = ch.font() == null ? null : type1Fonts.get(ch.font());
            if (glyph != null && glyph.gid() >= 0 && type1Font != null) {
                String key = ch.font() + '|' + glyph.gid();
                String pathId = outlinedGlyphs.get(key);
                if (pathId == null) {
                    pathId = "g" + outlinedGlyphs.size();
                    outlinedGlyphs.put(key, pathId);
                    defs.append("<path id=\"").append(pathId).append("\" d=\"")
                            .append(toSvgPath(type1Font, glyph.gid())).append("\"/>\n");
                }
                appendGlyphUse(body, pathId, glyph, glyphWalk.pageTransform());
            } else if (glyph != null) {
                // 非 Type1 但已对齐到 glyph：同样用完整 trm，保证拉伸/旋转/剪切一致
                appendTextWithMatrix(body, ch, classByFont, glyph, glyphWalk.pageTransform());
            } else {
                appendText(body, ch, classByFont);
            }
        }

        StringBuilder svg = new StringBuilder(defs.length() + body.length() + 1024);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"")
                .append(" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n")
                .append("  <defs>\n").append(defs).append("  </defs>\n")
                .append("  <rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
                .append(body)
                .append("</svg>\n");
        return svg.toString();
    }

    private static void appendGlyphUse(StringBuilder body, String pathId, GlyphRef glyph, Transform pageTransform) {
        Transform display = compose(pageTransform, glyph.trm());
        body.append("  <use xlink:href=\"#").append(pathId).append('"')
                .append(" transform=\"").append(svgTransform(display)).append('"')
                .append(" fill=\"#000000\"/>\n");
    }

    private static void appendTextWithMatrix(StringBuilder body, MuPdfService.CharResult ch,
                                             Map<String, String> classByFont, GlyphRef glyph,
                                             Transform pageTransform) {
        String className = classByFont.getOrDefault(ch.font(), "f0");
        Transform display = compose(pageTransform, glyph.trm());
        body.append("  <text x=\"0\" y=\"0\" font-size=\"1\" class=\"").append(className).append('"')
                .append(" xml:space=\"preserve\"")
                .append(" transform=\"").append(svgTransform(display)).append('"')
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
        for (MuPdfService.BlockResult block : page.blocks()) {
            if (block.lines() == null) {
                continue;
            }
            for (MuPdfService.LineResult line : block.lines()) {
                if (line.chars() == null) {
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
        for (MuPdfService.CharResult ch : chars) {
            if (ch.font() != null && !ch.font().isEmpty()) {
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
     * <p>两次遍历的顺序一致时（本类会同时校验 Unicode 与字体名），下标即可作为字符索引；
     * 任一处不一致就整体降级为文本渲染，避免画错字形。</p>
     */
    private static GlyphWalk matchGlyphs(String pdfPath, int pageNum,
                                          List<MuPdfService.CharResult> chars) {
        if (chars.isEmpty()) {
            return new GlyphWalk(Map.of(), Transform.IDENTITY);
        }
        RawWalk walk = collectGlyphs(pdfPath, pageNum);
        List<GlyphRef> glyphs = walk.glyphs();
        if (glyphs.size() != chars.size()) {
            log.warn("[MuPDF-SVG] 字形数与字符数不一致（{} vs {}），回退文本渲染", glyphs.size(), chars.size());
            return new GlyphWalk(Map.of(), walk.pageTransform());
        }
        Map<Integer, GlyphRef> result = new LinkedHashMap<>();
        for (int index = 0; index < chars.size(); index++) {
            MuPdfService.CharResult ch = chars.get(index);
            GlyphRef glyph = glyphs.get(index);
            int codePoint = ch.c().codePointAt(0);
            if (normalizeSpace(glyph.unicode()) != normalizeSpace(codePoint)
                    || !sameText(glyph.fontName(), ch.font())) {
                log.warn("[MuPDF-SVG] 第 {} 个字符字形对不上（U+{} / {} vs U+{} / {}），回退文本渲染",
                        index, Integer.toHexString(glyph.unicode()), glyph.fontName(),
                        Integer.toHexString(codePoint), ch.font());
                return new GlyphWalk(Map.of(), walk.pageTransform());
            }
            result.put(index, glyph);
        }
        return new GlyphWalk(result, walk.pageTransform());
    }

    private static RawWalk collectGlyphs(String pdfPath, int pageNum) {
        List<GlyphRef> glyphs = new ArrayList<>();
        Transform pageTransform = Transform.IDENTITY;
        Document doc = Document.openDocument(pdfPath);
        try {
            if (!(doc instanceof PDFDocument pdf)) {
                return new RawWalk(glyphs, pageTransform);
            }
            MuPdfFontExtractor.FontNameResolver resolver = MuPdfFontExtractor.pageFontNameResolver(pdf, pageNum);
            Page page = pdf.loadPage(pageNum);
            try {
                if (page instanceof PDFPage pdfPage) {
                    Matrix matrix = pdfPage.getTransform();
                    pageTransform = new Transform(matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f);
                }
                page.run(new GlyphCollectorDevice(resolver, glyphs), Matrix.Identity());
            } finally {
                page.destroy();
            }
        } finally {
            doc.destroy();
        }
        return new RawWalk(glyphs, pageTransform);
    }

    /**
     * 统一空白字符：结构化文本会把 U+3000（全角空格）归一成 U+0020，比对 glyph 时需对齐。
     */
    private static int normalizeSpace(int codePoint) {
        return codePoint == 0x3000 || codePoint == 0x00A0 ? 0x20 : codePoint;
    }

    private static boolean sameText(String left, String right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    /**
     * 只关心 {@link #fillText} 的设备：按内容顺序收集 glyph id。
     */
    private static final class GlyphCollectorDevice extends Device {

        private final MuPdfFontExtractor.FontNameResolver resolver;
        private final List<GlyphRef> glyphs;

        private GlyphCollectorDevice(MuPdfFontExtractor.FontNameResolver resolver, List<GlyphRef> glyphs) {
            this.resolver = resolver;
            this.glyphs = glyphs;
        }

        @Override
        public void close() {
        }

        @Override
        public void fillPath(com.artifex.mupdf.fitz.Path path, boolean evenOdd, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
        }

        @Override
        public void strokePath(com.artifex.mupdf.fitz.Path path, StrokeState stroke, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
        }

        @Override
        public void clipPath(com.artifex.mupdf.fitz.Path path, boolean evenOdd, Matrix ctm) {
        }

        @Override
        public void clipStrokePath(com.artifex.mupdf.fitz.Path path, StrokeState stroke, Matrix ctm) {
        }

        @Override
        public void fillText(Text text, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
            text.walk((font, trm, glyph, unicode, wmode) -> {
                String readable = resolver.resolve(font);
                glyphs.add(new GlyphRef(readable != null ? readable : font.getName(), unicode, glyph,
                        new Transform(trm.a, trm.b, trm.c, trm.d, trm.e, trm.f)));
            });
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
        }

        @Override
        public void fillImageMask(Image img, Matrix ctm, ColorSpace cs, float[] color, float alpha, int cp) {
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

    /**
     * MuPDF 渲染期字形引用。
     *
     * @param fontName 可读字体名
     * @param unicode  字符 Unicode
     * @param gid      MuPDF glyph id
     * @param trm      MuPDF 文本渲染矩阵（含字形原点、字号、拉伸/旋转）
     */
    private record GlyphRef(String fontName, int unicode, int gid, Transform trm) {
    }

    /**
     * 字形对齐结果。
     *
     * @param glyphs        字符索引 ➔ MuPDF 字形引用
     * @param pageTransform 页面变换矩阵（PDF 用户空间 ➔ 显示空间）
     */
    private record GlyphWalk(Map<Integer, GlyphRef> glyphs, Transform pageTransform) {
    }

    /**
     * 按内容顺序采集到的字形列表。
     *
     * @param glyphs        MuPDF 字形引用（内容顺序）
     * @param pageTransform 页面变换矩阵（PDF 用户空间 ➔ 显示空间）
     */
    private record RawWalk(List<GlyphRef> glyphs, Transform pageTransform) {
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
     * 组合出“AWT 字形坐标（em、y 向下） ➔ 页面显示坐标”的矩阵：
     * {@code page * trm * flipY}。
     */
    private static Transform compose(Transform page, Transform trm) {
        java.awt.geom.AffineTransform transform =
                new java.awt.geom.AffineTransform(page.a(), page.b(), page.c(), page.d(), page.e(), page.f());
        transform.concatenate(new java.awt.geom.AffineTransform(trm.a(), trm.b(), trm.c(), trm.d(), trm.e(), trm.f()));
        transform.concatenate(new java.awt.geom.AffineTransform(1, 0, 0, -1, 0, 0));
        return new Transform((float) transform.getScaleX(), (float) transform.getShearY(),
                (float) transform.getShearX(), (float) transform.getScaleY(),
                (float) transform.getTranslateX(), (float) transform.getTranslateY());
    }

    private static String svgTransform(Transform transform) {
        double epsilon = 1e-6;
        if (Math.abs(transform.b()) < epsilon && Math.abs(transform.c()) < epsilon) {
            if (Math.abs(transform.a() - transform.d()) < epsilon) {
                return "translate(" + num(transform.e()) + " " + num(transform.f()) + ") scale(" + num(transform.a()) + ")";
            }
            return "translate(" + num(transform.e()) + " " + num(transform.f()) + ") scale("
                    + num(transform.a()) + " " + num(transform.d()) + ")";
        }
        return "matrix(" + num(transform.a()) + " " + num(transform.b()) + " " + num(transform.c()) + " "
                + num(transform.d()) + " " + num(transform.e()) + " " + num(transform.f()) + ")";
    }

    // ------------------------------------------------------------------
    // 轮廓路径
    // ------------------------------------------------------------------

    private static String toSvgPath(java.awt.Font font, int glyphId) {
        GlyphVector glyphVector = font.createGlyphVector(FONT_RENDER_CONTEXT, new int[]{glyphId});
        Shape outline = glyphVector.getGlyphOutline(0);
        return toPathData(outline);
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

    private static void appendFontFace(StringBuilder css, String fontName, WebFont webFont) {
        css.append("@font-face{font-family:").append(cssString(fontName))
                .append(";src:url(data:").append(webFont.mimeType()).append(";base64,")
                .append(Base64.getEncoder().encodeToString(webFont.data()))
                .append(") format('").append(webFont.format()).append("');}")
                .append('\n');
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
    private static Map<String, WebFont> loadWebFonts(Path fontDir) throws IOException {
        Map<String, WebFont> fonts = new LinkedHashMap<>();
        if (fontDir == null || !Files.isDirectory(fontDir)) {
            return fonts;
        }
        try (Stream<Path> files = Files.list(fontDir)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
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
                fonts.putIfAbsent(fileName.substring(0, dot),
                        new WebFont(Files.readAllBytes(file), mimeType, format));
            }
        }
        return fonts;
    }

    /**
     * 扫描字体目录，按“文件名（不含后缀）”索引 Type1 字体（.pfa/.pfb）。
     */
    private static Map<String, java.awt.Font> loadType1Fonts(Path fontDir) throws IOException {
        Map<String, java.awt.Font> fonts = new LinkedHashMap<>();
        if (fontDir == null || !Files.isDirectory(fontDir)) {
            return fonts;
        }
        try (Stream<Path> files = Files.list(fontDir)) {
            for (Path file : files.toList()) {
                String fileName = file.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!"pfa".equals(extension) && !"pfb".equals(extension)) {
                    continue;
                }
                try {
                    java.awt.Font type1Font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, file.toFile());
                    fonts.putIfAbsent(fileName.substring(0, dot), type1Font);
                } catch (Throwable t) {
                    log.error("[MuPDF-SVG] 加载 Type1 字体失败: {}", file, t);
                }
            }
        }
        return fonts;
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
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String num(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.isEmpty() || "-0".equals(text) ? "0" : text;
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
