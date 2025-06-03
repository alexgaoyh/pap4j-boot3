package cn.net.pap.common.file;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

public class ReadTxtToStringUtil {
    /**
     * 检测文件编码格式
     */
    public static String detectEncoding(File file) throws IOException {
        byte[] buf = new byte[4096];
        try (FileInputStream fis = new FileInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            return encoding != null ? encoding : Charset.defaultCharset().name(); // 默认使用系统编码
        }
    }

    /**
     * 读取文件内容（自动识别编码）
     */
    public static String readFileContent(File file) throws IOException {
        if(file.exists()) {
            String encoding = detectEncoding(file);
            return Files.readString(file.toPath(), Charset.forName(encoding));
        } else {
            return null;
        }
    }

    /**
     * 读取文件内容为 List<String>
     */
    public static java.util.List<String> readFileLines(File file) throws IOException {
        String encoding = detectEncoding(file);
        return Files.readAllLines(file.toPath(), Charset.forName(encoding));
    }

}
