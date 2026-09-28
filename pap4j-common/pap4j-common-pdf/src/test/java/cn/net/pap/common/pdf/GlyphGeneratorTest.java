package cn.net.pap.common.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字形基准图生成测试。
 *
 * <p>在 A4@300DPI 画布上按网格逐字渲染，输出字形基准图，并记录每个字墨迹的
 * 真实外接矩形（Visual Bounds Ground Truth），供后续排版/裁切算法校验。</p>
 */
public class GlyphGeneratorTest {

    private static final Logger log = LoggerFactory.getLogger(GlyphGeneratorTest.class);

    /**
     * 目标分辨率（DPI）。
     */
    private static final int DPI = 300;

    /**
     * 每英寸对应的毫米数。
     */
    private static final double MM_PER_INCH = 25.4;

    /**
     * A4 纸张宽度（毫米）。
     */
    private static final double A4_WIDTH_MM = 210.0;

    /**
     * A4 纸张高度（毫米）。
     */
    private static final double A4_HEIGHT_MM = 297.0;

    /**
     * 画布宽度（像素）。
     */
    private static final int PAGE_W = (int) Math.round(A4_WIDTH_MM / MM_PER_INCH * DPI);   // 2480

    /**
     * 画布高度（像素）。
     */
    private static final int PAGE_H = (int) Math.round(A4_HEIGHT_MM / MM_PER_INCH * DPI);  // 3508

    /**
     * 网格列数（每行几个字）。
     */
    private static final int COLS = 8;

    /**
     * 单元格边长（正方形），按页宽均分，正好铺满一行。
     */
    private static final int CELL = PAGE_W / COLS;  // 310

    /**
     * 网格最大行数（超出部分不画）。
     */
    private static final int MAX_ROWS = PAGE_H / CELL;  // 11

    /**
     * 字形与单元格之间的留白比例（0.05 = 5%）。
     */
    private static final double PADDING_RATIO = 0.05;

    /**
     * 字体名，可被系统属性 -Dtest.font= 覆盖。
     */
    private static final String FONT_NAME = System.getProperty("test.font", "SimSun");

    /**
     * 基准字号（仅用于构造 Font，真实绘制时按墨迹框缩放）。
     */
    private static final int BASE_FONT_SIZE = 100;

    /**
     * 墨迹标注矩形的颜色。
     */
    private static final Color INK_BOX_COLOR = Color.RED;

    /**
     * 墨迹标注矩形的描边宽度。
     */
    private static final float INK_BOX_STROKE_WIDTH = 2.0f;

    /**
     * JPEG 输出文件后缀。
     */
    private static final String JPEG_SUFFIX = ".jpg";

    /**
     * ImageIO 查找 JPEG writer 时使用的后缀标识。
     */
    private static final String JPEG_FORMAT = "jpeg";

    /**
     * JPEG 原生元数据树格式名。
     */
    private static final String JPEG_METADATA_FORMAT = "javax_imageio_jpeg_image_1.0";

    /**
     * JFIF APP0 元数据节点名。
     */
    private static final String JFIF_NODE = "app0JFIF";

    /**
     * JFIF 水平密度属性名。
     */
    private static final String JFIF_X_DENSITY = "Xdensity";

    /**
     * JFIF 垂直密度属性名。
     */
    private static final String JFIF_Y_DENSITY = "Ydensity";

    /**
     * JFIF 密度单位属性名。
     */
    private static final String JFIF_RES_UNITS = "resUnits";

    /**
     * JFIF 密度单位：1 表示 dots per inch。
     */
    private static final String JFIF_RES_UNITS_DPI = "1";

    /**
     * 字形基准图临时文件前缀。
     */
    private static final String GLYPH_SHEET_PREFIX = "glyph_sheet";

    /**
     * 墨迹标注图临时文件前缀。
     */
    private static final String INK_OVERLAY_PREFIX = "glyph_sheet_ink_overlay";

