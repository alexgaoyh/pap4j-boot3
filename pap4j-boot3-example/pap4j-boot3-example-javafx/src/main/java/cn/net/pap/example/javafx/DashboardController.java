package cn.net.pap.example.javafx;

import cn.net.pap.example.javafx.config.ApplicationProperties;
import cn.net.pap.example.javafx.dto.FileTreeItem;
import cn.net.pap.example.javafx.dto.ImageViewDTO;
import cn.net.pap.example.javafx.util.ImageMagickUtil;
import cn.net.pap.example.javafx.util.PathHistoryManager;
import cn.net.pap.example.javafx.view.ZoomableImageView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class DashboardController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

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

    @FXML
    private TreeView<Path> folderTreeView;

    private Path currentFolder;

    private TreeItem<Path> rootItem;

    private ProgressIndicator progressIndicator;

    private StackPane loadingPane;

    private Task<Image> imageLoadTask;

    // 图像切换的时候，避免来回滚动
    private boolean restoringSelection = false;

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
    private void resetView() {
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
    private void handleOpenFolder(javafx.event.ActionEvent event) {
        // 创建目录选择对话框
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("选择图片文件夹");

        // 设置初始目录（如果有）
        if (currentFolder != null) {
            directoryChooser.setInitialDirectory(currentFolder.toFile());
        }

        // 显示对话框
        Stage stage = (Stage) scaleLabel.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            // 清空中间图像框
            zoomableView.dispose();

            currentFolder = selectedDirectory.toPath();
            loadFolderTree(currentFolder);
        }

        // 保持焦点在ZoomableImageView
        focusInZoomableView();
    }

    private void loadFolderTree(Path folderPath) {
        if(folderPath != null) {
            rootItem = new FileTreeItem(folderPath);
            folderTreeView.setRoot(rootItem);
            rootItem.setExpanded(true); // 只展开根节点
        }
    }

    @FXML
    private void handleDelImageTmp() throws Exception {
        // 先从 PathHistoryManager 中删除
        PathHistoryManager.cleanupExpiredHistory(Duration.ofHours(24));
        // 再读取文件夹下所有文件，找出指定时间之前的文件进行删除
        PathHistoryManager.deleteFilesBefore(Paths.get(ApplicationProperties.getImageTmpFolder()), Duration.ofHours(24));
        showSuccessAlert("临时文件夹删除", "操作成功。\n已删除24小时之前的临时图像信息");
    }

    @FXML
    private void imageRollback() throws Exception {
        String inputFilePath = zoomableView.getImageList().get(zoomableView.getCurrentIndex()).getImageAbsolutePath();
        String recentSavedPath = PathHistoryManager.popLatestHistoricalFile(inputFilePath);
        if(recentSavedPath != null && !recentSavedPath.isEmpty() && new File(recentSavedPath).exists()) {
            try (FileChannel inChannel = FileChannel.open(Paths.get(recentSavedPath), StandardOpenOption.READ);
                 FileChannel outChannel = FileChannel.open(Paths.get(inputFilePath), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                long size = inChannel.size();
                long transferred = 0;
                while (transferred < size) {
                    transferred += inChannel.transferTo(transferred, size - transferred, outChannel);
                }
            }

            Files.deleteIfExists(Paths.get(recentSavedPath));

            ImageView imageView = zoomableView.getImageView();
            double scaleX = imageView.getScaleX();
            double scaleY = imageView.getScaleY();
            double translateX = imageView.getTranslateX();
            double translateY = imageView.getTranslateY();
            zoomableView.reloadCurrentImage(scaleX, scaleY, translateX, translateY);
        } else {
            showErrorAlert("图像处理失败", "无法回退。\n原因: " + "未对当前图像进行操作或上一步图像已删除");
        }
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

    @FXML
    private void handleRefreshSelectedNode() {
        TreeItem<Path> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();

        if (selectedItem == null || !(selectedItem instanceof FileTreeItem)) {
            loadFolderTree(currentFolder);
            return;
        }

        FileTreeItem fileItem = (FileTreeItem) selectedItem;
        Path selectedPath = fileItem.getValue();

        // 保存当前选中路径
        final Path pathToReselect = selectedPath;

        // 确定要刷新的节点
        FileTreeItem nodeToRefresh;
        if (Files.isDirectory(selectedPath)) {
            nodeToRefresh = fileItem;
        } else {
            nodeToRefresh = (FileTreeItem) fileItem.getParent();
        }

        if (nodeToRefresh != null) {
            // 刷新节点
            refreshNode(nodeToRefresh);

            // 延迟重新选中（确保节点已刷新完成）
            Platform.runLater(() -> {
                findAndSelectPath(nodeToRefresh, pathToReselect);
            });
        }
    }

    @FXML
    private void handlePreviousImage() {
        TreeItem<Path> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getValue() == null) {
            showErrorAlert("提示", "请先选择一张图片");
            return;
        }

        // 获取当前选中的TreeItem
        TreeItem<Path> currentItem = selectedItem;

        // 如果是文件，先找到其父节点（目录）
        TreeItem<Path> parentItem = currentItem.getParent();
        if (parentItem == null) {
            return;
        }

        // 获取父节点下的所有子节点（包括目录和文件）
        List<TreeItem<Path>> allItems = new ArrayList<>();
        getAllChildren(parentItem, allItems);

        // 找到当前选中的项目在所有项目中的索引
        int currentIndex = -1;
        for (int i = 0; i < allItems.size(); i++) {
            if (allItems.get(i) == currentItem) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return;
        }

        // 查找前一个图片文件
        for (int i = currentIndex - 1; i >= 0; i--) {
            TreeItem<Path> item = allItems.get(i);
            Path path = item.getValue();
            if (path != null && isImageFile(path.toFile())) {
                folderTreeView.getSelectionModel().select(item);
                return;
            }
        }

        // 如果没有找到前一个图片，提示已经是第一张
        showErrorAlert("提示", "已经是第一张图片");
    }

    @FXML
    private void handleNextImage() {
        TreeItem<Path> selectedItem = folderTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getValue() == null) {
            showErrorAlert("提示", "请先选择一张图片");
            return;
        }

        // 获取当前选中的TreeItem
        TreeItem<Path> currentItem = selectedItem;

        // 如果是文件，先找到其父节点（目录）
        TreeItem<Path> parentItem = currentItem.getParent();
        if (parentItem == null) {
            return;
        }

        // 获取父节点下的所有子节点（包括目录和文件）
        List<TreeItem<Path>> allItems = new ArrayList<>();
        getAllChildren(parentItem, allItems);

        // 找到当前选中的项目在所有项目中的索引
        int currentIndex = -1;
        for (int i = 0; i < allItems.size(); i++) {
            if (allItems.get(i) == currentItem) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return;
        }

        // 查找下一个图片文件
        for (int i = currentIndex + 1; i < allItems.size(); i++) {
            TreeItem<Path> item = allItems.get(i);
            Path path = item.getValue();
            if (path != null && isImageFile(path.toFile())) {
                folderTreeView.getSelectionModel().select(item);
                return;
            }
        }

        // 如果没有找到下一个图片，提示已经是最后一张
        showErrorAlert("提示", "已经是最后一张图片");
    }

    /**
     * 递归获取所有子节点（包括嵌套的子节点）
     */
    private void getAllChildren(TreeItem<Path> parent, List<TreeItem<Path>> result) {
        if (parent == null) {
            return;
        }

        for (TreeItem<Path> child : parent.getChildren()) {
            result.add(child);
            // 如果子节点是目录，递归获取其子节点
            if (child.getValue() != null && Files.isDirectory(child.getValue())) {
                getAllChildren(child, result);
            }
        }
    }

    private void refreshNode(FileTreeItem item) {
        item.setExpanded(false);
        item.getChildren().clear();
        item.setExpanded(true);
    }

    private void findAndSelectPath(TreeItem<Path> startNode, Path targetPath) {
        if (startNode.getValue().equals(targetPath)) {
            folderTreeView.getSelectionModel().select(startNode);
            return;
        }

        for (TreeItem<Path> child : startNode.getChildren()) {
            if (child.getValue().equals(targetPath)) {
                folderTreeView.getSelectionModel().select(child);
                return;
            }
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
            Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            alertStage.getIcons().add(ApplicationProperties.APP_ICON);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showSuccessAlert(String title, String message) {
        // 确保在 JavaFX 线程中执行 UI 更新
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(Alert.AlertType.INFORMATION);
            Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            alertStage.getIcons().add(ApplicationProperties.APP_ICON);
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

        // 初始化TreeView
        initializeTreeView();

        // 初始化loading
        initLoadingIndicator();

        // 添加快捷键
        addKeyboardShortcuts();
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

    private void initializeTreeView() {
        // 设置TreeView的单元格工厂，用于自定义显示
        folderTreeView.setCellFactory(tv -> new TreeCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // 显示文件名
                    setText(item.getFileName().toString());
                }
            }
        });

        // 监听选择事件，当选择图片时在ImageView中显示
        folderTreeView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null && newValue.getValue() != null) {
                        if (restoringSelection) {
                            return;
                        }
                        Path selectedPath = newValue.getValue();
                        java.io.File selectedFile = selectedPath.toFile();

                        // 如果是图片文件，在ZoomableImageView中显示
                        if (selectedFile.isFile() && isImageFile(selectedFile)) {
                            if (imageLoadTask != null && imageLoadTask.isRunning()) {
                                restoringSelection = true;
                                Platform.runLater(() -> {
                                    folderTreeView.getSelectionModel().select(oldValue);
                                    restoringSelection = false;
                                });
                                return;
                            }
                            showLoading();
                            imageLoadTask = createSimpleLoadTask(selectedPath);

                            new Thread(imageLoadTask, "ImageLoadThread").start();
                        }
                    }
                }
        );
    }

    private Task<Image> createSimpleLoadTask(Path selectedPath) {
        return new Task<>() {
            @Override
            protected Image call() throws Exception {
                BufferedImage bufferedImage =
                        opencvRead(selectedPath.toAbsolutePath().toString());

                if (bufferedImage == null) {
                    throw new RuntimeException("读取图片失败");
                }

                return SwingFXUtils.toFXImage(bufferedImage, null);
            }

            @Override
            protected void succeeded() {
                Image fxImage = getValue();

                ImageViewDTO dto = new ImageViewDTO(
                        fxImage,
                        selectedPath.toAbsolutePath().toString()
                );

                List<ImageViewDTO> images = new ArrayList<>();
                images.add(dto);

                zoomableView.init(images);
                resetView();

                hideLoading();
                imageLoadTask = null;
            }

            @Override
            protected void failed() {
                Throwable e = getException();
                log.error("加载图片失败", e);

                showErrorAlert(
                        "加载图片失败",
                        e != null ? e.getMessage() : "未知错误"
                );

                hideLoading();
                imageLoadTask = null;
            }
        };
    }

    private boolean isImageFile(java.io.File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".tif") ||
                name.endsWith(".bmp") || name.endsWith(".tiff");
    }

    private void initLoadingIndicator() {
        progressIndicator = new ProgressIndicator();
        progressIndicator.setProgress(-1); // 无限旋转
        progressIndicator.setVisible(false);

        loadingPane = new StackPane();
        loadingPane.setStyle("-fx-background-color: rgba(255, 255, 255, 0.7);");
        loadingPane.getChildren().add(progressIndicator);
        loadingPane.setVisible(false);

        // 将 loadingPane 添加到 stackPane（假设 stackPane 是主容器）
        StackPane.setAlignment(progressIndicator, Pos.CENTER);
        stackPane.getChildren().add(loadingPane);
    }

    /**
     * 快捷键
     */
    private void addKeyboardShortcuts() {
        // 添加快捷键（initialize通常只调用一次）
        Platform.runLater(() -> {
            Scene scene = stackPane.getScene();
            if (scene != null) {
                // 直接添加，因为每次重新加载FXML都会创建新的控制器和场景
                scene.setOnKeyPressed(event -> {
                    // 只有当焦点不在文本输入控件时才响应
                    Node focusOwner = scene.getFocusOwner();
                    boolean isTextInput = focusOwner instanceof javafx.scene.control.TextInputControl;

                    if (!isTextInput) {
                        switch (event.getCode()) {
                            case LEFT:
                                handlePreviousImage();
                                event.consume();
                                break;
                            case RIGHT:
                                handleNextImage();
                                event.consume();
                                break;
                        }
                    }
                });
            }
        });
    }

    // 显示加载动画的方法
    private void showLoading() {
        Platform.runLater(() -> {
            loadingPane.setVisible(true);
            progressIndicator.setVisible(true);

            // 确保 loadingPane 覆盖整个 stackPane
            loadingPane.prefWidthProperty().bind(stackPane.widthProperty());
            loadingPane.prefHeightProperty().bind(stackPane.heightProperty());
            loadingPane.toFront();
        });
    }

    // 隐藏加载动画的方法
    private void hideLoading() {
        Platform.runLater(() -> {
            loadingPane.setVisible(false);
            progressIndicator.setVisible(false);
        });
    }

    public BufferedImage opencvRead(String path) {
        try {
            byte[] data = Files.readAllBytes(Paths.get(path));
            if (data.length == 0) return null;

            // Create Mat that wraps raw bytes (no copy)
            Mat encoded = new Mat(1, data.length, opencv_core.CV_8UC1);
            encoded.data().put(data);

            // Decode image (still no disk I/O)
            Mat image = opencv_imgcodecs.imdecode(encoded, opencv_imgcodecs.IMREAD_UNCHANGED);
            encoded.close(); // release early

            if (image == null || image.empty()) {
                image.close();
                return null;
            }

            int width = image.cols();
            int height = image.rows();
            int channels = image.channels();

            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] pixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

            UByteIndexer idx = image.createIndexer();

            if (channels == 4) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int b = idx.get(y, x, 0) & 0xFF;
                        int g = idx.get(y, x, 1) & 0xFF;
                        int r = idx.get(y, x, 2) & 0xFF;
                        int a = idx.get(y, x, 3) & 0xFF;
                        pixels[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            } else if (channels == 3) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int b = idx.get(y, x, 0) & 0xFF;
                        int g = idx.get(y, x, 1) & 0xFF;
                        int r = idx.get(y, x, 2) & 0xFF;
                        pixels[y * width + x] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    }
                }
            } else {
                // grayscale
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int gray = idx.get(y, x) & 0xFF;
                        pixels[y * width + x] = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
                    }
                }
            }

            idx.close();
            image.close();
            return result;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
