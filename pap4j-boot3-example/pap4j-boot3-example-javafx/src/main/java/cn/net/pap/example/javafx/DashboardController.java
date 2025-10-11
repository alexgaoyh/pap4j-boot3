package cn.net.pap.example.javafx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DashboardController {

    @FXML
    private Label welcomeLabel;

    public void setWelcomeMessage(String message) {
        welcomeLabel.setText(message);
    }

    @FXML
    protected void onLogoutClick() {
        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.close();
    }

}
