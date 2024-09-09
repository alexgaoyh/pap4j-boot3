package cn.net.pap.common.pdf;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 汉字工具类
 */
public class FontUtil {

    // 缓存 FontMetrics 以避免重复计算
    private static final Map<Font, FontMetrics> fontMetricsCache = new ConcurrentHashMap<>();

    /**
     * 计算指定字体和字号下的汉字矩形区域大小
     *
     * @param text 要计算的文本（汉字）
     * @param font 指定的字体
     * @return 字符的宽度和高度
     */
    public static Dimension getCharacterBounds(String text, Font font) {
        // 获取或缓存 FontMetrics 对象
        FontMetrics fontMetrics = fontMetricsCache.computeIfAbsent(font, f -> {
            // 创建一个空的 BufferedImage 只用于获取 Graphics2D 对象
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            g2d.setFont(f);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.dispose(); // 释放资源
            return fm;
        });

        // 获取文本宽度和高度
        int width = fontMetrics.stringWidth(text);  // 对多字符优化
        int height = fontMetrics.getHeight();       // 高度通常固定，对多字符无影响

        return new Dimension(width, height);
    }


}
