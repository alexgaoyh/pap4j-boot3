package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.TextPointDTO;
import cn.net.pap.common.pdf.enums.ChineseFont;
import com.itextpdf.text.pdf.BaseFont;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FontUtilTest {

    private static final Logger log = LoggerFactory.getLogger(FontUtilTest.class);

    @Test
    public void test1() {
        Dimension bimesion = FontUtil.getCharacterBounds("汉", new Font("宋体", Font.PLAIN, 24));
        System.out.println(bimesion);
    }

    @Test
    public void test2() {
        // 获取当前图形环境
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // 获取所有的字体
        Font[] fonts = ge.getAllFonts();
        // 遍历字体数组，检查每个字体是否支持中文字符
        for (Font font : fonts) {
            if (font.canDisplay('汉')) { // '汉'是一个中文字符
                Dimension bimesion = FontUtil.getCharacterBounds("汉", new Font(font.getName(), Font.PLAIN, 24));
                System.out.println(font.getName() + " : " + bimesion);
            }
        }
    }

    @Test
    public void test22() {
        // 相同字体信息下，不同汉字的大小.
        for(char c = '\u4E00'; c <= '\u9FA5'; c++) {
            Dimension bimesion = FontUtil.getCharacterBounds(String.valueOf(c), new Font("宋体", Font.PLAIN, 24));
            System.out.println(String.valueOf(c) + " : " + bimesion);
        }
        String quanJiaoKongGeStr = "　";
        Dimension quanJiaoKongGeDimension = FontUtil.getCharacterBounds(String.valueOf(quanJiaoKongGeStr), new Font("宋体", Font.PLAIN, 24));
        System.out.println(quanJiaoKongGeStr + " : " + quanJiaoKongGeDimension);
    }

    @Test
    public void test3() {
        Font simSunFont = new Font("宋体",0, 24);
        List<TextPointDTO> textPointDTOS = FontUtil.cutTextInVertical("河南省", 0f, 0f, 100f, 100f, simSunFont);
        System.out.println(textPointDTOS);
    }

    @Test
    void test4ScanSystemFontDirectories() throws Exception {
        List<File> fontFiles = FontUtil.findSystemFontFiles();

        assertFalse(fontFiles.isEmpty(), "应该找到系统字体文件");

        System.out.println("找到 " + fontFiles.size() + " 个字体文件");

        for (int i = 0; i < Math.min(Integer.MAX_VALUE, fontFiles.size()); i++) {
            File fontFile = fontFiles.get(i);
            try (InputStream is = new FileInputStream(fontFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead = is.read(buffer);

                assertTrue(bytesRead > 0, "字体文件应该可读");
                assertTrue(fontFile.length() > 0, "字体文件大小应该大于0");

                System.out.printf("字体文件: %s, 大小: %d bytes, 格式: %s%n",
                        fontFile.getName(),
                        fontFile.length(),
                        FontUtil.detectFontFormat(buffer));
            }
        }
    }

    @Test
    public void chineseFontFamilyNameTest() {
        for(ChineseFont chineseFont : ChineseFont.values()) {
            try (InputStream resourceAsStream = PDFUtil.class.getClassLoader().getResourceAsStream(ChineseFont.getLocation(chineseFont.getFontName()))) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, resourceAsStream);
                log.info(chineseFont.getFontName() + " : " + baseFont.getFamily());
            } catch (Exception e) {
                System.err.println("字体流加载失败: " + e.getMessage());
            }
        }
    }

}
