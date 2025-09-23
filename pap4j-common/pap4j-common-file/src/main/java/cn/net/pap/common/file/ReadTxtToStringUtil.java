package cn.net.pap.common.file;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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

            if(encoding == null) {
                try {
                    String s = guessEncoding(buf);
                    encoding = s;
                } catch (Exception e) {
                }
            }

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

            if(encoding == null) {
                try {
                    String s = guessEncoding(buf);
                    encoding = s;
                } catch (Exception e) {
                }
            }

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

    private static String guessEncoding(byte[] data) throws Exception {
        Charset gb2312 = Charset.forName("GB2312");
        Charset big5   = Charset.forName("Big5");

        int scoreGb2312 = scoreDecode(data, gb2312);
        int scoreBig5   = scoreDecode(data, big5);

        return scoreGb2312 >= scoreBig5 ? "GB2312" : "Big5";
    }

    private static int scoreDecode(byte[] data, Charset charset) throws Exception {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

        String decoded = decoder.decode(ByteBuffer.wrap(data)).toString();
        int score = 0;
        for (char c : decoded.toCharArray()) {
            // 简单判断：如果是常见中文字符则加分
            if (c >= 0x4E00 && c <= 0x9FFF) score++;
        }
        return score;
    }

}
