package cn.net.pap.common.datastructure.meanShift;

import java.io.Serializable;
import java.util.Map;

/**
 * <p><strong>PointX</strong> 表示在 Mean Shift（均值漂移）等算法中使用的点实体。</p>
 *
 * <p>它包含主要坐标以及与该点关联的扩展信息的映射（Map）。</p>
 *
 * <ul>
 *     <li><strong>x：</strong> x 坐标值。</li>
 *     <li><strong>info：</strong> 扩展的上下文信息。</li>
 * </ul>
 */
public class PointX implements Serializable {

    /**
     * <p>表示一个区域中心或一个点的值的 x 坐标。</p>
     */
    private double x;

    /**
     * <p>映射到该点的附加信息，类似于扩展元数据。</p>
     */
    private Map<String, Object> info;

    /**
     * <p>默认构造函数。</p>
     */
    public PointX() {
    }

    /**
     * <p>使用指定的值构造一个新的 <strong>PointX</strong>。</p>
     *
     * @param x    x 坐标值。
     * @param info 与该点关联的元数据。
     */
    public PointX(double x, Map<String, Object> info) {
        this.x = x;
        this.info = info;
    }

    /**
     * <p>获取 x 坐标。</p>
     *
     * @return x 坐标。
     */
    public double getX() {
        return x;
    }

    /**
     * <p>设置 x 坐标。</p>
     *
     * @param x 新的 x 坐标。
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * <p>获取扩展信息映射。</p>
     *
     * @return 包含点元数据的映射。
     */
    public Map<String, Object> getInfo() {
        return info;
    }

    /**
     * <p>设置扩展信息映射。</p>
     *
     * @param info 包含点元数据的映射。
     */
    public void setInfo(Map<String, Object> info) {
        this.info = info;
    }
}