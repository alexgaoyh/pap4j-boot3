package cn.net.pap.common.pdf;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class ITextUnicodeTest {

    @Test
    public void resourceTest() {
        try {
            // File file = new ClassPathResource("templates/模板.xlsx").getFile();
            ClassPathResource simfangResource = new ClassPathResource("simfang.ttf");
            if(simfangResource.exists()){
                File file = simfangResource.getFile();
                System.out.println(file.exists());
            } else {
                System.out.println(false);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载字体，并且判断字体中是否存在某个 字符
     */
    @Test
    public void unicodeSupportTest() {
        File simfangFile = null;
        byte[] fontBytes = null;
        try {
            simfangFile = TestResourceUtil.getFile("simfang.ttf");
            fontBytes = Files.readAllBytes(simfangFile.toPath());
            simfangFile.delete();
            BaseFont chineseFont = BaseFont.createFont(
                    "simfang.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontBytes,
                    null
            );
            System.out.println(chineseFont.charExists(0x1F600));// 😊 的码点 0x1F600
            System.out.println(chineseFont.charExists(0x9AD8 ));// 高 的码点 0x9AD8


        } catch (Exception e) {

        } finally {
            if (simfangFile != null && simfangFile.exists()) {
                simfangFile.delete();
            }
        }
    }

    /**
     * 加载字体，并且获得字体内支持的所有字符的范围 Range
     */
    @Test
    public void unicodeRangeTest() {
        // 用于存储Unicode存在的区间
        List<String> unicodeRanges = new ArrayList<>();
        File simfangFile = null;
        byte[] fontBytes = null;
        try {
            simfangFile = TestResourceUtil.getFile("simfang.ttf");
            fontBytes = Files.readAllBytes(simfangFile.toPath());
            simfangFile.delete();
            BaseFont chineseFont = BaseFont.createFont(
                    "simfang.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontBytes,
                    null
            );
            // 逐区间遍历Unicode范围
            for (int start = 0x0000; start <= 0x10FFFF; start += 0x1000) {
                int lastCodePoint = -1;  // 记录上一个存在的Unicode字符
                int rangeStart = -1;     // 当前区间的起始位置
                for (int codePoint = start; codePoint < start + 0x1000 && codePoint <= 0x10FFFF; codePoint++) {
                    if (chineseFont.charExists(codePoint)) {
                        if (lastCodePoint == -1) {
                            rangeStart = codePoint;  // 当前字符是一个新的区间的开始
                        } else if (codePoint != lastCodePoint + 1) {
                            // 当前字符与前一个字符不连续，结束当前区间并开始新区间
                            unicodeRanges.add(formatRange(rangeStart, lastCodePoint));
                            rangeStart = codePoint;  // 新区间的起始位置
                        }
                        lastCodePoint = codePoint;
                        System.out.println(codePoint + "   " + new String(Character.toChars(codePoint)));
                    }
                }
                // 结束当前区间
                if (lastCodePoint != -1) {
                    unicodeRanges.add(formatRange(rangeStart, lastCodePoint));
                }
            }
            for (String range : unicodeRanges) {
                System.out.println(range);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (simfangFile != null && simfangFile.exists()) {
                simfangFile.delete();
            }
        }
    }

    // 格式化区间为字符串，转换为十六进制格式，例如：0x400-0x40F 或 0x400
    private static String formatRange(int start, int end) {
        if (start == end) {
            return "0x" + Integer.toHexString(start).toUpperCase();
        } else {
            return "0x" + Integer.toHexString(start).toUpperCase() + "-0x" + Integer.toHexString(end).toUpperCase();
        }
    }

    /**
     * 生成PDF，多种字体的处理.
     */
    @Test
    public void emojiChineseTest() {
        // https://fonts.google.com/
        // https://fontforge.org/en-US/
        File simfangFile1 = null;
        File simfangFile2 = null;
        byte[] fontBytes1 = null;
        byte[] fontBytes2 = null;
        java.io.File tempOut = null;
        try {
            simfangFile1 = TestResourceUtil.getFile("simfang.ttf");
            simfangFile2 = TestResourceUtil.getFile("simfang.ttf");
            fontBytes1 = Files.readAllBytes(simfangFile1.toPath());
            simfangFile1.delete();
            fontBytes2 = Files.readAllBytes(simfangFile2.toPath());
            simfangFile2.delete();
            BaseFont chineseFont = BaseFont.createFont(
                    "simfang.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontBytes1,
                    null
            );
            BaseFont emojiFont = BaseFont.createFont(
                    "simfang.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontBytes2,
                    null
            );

            Font chineseStyle = new Font(chineseFont, 12);
            Font emojiStyle = new Font(emojiFont, 12);

            Document document = new Document();
            tempOut = java.io.File.createTempFile("emoji_demo", ".pdf");
            PdfWriter.getInstance(document, new FileOutputStream(tempOut));
            document.open();

            Paragraph paragraph = new Paragraph();
            paragraph.add(new Chunk("表情测试: ", chineseStyle));  // 汉字部分
            paragraph.add(new Chunk("😊 ❤️ 🚀", emojiStyle));     // 表情部分

            document.add(paragraph);
            document.close();
            System.out.println("PDF 生成成功！");

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (tempOut != null && tempOut.exists()) tempOut.delete();
            if (simfangFile1 != null && simfangFile1.exists()) simfangFile1.delete();
            if (simfangFile2 != null && simfangFile2.exists()) simfangFile2.delete();
        }
    }

    @Test
    public void emoji2PdfTest() {
        File simfangFile1 = null;
        File simfangFile2 = null;
        byte[] fontBytes1 = null;
        byte[] fontBytes2 = null;
        java.io.File tempOut = null;
        try {
            simfangFile1 = TestResourceUtil.getFile("simfang.ttf");
            simfangFile2 = TestResourceUtil.getFile("simfang.ttf");
            fontBytes1 = Files.readAllBytes(simfangFile1.toPath());
            simfangFile1.delete();
            fontBytes2 = Files.readAllBytes(simfangFile2.toPath());
            simfangFile2.delete();
            BaseFont chineseFont = BaseFont.createFont(
                    "simfang.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontBytes1,
                    null
            );
            BaseFont emojiFont = BaseFont.createFont(
                    "simfang.ttf",
                    BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED,
                    true,
                    fontBytes2,
                    null
            );

            Font chineseStyle = new Font(chineseFont, 12);
            Font emojiStyle = new Font(emojiFont, 12);

            Document document = new Document();
            tempOut = java.io.File.createTempFile("emoji_demo", ".pdf");
            PdfWriter.getInstance(document, new FileOutputStream(tempOut));
            document.open();

            // 逐区间遍历Unicode范围
            for (int start = 0x0000; start <= 0x10FFFF; start += 0x1000) {
                for (int codePoint = start; codePoint < start + 0x1000 && codePoint <= 0x10FFFF; codePoint++) {
                    if (emojiFont.charExists(codePoint)) {
                        Paragraph paragraph = new Paragraph();
                        paragraph.add(new Chunk(codePoint + " : ", chineseStyle));  // 汉字部分
                        paragraph.add(new Chunk(new String(Character.toChars(codePoint)), emojiStyle));     // 表情部分
                        document.add(paragraph);
                    }
                }
            }

            document.close();
            System.out.println("PDF 生成成功！");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (tempOut != null && tempOut.exists()) tempOut.delete();
            if (simfangFile1 != null && simfangFile1.exists()) simfangFile1.delete();
            if (simfangFile2 != null && simfangFile2.exists()) simfangFile2.delete();
        }
    }

}
