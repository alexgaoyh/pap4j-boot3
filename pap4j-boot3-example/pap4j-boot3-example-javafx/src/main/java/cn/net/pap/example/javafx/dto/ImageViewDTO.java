package cn.net.pap.example.javafx.dto;

import javafx.scene.image.Image;

import java.io.Serializable;

/**
 * 图像对象，含 image 和 imageAbsolutePath
 */
public class ImageViewDTO implements Serializable {

    /**
     * 图像信息
     */
    private final Image image;

    /**
     * 图像绝对路径
     */
    private final String imageAbsolutePath;

    public ImageViewDTO(Image image, String imageAbsolutePath) {
        this.image = image;
        this.imageAbsolutePath = imageAbsolutePath;
    }

    public Image getImage() {
        return image;
    }

    public String getImageAbsolutePath() {
        return imageAbsolutePath;
    }

}
