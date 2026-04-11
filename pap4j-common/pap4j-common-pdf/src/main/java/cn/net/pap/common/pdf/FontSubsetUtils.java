package cn.net.pap.common.pdf;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 字体子集化工具类
 */
public final class FontSubsetUtils {

    private FontSubsetUtils() {
        // 工具类隐藏构造函数
    }

    /**
     * 从源字体中提取指定字符，生成包含这些字符的子集字体文件。
     *
     * @param sourceFontPath 源字体文件路径 (通常为 .ttf)
     * @param targetFontPath 生成的子集字体文件路径
     * @param text           需要提取的所有字符组合成的字符串
     * @throws IOException 文件读写或字体解析异常
     */
    public static void createSubset(Path sourceFontPath, Path targetFontPath, String text) throws IOException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("需要提取的字符文本不能为空");
        }
        TTFParser parser = new TTFParser();
        try (RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(sourceFontPath.toFile());
             TrueTypeFont ttf = parser.parse(randomAccessRead);
             FileOutputStream outputStream = new FileOutputStream(targetFontPath.toFile())) {
            TTFSubsetter subsetter = new TTFSubsetter(ttf);
            text.codePoints().forEach(subsetter::add);
            subsetter.writeToStream(outputStream);

        }
    }

}
