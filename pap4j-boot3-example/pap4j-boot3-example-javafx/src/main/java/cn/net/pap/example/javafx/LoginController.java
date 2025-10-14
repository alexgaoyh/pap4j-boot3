package cn.net.pap.example.javafx;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

            DashboardController controller = loader.getController();
            controller.setWelcomeMessage("欢迎回来，" + username + "！");

            Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
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
        loginButton.setText("登录");
    }

}
