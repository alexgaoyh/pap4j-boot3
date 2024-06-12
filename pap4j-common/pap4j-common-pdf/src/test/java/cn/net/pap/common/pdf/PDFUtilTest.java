package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

}
