package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;

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

}
