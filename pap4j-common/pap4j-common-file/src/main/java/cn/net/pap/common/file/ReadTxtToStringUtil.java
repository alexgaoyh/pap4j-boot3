package cn.net.pap.common.file;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ReadTxtToStringUtil {
    /**
     * 检测文件编码，先检测 BOM，如果没有再使用 UniversalDetector
     */
    public static String detectEncoding(File file) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
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

            // 无BOM的情况，先进行ANSI编码判断
            if (read >= 2) {
                String ansiEncoding = detectANSICoding(bom, read);
                if (ansiEncoding != null) {
                    return ansiEncoding;
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
     * 入参调整，假设 文件流 的来源如下所示
     *        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
     *        boolean success = ftpClient.retrieveFile(TEST_FILE, outputStream);
     * @param outputStream
     * @return
     * @throws IOException
     */
    public static String detectEncoding(ByteArrayOutputStream outputStream) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(outputStream.toByteArray())) {
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

            // 无BOM的情况，先进行ANSI编码判断
            if (read >= 2) {
                String ansiEncoding = detectANSICoding(bom, read);
                if (ansiEncoding != null) {
                    return ansiEncoding;
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
        if(!file.exists()) {
            return null;
        }
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

    /**
     * 文件编码
     * @param filePath
     * @return
     * @throws IOException
     */
    public static String detectCharsetUsingICU4J(String filePath) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(filePath));
        com.ibm.icu.text.CharsetDetector detector = new com.ibm.icu.text.CharsetDetector();
        detector.setText(data);
        com.ibm.icu.text.CharsetMatch match = detector.detect();

        if (match != null) {
            return match.getName();
        } else {
            return null;
        }
    }

    /**
     * 通过BOM字节判断可能的ANSI编码
     */
    private static String detectANSICoding(byte[] bom, int read) {
        // ANSI编码通常没有BOM，但我们可以通过字节模式进行初步判断

        // 检查是否为可能的GBK/Big5编码（中文字符的字节特征）
        if (read >= 2) {
            // GBK/Big5中文字符的第一个字节通常在高位(0x81-0xFE)
            byte firstByte = bom[0];
            byte secondByte = bom[1];

            int first = firstByte & 0xFF;
            int second = secondByte & 0xFF;

            // GBK/Big5编码特征：第一个字节在0x81-0xFE，第二个字节在0x40-0xFE
            if (first >= 0x81 && first <= 0xFE) {
                if (second >= 0x40 && second <= 0xFE && second != 0x7F) {
                    // 进一步区分GBK和Big5
                    return distinguishGBKvsBig5(bom, read);
                }
            }

            // 检查西欧ANSI编码（Windows-1252）的特征
            if (isLikelyWindows1252(bom, read)) {
                return "Windows-1252";
            }
        }

        return null;
    }

    /**
     * 区分GBK和Big5编码
     */
    private static String distinguishGBKvsBig5(byte[] bom, int read) {
        if (read < 2) return "GBK"; // 默认返回GBK

        int first = bom[0] & 0xFF;
        int second = bom[1] & 0xFF;

        // Big5编码范围判断
        if (first >= 0xA1 && first <= 0xF9) {
            if ((second >= 0x40 && second <= 0x7E) || (second >= 0xA1 && second <= 0xFE)) {
                // 符合Big5编码范围特征
                if (read >= 4) {
                    // 如果有更多字节，可以进一步验证
                    if (hasBig5SpecificPatterns(bom, read)) {
                        return "Big5";
                    }
                }
                return "Big5"; // 可能是Big5
            }
        }

        // GBK编码范围更广，默认返回GBK
        return "GBK";
    }

    /**
     * 检查Big5特定模式
     */
    private static boolean hasBig5SpecificPatterns(byte[] bom, int read) {
        // Big5有一些特定的字符编码模式
        for (int i = 0; i < read - 1; i += 2) {
            int high = bom[i] & 0xFF;
            int low = bom[i + 1] & 0xFF;

            // Big5的特定范围
            if (high >= 0xA4 && high <= 0xC6) {
                // 常见Big5字符范围
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为Windows-1252编码
     */
    private static boolean isLikelyWindows1252(byte[] bom, int read) {
        // Windows-1252编码的字节通常在0x00-0xFF范围内
        // 主要检查是否为常见的西欧文本模式
        int latinCount = 0;
        int totalCount = 0;

        for (int i = 0; i < read; i++) {
            int b = bom[i] & 0xFF;

            // 常见的西欧字符范围
            if ((b >= 0x20 && b <= 0x7E) ||  // ASCII可打印字符
                    (b >= 0xA0 && b <= 0xFF) ||  // 扩展拉丁字符
                    b == 0x0A || b == 0x0D ||    // 换行符
                    b == 0x09) {                 // 制表符
                latinCount++;
            }
            totalCount++;
        }

        // 如果大部分字节都在西欧字符范围内
        return totalCount > 0 && (latinCount * 100 / totalCount) > 80;
    }

    /**
     * 快速ANSI编码检测（基于前几个字节）
     */
    private static String quickANSIDetection(byte[] content) {
        if (content == null || content.length < 2) {
            return Charset.defaultCharset().name();
        }

        // 检查前几个字节的模式
        for (int i = 0; i < Math.min(content.length - 1, 100); i += 2) {
            int high = content[i] & 0xFF;
            int low = content[i + 1] & 0xFF;

            // 中文字符特征判断
            if (high >= 0x81 && high <= 0xFE) {
                if (low >= 0x40 && low <= 0xFE && low != 0x7F) {
                    // 进一步判断是GBK还是Big5
                    if (high >= 0xA1 && high <= 0xF9 &&
                            ((low >= 0x40 && low <= 0x7E) || (low >= 0xA1 && low <= 0xFE))) {
                        return "Big5";
                    }
                    return "GBK";
                }
            }
        }

        return null;
    }

}
