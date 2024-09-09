package cn.net.pap.common.pdf;

import org.junit.jupiter.api.Test;

import java.awt.*;

public class FontUtilTest {

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
}
