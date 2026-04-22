package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.textStripper.PointPDFTextStripper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class PointPDFTextStripperTest {

    @Test
    public void textExtractor() {

        try (PDDocument document = Loader.loadPDF(new File(TestResourceUtil.getFile("format.pdf").getAbsolutePath()))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texts = stripper.getText(document);
            System.out.println(texts);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void pointTextExtractor() {
        List<PointTextDTO> pointTextDTOS = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(new File(TestResourceUtil.getFile("format.pdf").getAbsolutePath()))) {
            PointPDFTextStripper stripper = new PointPDFTextStripper();
            String textWithPoints = stripper.getText(document);
            for(String textWithPoint : textWithPoints.split("\n")) {
                if(!textWithPoint.contains("[")) {
                    break;
                }
                String text = textWithPoint.substring(0, textWithPoint.indexOf("["));
                String point = textWithPoint.substring(textWithPoint.indexOf("[") + 1, textWithPoint.indexOf("]"));
                PointTextDTO pointTextDTO = new PointTextDTO(string2Box(point), text);
                pointTextDTOS.add(pointTextDTO);
            }
            System.out.println(pointTextDTOS.size());
            ObjectMapper objectMapper = new ObjectMapper();
            System.out.println(objectMapper.writeValueAsString(pointTextDTOS));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Double> string2Box(String point) {
        List<Double> box = new ArrayList<>();
        for(String coor : point.split(",")) {
            box.add(Double.parseDouble(coor));
        }
        return box;
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
            return new StringJoiner(", ", PointTextDTO.class.getSimpleName() + "[", "]")
                    .add("box=" + box)
                    .add("text='" + text + "'")
                    .toString();
        }
    }
}
