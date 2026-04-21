package cn.net.pap.example.ftp.server.command;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.apache.ftpserver.listener.Listener;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Set;

/**
 * <p>用于监控 FTP 当前在线用户和服务器统计信息的自定义命令。</p>
 * <p>支持命令：</p>
 * <ul>
 *     <li><code>SITE MONITORUSERS USERS</code>：显示活跃用户列表</li>
 *     <li><code>SITE MONITORUSERS STATS</code>：显示服务器统计信息</li>
 *     <li><code>SITE MONITORUSERS HELP</code>：显示帮助菜单</li>
 * </ul>
 */
public class MonitorUsersCommand extends AbstractCommand {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * <p>执行用户监控和统计查询指令。</p>
     *
     * @param session FTP IO 会话对象
     * @param context FTP 服务器上下文
     * @param request FTP 客户端请求信息
     * @throws IOException IO 异常
     * @throws FtpException FTP 处理异常
     */
    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {

        String[] arguments = request.getArgument().split("\\s+");
        String subCommand = arguments.length > 1 ? arguments[1].toUpperCase() : "";

        switch (subCommand) {
            case "USERS":
                sendActiveUsers(session, context);
                break;
            case "STATS":
                sendServerStats(session, context);
                break;
            case "HELP":
            case "":
                sendHelp(session);
                break;
            default:
                session.write(new DefaultFtpReply(501, "501 Unknown SITE command."));
        }
    }

    private void sendActiveUsers(FtpIoSession session, FtpServerContext context) throws FtpException {
        Listener listener = context.getListener("default");
        Set<FtpIoSession> activeSessions = listener.getActiveSessions();

        StringBuilder response = new StringBuilder();
        response.append("200-Active FTP Users (").append(activeSessions.size()).append("):\r\n");
        response.append("200-==========================================\r\n");

        int index = 1;
        for (FtpIoSession ftpSession : activeSessions) {
            FtpSession ftpSessionImpl = ftpSession.getFtpletSession();
            String userInfo = String.format(
                    "200-%2d. User: %-15s | IP: %-15s | Login: %s | Current Dir: %s\r\n",
                    index++,
                    ftpSessionImpl.getUser().getName(),
                    ftpSessionImpl.getClientAddress().getAddress().getHostAddress(),
                    DATE_FORMAT.format(ftpSessionImpl.getLoginTime()),
                    ftpSessionImpl.getFileSystemView().getWorkingDirectory().getAbsolutePath()
            );
            response.append(userInfo);
        }

        response.append("200 End of user list.");
        session.write(new DefaultFtpReply(200, response.toString()));
    }

    private void sendServerStats(FtpIoSession session, FtpServerContext context) throws FtpException {
        Listener listener = context.getListener("default");
        Set<FtpIoSession> activeSessions = listener.getActiveSessions();

        long totalUptime = System.currentTimeMillis() - context.getFtpStatistics().getStartTime().getTime();

        StringBuilder response = new StringBuilder();
        response.append("200-FTP Server Statistics:\r\n");
        response.append("200-==========================================\r\n");
        response.append("200- Active sessions: ").append(activeSessions.size()).append("\r\n");
        response.append("200- Total uploads: ").append(context.getFtpStatistics().getTotalUploadNumber()).append("\r\n");
        response.append("200- Total downloads: ").append(context.getFtpStatistics().getTotalDownloadNumber()).append("\r\n");
        response.append("200- Total uploaded bytes: ").append(context.getFtpStatistics().getTotalUploadSize()).append("\r\n");
        response.append("200- Total downloaded bytes: ").append(context.getFtpStatistics().getTotalDownloadSize()).append("\r\n");
        response.append("200- Server uptime: ").append(formatUptime(totalUptime)).append("\r\n");
        response.append("200 End of statistics.");

        session.write(new DefaultFtpReply(200, response.toString()));
    }

    private void sendHelp(FtpIoSession session) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("200-Available SITE commands:\r\n");
        response.append("200- USERS  - Show active users\r\n");
        response.append("200- STATS  - Show server statistics\r\n");
        response.append("200- HELP   - Show this help\r\n");
        response.append("200 End of help.");

        session.write(new DefaultFtpReply(200, response.toString()));
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, secs);
    }
}