package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.constant.JavaFxConstant;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button exitButton;

    public void setWelcomeMessage(String message) {
        if (welcomeLabel != null) {
            welcomeLabel.setText(message);
        }
    }

    @FXML
    protected void onLogoutClick() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onHandleRefresh(javafx.event.ActionEvent event) throws IOException {
        Stage primaryStage = findPrimaryStage();
        if (primaryStage != null) {
            reloadFXML(primaryStage);
        }
    }

    private Stage findPrimaryStage() {
        // 查找当前显示的主舞台
        for (javafx.stage.Window window : javafx.stage.Window.getWindows()) {
            if (window instanceof Stage) {
                Stage stage = (Stage) window;
                if (stage.isShowing() && stage.getScene() != null) {
                    return stage;
                }
            }
        }
        return null;
    }

    private void reloadFXML(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        // 尝试从文件系统加载（开发时热重载）
        File fxmlFile = new File(JavaFxConstant.projectLocation + "src/main/resources/cn/net/pap/example/javafx/dashboard-view.fxml");

        URL fxmlUrl;
        if (fxmlFile.exists()) {
            System.out.println("从文件系统加载: " + fxmlFile.getAbsolutePath());
            fxmlUrl = fxmlFile.toURI().toURL();
        } else {
            System.out.println("从 Classpath 加载");
            fxmlUrl = getClass().getResource("dashboard-view.fxml");
        }

        loader.setLocation(fxmlUrl);
        Parent root = loader.load();
        Scene scene = new Scene(root);
        stage.setScene(scene);

        System.out.println("界面刷新成功");
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (welcomeLabel != null) {
            welcomeLabel.setText("欢迎:");
        }
        if (exitButton != null) {
            exitButton.setText("退出");
        }
    }

}
