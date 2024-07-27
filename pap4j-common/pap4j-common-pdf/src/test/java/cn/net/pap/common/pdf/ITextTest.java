package cn.net.pap.common.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.*;
import com.itextpdf.text.pdf.parser.Vector;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

public class ITextTest {

    @Test
    public void pointTextTest() {
        try {
            List<PointTextDTO> pointTextDTOS = new ArrayList<>();

            List<Map<String, Object>> centerXTextList = new ArrayList<>();

            File file = new File("C:\\Users\\86181\\Desktop\\0004A.pdf");
            PdfReader reader = new PdfReader(file.getAbsolutePath());
            Integer pageNum = 1;
            Rectangle pageSize = reader.getPageSize(pageNum);
            PapTextExtractionStrategy strategy = new PapTextExtractionStrategy(pageSize.getWidth(), pageSize.getHeight());
            String textWithPoints = PdfTextExtractor.getTextFromPage(reader, pageNum, strategy);

            for (String textWithPoint : textWithPoints.split("\n")) {
                String text = textWithPoint.substring(0, textWithPoint.indexOf("["));
                String point = textWithPoint.substring(textWithPoint.indexOf("[") + 1, textWithPoint.indexOf("]"));
                PointTextDTO pointTextDTO = new PointTextDTO(string2Box(point), text);
                pointTextDTOS.add(pointTextDTO);

                Map<String, Object> tmp = new LinkedHashMap<>();
                tmp.put("x", centerX(point));
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("point", point);
                info.put("text", text);
                tmp.put("info", info);
                centerXTextList.add(tmp);

            }
            System.out.println(pointTextDTOS.size());
            ObjectMapper objectMapper = new ObjectMapper();
            System.out.println(objectMapper.writeValueAsString(pointTextDTOS));
            System.out.println("-------------------------------");
            System.out.println(objectMapper.writeValueAsString(centerXTextList));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class PapTextExtractionStrategy implements TextExtractionStrategy {

        private StringBuilder withPointString = new StringBuilder();

        private float pageWidth = 0.0f;

        private float pageHeight = 0.0f;

        PapTextExtractionStrategy(float pageWidth, float pageHeight) {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        @Override
        public void beginTextBlock() {

        }

        @Override
        public void renderText(TextRenderInfo renderInfo) {
            List<TextRenderInfo> textRenderInfos = renderInfo.getCharacterRenderInfos();
            for (TextRenderInfo info : textRenderInfos) {
                LineSegment ascentLine = info.getAscentLine();
                LineSegment baseline = info.getBaseline();
                float height = ascentLine.getStartPoint().get(Vector.I2) - baseline.getStartPoint().get(Vector.I2);
                Rectangle2D rect = info.getDescentLine().getBoundingRectange();
                withPointString.append(info.getText())
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
            System.out.println(imageRenderInfo);
        }

        @Override
        public String getResultantText() {
            return withPointString.toString();
        }
    }

    private static List<Double> string2Box(String point) {
        List<Double> box = new ArrayList<>();
        for (String coor : point.split(",")) {
            box.add(new BigDecimal(coor).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue());
        }
        return box;
    }

    private static Double centerX(String point) {
        BigDecimal sum = BigDecimal.ZERO;
        String[] split = point.split(",");
        sum = sum.add(new BigDecimal(split[0]));
        sum = sum.add(new BigDecimal(split[1]));
        return sum.divide(new BigDecimal(2), 2, BigDecimal.ROUND_HALF_UP).doubleValue();
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
        public String toString() {
            return new StringJoiner(", ", PointPDFTextStripperTest.PointTextDTO.class.getSimpleName() + "[", "]")
                    .add("box=" + box)
                    .add("text='" + text + "'")
                    .toString();
        }
    }

}
