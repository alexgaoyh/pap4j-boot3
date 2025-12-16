package cn.net.pap.example.javafx.view;

import cn.net.pap.example.javafx.constant.JavaFxConstant;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ZoomableImageView extends StackPane {

    private final ImageView imageView = new ImageView();
    private final Pane selectionPane = new Pane(); // 用于放置选择框和控制点
    private Rectangle selectionRect; // 选择框
    private Rectangle mask; // 遮罩层
    private List<Circle> controlPoints = new ArrayList<>(); // 8个控制点
    private Circle draggedControlPoint = null; // 当前被拖拽的控制点

    private List<Image> imageList;

    private int currentIndex = 0;

    private final DoubleProperty scaleFactor = new SimpleDoubleProperty(1.0);

    private double dragStartX, dragStartY;
    private double imageStartX, imageStartY;

    // 矩形选择相关变量
    private double rectStartX, rectStartY;
    // 记录拖拽开始时的宽高
    private double rectStartWidth, rectStartHeight;
    private boolean isSelecting = false;
    private boolean ctrlPressed = false;

    public ZoomableImageView() {
        String desktop = System.getProperty("user.home") + File.separator + "Desktop";
        Image image = new Image(JavaFxConstant.FILE_PROTOCOL2 + desktop + File.separator + "alexgaoyh.jpg");
        List<Image> images = new ArrayList<Image>();
        images.add(image);

        init(images);
    }

    public ZoomableImageView(List<Image> images) {
        init(images);
    }

    public void init(List<Image> images) {
        this.imageList = images;
        if (images != null && !images.isEmpty()) {
            imageView.setImage(images.get(0));
        }

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setCache(true);

        // 初始化选择框和遮罩
        initSelectionTools();

        // 将图像视图和选择面板添加到堆栈面板
        getChildren().addAll(imageView, selectionPane);

        initEvents();

        // 确保组件可以获得焦点
        setFocusTraversable(true);
    }

    private void initSelectionTools() {
        // 创建遮罩层
        mask = new Rectangle();
        mask.setFill(Color.rgb(0, 0, 0, 0.3)); // 半透明黑色
        mask.setMouseTransparent(true); // 允许鼠标事件穿透
        mask.setVisible(false);

        // 创建选择框
        selectionRect = new Rectangle();
        selectionRect.setFill(Color.TRANSPARENT);
        selectionRect.setStroke(Color.RED);
        selectionRect.setStrokeWidth(2);
        selectionRect.setStrokeType(StrokeType.INSIDE);
        selectionRect.setVisible(false);

        // 创建8个控制点
        for (int i = 0; i < 8; i++) {
            Circle controlPoint = new Circle(6);
            controlPoint.setFill(Color.WHITE);
            controlPoint.setStroke(Color.RED);
            controlPoint.setStrokeWidth(2);
            controlPoint.setVisible(false);
            controlPoint.setCursor(getCursorForControlPoint(i));

            // 设置拖拽事件
            setupControlPointEvents(controlPoint, i);

            controlPoints.add(controlPoint);
        }

        // 添加到选择面板
        selectionPane.getChildren().addAll(mask, selectionRect);
        selectionPane.getChildren().addAll(controlPoints);
    }

    private Cursor getCursorForControlPoint(int index) {
        switch (index) {
            case 0: return Cursor.NW_RESIZE; // 左上角
            case 1: return Cursor.N_RESIZE;  // 上中点
            case 2: return Cursor.NE_RESIZE; // 右上角
            case 3: return Cursor.E_RESIZE;  // 右中点
            case 4: return Cursor.SE_RESIZE; // 右下角
            case 5: return Cursor.S_RESIZE;  // 下中点
            case 6: return Cursor.SW_RESIZE; // 左下角
            case 7: return Cursor.W_RESIZE;  // 左中点
            default: return Cursor.DEFAULT;
        }
    }

    private void setupControlPointEvents(Circle controlPoint, int index) {
        controlPoint.setOnMousePressed(e -> {
            draggedControlPoint = controlPoint;
            // 记录起始状态（坐标和尺寸）
            rectStartX = selectionRect.getX();
            rectStartY = selectionRect.getY();
            rectStartWidth = selectionRect.getWidth();   // 【关键修改】记录初始宽
            rectStartHeight = selectionRect.getHeight(); // 【关键修改】记录初始高

            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            e.consume();
        });

        controlPoint.setOnMouseDragged(e -> {
            if (draggedControlPoint == controlPoint) {
                double dx = e.getSceneX() - dragStartX;
                double dy = e.getSceneY() - dragStartY;

                double newX = rectStartX;
                double newY = rectStartY;
                // 基于初始尺寸计算，而不是 selectionRect.getWidth()
                double newWidth = rectStartWidth;
                double newHeight = rectStartHeight;

                // 根据控制点的位置调整矩形
                switch (index) {
                    case 0: // 左上角 (NW)
                        newX = rectStartX + dx;
                        newY = rectStartY + dy;
                        newWidth = rectStartWidth - dx;
                        newHeight = rectStartHeight - dy;
                        break;
                    case 1: // 上中点 (N)
                        newY = rectStartY + dy;
                        newHeight = rectStartHeight - dy;
                        break;
                    case 2: // 右上角 (NE)
                        newY = rectStartY + dy;
                        newWidth = rectStartWidth + dx;
                        newHeight = rectStartHeight - dy;
                        break;
                    case 3: // 右中点 (E)
                        newWidth = rectStartWidth + dx;
                        break;
                    case 4: // 右下角 (SE)
                        newWidth = rectStartWidth + dx;
                        newHeight = rectStartHeight + dy;
                        break;
                    case 5: // 下中点 (S)
                        newHeight = rectStartHeight + dy;
                        break;
                    case 6: // 左下角 (SW)
                        newX = rectStartX + dx;
                        newWidth = rectStartWidth - dx;
                        newHeight = rectStartHeight + dy;
                        break;
                    case 7: // 左中点 (W)
                        newX = rectStartX + dx;
                        newWidth = rectStartWidth - dx;
                        break;
                }

                // 限制最小尺寸，防止翻转
                if (newWidth < 10) {
                    // 如果宽度到达极小值，需要根据拉伸方向修正 X 坐标，
                    // 但为了逻辑简单，这里仅锁定宽度，实际商用项目通常允许翻转(flip)
                    newWidth = 10;
                    // 如果是左侧拖拽(0,6,7)，当限制宽度时，X不能再变了
                    if(index == 0 || index == 6 || index == 7) {
                        newX = rectStartX + rectStartWidth - 10;
                    }
                }

                if (newHeight < 10) {
                    newHeight = 10;
                    // 如果是上方拖拽(0,1,2)，当限制高度时，Y不能再变了
                    if(index == 0 || index == 1 || index == 2) {
                        newY = rectStartY + rectStartHeight - 10;
                    }
                }

                // 更新选择框
                selectionRect.setX(newX);
                selectionRect.setY(newY);
                selectionRect.setWidth(newWidth);
                selectionRect.setHeight(newHeight);

                updateMaskAndControlPoints();
                e.consume();
            }
        });

        controlPoint.setOnMouseReleased(e -> {
            draggedControlPoint = null;
            e.consume();
        });
    }

    private void updateMaskAndControlPoints() {
        // 更新遮罩层
        Bounds bounds = getBoundsInLocal();
        mask.setWidth(bounds.getWidth());
        mask.setHeight(bounds.getHeight());

        // 创建挖空效果
        mask.setClip(new Rectangle(
                selectionRect.getX() - 1,
                selectionRect.getY() - 1,
                selectionRect.getWidth() + 2,
                selectionRect.getHeight() + 2
        ));

        // 更新控制点位置
        double x = selectionRect.getX();
        double y = selectionRect.getY();
        double width = selectionRect.getWidth();
        double height = selectionRect.getHeight();

        // 左上角
        controlPoints.get(0).setCenterX(x);
        controlPoints.get(0).setCenterY(y);
        // 上中点
        controlPoints.get(1).setCenterX(x + width / 2);
        controlPoints.get(1).setCenterY(y);
        // 右上角
        controlPoints.get(2).setCenterX(x + width);
        controlPoints.get(2).setCenterY(y);
        // 右中点
        controlPoints.get(3).setCenterX(x + width);
        controlPoints.get(3).setCenterY(y + height / 2);
        // 右下角
        controlPoints.get(4).setCenterX(x + width);
        controlPoints.get(4).setCenterY(y + height);
        // 下中点
        controlPoints.get(5).setCenterX(x + width / 2);
        controlPoints.get(5).setCenterY(y + height);
        // 左下角
        controlPoints.get(6).setCenterX(x);
        controlPoints.get(6).setCenterY(y + height);
        // 左中点
        controlPoints.get(7).setCenterX(x);
        controlPoints.get(7).setCenterY(y + height / 2);
    }

    private void initEvents() {
        // 滚轮缩放
        addEventFilter(ScrollEvent.SCROLL, e -> {
            if (imageView.getImage() == null) return;
            double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;

            // 当前缩放因子
            double baseScale = scaleFactor.get();
            double newScale = baseScale * delta;

            // 限制缩放范围
            newScale = Math.max(0.1, Math.min(newScale, 10));

            Image img = imageView.getImage();

            // 使用 fitWidth / fitHeight 缩放
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(img.getWidth() * newScale);
            imageView.setFitHeight(img.getHeight() * newScale);

            // 更新缩放系数
            updateScale(newScale);

            e.consume();
        });

        // 拖拽（按下鼠标）
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            // 鼠标按下时请求焦点
            requestFocus();

            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            imageStartX = imageView.getTranslateX();
            imageStartY = imageView.getTranslateY();

            // 如果按下Ctrl键且不在控制点上，开始框选
            if (ctrlPressed && draggedControlPoint == null && !isSelecting) {
                isSelecting = true;
                rectStartX = e.getX();
                rectStartY = e.getY();

                // 初始化选择框
                selectionRect.setX(rectStartX);
                selectionRect.setY(rectStartY);
                selectionRect.setWidth(0);
                selectionRect.setHeight(0);
                selectionRect.setVisible(true);
                mask.setVisible(true);

                e.consume();
            }
        });

        // 拖拽平移（拖动中）
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (imageView.getImage() == null) return;

            if (isSelecting) {
                // 绘制选择框
                double currentX = e.getX();
                double currentY = e.getY();

                double x = Math.min(rectStartX, currentX);
                double y = Math.min(rectStartY, currentY);
                double width = Math.abs(currentX - rectStartX);
                double height = Math.abs(currentY - rectStartY);

                selectionRect.setX(x);
                selectionRect.setY(y);
                selectionRect.setWidth(width);
                selectionRect.setHeight(height);

                updateMaskAndControlPoints();
            } else if (draggedControlPoint == null) {
                // 平移图像
                double dx = e.getSceneX() - dragStartX;
                double dy = e.getSceneY() - dragStartY;
                imageView.setTranslateX(imageStartX + dx);
                imageView.setTranslateY(imageStartY + dy);
            }
        });

        // 鼠标释放
        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (isSelecting) {
                isSelecting = false;

                // 显示控制点
                for (Circle cp : controlPoints) {
                    cp.setVisible(true);
                }
                updateMaskAndControlPoints();
            }
        });

        // 键盘事件过滤器 - 监听Ctrl键
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.CONTROL) {
                ctrlPressed = true;
                if (!isSelecting && selectionRect.isVisible()) {
                    // 如果已有选择框，设置鼠标样式
                    setCursor(Cursor.CROSSHAIR);
                }
            }
        });

        addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == KeyCode.CONTROL) {
                ctrlPressed = false;
                if (!isSelecting) {
                    setCursor(Cursor.DEFAULT);
                }
            }
        });

        // 点击空白处取消选择
        addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (!ctrlPressed && selectionRect.isVisible() && draggedControlPoint == null) {
                // 检查是否点击在控制点或选择框上
                boolean clickedOnSelection = false;
                for (Circle cp : controlPoints) {
                    if (cp.contains(e.getX(), e.getY())) {
                        clickedOnSelection = true;
                        break;
                    }
                }

                if (!clickedOnSelection && !selectionRect.contains(e.getX(), e.getY())) {
                    // 隐藏选择框
                    clearSelection();
                }
            }
        });

        // 鼠标进入时设置光标
        setOnMouseEntered(e -> {
            if (ctrlPressed) {
                setCursor(Cursor.CROSSHAIR);
            }
        });

        // 鼠标退出时恢复默认光标
        setOnMouseExited(e -> {
            if (!isSelecting) {
                setCursor(Cursor.DEFAULT);
            }
        });
    }

    /**
     * 自适应填充
     **/
    public void fitImage(double width, double height) {
        if (imageView.getImage() == null) {
            return;
        }

        double viewportWidth = width;
        double viewportHeight = height;

        Image img = imageView.getImage();

        double scaleX = viewportWidth / img.getWidth();
        double scaleY = viewportHeight / img.getHeight();

        double scaleFactor = Math.min(scaleX, scaleY);

        // 设置适应大小
        imageView.setFitWidth(img.getWidth() * scaleFactor);
        imageView.setFitHeight(img.getHeight() * scaleFactor);

        imageView.setTranslateX(0);
        imageView.setTranslateY(0);

        updateScale(scaleFactor);
    }

    /**
     * 下一张
     **/
    public void nextImage() {
        if (imageList == null || imageList.isEmpty()) return;
        currentIndex = (currentIndex + 1) % imageList.size();
        imageView.setImage(imageList.get(currentIndex));
        // 切换图像时清除选择框
        clearSelection();
    }

    /**
     * 上一张
     **/
    public void previousImage() {
        if (imageList == null || imageList.isEmpty()) return;
        currentIndex = (currentIndex - 1 + imageList.size()) % imageList.size();
        imageView.setImage(imageList.get(currentIndex));
        // 切换图像时清除选择框
        clearSelection();
    }

    /**
     * 清除选择框
     **/
    public void clearSelection() {
        // 选择框
        Rectangle2D selectionInImageCoordinates = getSelectionInImageCoordinates();
        if(selectionInImageCoordinates != null) {
            System.out.println(selectionInImageCoordinates.toString());
        }

        selectionRect.setVisible(false);
        mask.setVisible(false);
        for (Circle cp : controlPoints) {
            cp.setVisible(false);
        }
        isSelecting = false;
        setCursor(Cursor.DEFAULT);
    }

    /**
     * 获取当前选择框的坐标和尺寸（相对于图像视图）
     **/
    public Rectangle getSelectionRect() {
        return selectionRect;
    }

    /**
     * 获取当前选择框在图像原始坐标中的位置 (Rectangle2D)
     **/
    public javafx.geometry.Rectangle2D getSelectionInImageCoordinates() {
        if (!selectionRect.isVisible() || imageView.getImage() == null) {
            return null;
        }

        Image img = imageView.getImage();

        // 1. 获取 ImageView 在 StackPane 中的【实际渲染边界】
        // getBoundsInParent() 包含了 StackPane 的居中布局、图片 Fit 的尺寸和拖拽 (Translate) 偏移
        Bounds imageBounds = imageView.getBoundsInParent();

        // 2. 计算实际缩放比例 (基于渲染尺寸)
        double actualScaleX = imageBounds.getWidth() / img.getWidth();
        double actualScaleY = imageBounds.getHeight() / img.getHeight();

        // 如果图片是 PreserveRatio=true，则 X 和 Y 比例应该相等
        double actualScale = Math.min(actualScaleX, actualScaleY);

        // 3. 计算相对坐标 (StackPane坐标 -> 图片原始像素坐标)

        // selectionRect.getX() 是选框左上角相对于 StackPane 左上角的距离。
        // imageBounds.getMinX() 是图片左上角相对于 StackPane 左上角的距离（包含居中+拖拽）。

        // 选框相对于图片左上角的距离（在 StackPane 坐标系下）
        double relativeX = selectionRect.getX() - imageBounds.getMinX();
        double relativeY = selectionRect.getY() - imageBounds.getMinY();

        // 4. 转换到原始图片像素坐标
        double finalX = relativeX / actualScale;
        double finalY = relativeY / actualScale;
        double finalWidth = selectionRect.getWidth() / actualScale;
        double finalHeight = selectionRect.getHeight() / actualScale;

        // 5. 边界修正（确保坐标在图片范围内）
        finalX = Math.max(0, finalX);
        finalY = Math.max(0, finalY);
        finalWidth = Math.min(finalWidth, img.getWidth() - finalX);
        finalHeight = Math.min(finalHeight, img.getHeight() - finalY);

        return new javafx.geometry.Rectangle2D(finalX, finalY, finalWidth, finalHeight);
    }

    public final void setScaleFactor(double scale) {
        this.scaleFactor.set(scale);
    }

    public final DoubleProperty scaleFactorProperty() {
        return scaleFactor;
    }

    // 当缩放变化时，你只需要：
    private void updateScale(double newScale) {
        setScaleFactor(newScale);
    }

    public ImageView getImageView() {
        return imageView;
    }

    /**
     * 重新加载当前图像并刷新 UI
     */
    public void reloadCurrentImage() {
        if (imageList == null || imageList.isEmpty() || currentIndex < 0 || currentIndex >= imageList.size()) {
            return;
        }

        // 获取当前 Image 的加载源 URL
        // 注意：要确保您的 imageList 中的 Image 对象有有效的 URL
        Image oldImage = imageList.get(currentIndex);
        String imageURL = oldImage.getUrl();

        if (imageURL == null || imageURL.isEmpty()) {
            // 如果无法获取URL (比如通过 Image(InputStream) 加载)，则使用最保守的刷新方法
            // 这种情况下，您可能需要重新加载文件内容。
            // 暂时跳过，我们假设您能够获取 URL
            System.err.println("Error: Cannot reload image without a valid URL.");
            return;
        }

        // 关键步骤：创建一个新的 Image 实例，并明确禁用缓存
        // JavaFX 看到这是一个新对象，会强制重新加载和渲染。
        Image newImage = new Image(imageURL, false); // 第二个参数设置为 false 表示禁用内部缓存

        // 1. 设置新的图像实例
        imageView.setImage(newImage);

        // 2. 清除平移，回到 StackPane 居中默认位置
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);

        // 3. 更新 imageList 中的引用（可选，取决于您是否希望 imageList 保持最新）
        imageList.set(currentIndex, newImage);

    }

}
