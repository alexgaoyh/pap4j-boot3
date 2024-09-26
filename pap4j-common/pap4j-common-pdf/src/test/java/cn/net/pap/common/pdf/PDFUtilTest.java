package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import cn.net.pap.common.pdf.dto.PointDTO;
import cn.net.pap.common.pdf.dto.TextPointDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PDFUtilTest {

    @Test
    public void addStampTest() {
        try {
            PDFUtil.addStamp("origin.pdf",
                    "alexgaoyh.png",
                    "output.pdf");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void addProtectTest() {
        try {
            PDFUtil.addProtect("origin.pdf",
                    "alexgaoyh",
                    "pap.net",
                    "output.pdf");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void addSignTest() {
        try {
            PDFUtil.addSign("origin.pdf",
                    "alexgaoyh.p12",
                    "alexgaoyh",
                    "output.pdf");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void convertPDFATest() {
        try {
            PDFUtil.convertPDFA("origin.pdf",
                    "output.pdf");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void drawTextTest() {
        try {
            List<CoordsDTO> coordsDTOList = new ArrayList<>();
            coordsDTOList.add(new CoordsDTO(300, 500, 20, 20, "许"));
            coordsDTOList.add(new CoordsDTO(300, 400, 30, 30, "昌"));
            coordsDTOList.add(new CoordsDTO(300, 300, 40, 40, "魏"));
            coordsDTOList.add(new CoordsDTO(300, 200, 50, 50, "都"));
            coordsDTOList.add(new CoordsDTO(300, 100, 60, 60, "区"));
            coordsDTOList.add(new CoordsDTO(100, 500, 20, 20, "一"));
            coordsDTOList.add(new CoordsDTO(100, 400, 30, 30, "个"));
            coordsDTOList.add(new CoordsDTO(100, 300, 40, 40, "打"));
            coordsDTOList.add(new CoordsDTO(100, 200, 50, 50, "杂"));
            coordsDTOList.add(new CoordsDTO(100, 100, 60, 60, "的"));
            PDFUtil.drawText("output.pdf", coordsDTOList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // @Test
    public void drawTextTest2() {
        try {
            Font simSunFont = new Font("宋体",0, 24);
            List<TextPointDTO> textPointDTOS = FontUtil.cutTextInVertical("河南省", 0f, 10f, 100f, 110f, simSunFont);
            List<CoordsDTO> coordsDTOList = FontUtil.convertTextPointDTO(textPointDTOS);
            PDFUtil.drawText("output.pdf", coordsDTOList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void readTextTest() {
        try {
            PDDocument document = Loader.loadPDF(new File("output.pdf"));
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            System.out.println(text);
            document.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void drawRectangleBy4PointTest() {
        try {
            List<Integer> coords = Arrays.asList(new Integer[]{100, 100, 160, 100, 160, 160, 100, 160});
            PointDTO[] pointDTOS = PointDTO.convert2RectangleBy4Point(coords);
            // drawRectangleBy4Point 方法的传参，同样可以使用 pointDTOS[0], pointDTOS[1], pointDTOS[2], pointDTOS[3]
            PDFUtil.drawRectangleBy4Point("C:\\Users\\86181\\Desktop\\output.pdf",
                    new PointDTO(100, 100),
                    new PointDTO(160, 100),
                    new PointDTO(160, 160),
                    new PointDTO(100, 160));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void convertPDFToJPGTest() {
        PDFUtil.convertPDFToJPG("pdf.pdf", "jpg.jpg", 300);
    }


    @Test
    public void utf16ToPdfTest() throws Exception {
        List<String> paragraphs = new ArrayList<>();
        String filePath = "utf16.txt";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_16))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 处理每一行内容
                // System.out.println(line);
                paragraphs.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        PDFUtil.drawParagraphs("utf16.pdf", paragraphs);
    }

    // @Test
    public void drawRect() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setLineWidth(2f);
                contentStream.setStrokingColor(new PDColor(new float[]{1, 0, 0}, PDDeviceRGB.INSTANCE));
                contentStream.addRect(100, 100, 100, 100);
                contentStream.stroke(); // 仅描边，不填充

                contentStream.setStrokingColor(new PDColor(new float[]{0, 0, 1}, PDDeviceRGB.INSTANCE));
                contentStream.setLineWidth(4);
                contentStream.addRect(200, 200, 100, 100);
                contentStream.stroke();
            }

            // 保存新创建的文档
            document.save("C:\\Users\\86181\\Desktop\\output.pdf");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
