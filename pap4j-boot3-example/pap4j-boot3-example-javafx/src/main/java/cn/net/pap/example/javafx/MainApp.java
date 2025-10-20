package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.h2.H2ServerManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        fxmlLoader.setCharset(StandardCharsets.UTF_8);
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("登录界面");
        stage.setScene(scene);
        stage.show();
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
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText("启动 H2 服务器失败: " + e.getMessage());
            alert.showAndWait();
        }

    }

    @Override
    public void stop() {
        // 应用关闭时停止服务器
        System.out.println(H2ServerManager.getConnectionInfo());
        System.out.println(H2ServerManager.getWebConsoleUrl());
        System.out.println(H2ServerManager.getDatabaseUrl());
        H2ServerManager.stopServers();
    }

}