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

/**
 * <p>使用方法：</p>
 * <pre>{@code
 * client.sendSiteCommand("ENCODING " + fileName);
 * client.getReplyString();
 * }</pre>
 */
public class EncodingCommand implements Command {

    /**
     * <p>执行 <code>ENCODING</code> 自定义指令，用于探测目标文件的字符编码集。</p>
     *
     * @param session FTP IO 会话对象
     * @param context FTP 服务器上下文
     * @param request FTP 客户端请求信息
     * @throws IOException IO 异常
     * @throws FtpException FTP 处理异常
     */
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

        String encoding = FileUtil.detectCharsetUsingICU4J(localFile.getAbsolutePath());
        session.write(new DefaultFtpReply(200, encoding));
    }

}
