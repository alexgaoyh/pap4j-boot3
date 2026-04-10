package cn.net.pap.common.pdf;

import cn.net.pap.common.pdf.dto.CoordsDTO;
import cn.net.pap.common.pdf.dto.TextPointDTO;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /**
     * 垂直切割，传入一个矩形区域和对应的文本，按照垂直方向进行切割，并注意平分数据.
     * @param columnText
     * @param x
     * @param y
     * @param width
     * @param height
     * @param font
     * @return
     */
    public static List<cn.net.pap.common.pdf.dto.TextPointDTO> cutTextInVertical(String columnText, Float x, Float y, Float width, Float height, Font font) {
        if(null != columnText && !"".equals(columnText) && null != x && null != y && null != width && null != height && null != font) {
            List<cn.net.pap.common.pdf.dto.TextPointDTO> textPointDTOS = new ArrayList<>();

            // 初始化数据
            char[] chars = columnText.toCharArray();
            for (int i = 0; i < chars.length;) {
                String c = "";
                if (Character.isHighSurrogate(chars[i])) {
                    // 如果是代理项的高位，则跳过两个字符
                    c = new String(Character.toChars(Character.toCodePoint(chars[i], chars[i + 1])));
                    i += 2;
                } else {
                    // 否则，只跳过一个字符
                    c = chars[i] + "";
                    i++;
                }

                Dimension characterBounds = getCharacterBounds(c, font);
                cn.net.pap.common.pdf.dto.TextPointDTO textPointDTO = new cn.net.pap.common.pdf.dto.TextPointDTO();
                textPointDTO.setText(c);
                textPointDTO.setCharacterBounds(characterBounds);
                textPointDTOS.add(textPointDTO);
            }

            // 重新封装数据
            int fontSize = font.getSize();
            Float newX = x;
            if(width > fontSize) {
                newX = x + (width - fontSize) / 2;
            }
            Float totalY = 0f;
            for(TextPointDTO textPointDTO : textPointDTOS) {
                totalY = totalY + Float.parseFloat(textPointDTO.getCharacterBounds().getHeight() + "");
            }
            Float spaceY = 0f;
            if(height > totalY) {
                spaceY = (height - totalY) / textPointDTOS.size();
                Float beforeY = y;
                for(int idx = 0; idx < textPointDTOS.size(); idx++) {
                    TextPointDTO textPointDTO = textPointDTOS.get(idx);
                    textPointDTO.setX(newX);
                    textPointDTO.setY(beforeY);
                    beforeY = beforeY + Float.parseFloat(textPointDTO.getCharacterBounds().getHeight() + "") + spaceY;
                }
            } else {
                // 高度不够,允许重叠
                float singleTextHeight = height / textPointDTOS.size();
                for(int idx = 0; idx < textPointDTOS.size(); idx++) {
                    TextPointDTO textPointDTO = textPointDTOS.get(idx);
                    textPointDTO.setX(newX);
                    textPointDTO.setY((idx + 1) * singleTextHeight);
                }
            }

            return textPointDTOS;


        } else {
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * 数据类型转换
     * @param textPointDTOS
     * @return
     */
    public static List<CoordsDTO> convertTextPointDTO(List<TextPointDTO> textPointDTOS) {
        List<CoordsDTO> coordsDTOS = new ArrayList<>();
        if(null != textPointDTOS && textPointDTOS.size() > 0) {
            for (TextPointDTO textPointDTO : textPointDTOS) {
                CoordsDTO coordsDTO = new CoordsDTO(textPointDTO.getX(), textPointDTO.getY(),
                        Float.parseFloat(textPointDTO.getCharacterBounds().getWidth() + ""),
                        Float.parseFloat(textPointDTO.getCharacterBounds().getHeight() + ""),
                        textPointDTO.getText());
                coordsDTOS.add(coordsDTO);
            }
        }
        return coordsDTOS;
    }

    /**
     * 读取操作系统下的字体
     * @return
     */
    public static List<File> findSystemFontFiles() {
        List<File> fontFiles = new ArrayList<>();
        String[] fontDirectories = getSystemFontDirectories();

        for (String directory : fontDirectories) {
            Path dirPath = Paths.get(directory);
            if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                try {
                    Files.walk(dirPath)
                            .filter(path -> isFontFile(path.toString()))
                            .forEach(path -> fontFiles.add(path.toFile()));
                } catch (Exception e) {
                    System.out.println("扫描目录失败: " + directory + ", 错误: " + e.getMessage());
                }
            }
        }

        return fontFiles;
    }

    /**
     * 字体目录
     * @return
     */
    public static String[] getSystemFontDirectories() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return new String[]{
                    "C:\\Windows\\Fonts",
                    System.getenv("LOCALAPPDATA") + "\\Microsoft\\Windows\\Fonts"
            };
        } else if (os.contains("mac")) {
            return new String[]{
                    "/Library/Fonts",
                    "/System/Library/Fonts",
                    System.getProperty("user.home") + "/Library/Fonts"
            };
        } else { // Linux/Unix
            return new String[]{
                    "/usr/share/fonts",
                    "/usr/local/share/fonts",
                    System.getProperty("user.home") + "/.fonts"
            };
        }
    }

    /**
     * 根据后缀判断是否是字体文件
     * @param filename
     * @return
     */
    public static boolean isFontFile(String filename) {
        String lower = filename.toLowerCase();
        // todo 或者有其他后缀
        return lower.endsWith(".ttf");
    }

    /**
     * 字体格式校验
     * @param header
     * @return
     */
    public static String detectFontFormat(byte[] header) {
        if (header.length < 4) return "Unknown";

        // 检查TTF/OTF签名 (0x00010000 or "OTTO")
        if ((header[0] == 0x00 && header[1] == 0x01 &&
                header[2] == 0x00 && header[3] == 0x00) ||
                (header[0] == 'O' && header[1] == 'T' &&
                        header[2] == 'T' && header[3] == 'O')) {
            return "TrueType/OpenType";
        }

        // 检查WOFF签名 ("wOFF")
        if (header[0] == 'w' && header[1] == 'O' &&
                header[2] == 'F' && header[3] == 'F') {
            return "WOFF";
        }

        // 检查WOFF2签名 ("wOF2")
        if (header[0] == 'w' && header[1] == 'O' &&
                header[2] == 'F' && header[3] == '2') {
            return "WOFF2";
        }

        return "Unknown";
    }

}
