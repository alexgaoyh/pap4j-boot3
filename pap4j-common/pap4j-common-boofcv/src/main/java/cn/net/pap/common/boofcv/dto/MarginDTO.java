package cn.net.pap.common.boofcv.dto;

import java.io.Serializable;
import java.util.StringJoiner;

/**
 * 四个方向的外边距
 * margin-top    margin-right    margin-bottom    margin-left，即“上-右-下-左”
 */
public class MarginDTO implements Serializable {

    /**
     * 上边距
     */
    private Double marginTop;

    /**
     * 右边距
     */
    private Double marginRight;

    /**
     * 下边距
     */
    private Double marginBottom;

    /**
     * 左边距
     */
    private Double marginLeft;

    /**
     * 构造函数
     */
    public MarginDTO() {
    }

    /**
     * 构造函数
     *
     * @param marginTop
     * @param marginRight
     * @param marginBottom
     * @param marginLeft
     */
    public MarginDTO(Double marginTop, Double marginRight, Double marginBottom, Double marginLeft) {
        this.marginTop = marginTop;
        this.marginRight = marginRight;
        this.marginBottom = marginBottom;
        this.marginLeft = marginLeft;
    }

    public Double getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(Double marginTop) {
        this.marginTop = marginTop;
    }

    public Double getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(Double marginRight) {
        this.marginRight = marginRight;
    }

    public Double getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(Double marginBottom) {
        this.marginBottom = marginBottom;
    }

    public Double getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(Double marginLeft) {
        this.marginLeft = marginLeft;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MarginDTO.class.getSimpleName() + "[", "]")
                .add("marginTop=" + marginTop)
                .add("marginRight=" + marginRight)
                .add("marginBottom=" + marginBottom)
                .add("marginLeft=" + marginLeft)
                .toString();
    }
}
