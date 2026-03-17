package cn.net.pap.common.boofcv;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import cn.net.pap.common.boofcv.dto.LineSegment;
import cn.net.pap.common.boofcv.dto.MarginDTO;
import georegression.struct.point.Point2D_I32;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canny 边缘检测，仿照 https://github.com/lessthanoptimal/BoofCV/blob/v1.1.4/examples/src/main/java/boofcv/examples/features/ExampleCannyEdge.java
 */
public class CannyEdgeUtilss {

    /**
     * 传入一张图像，获得四周的边框对应的 MarginDTO 对象，记录四个方向的距离。
     *
     * @param imgPath
     * @return
     */
    public static MarginDTO getBlackMargin(String imgPath) {
        BufferedImage image = UtilImageIO.loadImageNotNull(UtilIO.pathExample(imgPath));

        GrayU8 gray = ConvertBufferedImage.convertFrom(image, (GrayU8) null);
        GrayU8 edgeImage = gray.createSameShape();

        CannyEdge<GrayU8, GrayS16> canny = FactoryEdgeDetectors.canny(2, true, true, GrayU8.class, GrayS16.class);

        canny.process(gray, 0.1f, 0.3f, edgeImage);

        List<Contour> contours = BinaryImageOps.contourExternal(edgeImage, ConnectRule.EIGHT);

        // 获取轮廓点组成的线条路径集合
        Map<Double, LineSegment> lineSegmentDistanceMap = new TreeMap<>();
        List<Path2D> paths = getContourPaths(contours);
        for (Path2D path : paths) {
            List<LineSegment> lineSegments = getMergedLineSegments(path);
            // 打印每个线段的起始点和结束点
            for (LineSegment segment : lineSegments) {
                lineSegmentDistanceMap.put(calculateEuclideanDistance(segment.getStart().getX(), segment.getStart().getY(), segment.getEnd().getX(), segment.getEnd().getY()), segment);
            }
        }
        // 获取键的递减排序视图
        Map<Double, LineSegment> descendingMap = ((TreeMap<Double, LineSegment>) lineSegmentDistanceMap).descendingMap();
        // 四周的黑框区域
        MarginDTO marginDTOInLineSegmentMap = getMarginDTOInLineSegmentMap(descendingMap, image.getWidth(), image.getHeight(), 0.08);
        return marginDTOInLineSegmentMap;
    }

    /**
     * 将轮廓点转换为线条路径集合
     *
     * @param contours 轮廓点集合
     * @return 线条路径集合
     */
    private static List<Path2D> getContourPaths(List<Contour> contours) {
        List<Path2D> paths = new ArrayList<>();

        for (Contour contour : contours) {
            Path2D path = new Path2D.Double();
            List<Point2D_I32> points = contour.external;

            if (points.size() > 0) {
                Point2D_I32 startPoint = points.get(0);
                path.moveTo(startPoint.x, startPoint.y);

                for (int i = 1; i < points.size(); i++) {
                    Point2D_I32 point = points.get(i);
                    path.lineTo(point.x, point.y);
                }

                // Close the path if needed
                path.closePath();
            }

            paths.add(path);
        }

        return paths;
    }

