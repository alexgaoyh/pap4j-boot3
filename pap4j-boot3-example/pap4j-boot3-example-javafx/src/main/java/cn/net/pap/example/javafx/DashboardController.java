package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.constant.JavaFxConstant;
import cn.net.pap.example.javafx.view.ZoomableImageView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label scaleLabel;

    @FXML
    private Button exitButton;

    @FXML
    private ZoomableImageView zoomableView;

    @FXML
    private StackPane stackPane;

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

    @FXML
    private void resetView() throws IOException {
        Platform.runLater(() ->
                zoomableView.fitImage(stackPane.getWidth(), stackPane.getHeight())
        );
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
        // 保存当前窗口状态
        boolean wasMaximized = stage.isMaximized();

        // 保存窗口位置和尺寸（用于非最大化状态）
        double windowX = stage.getX();
        double windowY = stage.getY();
        double windowWidth = stage.getWidth();
        double windowHeight = stage.getHeight();

        // 1. 先隐藏窗口，避免视觉抖动
        stage.hide();

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

        // 2. 设置新场景
        stage.setScene(scene);

        // 3. 在显示前设置窗口状态
        if (wasMaximized) {
            // 直接设置为最大化状态
            stage.setMaximized(true);
        } else {
            // 恢复窗口位置和尺寸
            stage.setX(windowX);
            stage.setY(windowY);
            stage.setWidth(windowWidth);
            stage.setHeight(windowHeight);
        }

        stage.setTitle("首页");
        // 4. 显示窗口
        stage.show();

        // 5. 确保最大化状态正确（如果需要的话）
        if (wasMaximized) {
            javafx.application.Platform.runLater(() -> {
                // 检查并确保最大化状态
                if (!stage.isMaximized()) {
                    stage.setMaximized(true);
                }
            });
        }

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
        Platform.runLater(() ->
            zoomableView.fitImage(stackPane.getWidth(), stackPane.getHeight())
        );
        // 值单向绑定
        scaleLabel.textProperty().bind(zoomableView.scaleFactorProperty().multiply(100).asString("%.0f%%"));
    }

}