    /**
     * 全部中文，按形态分类：扁 / 窄 / 宽 / 方正 / 复杂 / 简单 / 各种结构。
     */
    private static final String[] CHARS = {
            // —— 扁 ——
            "", "二", "三", "曰", "旦", "亘", "互", "",
            // —— 窄 ——
            "", "亅", "亻", "彳", "川", "州", "卜", "",
            // —— 方正 ——
            "", "日", "目", "田", "国", "回", "囚", "",
            // —— 宽 ——
            "", "四", "西", "而", "雨", "血", "甘", "",
            // —— 舒展（撇捺）——
            "", "水", "火", "大", "人", "入", "八", "",
            // —— 复杂 ——
            "", "疆", "鬱", "龘", "鑫", "羴", "犇", "",
            // —— 简单 ——
            "", "乙", "七", "乃", "九", "了", "力", "",
            // —— 混合结构 ——
            "", "申", "甲", "由", "电", "男", "史", "",
            // —— 上下结构 ——
            "", "昌", "品", "晶", "圭", "炎", "多", "",
            // —— 左右结构 ——
            "", "林", "朋", "双", "竹", "羽", "弱", "",
            // —— 补充 ——
            "", "石", "土", "木", "禾", "米", "舟", ""
    };

    @Test
    @DisplayName("生成 A4@300DPI 字形基准图 glyph_sheet.jpg，每格一字，填满单元格")
    void generateGlyphSheet() throws IOException {
        GlyphSheet sheet = buildGlyphSheet();

        assertEquals(PAGE_W, sheet.image().getWidth());
        assertEquals(PAGE_H, sheet.image().getHeight());

        Path out = File.createTempFile(GLYPH_SHEET_PREFIX, JPEG_SUFFIX).toPath();
        writeJpegWithDpi(sheet.image(), out, DPI);

        assertTrue(Files.exists(out), "输出文件应存在");
        assertTrue(Files.size(out) > 0, "输出文件不应为空");

        log.info("[GlyphSheet-Generate] written: {}, size={}x{} (A4@{}dpi), cell={}, cols={}, rows={}, glyphs={}, font={}",
                out.toAbsolutePath(), PAGE_W, PAGE_H, DPI, CELL, COLS, sheet.rows(), sheet.glyphCount(), FONT_NAME);
        log.info("[GlyphSheet-Generate] 各字符墨迹矩形区域 (Visual Bounds Ground Truth):");
        for (GlyphInkBox box : sheet.inkBoxes()) {
            log.info("[GlyphSheet-Generate] {}", box);
        }
    }

    @Test
    @DisplayName("在字形基准图上用红框标出墨迹矩形，输出 glyph_sheet_ink_overlay.jpg")
    void generateGlyphSheetWithInkBoxOverlay() throws IOException {
        GlyphSheet sheet = buildGlyphSheet();

        Graphics2D overlay = sheet.image().createGraphics();
        try {
            overlay.setColor(INK_BOX_COLOR);
            overlay.setStroke(new BasicStroke(INK_BOX_STROKE_WIDTH));
            for (GlyphInkBox box : sheet.inkBoxes()) {
                overlay.draw(new Rectangle2D.Double(box.x(), box.y(), box.width(), box.height()));
            }
        } finally {
            overlay.dispose();
        }

        Path out = File.createTempFile(INK_OVERLAY_PREFIX, JPEG_SUFFIX).toPath();
        writeJpegWithDpi(sheet.image(), out, DPI);

        assertTrue(Files.exists(out), "墨迹标注图应存在");
        assertTrue(Files.size(out) > 0, "墨迹标注图不应为空");

        log.info("[GlyphSheet-InkOverlay] written: {}, boxes={}, color={}, stroke={}",
                out.toAbsolutePath(), sheet.inkBoxes().size(), INK_BOX_COLOR, INK_BOX_STROKE_WIDTH);
    }

    /**
     * 构建字形基准图，返回画布、逐字墨迹框、总字数与网格行数。
     *
     * @return 字形基准图渲染结果
     */
    private static GlyphSheet buildGlyphSheet() {
        int capacity = MAX_ROWS * COLS;
        List<String> chars = new ArrayList<>(capacity);
        for (String c : CHARS) {
            if (chars.size() >= capacity) {
                break;
            }
            chars.add(c);
        }
        int rows = (int) Math.ceil(chars.size() / (double) COLS);

        BufferedImage img = new BufferedImage(PAGE_W, PAGE_H, BufferedImage.TYPE_INT_RGB);
        List<GlyphInkBox> inkBoxes = new ArrayList<>(chars.size());
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, PAGE_W, PAGE_H);
            applyRenderingHints(g);

