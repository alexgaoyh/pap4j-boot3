package cn.net.pap.common.boofcv.dto;

import boofcv.struct.geo.AssociatedTriple;

import java.io.Serializable;

/**
 * 相似点集合
 */
public class AssociatedTripleDTO implements Serializable {

    /**
     * x 的差值
     */
    private Integer xDiffValue;

    /**
     * y 的差值
     */
    private Integer yDiffValue;

    /**
     * 相似点坐标
     */
    private AssociatedTriple associatedTriple;

    /**
     * 构造函数
     * @param xDiffValue
     * @param yDiffValue
     * @param associatedTriple
     */
    public AssociatedTripleDTO(Integer xDiffValue, Integer yDiffValue, AssociatedTriple associatedTriple) {
        this.xDiffValue = xDiffValue;
        this.yDiffValue = yDiffValue;
        this.associatedTriple = associatedTriple;
    }

    public Integer getxDiffValue() {
        return xDiffValue;
    }

    public void setxDiffValue(Integer xDiffValue) {
        this.xDiffValue = xDiffValue;
    }

    public Integer getyDiffValue() {
        return yDiffValue;
    }

    public void setyDiffValue(Integer yDiffValue) {
        this.yDiffValue = yDiffValue;
    }

    public AssociatedTriple getAssociatedTriple() {
        return associatedTriple;
    }

    public void setAssociatedTriple(AssociatedTriple associatedTriple) {
        this.associatedTriple = associatedTriple;
    }
}
