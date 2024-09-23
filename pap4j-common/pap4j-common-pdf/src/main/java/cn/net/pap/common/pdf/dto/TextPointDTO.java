package cn.net.pap.common.pdf.dto;

import java.awt.*;
import java.io.Serializable;

/**
 * 字体区域信息
 */
public class TextPointDTO implements Serializable {

    /**
     * 文本
     */
    private String text;

    /**
     * 矩形区域坐标
     */
    private Dimension characterBounds;

    /**
     * x轴
     */
    private Float x;

    /**
     * y轴
     */
    private Float y;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Dimension getCharacterBounds() {
        return characterBounds;
    }

    public void setCharacterBounds(Dimension characterBounds) {
        this.characterBounds = characterBounds;
    }

    public Float getX() {
        return x;
    }

    public void setX(Float x) {
        this.x = x;
    }

    public Float getY() {
        return y;
    }

    public void setY(Float y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return "TextPointDTO{" +
                "text='" + text + '\'' +
                ", characterBounds=" + characterBounds +
                ", x=" + x +
                ", y=" + y +
                '}';
    }
}
