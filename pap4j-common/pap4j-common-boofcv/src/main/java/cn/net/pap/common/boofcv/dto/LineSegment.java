package cn.net.pap.common.boofcv.dto;

import java.awt.geom.Point2D;
import java.io.Serializable;
import java.util.StringJoiner;

/**
 * 直线对象，记录直线的起止点
 */
public class LineSegment implements Serializable {

    /**
     * 直线开始点
     */
    private Point2D.Double start;

    /**
     * 直线终止点
     */
    private Point2D.Double end;

    /**
     * 构造函数
     *
     * @param start
     * @param end
     */
    public LineSegment(Point2D.Double start, Point2D.Double end) {
        this.start = start;
        this.end = end;
    }

    public Point2D.Double getStart() {
        return start;
    }

    public void setStart(Point2D.Double start) {
        this.start = start;
    }

    public Point2D.Double getEnd() {
        return end;
    }

    public void setEnd(Point2D.Double end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "[", "]")
                .add("startX=" + start.getX())
                .add("startY=" + start.getY())
                .add("endX=" + end.getX())
                .add("endY=" + end.getY())
                .toString();
    }
}
