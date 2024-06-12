package cn.net.pap.common.pdf.dto;

import java.io.Serializable;

/**
 * 坐标与文本对象
 */
public class CoordsDTO implements Serializable {

    /**
     * x
     */
    private float x;

    /**
     * y
     */
    private float y;

    /**
     * width
     */
    private float width;

    /**
     * height
     */
    private float height;

    /**
     * text
     */
    private String text;

    /**
     * 构造函数
     * @param x
     * @param y
     * @param width
     * @param height
     * @param text
     */
    public CoordsDTO(float x, float y, float width, float height, String text) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
