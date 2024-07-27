package cn.net.pap.common.datastructure.meanShift;

import java.io.Serializable;
import java.util.Map;

/**
 * 点信息
 */
public class PointX implements Serializable {

    /**
     * 矩形区域中心的 x 坐标
     */
    private double x;

    /**
     * 点信息，类似 ext 信息
     */
    private Map<String, Object> info;

    public PointX() {
    }

    public PointX(double x, Map<String, Object> info) {
        this.x = x;
        this.info = info;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public Map<String, Object> getInfo() {
        return info;
    }

    public void setInfo(Map<String, Object> info) {
        this.info = info;
    }
}
