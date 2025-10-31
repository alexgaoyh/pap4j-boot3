package cn.net.pap.example.ftp.server.command;

import org.apache.ftpserver.command.Command;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;

/**
 * 使用方法： client.sendSiteCommand("ENCODING " + fileName);
 */
public class EncodingCommand implements Command {

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws IOException, FtpException {
        String args = request.getArgument();
        if (args == null || args.isEmpty()) {
            session.write(new DefaultFtpReply(501, "Syntax: SITE ENCODING <file>"));
            return;
        }

        // 去掉前缀 "ENCODING"（忽略大小写）
        String filePath = args.trim();
        if (filePath.toUpperCase().startsWith("ENCODING")) {
            filePath = filePath.substring("ENCODING".length()).trim();
        }

        FtpFile ftpFile = session.getFileSystemView().getFile(filePath);
        if (!ftpFile.doesExist()) {
            session.write(new DefaultFtpReply(550, "File not found: " + filePath));
            return;
        }

        // 转为本地路径
        File localFile = new File(((File) ftpFile.getPhysicalFile()).getAbsolutePath());
        if (!localFile.exists() || !localFile.isFile()) {
            session.write(new DefaultFtpReply(550, "Not a valid file: " + filePath));
            return;
        }

        String encoding = detectEncoding(localFile);
        session.write(new DefaultFtpReply(200, encoding));
    }

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

            if (encoding == null) {
                try {
                    String s = guessEncoding(buf);
                    encoding = s;
                } catch (Exception e) {
                }
            }

            return encoding != null ? encoding : Charset.defaultCharset().name();
        }
    }

    private static String guessEncoding(byte[] data) throws IOException {
        Charset gb2312 = Charset.forName("GB2312");
        Charset big5 = Charset.forName("Big5");

        int scoreGb2312 = scoreDecode(data, gb2312);
        int scoreBig5 = scoreDecode(data, big5);

        return scoreGb2312 >= scoreBig5 ? "GB2312" : "Big5";
    }

    private static int scoreDecode(byte[] data, Charset charset) throws IOException {
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
