package cn.net.pap.common.pdf.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 点 DTO
 */
public class PointDTO implements Serializable {

    /**
     * x
     */
    private float x;

    /**
     * y
     */
    private float y;

    /**
     * 构造函数
     * @param x
     * @param y
     */
    public PointDTO(float x, float y) {
        this.x = x;
        this.y = y;
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

    /**
     * 数据类型转换
     * @param coords  一个8位长度的集合，两两一组，分别对应 左下、右下、右上、左上 四个坐标点  [1744, 324, 2241, 324, 2241, 484, 1744, 484]
     * @return
     */
    public static PointDTO[] convert2RectangleBy4Point(List<Integer> coords) {
        if(coords == null || coords.size() != 8) {
            return null;
        }
        PointDTO[] returnPointDTO = new PointDTO[4];
        returnPointDTO[0] = new PointDTO(coords.get(0), coords.get(1));
        returnPointDTO[1] = new PointDTO(coords.get(2), coords.get(3));
        returnPointDTO[2] = new PointDTO(coords.get(4), coords.get(5));
        returnPointDTO[3] = new PointDTO(coords.get(6), coords.get(7));
        return returnPointDTO;
    }

}
