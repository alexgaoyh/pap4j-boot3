package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label passwordLabel;
    @FXML
    private Button loginButton;

    @FXML
    protected void onLoginButtonClick(ActionEvent event) throws IOException {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if ("admin".equals(username) && "123456".equals(password)) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard-view.fxml"));
            loader.setCharset(StandardCharsets.UTF_8);
            Parent root = loader.load();

            Stage currentStage = (Stage) ((Button) event.getSource()).getScene().getWindow();
            boolean wasMaximized = currentStage.isMaximized();

            // 创建新Stage，而不是重用旧的
            Stage newStage = new Stage();
            newStage.setScene(new Scene(root));
            newStage.setTitle("首页");

            if (wasMaximized) {
                newStage.setMaximized(true);
            } else {
                // 复制旧窗口的位置和大小
                newStage.setX(currentStage.getX());
                newStage.setY(currentStage.getY());
                newStage.setWidth(currentStage.getWidth());
                newStage.setHeight(currentStage.getHeight());
            }

            newStage.getIcons().add(ApplicationProperties.APP_ICON);

            // 关闭旧窗口
            currentStage.close();
            // 显示新窗口
            newStage.show();

        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("登录失败");
            alert.setContentText("用户名或密码错误！");
            alert.showAndWait();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        usernameLabel.setText("用户名:");
        passwordLabel.setText("密码:");
        usernameField.setText("admin");
        passwordField.setText("123456");
        loginButton.setText("登录");
    }

}
