package cn.net.pap.common.pdf;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 汉字工具类
 */
public class FontUtil {

    /**
     * 计算指定字体和字号下的汉字矩形区域大小
     *
     * @param text 要计算的文本（汉字）
     * @param font 指定的字体
     * @return 字符的宽度和高度
     */
    public static Dimension getCharacterBounds(String text, Font font) {
        // 创建一个空的 BufferedImage
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置字体
        g2d.setFont(font);

        // 获取 FontMetrics 对象
        FontMetrics fontMetrics = g2d.getFontMetrics();

        // 获取字符宽度和高度
        int width = fontMetrics.stringWidth(text);
        int height = fontMetrics.getHeight();

        g2d.dispose(); // 释放 Graphics2D 资源

        return new Dimension(width, height);
    }

}
