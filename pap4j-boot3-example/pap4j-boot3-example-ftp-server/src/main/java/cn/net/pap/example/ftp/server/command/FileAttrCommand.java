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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * <p>使用方法：</p>
 * <pre>{@code
 * client.sendSiteCommand("FILEATTR " + fileName);
 * client.getReplyString();
 * }</pre>
 */
public class FileAttrCommand implements Command {

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        String args = request.getArgument();
        if (args == null || args.isEmpty()) {
            session.write(new DefaultFtpReply(501, "Syntax: SITE FILEATTR <file>"));
            return;
        }

        // 去掉前缀 "FILEATTR"（忽略大小写）
        String filePath = args.trim();
        if (filePath.toUpperCase().startsWith("FILEATTR")) {
            filePath = filePath.substring("FILEATTR".length()).trim();
        }

        FtpFile ftpFile = session.getFileSystemView().getFile(filePath);
        if (!ftpFile.doesExist()) {
            session.write(new DefaultFtpReply(550, "File not found: " + filePath));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Size=").append(ftpFile.getSize()).append(";");
        sb.append("Owner=").append(ftpFile.getOwnerName()).append(";");
        sb.append("Group=").append(ftpFile.getGroupName()).append(";");
        sb.append("IsDirectory=").append(ftpFile.isDirectory()).append(";");
        sb.append("IsFile=").append(ftpFile.isFile()).append(";");
        sb.append("IsReadable=").append(ftpFile.isReadable()).append(";");
        sb.append("IsWritable=").append(ftpFile.isWritable()).append(";");
        sb.append("IsHidden=").append(ftpFile.isHidden()).append(";");
        sb.append("LastModified=").append(ftpFile.getLastModified());

        Object physicalFile = ftpFile.getPhysicalFile();
        if (physicalFile instanceof File file) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(Paths.get(file.getAbsolutePath()), BasicFileAttributes.class);
                sb.append(";CreationTime=").append(attrs.creationTime().toMillis());
                sb.append(";LastAccessTime=").append(attrs.lastAccessTime().toMillis());
                sb.append(";LastModifiedTime=").append(attrs.lastModifiedTime().toMillis());
                sb.append(";IsSymbolicLink=").append(attrs.isSymbolicLink());
                sb.append(";IsOther=").append(attrs.isOther());
            } catch (IOException e) {
                // Ignore or handle BasicFileAttributes read error
            }
        }

        session.write(new DefaultFtpReply(200, sb.toString()));
    }
}