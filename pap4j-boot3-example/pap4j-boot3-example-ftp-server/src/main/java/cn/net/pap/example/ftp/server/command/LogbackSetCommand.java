package cn.net.pap.example.ftp.server.command;

import cn.net.pap.example.ftp.server.logback.LogbackLevelManager;
import org.apache.ftpserver.command.Command;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

import java.io.IOException;

/**
 *             ftpClient.sendSiteCommand("LOGBACKSET " + "org.apache.ftpserver " + "ERROR");
 *             int replyCode = ftpClient.getReplyCode();
 *             String replyString = ftpClient.getReplyString();
 */
public class LogbackSetCommand implements Command {

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws IOException, FtpException {
        String args = request.getArgument();
        if (args == null || args.isEmpty()) {
            session.write(new DefaultFtpReply(501, "Syntax: SITE LOGBACKSET <file>"));
            return;
        }

        String params = args.trim();
        if (params.toUpperCase().startsWith("LOGBACKSET")) {
            params = params.substring("LOGBACKSET".length()).trim();
        }

        try {
            LogbackLevelManager.setLoggerLevel(params.split(" ")[0], params.split(" ")[1]);
            session.write(new DefaultFtpReply(200, "success"));
        } catch (Exception e) {
            session.write(new DefaultFtpReply(550, "Error reading file: " + e.getMessage()));
        }
    }


}
