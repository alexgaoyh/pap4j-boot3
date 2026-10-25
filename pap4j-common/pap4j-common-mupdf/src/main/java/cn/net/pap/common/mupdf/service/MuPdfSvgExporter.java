package cn.net.pap.common.mupdf.service;

import com.artifex.mupdf.fitz.Buffer;
import com.artifex.mupdf.fitz.ColorSpace;
import com.artifex.mupdf.fitz.DefaultColorSpaces;
import com.artifex.mupdf.fitz.Device;
import com.artifex.mupdf.fitz.Document;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
            PageGraphics graphics = collectPageGraphics(pdfPath, pageNum);
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
                    appendFontFace(css, fontName, webFonts.get(fontName));
                    classByFont.put(fontName, "f" + classByFont.size());
                    appendTextClass(css, classByFont.get(fontName), fontName, null);
                } else {
                    fallbackFonts.add(fontName);
                    classByFont.put(fontName, "f" + classByFont.size());
                    appendTextClass(css, classByFont.get(fontName), fontName, FALLBACK_FAMILY);
                }
            }

            String svg = buildSvg(page, chars, glyphs, graphics.paths(), graphics.images(),
                    type1Fonts, classByFont, css);
            Path parent = svgFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(svgFile, svg, StandardCharsets.UTF_8);

            log.info("[MuPDF-SVG] 导出完成: {} 第 {} 页 | 矢量路径 {} 条 | 内嵌图片 {} 张 | 轮廓字体 {} 个 | 内嵌字体 {} 个 | 回退宋体 {} 个 | {}",
                    pdfPath, pageNum + 1, graphics.paths().size(), graphics.images().size(),
                    outlineFonts.size(), embeddedFonts.size(), fallbackFonts.size(), svgFile.toAbsolutePath());
            return new ExportResult(svgFile, embeddedFonts, outlineFonts, fallbackFonts,
                    graphics.paths().size(), graphics.images().size());
        } finally {
            service.close();
        }
    }

    // ------------------------------------------------------------------
    // SVG 构建
    // ------------------------------------------------------------------

    private static String buildSvg(MuPdfService.PageResult page, List<MuPdfService.CharResult> chars,
                                   Map<Integer, GlyphRef> glyphs, List<SvgPath> paths, List<SvgImage> images,
                                   Map<String, java.awt.Font> type1Fonts,
                                   Map<String, String> classByFont, StringBuilder css) {
        double width = page.width() > 0 ? page.width() : 595.0;
        double height = page.height() > 0 ? page.height() : 842.0;

        StringBuilder defs = new StringBuilder(8192);
        StringBuilder body = new StringBuilder(16384);
        Map<String, String> outlinedGlyphs = new LinkedHashMap<>();
        if (!css.isEmpty()) {
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
        for (int index = 0; index < chars.size(); index++) {
            MuPdfService.CharResult ch = chars.get(index);
            if (ch == null || ch.c() == null || ch.c().isEmpty()) {
                continue;
            }
            GlyphRef glyph = glyphs.get(index);
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
                appendGlyphUse(body, pathId, glyph);
            } else if (glyph != null) {
                // 非 Type1 但已对齐到 glyph：同样用完整 trm，保证拉伸/旋转/剪切一致
                appendTextWithMatrix(body, ch, classByFont, glyph);
            } else {
                appendText(body, ch, classByFont);
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
     * <p>遇到由 StructuredText 合成的空白字符（PDF 流中无对应 glyph）时自动跳过；
     * 对可见字符校验 Unicode 与字体名，支持小范围滑动窗口同步，避免单字符失配导致整页回退。</p>
     */
    private static Map<Integer, GlyphRef> matchGlyphs(List<GlyphRef> glyphs,
                                                     List<MuPdfService.CharResult> chars,
                                                     int pageNum) {
        if (chars.isEmpty() || glyphs.isEmpty()) {
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

            // 1. 若当前字符为空白，但底层 glyph 不是空白，说明是 StructuredText 合成的词间距空格，直接跳过
            if (isWhitespace(charCodePoint)) {
                GlyphRef currentGlyph = glyphs.get(glyphIndex);
                if (!isWhitespace(currentGlyph.unicode())) {
                    continue;
                }
            }

            GlyphRef glyph = glyphs.get(glyphIndex);

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
                    result.put(charIndex, glyphs.get(glyphIndex));
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
            if (normalizeSpace(glyphs.get(glyphIndex + offset).unicode()) == target) {
                return offset;
            }
        }
        return -1;
    }

    private static PageGraphics collectPageGraphics(String pdfPath, int pageNum) {
        List<GlyphRef> glyphs = new ArrayList<>();
        List<SvgPath> paths = new ArrayList<>();
        List<SvgImage> images = new ArrayList<>();

        Document doc = Document.openDocument(pdfPath);
        try {
            if (!(doc instanceof PDFDocument pdf)) {
                return new PageGraphics(glyphs, paths, images);
            }
            MuPdfFontExtractor.FontNameResolver resolver = MuPdfFontExtractor.pageFontNameResolver(pdf, pageNum);
            Page page = pdf.loadPage(pageNum);
            try {
                GlyphCollectorDevice device = new GlyphCollectorDevice(resolver, glyphs, paths, images);
                try {
                    page.run(device, Matrix.Identity());
                } finally {
                    device.destroy();
                }
            } finally {
                page.destroy();
            }
        } finally {
            doc.destroy();
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
            if (path == null) {
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
            if (path == null) {
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
            text.walk((font, trm, glyph, unicode, wmode) -> {
                String readable = resolver.resolve(font);
                Transform display = compose(ctm, trm);
                glyphs.add(new GlyphRef(readable != null ? readable : font.getName(), unicode, glyph, display));
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
            if (img == null) {
                return;
            }
            Pixmap pix = null;
            Buffer buf = null;
            try {
                pix = img.toPixmap();
                if (pix != null) {
                    buf = pix.asPNG();
                    if (buf != null) {
                        byte[] bytes = buf.asByteArray();
                        Transform imgTr = composeImageTransform(ctm);
                        images.add(new SvgImage(imgTr, bytes, img.getWidth(), img.getHeight()));
                    }
                }
            } catch (Throwable t) {
                log.warn("[MuPDF-SVG] 提取页面内嵌图片失败", t);
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
        if (path == null) {
            return "";
        }
        StringBuilder d = new StringBuilder(64);
        path.walk(new com.artifex.mupdf.fitz.PathWalker() {
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
        });
        return d.toString();
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
            sb.append("  <image x=\"0\" y=\"0\" width=\"1\" height=\"1\"")
                    .append(" transform=\"").append(svgTransform(transform)).append('"')
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
        java.awt.geom.AffineTransform transform =
                new java.awt.geom.AffineTransform(ctm.a, ctm.b, ctm.c, ctm.d, ctm.e, ctm.f);
        transform.concatenate(new java.awt.geom.AffineTransform(trm.a, trm.b, trm.c, trm.d, trm.e, trm.f));
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
