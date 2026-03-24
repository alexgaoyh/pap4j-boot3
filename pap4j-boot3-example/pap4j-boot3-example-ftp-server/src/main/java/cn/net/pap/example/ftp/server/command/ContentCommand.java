package cn.net.pap.example.ftp.server.command;

import cn.net.pap.example.ftp.server.util.FileUtil;
import org.apache.ftpserver.command.Command;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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
        String encode = FileUtil.detectCharsetUsingICU4J(filePath);
        String content = Files.readString(Paths.get(filePath), Charset.forName(encode));
        return content;
    }


}