package cn.net.pap.example.ftp.server.command;

import org.apache.ftpserver.command.Command;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 使用方法： client.sendSiteCommand("CONTENT " + fileName);  client.getReplyString();
 */
public class ContentCommand implements Command {

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        String args = request.getArgument();
        if (args == null || args.isEmpty()) {
            session.write(new DefaultFtpReply(501, "Syntax: SITE CONTENT <file>"));
            return;
        }

        // 去掉前缀 "CONTENT"（忽略大小写）
        String filePath = args.trim();
        if (filePath.toUpperCase().startsWith("CONTENT")) {
            filePath = filePath.substring("CONTENT".length()).trim();
        }

        FtpFile ftpFile = session.getFileSystemView().getFile(filePath);
        if (!ftpFile.doesExist()) {
            session.write(new DefaultFtpReply(550, "File not found: " + filePath));
            return;
        }

        // 检查文件大小，避免读取过大文件
        if (ftpFile.getSize() > 1024 * 1024) { // 1MB限制
            session.write(new DefaultFtpReply(550, "File too large (max 1MB): " + filePath));
            return;
        }

        // 转为本地路径
        File localFile = new File(((File) ftpFile.getPhysicalFile()).getAbsolutePath());
        if (!localFile.exists() || !localFile.isFile()) {
            session.write(new DefaultFtpReply(550, "Not a valid file: " + filePath));
            return;
        }

        // 检查文件扩展名（可选，增强安全性）
        String fileName = localFile.getName().toLowerCase();
        if (!fileName.endsWith(".txt") && !fileName.endsWith(".log") && !fileName.endsWith(".json")) {
            session.write(new DefaultFtpReply(550, "Only text files (.txt, .log, .json) are supported: " + filePath));
            return;
        }

        try {
            String content = readFileContent(localFile.getAbsolutePath());
            session.write(new DefaultFtpReply(200, content));
        } catch (Exception e) {
            session.write(new DefaultFtpReply(550, "Error reading file: " + e.getMessage()));
        }
    }

    /**
     * 读取文件内容并自动检测编码
     */
    public static String readFileContent(String filePath) throws IOException {
        String encode = detectCharsetUsingICU4J(filePath);
        String content = Files.readString(Paths.get(filePath), Charset.forName(encode));
        return content;
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
            String name = match.getName();
            if(name != null && ("ISO-8859-1".equals(name) || "ISO-8859-7".equals(name) || "Big5".equals(name))) {
                return guessEncoding(data);
            } else {
                return name;
            }
        } else {
            return null;
        }
    }

    private static String guessEncoding(byte[] data) throws IOException {
        Charset gbk = Charset.forName("GBK");
        Charset gb2312 = Charset.forName("GB2312");
        Charset big5   = Charset.forName("Big5");

        int scoreGbk = scoreDecode(data, gbk);
        int scoreGb2312 = scoreDecode(data, gb2312);
        int scoreBig5   = scoreDecode(data, big5);

        // 优先选择GBK，因为它兼容GB2312
        if (scoreGbk > 0 && scoreGbk >= scoreBig5) {
            return "GBK";
        } else if (scoreGb2312 > 0 && scoreGb2312 >= scoreBig5) {
            return "GB2312";
        } else {
            return "Big5";
        }
    }

    private static int scoreDecode(byte[] data, Charset charset) throws IOException {
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            String decoded = decoder.decode(ByteBuffer.wrap(data)).toString();
            int score = 0;
            for (char c : decoded.toCharArray()) {
                // 扩展字符范围判断
                if (isChineseCharacter(c)) {
                    score++;
                }
            }
            return score;
        } catch (CharacterCodingException e) {
            // 如果解码失败，返回负分
            return -1;
        }
    }

    private static boolean isChineseCharacter(char c) {
        // 扩展中文字符范围
        return (c >= 0x4E00 && c <= 0x9FFF) ||       // 基本汉字
                (c >= 0x3400 && c <= 0x4DBF) ||       // 扩展A
                (c >= 0x20000 && c <= 0x2A6DF) ||     // 扩展B
                (c >= 0x2A700 && c <= 0x2B73F) ||     // 扩展C
                (c >= 0x2B740 && c <= 0x2B81F) ||     // 扩展D
                (c >= 0xF900 && c <= 0xFAFF) ||       // 兼容汉字
                (c >= 0x2F800 && c <= 0x2FA1F);       // 补充兼容汉字
    }

}