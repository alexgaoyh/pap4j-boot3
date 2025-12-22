package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.util.ImageMagickUtil;
import cn.net.pap.example.javafx.view.ZoomableImageView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
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

    @FXML
    private Canvas gridOverlay;

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
        focusInZoomableView();
    }

    @FXML
    private void sizeView() throws IOException {
        Platform.runLater(() ->
                {
                    ImageView imageView = zoomableView.getImageView();
                    imageView.setScaleX(1.0);
                    imageView.setScaleY(1.0);
                    imageView.setTranslateX(0);
                    imageView.setTranslateY(0);
                    zoomableView.updateScale(1.0);
                }
        );
        focusInZoomableView();
    }

    @FXML
    private void imageRemoveIn() throws Exception {
        ImageView imageView = zoomableView.getImageView();
        Rectangle2D rectangle2D = zoomableView.getSelectionInImageCoordinates();
        String inputFilePath = zoomableView.getImageList().get(zoomableView.getCurrentIndex()).getImageAbsolutePath();
        if (rectangle2D != null) {
            ImageMagickUtil.ExecResult execResult = ImageMagickUtil.magick_imageRemoveIn(inputFilePath, inputFilePath, rectangle2D.getMinX(), rectangle2D.getMinY(), rectangle2D.getMaxX(), rectangle2D.getMaxY());
            if (execResult.isSuccess()) {
                zoomableView.clearSelection();
                double scaleX = imageView.getScaleX();
                double scaleY = imageView.getScaleY();
                double translateX = imageView.getTranslateX();
                double translateY = imageView.getTranslateY();
                zoomableView.reloadCurrentImage(scaleX, scaleY, translateX, translateY);
            } else {
                showErrorAlert("图像处理失败", "执行图像操作时发生错误。\n原因: " + execResult.getStderr());
            }
        } else {
            showErrorAlert("图像处理失败", "执行图像操作时发生错误。\n原因: " + "未获得有效矩形框");
        }
        focusInZoomableView();
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
        URL resourceUrl = getClass().getClassLoader().getResource("cn/net/pap/example/javafx/dashboard-view.fxml");
        File fxmlFile = new File(resourceUrl.getFile());

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

        focusInZoomableView();
    }

    private void focusInZoomableView() {
        Platform.runLater(() -> {
            // 确保该组件可以接收焦点
            zoomableView.setFocusTraversable(true);
            // 请求焦点
            zoomableView.requestFocus();
        });
    }

    /**
     * 辅助方法：显示错误提示框
     */
    private void showErrorAlert(String title, String message) {
        // 确保在 JavaFX 线程中执行 UI 更新
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
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

        stackPane.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            drawGrid(newBounds.getWidth(), newBounds.getHeight());
        });

        focusInZoomableView();
    }

    private void drawGrid(double width, double height) {
        if (gridOverlay != null) {
            gridOverlay.setWidth(width);
            gridOverlay.setHeight(height);

            var gc = gridOverlay.getGraphicsContext2D();
            gc.clearRect(0, 0, width, height);

            gc.setStroke(Color.rgb(242, 255, 213, 0.8));
            gc.setLineWidth(2);

            double gridSize = 30; // 网格间距，可调
            // 纵线
            for (double x = 0; x <= width; x += gridSize) {
                gc.strokeLine(x, 0, x, height);
            }
            // 横线
            for (double y = 0; y <= height; y += gridSize) {
                gc.strokeLine(0, y, width, y);
            }
        }
    }

}
