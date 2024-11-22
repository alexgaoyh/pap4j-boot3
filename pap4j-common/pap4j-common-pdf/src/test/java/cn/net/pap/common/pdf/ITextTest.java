package cn.net.pap.common.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.*;
import com.itextpdf.text.pdf.parser.Vector;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.*;

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
            PapTextExtractionStrategy strategy = new PapTextExtractionStrategy(pageSize.getWidth(), pageSize.getHeight());
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
         * 记录使用的字符编码
         */
        private Set<String> encodingSet = new HashSet<>();

        PapTextExtractionStrategy(float pageWidth, float pageHeight) {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        @Override
        public void beginTextBlock() {

        }

        @Override
        public void renderText(TextRenderInfo renderInfo) {
            if(renderInfo.getFont() != null
                    && renderInfo.getFont().getEncoding() != null
                    && !"".equals(renderInfo.getFont().getEncoding())) {
                encodingSet.add(renderInfo.getFont().getEncoding());
            }
            List<TextRenderInfo> textRenderInfos = renderInfo.getCharacterRenderInfos();
            for (TextRenderInfo info : textRenderInfos) {
                LineSegment ascentLine = info.getAscentLine();
                LineSegment baseline = info.getBaseline();
                float height = ascentLine.getStartPoint().get(Vector.I2) - baseline.getStartPoint().get(Vector.I2);
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
                        .append(rect.getX()).append(",").append(rect.getX() + rect.getWidth()).append(",").append(pageHeight - rect.getY()).append(",").append(pageHeight - rect.getY() + height)
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

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public String getResultantText() {
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
