package cn.net.pap.common.docx;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class ReadDocDocUtils {

    public static String readWord(String filePath) {
        if (filePath == null || (!filePath.endsWith(".doc") && !filePath.endsWith(".docx"))) {
            throw new IllegalArgumentException("Only .doc and .docx files are supported.");
        }

        try (FileInputStream fis = new FileInputStream(filePath)) {

            if (filePath.endsWith(".doc")) {
                try (HWPFDocument doc = new HWPFDocument(fis);
                     WordExtractor extractor = new WordExtractor(doc)) {
                    return extractor.getText();
                }
            }

            if (filePath.endsWith(".docx")) {
                try (XWPFDocument docx = new XWPFDocument(fis)) {
                    StringBuilder sb = new StringBuilder();
                    List<XWPFParagraph> paragraphs = docx.getParagraphs();
                    for (XWPFParagraph para : paragraphs) {
                        sb.append(para.getText()).append("\n");
                    }
                    return sb.toString();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

}
