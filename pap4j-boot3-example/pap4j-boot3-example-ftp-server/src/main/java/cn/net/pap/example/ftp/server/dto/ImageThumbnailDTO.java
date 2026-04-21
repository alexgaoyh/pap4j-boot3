package cn.net.pap.example.ftp.server.dto;

import java.io.InputStream;
import java.io.Serializable;

/**
 * <p>应用于 ImgSendCommand 包含图像流和图像宽高。</p>
 */
public class ImageThumbnailDTO implements Serializable {

    private InputStream inputStream;

    private Integer width;

    private Integer height;

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

}