    /**
     * 获取 Path2D 对象中的合并后的线段
     *
     * @param path Path2D 对象
     * @return 线段列表
     */
    private static List<LineSegment> getMergedLineSegments(Path2D path) {
        List<LineSegment> segments = new ArrayList<>();
        PathIterator iterator = path.getPathIterator(null);
        double[] coords = new double[6];
        Point2D.Double lastMoveTo = new Point2D.Double();
        Point2D.Double lastPoint = new Point2D.Double();
        Point2D.Double startPoint = null;
        Point2D.Double previousPoint = null;

        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    lastMoveTo.setLocation(coords[0], coords[1]);
                    lastPoint.setLocation(coords[0], coords[1]);
                    startPoint = new Point2D.Double(coords[0], coords[1]);
                    previousPoint = startPoint;
                    break;
                case PathIterator.SEG_LINETO:
                    Point2D.Double currentPoint = new Point2D.Double(coords[0], coords[1]);
                    if (previousPoint != null && startPoint != null) {
                        if (!isCollinear(startPoint, previousPoint, currentPoint)) {
                            segments.add(new LineSegment(startPoint, previousPoint));
                            startPoint = previousPoint;
                        }
                    }
                    previousPoint = currentPoint;
                    lastPoint.setLocation(coords[0], coords[1]);
                    break;
                case PathIterator.SEG_CLOSE:
                    if (startPoint != null && previousPoint != null) {
                        segments.add(new LineSegment(startPoint, lastMoveTo));
                    }
                    lastPoint.setLocation(lastMoveTo.x, lastMoveTo.y);
                    break;
            }
            iterator.next();
        }

        // 添加最后的线段
        if (startPoint != null && previousPoint != null && startPoint != previousPoint) {
            segments.add(new LineSegment(startPoint, previousPoint));
        }

        return segments;
    }

    /**
     * 计算两点之间的欧氏距离
     *
     * @param x1 第一点的x坐标
     * @param y1 第一点的y坐标
     * @param x2 第二点的x坐标
     * @param y2 第二点的y坐标
     * @return 两点之间的距离
     */
    private static double calculateEuclideanDistance(double x1, double y1, double x2, double y2) {
        double deltaX = x2 - x1;
        double deltaY = y2 - y1;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    /**
     * 检查三个点是否在同一条直线上
     *
     * @param p1 点1
     * @param p2 点2
     * @param p3 点3
     * @return 如果三个点共线则为 true，否则为 false
     */
    private static boolean isCollinear(Point2D.Double p1, Point2D.Double p2, Point2D.Double p3) {
        return (p3.y - p1.y) * (p2.x - p1.x) == (p2.y - p1.y) * (p3.x - p1.x);
    }

    /**
     * 计算四周黑框的数值
     * 注意在实际场景中，由于四周黑框的线条宽度的不同，有可能需要执行多次去黑边操作，或者执行过程中增加一些冗余.
     *
     * @param lineSegmentMap  使用 CannyEdge 获得的线条集合
     * @param originImgWidth  原图像的宽度
     * @param originImgHeight 原图像的高度
     * @param lineRatio       线条的长度占原图像宽高的比例，默认是个小数
     * @return
     */
    private static MarginDTO getMarginDTOInLineSegmentMap(Map<Double, LineSegment> lineSegmentMap,
                                                          Integer originImgWidth, Integer originImgHeight,
                                                          Double lineRatio) {

        List<Double> minXList = new ArrayList<>();
        List<Double> maxXList = new ArrayList<>();
        List<Double> minYList = new ArrayList<>();
        List<Double> maxYList = new ArrayList<>();

        for (Map.Entry<Double, LineSegment> lineSegmentEntry : lineSegmentMap.entrySet()) {
            double startX = lineSegmentEntry.getValue().getStart().getX();
            double startY = lineSegmentEntry.getValue().getStart().getY();
            double endX = lineSegmentEntry.getValue().getEnd().getX();
            double endY = lineSegmentEntry.getValue().getEnd().getY();

            if (lineSegmentEntry.getKey() > Math.min(originImgWidth, originImgHeight) * lineRatio) {
                if (checkOrientation(startX, startY, endX, endY).equals("vertical")) {
                    // 垂直线
                    if (startX < originImgWidth * 0.4 && endX < originImgWidth * 0.4) {
                        // 这里说明是左侧的垂直线
                        minXList.add(Math.max(startX, endX));
                    }
                    if (startX > originImgWidth * 0.6 && endX > originImgWidth * 0.6) {
                        // 这里说明是右侧的垂直线
                        maxXList.add(Math.min(startX, endX));
                    }

                }
                if (checkOrientation(startX, startY, endX, endY).equals("horizon")) {
                    // 水平线
                    if (startY < originImgHeight * 0.4 && endY < originImgHeight * 0.4) {
                        // 这里说明是下侧的水平线
                        minYList.add(Math.max(startY, endY));
                    }
                    if (startY > originImgHeight * 0.6 && endY > originImgHeight * 0.6) {
                        // 这里说明是上侧的水平线
                        maxYList.add(Math.min(startY, endY));
                        // 上册的水平线，最右侧的x坐标有可能是一条线的最右侧的点。这里可以将他添加进来。
                        maxXList.add(Math.max(startX, endX));
                    }
                }
            }
        }

        return new MarginDTO(originImgHeight - getMin(maxYList), originImgWidth - getMin(maxXList), getMax(minYList), getMax(minXList));

    }

    /**
     * 判断直线的方向 horizon-水平 vertical-垂直
     *
     * @param x1 起点的x坐标
     * @param y1 起点的y坐标
     * @param x2 终点的x坐标
     * @param y2 终点的y坐标
     * @return 直线的方向（水平、垂直或其他）
     */
    private static String checkOrientation(Double x1, Double y1, Double x2, Double y2) {
        Double dx = Math.abs(x2 - x1);
        Double dy = Math.abs(y2 - y1);
        if (dx != 0 && dy == 0) {
            return "horizon";
        }
        if (dx == 0 && dy != 0) {
            return "vertical";
        }

        Double max = Math.max(dx, dy);

        double ratio = (double) dx / max;

        if (ratio < 0.01) {
            return "vertical";
        } else if (1 - ratio < 0.01) {
            return "horizon";
        } else {
            return "unknown";
        }
    }

    /**
     * 集合中取最大值
     *
     * @param list
     * @return
     */
    private static Double getMax(List<Double> list) {
        double max = Double.MIN_VALUE;
        for (Double value : list) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    /**
     * 集合中取最小值
     *
     * @param list
     * @return
     */
    private static Double getMin(List<Double> list) {
        double min = Double.MAX_VALUE;
        for (Double value : list) {
            if (value < min) {
                min = value;
            }
        }
        return min;
    }

    /**
     * 计算两点间斜率
     *
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     * @return
     */
    public static double getAngleByPoint(Double x1, Double y1, Double x2, Double y2) {
        // 计算斜率
        double slope = (y2 - y1) / (x2 - x1);
        if (x2 - x1 == 0) {
            // 如果分母为零，直线垂直于x轴
            double angleInDegrees = (y2 > y1) ? 0 : 0;
            return angleInDegrees;
        } else {
            // 计算倾斜角度（以度为单位）
            double angleInDegrees = Math.atan(slope) * (180 / Math.PI);
            return angleInDegrees;
        }
    }
}
