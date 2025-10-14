package cn.net.pap.example.javafx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
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
        launch();
    }

}