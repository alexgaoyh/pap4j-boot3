package cn.net.pap.example.javafx;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button exitButton;

    public void setWelcomeMessage(String message) {
        welcomeLabel.setText(message);
    }

    @FXML
    protected void onLogoutClick() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.close();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        welcomeLabel.setText("欢迎:");
        exitButton.setText("退出");
    }

}
