package cn.net.pap.example.ftp.server.config;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class FtpServerConfig {

    @Value("${ftp.port:2121}")
    private int port;

    @Value("${ftp.username:admin}")
    private String username;

    @Value("${ftp.password:123456}")
    private String password;

    @Value("${ftp.home.dir:d:/knowledge}")
    private String homeDir;

    @Bean
    public FtpServer ftpServer() throws FtpException {
        FtpServerFactory serverFactory = new FtpServerFactory();

        // 配置监听器
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);
        serverFactory.addListener("default", listenerFactory.createListener());

        // 确保主目录存在
        ensureHomeDirectoryExists(homeDir);

        // 配置用户
        BaseUser user = new BaseUser();
        user.setName(username);
        user.setPassword(password);
        user.setHomeDirectory(homeDir);

        // 设置用户权限
        List<Authority> authorities = new ArrayList<>();
        authorities.add(new WritePermission());
        user.setAuthorities(authorities);

        // 添加用户到用户管理器
        serverFactory.getUserManager().save(user);

        // 设置系统属性，使FTP服务器使用UTF-8编码
        System.setProperty("ftpserver.encoding", "UTF-8");

        // 创建并返回FTP服务器
        FtpServer server = serverFactory.createServer();
        server.start();
        return server;
    }

    /**
     * 确保FTP主目录存在
     *
     * @param homeDirPath 主目录路径
     */
    private void ensureHomeDirectoryExists(String homeDirPath) {
        File homeDir = new File(homeDirPath);
        if (!homeDir.exists()) {
            boolean created = homeDir.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create FTP home directory: " + homeDirPath);
            }
        }
    }
}