            Font baseFont = new Font(FONT_NAME, Font.PLAIN, BASE_FONT_SIZE);
            FontRenderContext frc = g.getFontRenderContext();

            for (int i = 0; i < chars.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int cx = col * CELL;
                int cy = row * CELL;

                Rectangle2D box = drawGlyphFilled(g, baseFont, frc, chars.get(i), cx, cy, CELL);
                if (box != null) {
                    inkBoxes.add(new GlyphInkBox(
                            i, chars.get(i), col, row,
                            box.getX(), box.getY(), box.getWidth(), box.getHeight()
                    ));
                }
            }
        } finally {
            g.dispose();
        }
        return new GlyphSheet(img, inkBoxes, chars.size(), rows);
    }

    /**
     * 开启高质量抗锯齿渲染提示。
     *
     * @param g 目标画笔
     */
    private static void applyRenderingHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * 把单个字符缩放到填满单元格（保留 padding）。
     * 用 GlyphVector 的墨迹框（visual bounds）来计算缩放，而不是 advance。
     *
     * @param g        目标画笔
     * @param baseFont 基准字体
     * @param frc      字体渲染上下文
     * @param ch       待绘制字符
     * @param cellX    单元格左上角 X 坐标
     * @param cellY    单元格左上角 Y 坐标
     * @param cellSize 单元格边长
     * @return 缩放后墨迹的绝对外接矩形；字符无笔画时返回 null
     */
    private static Rectangle2D drawGlyphFilled(Graphics2D g, Font baseFont, FontRenderContext frc,
                                               String ch, int cellX, int cellY, int cellSize) {
        GlyphVector gv = baseFont.createGlyphVector(frc, ch);

        // 获取字符在基准字号下的纯墨迹外接矩形（Visual Bounds），与 logicalBounds（排版字符格子）不同，它只紧贴实际画出的笔画边缘，排除了行距与字距空白
        Rectangle2D ink = gv.getVisualBounds();

        // 字符为空白或无笔画（如空格、控制字符）时直接跳过
        if (ink.getWidth() <= 0 || ink.getHeight() <= 0) {
            return null;
        }

        // 计算留白后的单元格可用绘制尺寸
        int padding = (int) Math.round(cellSize * PADDING_RATIO);
        int targetW = cellSize - padding * 2;
        int targetH = cellSize - padding * 2;

        FittedInk fitted = fitInk(ink, targetW, targetH, cellX, cellY, cellSize);

        // 提取矢量轮廓并执行最终墨迹渲染
        Shape transformed = fitted.transform().createTransformedShape(gv.getOutline());
        g.setColor(Color.BLACK);
        g.fill(transformed);

        return fitted.bounds();
    }

    /**
     * 计算把基准字号墨迹等比缩放到目标尺寸并在单元格内居中所需的仿射变换与外接矩形。
     *
     * @param ink      基准字号下的墨迹外接矩形
     * @param targetW  目标可用宽度
     * @param targetH  目标可用高度
     * @param cellX    单元格左上角 X 坐标
     * @param cellY    单元格左上角 Y 坐标
     * @param cellSize 单元格边长
     * @return 变换与缩放后墨迹的绝对外接矩形
     */
    private static FittedInk fitInk(Rectangle2D ink, int targetW, int targetH,
                                    int cellX, int cellY, int cellSize) {
        // 等比满格缩放比：min(scaleX, scaleY)，扁字由宽决定缩放比，瘦高字由高决定缩放比
        double scale = Math.min(targetW / ink.getWidth(), targetH / ink.getHeight());
        double scaledInkW = ink.getWidth() * scale;
        double scaledInkH = ink.getHeight() * scale;

        // 使墨迹在单元格内绝对居中，得到整页画布中的绝对坐标 【墨迹真值 (Ground Truth)】
        double targetInkX = cellX + (cellSize - scaledInkW) / 2.0;
        double targetInkY = cellY + (cellSize - scaledInkH) / 2.0;

        // 仿射变换：先抵消基线偏移 inkX/inkY，再等比缩放，最后平移到目标位置
        AffineTransform at = new AffineTransform();
        at.translate(targetInkX, targetInkY);
        at.scale(scale, scale);
        at.translate(-ink.getX(), -ink.getY());

        return new FittedInk(at, new Rectangle2D.Double(targetInkX, targetInkY, scaledInkW, scaledInkH));
    }

    @Test
    @DisplayName("确认目标字体在当前系统可用")
    void fontShouldBeAvailable() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font[] fonts = ge.getAllFonts();

        boolean found = false;
        for (Font f : fonts) {
            if (f.getFontName().equalsIgnoreCase(FONT_NAME)
                || f.getFamily().equalsIgnoreCase(FONT_NAME)) {
                found = true;
                break;
            }
        }

        if (!found) {
            log.warn("[GlyphFont-Check] 字体未找到: {}", FONT_NAME);
            log.warn("[GlyphFont-Check] 可用字体（前 50 个）:");
            int limit = Math.min(50, fonts.length);
            for (int i = 0; i < limit; i++) {
                log.warn("[GlyphFont-Check] - {} (family={})", fonts[i].getFontName(), fonts[i].getFamily());
            }
        }

        Font targetFont = new Font(FONT_NAME, Font.PLAIN, BASE_FONT_SIZE);
        assertNotNull(targetFont.getFamily(), "目标字体 family 不应为空");
    }

    /**
     * 把 BufferedImage 写成 JPEG，并写入指定的 DPI（存入 JFIF APP0 段）。
     *
     * @param img 待写出的图像
     * @param out 输出文件路径
     * @param dpi 写入 JFIF 的密度值
     * @throws IOException 图像写出失败时抛出
     */
    private static void writeJpegWithDpi(BufferedImage img, Path out, int dpi) throws IOException {
        // 获取 JPEG writer
        ImageWriter writer = ImageIO.getImageWritersBySuffix(JPEG_FORMAT).next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out.toFile())) {
            writer.setOutput(ios);

            // 创建默认元数据（基于 jpeg 原生格式）
            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(img), null);

            // 取出原生树，找到 app0JFIF 节点，设置密度
            Element tree = (Element) metadata.getAsTree(JPEG_METADATA_FORMAT);
            Element jfif = (Element) tree.getElementsByTagName(JFIF_NODE).item(0);
            jfif.setAttribute(JFIF_X_DENSITY, Integer.toString(dpi));
            jfif.setAttribute(JFIF_Y_DENSITY, Integer.toString(dpi));
            jfif.setAttribute(JFIF_RES_UNITS, JFIF_RES_UNITS_DPI);

            // 把修改后的树合并回元数据
            metadata.mergeTree(JPEG_METADATA_FORMAT, tree);

            // 写出：注意第二个参数是 null，第三个才是带 DPI 的元数据
            writer.write(null, new IIOImage(img, null, metadata), null);
        } finally {
            writer.dispose();
        }
    }

    /**
     * 字符墨迹真实矩形边界（Ground Truth）。
     *
     * @param index  字符在页面中的绘制序号（从 0 开始）
     * @param ch     字符本身
     * @param col    所在网格列索引（从 0 开始）
     * @param row    所在网格行索引（从 0 开始）
     * @param x      墨迹外接矩形左上角 X 坐标（像素）
     * @param y      墨迹外接矩形左上角 Y 坐标（像素）
     * @param width  墨迹外接矩形宽度（像素）
     * @param height 墨迹外接矩形高度（像素）
     */
    public record GlyphInkBox(
            int index,
            String ch,
            int col,
            int row,
            double x,
            double y,
            double width,
            double height
    ) {
        @Override
        public String toString() {
            return String.format("[%2d] '%s' (col=%d, row=%d) -> x=%.2f, y=%.2f, w=%.2f, h=%.2f, r=%.2f, b=%.2f",
                    index, ch, col, row, x, y, width, height, x + width, y + height);
        }
    }

    /**
     * 墨迹适配结果。
     *
     * @param transform 墨迹仿射变换
     * @param bounds    缩放后墨迹的绝对外接矩形
     */
    private record FittedInk(AffineTransform transform, Rectangle2D bounds) {
    }

    /**
     * 字形基准图渲染结果。
     *
     * @param image      渲染完成的画布
     * @param inkBoxes   逐字墨迹外接矩形
     * @param glyphCount 参与渲染的字符总数
     * @param rows       实际使用的网格行数
     */
    private record GlyphSheet(
            BufferedImage image,
            List<GlyphInkBox> inkBoxes,
            int glyphCount,
            int rows
    ) {
    }

}
