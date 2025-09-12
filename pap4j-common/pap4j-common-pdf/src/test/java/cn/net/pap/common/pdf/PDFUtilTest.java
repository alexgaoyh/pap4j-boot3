package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import cn.net.pap.common.pdf.dto.PointDTO;
import cn.net.pap.common.pdf.dto.TextPointDTO;
import cn.net.pap.common.pdf.enums.ChineseFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.*;
import java.net.URL;
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
    public void analyzePdfTest() {
        try {
            PDFUtil.analyzePdf("C:\\Users\\86181\\Desktop\\origin.pdf");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void drawTextTest() {
        try {
            // 这里的字体调整一下， 不要多次引入。 不太好看。
            // 能不能  java 获得某一个字体下的 字 。
            List<CoordsDTO> coordsDTOList = new ArrayList<>();
            coordsDTOList.add(new CoordsDTO(300, 500, 20, 20, "¥"));
            coordsDTOList.add(new CoordsDTO(300, 400, 30, 30, "ϕ"));
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

    // @Test
    public void getFontTest() throws Exception {
        String filePath = "CJK-Unified-Ideographs-Extension-A.txt";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                PDType0Font font = PDFUtil.findFont(line.split("\t")[1]);
                System.out.println(line.split("\t")[1] + " : " + font.getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    public void drawText2() throws Exception {
        try (PDDocument document = new PDDocument()) {
            // 仿宋
            PDType0Font simfangFont = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("仿宋")));

            Integer pageWidth = 842;
            Integer pageHeight = 595;
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDColor pdColor3 = hexToPDColor("#000000");

                // 字
                drawFont(contentStream, pdColor3, simfangFont, 12, pageHeight, 417.81f, 77.4f + 12f * 1, "海");
                drawFont(contentStream, pdColor3, simfangFont, 12, pageHeight, 417.81f, 93.24f + 12f * 1, "王");

            }

            // 保存新创建的文档
            document.save("C:\\Users\\86181\\Desktop\\output.pdf");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void drawRect() throws Exception {
        try (PDDocument document = new PDDocument()) {
            // 仿宋
            PDType0Font simfangFont = PDType0Font.load(document, PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation("仿宋")));

            Integer pageWidth = 1100;
            Integer pageHeight = 1807;
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {

                // 矩形
                List<Float> rec1List = Arrays.asList(new Float[]{121f, 1016f, 354f, 1656f});
                PDColor pdColor1 = hexToPDColor("#FF0000");
                drawRec(contentStream, pdColor1, 2f, pageHeight, rec1List);

                List<Float> rec2List = Arrays.asList(new Float[]{131f, 1006f, 364f, 1646f});
                PDColor pdColor2 = hexToPDColor("#0000FF");
                drawRec(contentStream, pdColor2, 4f, pageHeight, rec2List);

                List<Float> rec3List = Arrays.asList(new Float[]{164f, 257f, 367f, 1639f});
                drawRec(contentStream, pdColor2, 1f, pageHeight, rec3List);

                // 线
                List<Float> line1List = Arrays.asList(new Float[]{277f, 277f, 364f, 1646f});
                PDColor pdColor3 = hexToPDColor("#000000");
                drawLine(contentStream, pdColor3, 1f, pageHeight, line1List);

                List<Float> line2List = Arrays.asList(new Float[]{423f, 423f, 364f, 1646f});
                drawLine(contentStream, pdColor3, 1f, pageHeight, line2List);

                // 字
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 1, "张");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 2, "三");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 3, "李");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 4, "四");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 5, "王");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 6, "吴");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 7, "照");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 8, "留");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 9, "词");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 10, "个");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 11, "高");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 12, "和");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 13, "河");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 14, "男");
                drawFont(contentStream, pdColor3, simfangFont, 87, pageHeight, 163f, 367f + 84f * 15, "省");

                // image
                String imageURL = "https://foruda.gitee.com/avatar/1676898910937495644/73661_alexgaoyh_1578916342.png!avatar100";
                drawImageFromURL(document, contentStream, imageURL, pageHeight, 450f, 400f, 50f, 50f);

            }

            // 保存新创建的文档
            document.save("C:\\Users\\86181\\Desktop\\output.pdf");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // @Test
    public void picRectTest() throws Exception {
        try (PDDocument document = new PDDocument()) {
            Integer pageWidth = 2412;
            Integer pageHeight = 4741;
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {

                PDImageXObject imageXObject = PDImageXObject.createFromFile("C:\\Users\\86181\\Desktop\\0073.jpg", document);
                float imageWidth = imageXObject.getWidth();
                float imageHeight = imageXObject.getHeight();
                contentStream.drawImage(imageXObject, 0, 0, imageWidth, imageHeight);

                // 左上右下  x y x‘ y'
                String page_middle_area = "262.50,1047.50,2372.00,4306.00";
                String[] page_middle_area_array = page_middle_area.split(",");
                List<Float> rec1List = Arrays.asList(new Float[]{
                        Float.parseFloat(page_middle_area_array[0]),
                        Float.parseFloat(page_middle_area_array[2]),
                        Float.parseFloat(page_middle_area_array[1]),
                        Float.parseFloat(page_middle_area_array[3])
                });
                PDColor pdColor1 = hexToPDColor("#FF0000");
                drawRec(contentStream, pdColor1, 10f, pageHeight, rec1List);


            }

            // 保存新创建的文档
            document.save("C:\\Users\\86181\\Desktop\\picRectTest.pdf");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 画矩形
     * @param contentStream
     * @param pdColor
     * @param lineWidth
     * @param pageHeight
     * @param recList   x x' y y'
     * @throws Exception
     */
    private void drawRec(PDPageContentStream contentStream, PDColor pdColor, Float lineWidth , Integer pageHeight, List<Float> recList) throws Exception {
        contentStream.setLineWidth(lineWidth);
        contentStream.setStrokingColor(pdColor);
        contentStream.addRect(recList.get(0), pageHeight - recList.get(3), recList.get(1) - recList.get(0), recList.get(3) - recList.get(2));
        contentStream.stroke();
    }

    /**
     * 画线
     * @param contentStream
     * @param pdColor
     * @param lineWidth
     * @param pageHeight
     * @param lineList
     * @throws Exception
     */
    private void drawLine(PDPageContentStream contentStream, PDColor pdColor, Float lineWidth , Integer pageHeight, List<Float> lineList) throws Exception {
        contentStream.setLineWidth(lineWidth);
        contentStream.setStrokingColor(pdColor);
        contentStream.moveTo(lineList.get(0), pageHeight - lineList.get(2));
        contentStream.lineTo(lineList.get(1), pageHeight - lineList.get(3));
        contentStream.stroke();
    }

    /**
     * 画字
     * @param contentStream
     * @param pdColor
     * @param pdFont
     * @param fontSize
     * @param pageHeight
     * @param x
     * @param y
     * @param text
     * @throws Exception
     */
    private void drawFont(PDPageContentStream contentStream, PDColor pdColor, PDFont pdFont, Integer fontSize , Integer pageHeight, Float x, Float y, String text) throws Exception {
        contentStream.setFont(pdFont, fontSize);
        contentStream.setNonStrokingColor(pdColor);
        contentStream.beginText();
        contentStream.newLineAtOffset(x, pageHeight - y);
        contentStream.showText(text);
        contentStream.endText();
    }

    /**
     * 从URL中取图并画图
     * @param document
     * @param contentStream
     * @param url
     * @param pageHeight
     * @param x
     * @param y
     * @param w
     * @param h
     * @throws Exception
     */
    private void drawImageFromURL(PDDocument document, PDPageContentStream contentStream, String url, Integer pageHeight, Float x, Float y, Float w, Float h) throws Exception {
        InputStream imageStream = getImageStreamFromUrl(url);
        byte[] bytes = inputStreamToByteArray(imageStream);
        PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, null);
        contentStream.drawImage(image, x, pageHeight - y, w, h);
    }

    /**
     * 颜色转换
     * @param hex
     * @return
     */
    private static PDColor hexToPDColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new PDColor(new float[]{r / 255f, g / 255f, b / 255f}, PDDeviceRGB.INSTANCE);
    }

    /**
     * 获得图像流
     * @param imageUrl
     * @return
     * @throws Exception
     */
    private static InputStream getImageStreamFromUrl(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        return url.openStream();
    }

    /**
     * 类型转换
     * @param inputStream
     * @return
     * @throws IOException
     */
    private static byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

}
