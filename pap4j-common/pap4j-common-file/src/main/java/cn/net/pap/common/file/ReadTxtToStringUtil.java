package cn.net.pap.common.file;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

public class ReadTxtToStringUtil {
    /**
     * 检测文件编码，先检测 BOM，如果没有再使用 UniversalDetector
     */
    public static String detectEncoding(File file) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            bis.mark(4); // 标记当前位置，后续可重置

            byte[] bom = new byte[4];
            int read = bis.read(bom, 0, 4);
            bis.reset();

            if (read >= 3) {
                if ((bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
                    return "UTF-8";
                }
            }
            if (read >= 2) {
                if ((bom[0] & 0xFF) == 0xFF && (bom[1] & 0xFF) == 0xFE) {
                    return "UTF-16LE";
                }
                if ((bom[0] & 0xFF) == 0xFE && (bom[1] & 0xFF) == 0xFF) {
                    return "UTF-16BE";
                }
            }

            // 无 BOM，使用通用检测器
            UniversalDetector detector = new UniversalDetector(null);
            byte[] buf = new byte[4096];
            int nread;
            while ((nread = bis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();

            return encoding != null ? encoding : Charset.defaultCharset().name();
        }
    }

    /**
     * 读取文件内容为 String（自动识别编码）
     */
    public static String readFileContent(File file) throws IOException {
        String encoding = detectEncoding(file);
        return Files.readString(file.toPath(), Charset.forName(encoding));
    }

    /**
     * 读取文件内容为 List<String>
     */
    public static List<String> readFileLines(File file) throws IOException {
        String encoding = detectEncoding(file);
        return Files.readAllLines(file.toPath(), Charset.forName(encoding));
    }

}
