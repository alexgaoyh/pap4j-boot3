package cn.net.pap.common.pdf;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ItextExtractPathsTest {

    /**
     * PDF 线条信息、矩形信息 提取
     * 创建 pdf 的过程：
     * 1、通过 Acrobat 实现
     * 1.1、打开 Acrobat，点击 工具-> 创建 PDF -> 空白页 -> 点击创建。
     * 1.2、点击右侧工具栏的 注释。在顶部出现的注释工具栏中，找到 绘图工具。选择“矩形”或“线条”，在空白页上画图。你可以右键点击画好的形状 -> 属性，修改颜色和线条粗细。
     * 1.3、将注释“压”入底层（Flatten）：注意：直接保存是不行的。点击 文件->打印 。在打印机列表中，选择 Adobe PDF（或者 Microsoft Print to PDF 也可以），点击 打印。
     *
     */
    @Test
    public void extractPathTest() throws Exception {
        PdfReader pdfReader = new PdfReader(TestResourceUtil.getFile("format.pdf").getAbsolutePath());

        for (int page = 1; page <= pdfReader.getNumberOfPages(); page++) {
            System.out.printf("Page %s\n", page);

            List<PDFPathDTO> pathData = new ArrayList<>();
            ExtRenderListener extRenderListener = new PapExtRenderListener(pathData);

            PdfReaderContentParser parser = new PdfReaderContentParser(pdfReader);
            parser.processContent(page, extRenderListener);

            for (PDFPathDTO path : pathData) {
                System.out.println(path);
            }
        }
    }

    /**
     * ExtRenderListener 实现类
     */
    public static class PapExtRenderListener implements ExtRenderListener {

        private static final Logger log = LoggerFactory.getLogger(PapExtRenderListener.class);

        private final List<PDFPathDTO> pathData;

        public PapExtRenderListener(List<PDFPathDTO> pathData) {
            this.pathData = pathData;
        }

        @Override
        public void beginTextBlock() {
        }

        @Override
        public void renderText(TextRenderInfo renderInfo) {
        }

        @Override
        public void endTextBlock() {
        }

        @Override
        public void renderImage(ImageRenderInfo renderInfo) {
        }

        @Override
        public void modifyPath(PathConstructionRenderInfo renderInfo) {
            pathInfos.add(renderInfo);
        }

        @Override
        public Path renderPath(PathPaintingRenderInfo renderInfo) {
            GraphicsState graphicsState;
            try {
                graphicsState = getGraphicsState(renderInfo);
            } catch (NoSuchFieldException | SecurityException | IllegalArgumentException |
                     IllegalAccessException e) {
                log.error(e.getMessage());
                return null;
            }

            Matrix ctm = graphicsState.getCtm();

            PDFPathDTO pathDTO = new PDFPathDTO();

            if ((renderInfo.getOperation() & PathPaintingRenderInfo.FILL) != 0) {
                pathDTO.setFillColor(toString(graphicsState.getFillColor()));
            }
            if ((renderInfo.getOperation() & PathPaintingRenderInfo.STROKE) != 0) {
                pathDTO.setStroke(toString(graphicsState.getStrokeColor()));
            }
            if(graphicsState.getLineWidth() != 0) {
                pathDTO.setLineWidth(graphicsState.getLineWidth());
            }

            for (PathConstructionRenderInfo pathConstructionRenderInfo : pathInfos) {
                switch (pathConstructionRenderInfo.getOperation()) {
                    case PathConstructionRenderInfo.MOVETO:
                        // 起点
                        pathDTO.setLineStart(transform(ctm, pathConstructionRenderInfo.getSegmentData()));
                        break;
                    case PathConstructionRenderInfo.CLOSE:
                        // 闭合路径 表示闭合当前路径，即绘制一条从路径的当前位置到起始点的直线，使路径成为一个封闭的形状。
                        // 用法：用于在绘制完路径的一部分后将路径闭合，通常用于绘制多边形、矩形等闭合形状。
                        // transform(ctm, pathConstructionRenderInfo.getSegmentData())
                        // break;
                        throw new RuntimeException("未适配: " + pathConstructionRenderInfo.getOperation());
                    case PathConstructionRenderInfo.CURVE_123:
                        // 含义：表示贝塞尔曲线操作，具体来说是三次贝塞尔曲线。它使用三个点来定义曲线：起始点、控制点1和控制点2、结束点。
                        // 用法：用来创建复杂的曲线路径，三次贝塞尔曲线在绘制平滑曲线时非常有用。
                        // transform(ctm, pathConstructionRenderInfo.getSegmentData())
                        // break;
                        throw new RuntimeException("未适配: " + pathConstructionRenderInfo.getOperation());
                    case PathConstructionRenderInfo.CURVE_13:
                        // 含义：这是另一个类型的曲线构造，它同样是贝塞尔曲线，但从名字上看，它可能是由起始点、控制点1和结束点来定义。
                        // 用法：类似于 CURVE_123，但它的具体实现和参数有所不同，通常是为了处理特殊的路径构造需求。
                        // transform(ctm, pathConstructionRenderInfo.getSegmentData())
                        // break;
                        throw new RuntimeException("未适配: " + pathConstructionRenderInfo.getOperation());
                    case PathConstructionRenderInfo.CURVE_23:
                        // 含义：表示二次贝塞尔曲线操作，它用两个点来定义曲线：控制点和结束点。起始点是当前路径位置。
                        // 用法：用于绘制二次贝塞尔曲线，适用于较为简单的曲线形状。
                        // transform(ctm, pathConstructionRenderInfo.getSegmentData())
                        // break;
                        throw new RuntimeException("未适配: " + pathConstructionRenderInfo.getOperation());
                    case PathConstructionRenderInfo.LINETO:
                        // 直线
                        pathDTO.setLineEnd(transform(ctm, pathConstructionRenderInfo.getSegmentData()));
                        pathDTO.setType("LINE");
                        break;
                    case PathConstructionRenderInfo.RECT:
                        pathDTO.setRectangle(transform(ctm, expandRectangleCoordinates(pathConstructionRenderInfo.getSegmentData())));
                        pathDTO.setType("RECTANGLE");
                        break;
                }
            }

            pathData.add(pathDTO);

            pathInfos.clear();
            return null;
        }

        @Override
        public void clipPath(int rule) {
        }

        private List<Float> transform(Matrix ctm, List<Float> coordinates) {
            List<Float> result = new ArrayList<>();
            for (int i = 0; i + 1 < coordinates.size(); i += 2) {
                Vector vector = new Vector(coordinates.get(i), coordinates.get(i + 1), 1);
                vector = vector.cross(ctm);
                result.add(vector.get(Vector.I1));
                result.add(vector.get(Vector.I2));
            }
            return result;
        }

        private List<Float> expandRectangleCoordinates(List<Float> rectangle) {
            if (rectangle.size() < 4)
                return Collections.emptyList();
            return Arrays.asList(
                    rectangle.get(0),
                    rectangle.get(1),
                    rectangle.get(0) + rectangle.get(2),
                    rectangle.get(1) + rectangle.get(3)
            );
        }

        private String toString(BaseColor baseColor) {
            if (baseColor == null) {
                return "";
            } else {
                return String.format("%s,%s,%s", baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue());
            }
        }

        private GraphicsState getGraphicsState(PathPaintingRenderInfo renderInfo) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
            Field gsField = PathPaintingRenderInfo.class.getDeclaredField("gs");
            gsField.setAccessible(true);
            return (GraphicsState) gsField.get(renderInfo);
        }

        final List<PathConstructionRenderInfo> pathInfos = new ArrayList<>();
    }

    /**
     * PDF 线条信息、矩形信息 DTO
     */
    public static final class PDFPathDTO implements Serializable {

        private String fillColor;

        private String stroke;

        private String type;

        private List<Float> lineStart;

        private List<Float> lineEnd;

        private Float lineWidth;

        private List<Float> rectangle;

        public String getFillColor() {
            return fillColor;
        }

        public void setFillColor(String fillColor) {
            this.fillColor = fillColor;
        }

        public String getStroke() {
            return stroke;
        }

        public void setStroke(String stroke) {
            this.stroke = stroke;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Float> getLineStart() {
            return lineStart;
        }

        public void setLineStart(List<Float> lineStart) {
            this.lineStart = lineStart;
        }

        public List<Float> getLineEnd() {
            return lineEnd;
        }

        public void setLineEnd(List<Float> lineEnd) {
            this.lineEnd = lineEnd;
        }

        public Float getLineWidth() {
            return lineWidth;
        }

        public void setLineWidth(Float lineWidth) {
            this.lineWidth = lineWidth;
        }

        public List<Float> getRectangle() {
            return rectangle;
        }

        public void setRectangle(List<Float> rectangle) {
            this.rectangle = rectangle;
        }

        @Override
        public String toString() {
            return "PDFPathDTO{" +
                    "fillColor='" + fillColor + '\'' +
                    ", stroke='" + stroke + '\'' +
                    ", type='" + type + '\'' +
                    ", lineStart=" + lineStart +
                    ", lineEnd=" + lineEnd +
                    ", lineWidth=" + lineWidth +
                    ", rectangle=" + rectangle +
                    '}';
        }
    }

}
