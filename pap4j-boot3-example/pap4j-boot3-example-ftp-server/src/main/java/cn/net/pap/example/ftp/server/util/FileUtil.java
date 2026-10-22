package cn.net.pap.example.ftp.server.util;

import cn.net.pap.common.core.util.CharsetDetectorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * <p>文件处理相关工具类，提供文件编码推测等功能。</p>
 */
public class FileUtil {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileUtil.class);

    /**
     * <p>文件编码</p>
     *
     * @param filePath 文件路径
     * @return 编码结果
     * @throws IOException IO异常
     */
    public static String detectCharsetUsingICU4J(String filePath) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(filePath));
        com.ibm.icu.text.CharsetDetector detector = new com.ibm.icu.text.CharsetDetector();
        detector.setText(data);
        com.ibm.icu.text.CharsetMatch match = detector.detect();

        if (match != null) {
            String name = match.getName();
            if (name != null && ("ISO-8859-1".equals(name) || "ISO-8859-7".equals(name) || "Big5".equals(name))) {
                return guessEncoding(data);
            } else {
                return name;
            }
        } else {
            return null;
        }
    }

    private static String guessEncoding(byte[] data) throws IOException {
        return CharsetDetectorUtil.guessEncoding(data);
    }
}
