package cn.net.pap.example.ftp.server.config;

import cn.net.pap.example.ftp.server.command.ContentCommand;
import cn.net.pap.example.ftp.server.command.EncodingCommand;
import cn.net.pap.example.ftp.server.command.MonitorUsersCommand;
import cn.net.pap.example.ftp.server.ftplet.RateLimitFtplet;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.command.CommandFactoryFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ftp")
public class FtpServerConfig {

    private int port = 21;  // 默认端口

    private int connectRateLimit = 50;

    private List<FtpUserProperties> users = new ArrayList<>();

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectRateLimit() {
        return connectRateLimit;
    }

    public void setConnectRateLimit(int connectRateLimit) {
        this.connectRateLimit = connectRateLimit;
    }

    public List<FtpUserProperties> getUsers() {
        return users;
    }

    public void setUsers(List<FtpUserProperties> users) {
        this.users = users;
    }

    @Bean
    public FtpServer ftpServer() throws FtpException {
        FtpServerFactory serverFactory = new FtpServerFactory();

        // 配置监听器
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);
        serverFactory.addListener("default", listenerFactory.createListener());

        // 流控的功能
        serverFactory.getFtplets().put("rateLimit", new RateLimitFtplet(connectRateLimit));

        // 创建用户管理器
        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(null); // 不使用文件存储用户
        UserManager userManager = userManagerFactory.createUserManager();

        // 添加用户
        for (FtpUserProperties userProp : users) {
            ensureHomeDirectoryExists(userProp.getHomeDirectory());

            BaseUser user = new BaseUser();
            user.setName(userProp.getUsername());
            user.setPassword(userProp.getPassword());
            user.setHomeDirectory(userProp.getHomeDirectory());

            List<Authority> authorities = new ArrayList<>();
            authorities.add(new WritePermission());
            user.setAuthorities(authorities);

            userManager.save(user);
        }
        serverFactory.setUserManager(userManager);

        // 注册自定义命令
        registerCommand(serverFactory);

        // 设置系统属性，使FTP服务器使用UTF-8编码
        System.setProperty("ftpserver.encoding", "UTF-8");

        // 创建并返回FTP服务器
        FtpServer server = serverFactory.createServer();
        server.start();
        return server;
    }


    /**
     * 注册 SITE ENCODING 命令
     */
    private void registerCommand(FtpServerFactory serverFactory) {
        CommandFactoryFactory factoryFactory = new CommandFactoryFactory();

        // 先保留默认命令
        factoryFactory.setUseDefaultCommands(true);

        // 添加自定义 SITE 命令（注意必须大写，前缀 SITE_）
        factoryFactory.addCommand("SITE_ENCODING", new EncodingCommand());
        factoryFactory.addCommand("SITE_CONTENT", new ContentCommand());
        factoryFactory.addCommand("SITE_MONITORUSERS", new MonitorUsersCommand());


        // 注册到 serverFactory
        serverFactory.setCommandFactory(factoryFactory.createCommandFactory());
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


    /**
     * 用户配置对象
     */
    public static class FtpUserProperties {
        private final String username;
        private final String password;
        private final String homeDirectory;

        public FtpUserProperties(String username, String password, String homeDirectory) {
            this.username = username;
            this.password = password;
            this.homeDirectory = homeDirectory;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getHomeDirectory() {
            return homeDirectory;
        }
    }
}