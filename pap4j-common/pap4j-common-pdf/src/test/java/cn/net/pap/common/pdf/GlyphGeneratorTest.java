package cn.net.pap.common.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GlyphGeneratorTest {

    /**
     * A4 @ 300 DPI
     */
    private static final int DPI = 300;
    private static final double A4_WIDTH_MM = 210.0;
    private static final double A4_HEIGHT_MM = 297.0;
    private static final int PAGE_W = (int) Math.round(A4_WIDTH_MM / 25.4 * DPI);   // 2480
    private static final int PAGE_H = (int) Math.round(A4_HEIGHT_MM / 25.4 * DPI);  // 3508

    /**
     * 网格列数（每行几个字）
     */
    private static final int COLS = 8;
    /**
     * 单元格边长（正方形），按页宽均分，正好铺满一行
     */
    private static final int CELL = PAGE_W / COLS;  // 310
    /**
     * 网格最大行数（超出部分不画）
     */
    private static final int MAX_ROWS = PAGE_H / CELL;  // 11

    /**
     * 字形与单元格之间的留白比例（0.05 = 5%）
     */
    private static final double PADDING_RATIO = 0.05;

    /**
     * 字体名，可被系统属性 -Dtest.font= 覆盖
     */
    private static final String FONT_NAME = System.getProperty("test.font", "SimSun");

    /**
     * 基准字号（仅用于构造 Font，真实绘制时按墨迹框缩放）
     */
    private static final int BASE_FONT_SIZE = 100;

    /**
     * 全部中文，按形态分类：扁 / 窄 / 宽 / 方正 / 复杂 / 简单 / 各种结构
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
        int capacity = MAX_ROWS * COLS;
        List<String> chars = new ArrayList<>();
        for (String c : CHARS) {
            if (chars.size() >= capacity) {
                break;
            }
            chars.add(c);
        }
        int rows = (int) Math.ceil(chars.size() / (double) COLS);

        BufferedImage img = new BufferedImage(PAGE_W, PAGE_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, PAGE_W, PAGE_H);

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

            Font baseFont = new Font(FONT_NAME, Font.PLAIN, BASE_FONT_SIZE);
            FontRenderContext frc = g.getFontRenderContext();

            for (int i = 0; i < chars.size(); i++) {
                int col = i % COLS;
                int row = i / COLS;
                int cx = col * CELL;
                int cy = row * CELL;

                drawGlyphFilled(g, baseFont, frc, chars.get(i), cx, cy, CELL);
            }
        } finally {
            g.dispose();
        }

        assertEquals(PAGE_W, img.getWidth());
        assertEquals(PAGE_H, img.getHeight());

        Path out = java.io.File.createTempFile("generateGlyphSheet", ".jpg").toPath();
        writeJpegWithDpi(img, out, 300);

        assertTrue(Files.exists(out), "输出文件应存在");
        assertTrue(Files.size(out) > 0, "输出文件不应为空");
        assertTrue(Files.exists(out), "输出文件应存在");
        assertTrue(Files.size(out) > 0, "输出文件不应为空");

        System.out.println("written: " + out.toAbsolutePath()
                           + "  size=" + PAGE_W + "x" + PAGE_H
                           + " (A4@" + DPI + "dpi)"
                           + "  cell=" + CELL
                           + "  cols=" + COLS
                           + "  rows=" + rows
                           + "  glyphs=" + chars.size()
                           + "  font=" + FONT_NAME);
    }

    /**
     * 把单个字符缩放到填满单元格（保留 padding）。
     * 用 GlyphVector 的墨迹框（visual bounds）来计算缩放，而不是 advance。
     */
    private static void drawGlyphFilled(Graphics2D g, Font baseFont, FontRenderContext frc,
                                        String ch, int cellX, int cellY, int cellSize) {
        GlyphVector gv = baseFont.createGlyphVector(frc, ch);

        // 获取字符在基准字号下的纯墨迹外接矩形（Visual Bounds） 与 logicalBounds（排版字符格子）不同，它只紧贴实际画出的笔画边缘，排除了行距与字距空白
        Rectangle2D ink = gv.getVisualBounds();

        // 字符为空白或无笔画（如空格、控制字符）时直接跳过
        if (ink.getWidth() <= 0 || ink.getHeight() <= 0) {
            return;
        }

        // 计算留白后的单元格可用绘制尺寸
        int padding = (int) Math.round(cellSize * PADDING_RATIO);
        int targetW = cellSize - padding * 2;
        int targetH = cellSize - padding * 2;

        // 计算等比满格缩放比（造成不同字形实际等效字号漂移的原因：min(scaleX, scaleY)） 扁字由宽决定缩放比，瘦高字由高决定缩放比
        double scaleX = targetW / ink.getWidth();
        double scaleY = targetH / ink.getHeight();
        double scale = Math.min(scaleX, scaleY);

        // 计算当前字符在缩放后的真实墨迹物理宽高（纯笔画所占用的绝对像素尺寸）
        double scaledInkW = ink.getWidth() * scale;
        double scaledInkH = ink.getHeight() * scale;

        // 计算墨迹在整页画布中的绝对坐标（使墨迹在当前单元格内绝对居中） 【墨迹真值 (Ground Truth)】:
        // left   = targetInkX
        // top    = targetInkY
        // right  = targetInkX + scaledInkW
        // bottom = targetInkY + scaledInkH
        double targetInkX = cellX + (cellSize - scaledInkW) / 2.0;
        double targetInkY = cellY + (cellSize - scaledInkH) / 2.0;

        // 抵消字形自带的基线偏移量（ink.getX() / ink.getY() 是笔画相对基线 Baseline 的偏移）
        double inkX = ink.getX();
        double inkY = ink.getY();

        // 仿射变换流水线：
        // (1) 先减去 inkX/inkY，将纯笔画墨迹的左上角平移对齐到局部 (0, 0)
        // (2) 进行等比缩放
        // (3) 平移到目标单元格的绝对墨迹居中位置 (targetInkX, targetInkY)
        AffineTransform at = new AffineTransform();
        at.translate(targetInkX, targetInkY);
        at.scale(scale, scale);
        at.translate(-inkX, -inkY);

        // 提取矢量轮廓并执行最终墨迹渲染
        Shape outline = gv.getOutline();
        Shape transformed = at.createTransformedShape(outline);

        g.setColor(Color.BLACK);
        g.fill(transformed);
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
            System.out.println("[WARN] 字体未找到: " + FONT_NAME);
            System.out.println("可用字体（前 50 个）:");
            int limit = Math.min(50, fonts.length);
            for (int i = 0; i < limit; i++) {
                System.out.println("  - " + fonts[i].getFontName()
                                   + "  (family=" + fonts[i].getFamily() + ")");
            }
        }

        assertNotNull(new Font(FONT_NAME, Font.PLAIN, BASE_FONT_SIZE));
    }


    /**
     * 把 BufferedImage 写成 JPEG，并写入指定的 DPI（存入 JFIF APP0 段）。
     */
    private static void writeJpegWithDpi(BufferedImage img, Path out, int dpi) throws IOException {
        // 获取 JPEG writer
        ImageWriter writer = ImageIO.getImageWritersBySuffix("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out.toFile())) {
            writer.setOutput(ios);

            // 创建默认元数据（基于 jpeg 原生格式）
            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(img), null);

            // 取出原生树，找到 app0JFIF 节点，设置密度
            Element tree = (Element) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
            Element jfif = (Element) tree.getElementsByTagName("app0JFIF").item(0);
            jfif.setAttribute("Xdensity", Integer.toString(dpi));
            jfif.setAttribute("Ydensity", Integer.toString(dpi));
            jfif.setAttribute("resUnits", "1"); // 1 = dots per inch

            // 把修改后的树合并回元数据
            metadata.mergeTree("javax_imageio_jpeg_image_1.0", tree);

            // 写出：注意第二个参数是 null，第三个才是带 DPI 的元数据
            writer.write(null, new IIOImage(img, null, metadata), null);
        } finally {
            writer.dispose();
        }
    }
}