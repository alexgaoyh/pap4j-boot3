package cn.net.pap.example.javafx.view;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
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

    private double scale = 1.0;

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

        // 当容器大小变化时自动适配
        widthProperty().addListener((obs, oldV, newV) -> fitImage());
        heightProperty().addListener((obs, oldV, newV) -> fitImage());
    }

    private void initEvents() {
        // 滚轮缩放
        addEventFilter(ScrollEvent.SCROLL, e -> {
            if (imageView.getImage() == null) return;
            double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
            scale *= delta;
            scale = Math.max(0.2, Math.min(scale, 10)); // 限制缩放范围
            updateScale(scale);
            imageView.setScaleX(scale);
            imageView.setScaleY(scale);
            e.consume();
        });

        // 拖拽
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            imageStartX = imageView.getTranslateX();
            imageStartY = imageView.getTranslateY();
        });

        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (imageView.getImage() == null) return;
            double dx = e.getSceneX() - dragStartX;
            double dy = e.getSceneY() - dragStartY;
            imageView.setTranslateX(imageStartX + dx);
            imageView.setTranslateY(imageStartY + dy);
        });

        // 双击恢复
        addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2) resetView();
        });
    }

    /**
     * 自适应填充
     **/
    private void fitImage() {
        if (imageView.getImage() == null) return;

        double boxWidth = getWidth();
        double boxHeight = getHeight();

        Image img = imageView.getImage();
        double imgRatio = img.getWidth() / img.getHeight();
        double boxRatio = boxWidth / boxHeight;

        System.out.println(img.getWidth() + " " + img.getHeight() + " " + boxWidth + " " + boxHeight);

        if (imgRatio > boxRatio) {
            imageView.setFitWidth(boxWidth);
            imageView.setFitHeight(boxWidth / imgRatio);
        } else {
            imageView.setFitHeight(boxHeight);
            imageView.setFitWidth(boxHeight * imgRatio);
        }

        resetView();
    }

    /**
     * 恢复原图（缩放=1，居中）
     **/
    public void resetView() {
        scale = 1.0;
        updateScale(scale);
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);

        // 居中对齐
        Bounds bounds = imageView.getBoundsInParent();
        imageView.setTranslateX((getWidth() - bounds.getWidth()) / 2);
        imageView.setTranslateY((getHeight() - bounds.getHeight()) / 2);
    }

    /**
     * 下一张
     **/
    public void nextImage() {
        if (imageList == null || imageList.isEmpty()) return;
        currentIndex = (currentIndex + 1) % imageList.size();
        imageView.setImage(imageList.get(currentIndex));
        fitImage();
    }

    /**
     * 上一张
     **/
    public void previousImage() {
        if (imageList == null || imageList.isEmpty()) return;
        currentIndex = (currentIndex - 1 + imageList.size()) % imageList.size();
        imageView.setImage(imageList.get(currentIndex));
        fitImage();
    }

    public final double getScaleFactor() {
        return scaleFactor.get();
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

    public int getCurrentIndex() {
        return currentIndex;
    }

}
