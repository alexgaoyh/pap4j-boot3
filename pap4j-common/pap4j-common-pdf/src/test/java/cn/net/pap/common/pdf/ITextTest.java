package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.enums.ChineseFont;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.*;
import com.itextpdf.text.pdf.parser.Vector;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

public class ITextTest {

    /**
     * dpi 转换  PDF是72， IMAGE是 真是获取的
     */
    private static BigDecimal dpi72ToReal = new BigDecimal(300).divide(new BigDecimal(72), 2, BigDecimal.ROUND_HALF_UP);

    @Test
    public void pointTextTest() {
        try {
            LinkedHashSet<PointTextDTO> pointTextDTOS = new LinkedHashSet<>();

            List<Map<String, Object>> centerXTextList = new ArrayList<>();

            BigDecimal minWidth = new BigDecimal(Integer.MAX_VALUE);
            BigDecimal maxWidth = new BigDecimal(Integer.MIN_VALUE);

            File file = new File("C:\\Users\\86181\\Desktop\\0029A.pdf");
            PdfReader reader = new PdfReader(file.getAbsolutePath());
            Integer pageNum = 1;
            Rectangle pageSize = reader.getPageSize(pageNum);
            PapTextExtractionStrategy strategy = new PapTextExtractionStrategy(pageSize.getWidth(), pageSize.getHeight(), reader.getPageRotation(pageNum));
            String textWithPoints = PdfTextExtractor.getTextFromPage(reader, pageNum, strategy);

            String dpi = textWithPoints.substring(textWithPoints.indexOf("[") + 1, textWithPoints.indexOf("]"));
            textWithPoints = textWithPoints.substring(textWithPoints.indexOf("]") + 1);

            dpi72ToReal = new BigDecimal(dpi).divide(new BigDecimal(72), 2, BigDecimal.ROUND_HALF_UP);

            if(textWithPoints != null && !"".equals(textWithPoints)) {
                for (String textWithPoint : textWithPoints.split("\n")) {
                    String text = textWithPoint.substring(0, textWithPoint.indexOf("["));
                    if(text != null && !"".equals(text) && !"".equals(text.trim())) {
                        String point = textWithPoint.substring(textWithPoint.indexOf("[") + 1, textWithPoint.indexOf("]"));
                        PointTextDTO pointTextDTO = new PointTextDTO(string2Box(point, dpi72ToReal), text);
                        boolean add = pointTextDTOS.add(pointTextDTO);

                        if(add) {
                            Map<String, Object> tmp = new LinkedHashMap<>();
                            tmp.put("x", centerX(point, dpi72ToReal));
                            Map<String, Object> info = new LinkedHashMap<>();
                            info.put("point", string2Box(point, dpi72ToReal));
                            info.put("text", text);
                            tmp.put("info", info);
                            if(minWidth.compareTo(new BigDecimal(centerWidth(point, dpi72ToReal))) > 0) {
                                minWidth = new BigDecimal(centerWidth(point, dpi72ToReal)).setScale(2 , BigDecimal.ROUND_HALF_UP);
                            }
                            if(maxWidth.compareTo(new BigDecimal(centerWidth(point, dpi72ToReal))) < 0) {
                                maxWidth = new BigDecimal(centerWidth(point, dpi72ToReal)).setScale(2 , BigDecimal.ROUND_HALF_UP);
                            }
                            centerXTextList.add(tmp);
                        }
                    }

                }
            }

            System.out.println(pointTextDTOS.size());
            System.out.println(dpi);
            ObjectMapper objectMapper = new ObjectMapper();
            System.out.println(objectMapper.writeValueAsString(pointTextDTOS));
            System.out.println("-------------------------------");
            System.out.println(minWidth);
            System.out.println(maxWidth);
            System.out.println(objectMapper.writeValueAsString(centerXTextList));

            saveRotation90Chcek(reader.getPageRotation(pageNum), pageSize, pointTextDTOS, dpi72ToReal);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class PapTextExtractionStrategy implements TextExtractionStrategy {

        private StringBuilder withPointString = new StringBuilder();

        private Integer imageDPI = 300;

        private float pageWidth = 0.0f;

        private float pageHeight = 0.0f;

        /**
         * 方向 - 不同 PDF 设置的方向不同，所以可能对应的一系列宽高和坐标就不同
         * 0:正常 ; 90:顺时针旋转 ; -90:逆时针旋转
         */
        private Integer pageRotation;

        /**
         * 记录使用的字符编码
         */
        private Set<String> encodingSet = new HashSet<>();

        private List<ImageRenderInfo> allImages = new ArrayList<>();

        PapTextExtractionStrategy(float pageWidth, float pageHeight, Integer pageRotation) {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.pageRotation = pageRotation;
        }

        @Override
        public void beginTextBlock() {

        }

        @Override
        public void renderText(TextRenderInfo renderInfo) {
            LineSegment baselineOuter = renderInfo.getBaseline();
            if(renderInfo.getFont() != null
                    && renderInfo.getFont().getEncoding() != null
                    && !"".equals(renderInfo.getFont().getEncoding())) {
                encodingSet.add(renderInfo.getFont().getEncoding());
            }
            List<TextRenderInfo> textRenderInfos = renderInfo.getCharacterRenderInfos();
            for (int idx = 0; idx < textRenderInfos.size(); idx++) {
                TextRenderInfo info = textRenderInfos.get(idx);
                GraphicsState graphicsState = getGraphicsState(info);
                float singleSpaceWidth = graphicsState.getCharacterSpacing();
                LineSegment ascentLine = info.getAscentLine();
                LineSegment baselineInner = info.getBaseline();
                float height = getHeightByRotation(pageRotation, ascentLine, baselineOuter, baselineInner);
                Rectangle2D rect = info.getDescentLine().getBoundingRectange();
                String text = info.getText();
                if(null == text || "".equals(text)) {
                    try {
                        if(encodingSet != null && encodingSet.size() > 0 && encodingSet.contains("Cp1252")) {
                            String tmp = new String(info.getPdfString().toString().getBytes("ISO-8859-1"), "GBK");
                            if(isChineseByUnicodeBlock(tmp)) {
                                text = tmp;
                            }
                        }
                    } catch (UnsupportedEncodingException e) {
                    }
                }
                withPointString.append(text)
                        .append("[")
                        // x x' y y'
                        .append(getCoorsByRotation(pageRotation, rect, pageWidth, pageHeight, height, baselineOuter, baselineInner, idx, singleSpaceWidth, graphicsState.getFontSize()))
                        .append("]")
                        .append("{").append("").append("}")
                        .append("<").append("").append(",").append("").append(">")
                        .append("\n");
            }
            textRenderInfos.addAll(textRenderInfos);
        }

        @Override
        public void endTextBlock() {

        }

        @Override
        public void renderImage(ImageRenderInfo imageRenderInfo) {
            try {
                PdfImageObject image = imageRenderInfo.getImage();
                if (image == null) return;

                allImages.add(imageRenderInfo);

                float widthPx = Float.parseFloat(imageRenderInfo.getImage().getDictionary().get(PdfName.WIDTH).toString());
                float heightPx = Float.parseFloat(imageRenderInfo.getImage().getDictionary().get(PdfName.HEIGHT).toString());

                float widthPt = imageRenderInfo.getImageCTM().get(Matrix.I11);
                float heightPt = imageRenderInfo.getImageCTM().get(Matrix.I22);

                float widthInches = widthPt / 72;
                float heightInches = heightPt / 72;

                float dpiX = widthPx / widthInches;
                float dpiY = heightPx / heightInches;

                Integer dpiXInt = Math.round(dpiX);
                Integer dpiYInt = Math.round(dpiY);

                if (dpiXInt.equals(dpiYInt) || Math.abs(dpiXInt - dpiYInt) < 3) {
                    this.imageDPI = Math.max(dpiXInt, dpiYInt);
                }
                // 给一个默认的 72
                if(dpiXInt.equals(dpiYInt) && dpiXInt == Integer.MAX_VALUE && dpiYInt == Integer.MAX_VALUE) {
                    this.imageDPI = 72;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String getResultantText() {
            // pdf 内可能存在多张图像，这里可以做一个拼接
//            List<ImageRenderInfo> allImagesTmp = this.allImages;
//            if(allImagesTmp != null && allImagesTmp.size() > 0) {
//                try {
//                    BufferedImage fullImage = mergeImagesByPosition(allImages, this.pageWidth, this.pageHeight, this.imageDPI);
//                    ImageIO.write(fullImage, "jpg", new File("C:\\Users\\86181\\Desktop\\0002B.jpg"));
//                } catch (IOException e) {
//                    throw new RuntimeException(e);
//                }
//            }
            return "[" + imageDPI + "]" + withPointString.toString();
        }
    }

    private static List<Double> string2Box(String point, BigDecimal dpi72ToReal) {
        List<Double> box = new ArrayList<>();
        for (String coor : point.split(",")) {
            box.add(new BigDecimal(coor).multiply(dpi72ToReal).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue());
        }
        return box;
    }

    private static Double centerX(String point, BigDecimal dpi72ToReal) {
        BigDecimal sum = BigDecimal.ZERO;
        String[] split = point.split(",");
        sum = sum.add(new BigDecimal(split[0]).multiply(dpi72ToReal));
        sum = sum.add(new BigDecimal(split[1]).multiply(dpi72ToReal));
        return sum.divide(new BigDecimal(2), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private static Double centerWidth(String point, BigDecimal dpi72ToReal) {
        String[] split = point.split(",");
        return new BigDecimal(split[1]).multiply(dpi72ToReal).subtract(new BigDecimal(split[0]).multiply(dpi72ToReal)).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static boolean isChineseByUnicodeBlock(String str) {
        for (char c : str.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据 pdf 是否旋转，获得 文本 的高度
     * 原方向和顺时针90度旋转后的高度取值不同
     * @param pageRotation
     * @param ascentLine
     * @param baselineOuter
     * @param baselineInner
     * @return
     */
    public static float getHeightByRotation(Integer pageRotation, LineSegment ascentLine, LineSegment baselineOuter, LineSegment baselineInner) {
        if(pageRotation == 0) {
            return baselineInner.getStartPoint().get(Vector.I2) - baselineInner.getStartPoint().get(Vector.I2);
        }
        if(pageRotation == 90) {
            return baselineInner.getEndPoint().get(Vector.I2) - baselineInner.getStartPoint().get(Vector.I2);
        }
        throw new RuntimeException("未匹配到合适的旋转方向,请联系开发人员!");
    }

    /**
     * 根据 pdf 是否旋转，获得 文本 的坐标
     * 原方向和顺时针90度旋转后的坐标不同
     * @param pageRotation
     * @param rect
     * @param pageWidth
     * @param pageHeight
     * @param widthOrHeight
     * @return
     */
    public static String getCoorsByRotation(Integer pageRotation, Rectangle2D rect, float pageWidth, float pageHeight, float widthOrHeight, LineSegment baselineOuter, LineSegment baselineInner, Integer idx, float singleSpaceWidth, float fontSize) {
        if(pageRotation == 0) {
            return new StringBuilder(rect.getX() + "").append(",").append(rect.getX() + rect.getWidth()).append(",").append(pageHeight - rect.getY()).append(",").append(pageHeight - rect.getY() + widthOrHeight).toString();
        }
        if(pageRotation == 90) {
            double rectHeight = rect.getHeight();
            if(rectHeight == 0.0d) {
                rectHeight = Math.round(Math.abs(widthOrHeight));
            }
            if(rectHeight == 0.0d) {
                rectHeight = fontSize;
            }
            Vector startOuter = baselineOuter.getStartPoint();
            float adjustedX = startOuter.get(1);
            float adjustedY = pageWidth - baselineOuter.getStartPoint().get(0) - Float.parseFloat(Math.round(rectHeight) + Math.abs(singleSpaceWidth) + "") * (idx + 1);

            return new StringBuilder(adjustedX - Math.round(rectHeight / 2) + "").append(",").append(adjustedX + Math.round(rectHeight / 2) + "").append(",").append(adjustedY + "").append(",").append(adjustedY + "").toString();
        }
        throw new RuntimeException("未匹配到合适的旋转方向,请联系开发人员!");
    }

    /**
     * 反射获得 GraphicsState
     * @param renderInfo
     * @return
     */
    public static GraphicsState getGraphicsState(TextRenderInfo renderInfo) {
        try {
            Field graphicsStateField = TextRenderInfo.class.getDeclaredField("gs");
            graphicsStateField.setAccessible(true);
            return (GraphicsState) graphicsStateField.get(renderInfo);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static BufferedImage mergeImagesByPosition(List<ImageRenderInfo> imageInfos, float pageWidthPt, float pageHeightPt, int dpi) throws IOException {
        float scale = dpi / 72f; // 1pt = 1/72 inch

        int canvasWidth = Math.round(pageWidthPt * scale);
        int canvasHeight = Math.round(pageHeightPt * scale);

        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();

        // 设置白底
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, canvasWidth, canvasHeight);

        for (ImageRenderInfo imageInfo : imageInfos) {
            PdfImageObject imageObject = imageInfo.getImage();
            if (imageObject == null) continue;

            BufferedImage img = imageObject.getBufferedImage();
            if (img == null) continue;

            Matrix ctm = imageInfo.getImageCTM();

            float xPt = ctm.get(Matrix.I31);     // X位置 (PDF左下角)
            float yPt = ctm.get(Matrix.I32);     // Y位置 (PDF左下角)
            float wPt = ctm.get(Matrix.I11);     // 宽度
            float hPt = ctm.get(Matrix.I22);     // 高度

            // PDF原点在左下，Java画布在左上 → Y轴需翻转
            int xPx = Math.round(xPt * scale);
            int yPx = Math.round((pageHeightPt - yPt - hPt) * scale); // 上翻
            int wPx = Math.round(wPt * scale);
            int hPx = Math.round(hPt * scale);

            g.drawImage(img, xPx, yPx, wPx, hPx, null);
        }

        g.dispose();
        return canvas;
    }

    /**
     * 保存验证
     * @param pageSize
     * @param pointTextDTOS
     */
    public static void saveRotation90Chcek(Integer pageRotation, Rectangle pageSize, LinkedHashSet<PointTextDTO> pointTextDTOS, BigDecimal dpi72ToReal) {
        if(pageRotation == 90) {
            try (PDDocument document = new PDDocument()) {
                Integer pageWidth = Math.round(pageSize.getHeight() * dpi72ToReal.floatValue());
                Integer pageHeight = Math.round(pageSize.getWidth() * dpi72ToReal.floatValue());
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    PDColor pdColor = new PDColor(new float[]{0f, 0f, 0f}, PDDeviceRGB.INSTANCE);
                    Map<String, PDType0Font> fonts = new HashMap<>();
                    for(ChineseFont chineseFont : ChineseFont.values()) {
                        PDType0Font tmp = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation(chineseFont.getFontName())));
                        fonts.put(chineseFont.getFontName(), tmp);
                    }
                    for(PointTextDTO pointTextDTO : pointTextDTOS) {
                        for(Map.Entry<String, PDType0Font> entry : fonts.entrySet()) {
                            try {
                                if(entry.getValue().getStringWidth(String.valueOf(pointTextDTO.getText())) > 0) {
                                    contentStream.setFont(entry.getValue(), (Float.parseFloat(pointTextDTO.getBox().get(1) + "") - Float.parseFloat(pointTextDTO.getBox().get(0) + "")) * dpi72ToReal.floatValue());
                                    contentStream.setNonStrokingColor(pdColor);
                                    contentStream.beginText();
                                    contentStream.newLineAtOffset(Float.parseFloat(pointTextDTO.getBox().get(0) + ""), Float.parseFloat(pointTextDTO.getBox().get(2) + ""));
                                    contentStream.showText(pointTextDTO.getText());
                                    contentStream.endText();
                                    break;
                                }
                            } catch (Exception e) {
                            }
                        }
                    }
                }
                document.save("C:\\Users\\86181\\Desktop\\123456.pdf");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class PointTextDTO implements Serializable {

        /**
         * 单字坐标区域
         */
        private List<Double> box;

        /**
         * 单字文本
         */
        private String text;

        public PointTextDTO(List<Double> box, String text) {
            this.box = box;
            this.text = text;
        }

        public List<Double> getBox() {
            return box;
        }

        public void setBox(List<Double> box) {
            this.box = box;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PointTextDTO that = (PointTextDTO) o;
            return Objects.equals(box, that.box) &&
                    Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(box, text);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", PointPDFTextStripperTest.PointTextDTO.class.getSimpleName() + "[", "]")
                    .add("box=" + box)
                    .add("text='" + text + "'")
                    .toString();
        }
    }

}
