package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import cn.net.pap.example.javafx.h2.H2ServerManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage stage) throws Exception {
        // 重要：设置当最后一个窗口关闭时不退出应用
        Platform.setImplicitExit(false);

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        fxmlLoader.setCharset(StandardCharsets.UTF_8);
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("登录界面");
        stage.setScene(scene);

        // 设置主窗口左上角图标（标题栏 / 任务栏 / Alt+Tab）
        stage.getIcons().add(ApplicationProperties.APP_ICON);

        // 设置托盘图标
        URL icoURL = MainApp.class.getClassLoader().getResource("alexgaoyh.png");
        Image trayIconImage = Toolkit.getDefaultToolkit().getImage(icoURL);

        // 创建系统托盘
        SystemTray systemTray = SystemTray.getSystemTray();
        TrayIcon trayIcon = new TrayIcon(trayIconImage);
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("pap4j-boot3-example-javafx Application");

        // 双击托盘图标时的事件
        trayIcon.addActionListener(event -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                stage.show();
            }
            if (stage.isIconified()) {
                stage.setIconified(false);
            }
            stage.toFront();
        }));

        // 创建弹出菜单
        java.awt.PopupMenu popupMenu = new java.awt.PopupMenu();

        // 显示窗口菜单项
        java.awt.MenuItem showItem = new java.awt.MenuItem("Show Window");
        showItem.addActionListener(event -> Platform.runLater(() -> {
            if (!stage.isShowing()) {
                stage.show();
            }
            if (stage.isIconified()) {
                stage.setIconified(false);
            }
            stage.toFront();
        }));

        // 退出菜单项
        java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
        exitItem.addActionListener(event -> {
            // 先移除托盘图标
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception e) {
                log.error("移除托盘图标失败", e);
            }
            // 先执行必要的清理工作
            Platform.runLater(() -> {
                System.out.println("关闭舞台...");
                // 关闭所有窗口
                stage.close();
                // 强制退出所有 JavaFX 线程
                Platform.exit();
                // 添加一个延迟，确保日志输出
                new Timer("force-exit-timer", true).schedule(new TimerTask() {
                    @Override
                    public void run() {
                        log.warn("强制退出 JVM（Timer 兜底）");
                        System.exit(0);
                    }
                }, 500);
            });
        });
        popupMenu.add(showItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);
        trayIcon.setPopupMenu(popupMenu);
        try {
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            log.error("systemTray启动服务失败", e);
        }
        // 设置窗口关闭事件
        stage.setOnCloseRequest(event -> {
            event.consume(); // 阻止默认关闭行为
            stage.hide(); // 只是隐藏窗口，不退出应用
        });
    }

    /**
     * maven 执行 javafx:run 这个命令启动.
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            // 启动 H2 服务器
            H2ServerManager.startH2Servers();

            launch();

        } catch (Exception e) {
            log.error("启动服务失败", e);
            System.exit(1);      // 直接退出
        }

    }

    @Override
    public void stop() {
        // 应用关闭时停止服务器
        log.info("{}", H2ServerManager.getConnectionInfo());
        log.info("{}", H2ServerManager.getWebConsoleUrl());
        log.info("{}", H2ServerManager.getDatabaseUrl());
        H2ServerManager.stopServers();
    }

}