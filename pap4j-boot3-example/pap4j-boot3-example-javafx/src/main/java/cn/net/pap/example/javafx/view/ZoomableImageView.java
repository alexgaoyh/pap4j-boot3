package cn.net.pap.example.javafx.view;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

public class ZoomableImageView extends StackPane {

    private final ImageView imageView = new ImageView();

    private List<Image> imageList;

    private int currentIndex = 0;

    private final DoubleProperty scaleFactor = new SimpleDoubleProperty(1.0);

    private double dragStartX, dragStartY;
    private double imageStartX, imageStartY;

    public ZoomableImageView() {
        Image image = new Image(getClass().getResourceAsStream("/alexgaoyh.png"));
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

        getChildren().add(imageView);

        initEvents();

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
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            imageStartX = imageView.getTranslateX();
            imageStartY = imageView.getTranslateY();
        });

        // 拖拽平移（拖动中）
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (imageView.getImage() == null) return;
            double dx = e.getSceneX() - dragStartX;
            double dy = e.getSceneY() - dragStartY;
            imageView.setTranslateX(imageStartX + dx);
            imageView.setTranslateY(imageStartY + dy);
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
    }

    /**
     * 上一张
     **/
    public void previousImage() {
        if (imageList == null || imageList.isEmpty()) return;
        currentIndex = (currentIndex - 1 + imageList.size()) % imageList.size();
        imageView.setImage(imageList.get(currentIndex));
    }

    public final void setScaleFactor(double scale) {
        this.scaleFactor.set(scale);
    }

    public final DoubleProperty scaleFactorProperty() {
        return scaleFactor;
    }

    // 当缩放变化时，你只需要：
    private void updateScale(double newScale) {
        newScale = Math.round(newScale * 100.0) / 100.0;
        setScaleFactor(newScale);
    }

}
